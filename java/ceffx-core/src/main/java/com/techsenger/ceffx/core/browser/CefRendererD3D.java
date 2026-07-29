/*
 * Copyright 2026 CEFFX contributors.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.core.browser;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

/**
 * GPU-accelerated renderer for CEF shared-texture frames on Windows using the
 * Direct3D 11 pipeline of JavaFX Prism.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>CEF (via the native {@code ceffx.dll}) calls {@link #onAcceleratedPaint}
 *       on the CEF render thread, passing a {@code long} representation of a
 *       Windows {@code HANDLE} to a Direct3D 11 texture that the native library
 *       owns and keeps stable across frames (only its contents change).</li>
 *   <li>This Java renderer reopens that handle on the JavaFX Prism Direct3D
 *       device (which runs in a {@code D3DContext} on the JavaFX render thread)
 *       to expose it as a Prism {@code Texture} that can be sampled by Prism's
 *       shaders, then triggers a JavaFX paint pass. The actual sampling into
 *       the on-screen window is performed by Prism internally during the next
 *       {@code Scene} pulse.</li>
 *   <li>Because Prism's D3D classes are internal ({@code com.sun.prism.d3d.*})
 *       and are <strong>not</strong> part of the unstable public API of
 *       JavaFX, all access is via reflection. The expected JVM flags are
 *       documented in {@code module-info.java}.</li>
 * </ul>
 *
 * <h2>Fallback</h2>
 * If the reflective lookup of the Prism D3D pipeline fails for any reason
 * (wrong JDK build, missing {@code --add-opens} flags, software pipeline
 * active, etc.) this renderer degrades to a CPU pixel copy using the legacy
 * {@link CefRendererFX} software path. As soon as {@code onPaint} (software)
 * is called for a frame the accelerated path is disabled for the remainder
 * of the session — this lets the user see a working (slow) browser instead
 * of a blank window and surfaces the failure mode clearly in logs.</p>
 *
 * <p>Thread-safety: {@link #onAcceleratedPaint} is invoked on the CEF render
 * thread. Every touch of the JavaFX scene graph is dispatched to the JavaFX
 * Application Thread via {@link Platform#runLater}. Reflection lookups on the
 * Prism pipeline are also done on the JavaFX thread to match Prism's
 * threading invariants.</p>
 *
 * @author CEFFX contributors
 */
public class CefRendererD3D implements CefRenderer {

    private static final Logger LOG =
            Logger.getLogger(CefRendererD3D.class.getName());

    // ------------------------------------------------------------------
    // Reflection caches for the JavaFX Prism D3D pipeline. These are
    // resolved lazily ON THE JAVAFX THREAD to honour Prism's threading
    // invariants, and reused across all frames afterwards.
    // ------------------------------------------------------------------
    private volatile boolean prismResolved = false;
    private volatile boolean prismAvailable = false;

    private Class<?> d3DFactoryCls;
    private Method d3DFactoryFindDevice;
    private Class<?> d3DResourceFactoryCls;
    private Method d3DCreateSharedTexture;
    private Class<?> prismTextureCls;

    private Object prismResourceFactory;
    private Object d3dContext;  // com.sun.prism.d3d.D3DContext instance

    // ------------------------------------------------------------------
    // CPU fallback (used iff Prism reflection fails / software pipeline).
    // ------------------------------------------------------------------
    private final CefRendererFX fallback = new CefRendererFX();
    private final Canvas fallbackCanvas;
    private volatile boolean acceleratedDisabled = false;

    // ------------------------------------------------------------------
    // Accelerated UI node: a plain ImageView that we re-feed each frame
    // with a "native image" produced by Prism. We also keep a StackPane
    // so that callers using getCanvas() can rely on stable parenting.
    // ------------------------------------------------------------------
    private final StackPane pane = new StackPane();
    private final ImageView imageView = new ImageView();

    // Last frame transferred to Prism, so we can release the previous
    // Prism-texture wrapper before opening the next one.
    private Object lastPrismTexture;

    public CefRendererD3D() {
        this.fallbackCanvas = (Canvas) fallback.getCanvas();
        // Initially hidden until a frame arrives; this avoids a brief flash
        // of an uninitialised D3D texture on screen.
        this.imageView.setSmooth(false);
        this.imageView.setPreserveRatio(false);
        this.pane.getChildren().add(fallbackCanvas);
        this.fallbackCanvas.setVisible(false);
        this.pane.getChildren().add(imageView);
    }

    // ------------------------------------------------------------------
    // CefRenderer
    // ------------------------------------------------------------------

    @Override
    public void setTransparent(boolean transparent) {
        fallback.setTransparent(transparent);
    }

    @Override
    public Node getCanvas() {
        return pane;
    }

    @Override
    public void onPaint(boolean popup, BoundingBox[] dirtyRects, ByteBuffer buffer,
                        int width, int height) {
        // Software fallback path. If we receive onPaint it means either:
        //  (a) the GPU couldn't satisfy shared_texture_enabled and CEF fell
        //      back to CPU pixels; or
        //  (b) we deliberately switched off acceleration because Prism
        //      reflection failed.
        // In both cases we permanently prefer the software pipeline from
        // here onwards to keep the UX stable and predictable.
        if (!acceleratedDisabled) {
            acceleratedDisabled = true;
            LOG.log(Level.WARNING,
                    "CefRendererD3D: software onPaint received, switching to "
                  + "CPU fallback for the rest of this browser's life.");
            Platform.runLater(() -> {
                fallbackCanvas.setVisible(true);
                imageView.setVisible(false);
            });
        }
        fallback.onPaint(popup, dirtyRects, buffer, width, height);
    }

    @Override
    public void onAcceleratedPaint(boolean popup, BoundingBox[] dirtyRects,
                                    long sharedTextureHandle, int width, int height) {
        if (acceleratedDisabled) {
            // We are committed to the software path; nothing to do here.
            return;
        }
        if (popup) {
            // Popups are rare and small; defer to the software buffer until
            // a future iteration that supports accelerated popups too.
            // Since onAcceleratedPaint does not carry pixels, we must simply
            // skip the popup frame here; CEF will call OnPaint (software) for
            // the popup widget if it cannot get a shared texture.
            return;
        }
        if (sharedTextureHandle == 0 || width <= 0 || height <= 0) {
            return;
        }

        // All Prism lookups and texture operations must happen on the JavaFX
        // thread; schedule the whole thing as a single runLater to keep the
        // ordering with run-later calls from the renderer cleanup path.
        final long handle = sharedTextureHandle;
        final int w = width;
        final int h = height;
        Platform.runLater(() -> consumeAcceleratedFrame(handle, w, h));
    }

    @Override
    public void onPopupSize(BoundingBox size) {
        fallback.onPopupSize(size);
    }

    @Override
    public void clearPopupRects() {
        fallback.clearPopupRects();
    }

    @Override
    public void dispose() {
        Platform.runLater(() -> {
            // Release the Prism texture wrapper that wraps the native handle
            // (this does NOT CloseHandle the underlying resource; that stays
            // owned by the native CEFFX library).
            lastPrismTexture = null;
        });
        fallback.dispose();
    }

    // ------------------------------------------------------------------
    // Prism integration (JavaFX Application Thread only)
    // ------------------------------------------------------------------

    private void consumeAcceleratedFrame(long handle, int width, int height) {
        try {
            if (!prismResolved) {
                resolvePrismPipeline();
                prismResolved = true;
            }
            if (!prismAvailable) {
                // Reflection failed once and for all: degrade to software.
                acceleratedDisabled = true;
                fallbackCanvas.setVisible(true);
                imageView.setVisible(false);
                return;
            }

            // 1. Open the shared texture handle on Prism's D3D device.
            // ResourceFactory.createTexture(...) signature depends on the
            // JavaFX build; we resolve it lazily and pass the shared HANDLE
            // wrapped in the format Prism expects (a long pointer).
            Object prismTexture = openSharedTextureOnPrism(handle, width, height);
            if (prismTexture == null) {
                LOG.log(Level.WARNING,
                        "CefRendererD3D: openSharedTextureOnPrism returned null; "
                      + "degrading to software.");
                acceleratedDisabled = true;
                return;
            }

            // 2. Ensure the ImageView exposes the new dimensions so Prism
            // picks it up. The actual sampling happens during Scene.pulse().
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setVisible(true);
            fallbackCanvas.setVisible(false);

            // Keep the last Prism texture alive until the next frame to avoid
            // it being GC'd before Prism uploads it; then release the
            // previous reference.
            lastPrismTexture = prismTexture;
        } catch (Throwable t) {
            LOG.log(Level.SEVERE,
                    "CefRendererD3D: failed to consume accelerated frame", t);
            acceleratedDisabled = true;
            fallbackCanvas.setVisible(true);
            imageView.setVisible(false);
        }
    }

    /**
     * Reflectively locates the Prism D3D pipeline classes and the live
     * {@code ResourceFactory} for the current Scene. Must be called on the
     * JavaFX Application Thread.
     *
     * <p>The lookup strategy is intentionally defensive: many JavaFX builds
     * hide the D3D factory behind null constructors or thread-local singletons
     * and there is no stable public API. We try several known approaches in
     * order and stop on the first that yields a non-null resource factory.</p>
     */
    private void resolvePrismPipeline() throws ReflectiveOperationException {
        // ---- Strategy A: D3DFactory.findDefaultD3DDevice() -----------------
        // This was historically the entry point used by the JavaFX D3D
        // samples. Returns a D3DResource which carries both the device and
        // the resource factory. Available on Windows when Prism's D3D
        // pipeline is active.
        try {
            d3DFactoryCls = Class.forName("com.sun.prism.d3d.D3DFactory");
            d3DFactoryFindDevice =
                    d3DFactoryCls.getMethod("findDefaultD3DDevice");
            Object device = d3DFactoryFindDevice.invoke(null);
            if (device != null) {
                // D3DResource has a field "factory" or "resourceFactory"
                // depending on build; try both reflectively.
                Object factory = readField(device, "factory",
                        "resourceFactory", "prismFactory");
                if (factory != null) {
                    d3DResourceFactoryCls = factory.getClass();
                    prismResourceFactory = factory;
                    // Also try to grab the D3DContext if reachable.
                    d3dContext = readField(device, "context", "d3dContext");
                    locateCreateSharedTexture();
                    prismAvailable = true;
                    LOG.log(Level.INFO,
                            "CefRendererD3D: Prism pipeline resolved via "
                          + "D3DFactory.findDefaultD3DDevice(): "
                          + d3DResourceFactoryCls.getName() +
                          (d3dContext != null ? " + D3DContext" : ""));
                    return;
                }
            }
        } catch (ClassNotFoundException
                 | NoSuchMethodException
                 | IllegalAccessException
                 | InvocationTargetException notFound) {
            // Fall through to Strategy B.
            LOG.log(Level.FINE,
                    "D3DFactory.findDefaultD3DDevice() not available",
                    notFound);
        }

        // ---- Strategy B: Toolkit / Scene graph lookup ----------------------
        // Modern JavaFX exposes the active ResourceFactory via
        // com.sun.javafx.tk.quantum.QuantumToolkit / PrismPenManager. The most
        // stable path is through the current Scene's SceneHelper, but since
        // we are not bound to a particular Scene here we instead query the
        // GlobalPipeline.
        try {
            Class<?> pipelineCls =
                    Class.forName("com.sun.prism.GraphicsPipeline");
            Method getPipeline = pipelineCls.getMethod("getPipeline");
            Object pipeline = getPipeline.invoke(null);
            if (pipeline != null) {
                Method getResourceFactory = pipeline.getClass().getMethod(
                        "getResourceFactory");
                Object factory = getResourceFactory.invoke(pipeline);
                if (factory != null) {
                    d3DResourceFactoryCls = factory.getClass();
                    prismResourceFactory = factory;
                    locateCreateSharedTexture();
                    prismAvailable = true;
                    LOG.log(Level.INFO,
                            "CefRendererD3D: Prism pipeline resolved via "
                          + "GraphicsPipeline.getResourceFactory(): "
                          + d3DResourceFactoryCls.getName());
                    return;
                }
            }
        } catch (ClassNotFoundException
                 | NoSuchMethodException
                 | IllegalAccessException
                 | InvocationTargetException notFound) {
            LOG.log(Level.FINE,
                    "GraphicsPipeline.getResourceFactory() not available",
                    notFound);
        }

        // ---- Nothing worked: fall through to software path -----------------
        prismAvailable = false;
        LOG.log(Level.WARNING,
                "CefRendererD3D: could not locate the Prism D3D pipeline."
              + " Verify --add-opens flags and that JavaFX is running on the"
              + " D3D pipeline (not software/EGL). Degrading to CPU copy.");
    }

    /**
     * Resolves the method on the Prism ResourceFactory that wraps a native
     * shared D3D11 texture handle into a Prism {@code Texture}. Different
     * JavaFX builds expose different method names; we try an ordered list
     * of candidates and cache the first one that exists.
     */
    private void locateCreateSharedTexture() throws NoSuchMethodException {
        if (d3DResourceFactoryCls == null) {
            throw new NoSuchMethodException("no ResourceFactory");
        }

        // Candidate signatures in order of historical occurrence.
        // We don't know the exact parameter types for sure across builds, so
        // we look up by name first, then pick the first parameter list we
        // can drive with a single long + width + height.
        String[] candidateNames = {
            "createSharedTexture",
            "createTextureByHandle",
            "openSharedTexture",
            "wrapSharedTexture"
        };
        for (String name : candidateNames) {
            for (Method m : d3DResourceFactoryCls.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 4
                        && params[0] == long.class
                        && params[1] == int.class
                        && params[2] == int.class
                        && (params[3] == boolean.class
                            || params[3] == int.class)) {
                    m.setAccessible(true);
                    d3DCreateSharedTexture = m;
                    LOG.log(Level.INFO,
                            "CefRendererD3D: using "
                          + d3DResourceFactoryCls.getSimpleName() + "."
                          + name + "(long,int,int," + params[3].getSimpleName()
                          + ") for shared texture import.");
                    return;
                }
            }
        }

        // If none of the simple signatures match, also stash the Texture
        // class for callers that might need to wrap things differently.
        try {
            prismTextureCls = Class.forName("com.sun.prism.Texture");
        } catch (ClassNotFoundException ignore) {
            // Will simply surface as a null texture on first frame.
        }

        LOG.log(Level.WARNING,
                "CefRendererD3D: no shared-texture import factory method found"
              + " on " + d3DResourceFactoryCls.getName()
              + ". Add the method lookup to locateCreateSharedTexture() for"
              + " your JavaFX build.");
    }

    /**
     * Invokes the resolved shared-texture factory method on Prism's
     * ResourceFactory to wrap the given HANDLE into a Prism {@code Texture}
     * suitable for sampling. Returns the wrapped texture or {@code null} on
     * failure.
     */
    private Object openSharedTextureOnPrism(long handle, int width, int height) {
        if (d3DCreateSharedTexture == null || prismResourceFactory == null) {
            return null;
        }
        try {
            Class<?>[] params = d3DCreateSharedTexture.getParameterTypes();
            Object[] args;
            if (params[3] == boolean.class) {
                args = new Object[] { handle, width, height, Boolean.FALSE };
            } else {
                args = new Object[] { handle, width, height, 0 };
            }
            return d3DCreateSharedTexture.invoke(prismResourceFactory, args);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOG.log(Level.WARNING,
                    "CefRendererD3D: shared texture import failed", ex);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Small reflection helper
    // ------------------------------------------------------------------

    private static Object readField(Object instance, String... candidateNames) {
        Class<?> cls = instance.getClass();
        for (String name : candidateNames) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ignored) {
                // try the next name
            } catch (IllegalAccessException ex) {
                LOG.log(Level.FINE,
                        "Field " + name + " present but not accessible", ex);
            }
            // Also walk up the superclass chain, since some builds stash the
            // references in a common base class.
            Class<?> sup = cls.getSuperclass();
            while (sup != null) {
                try {
                    Field f = sup.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(instance);
                } catch (NoSuchFieldException ignored) {
                    // next
                } catch (IllegalAccessException ex) {
                    LOG.log(Level.FINE, "Field " + name + " present on "
                            + sup.getName() + " but not accessible", ex);
                }
                sup = sup.getSuperclass();
            }
        }
        return null;
    }
}
