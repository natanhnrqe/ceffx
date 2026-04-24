// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.CefBrowserSettings;
import com.techsenger.ceffx.core.CefClient;
import com.techsenger.ceffx.core.callback.CefDragData;
import com.techsenger.ceffx.core.handler.CefRenderHandler;
import com.techsenger.ceffx.core.handler.CefScreenInfo;
import com.techsenger.ceffx.core.misc.Size;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;

/**
 * Off-screen rendered browser backed by a JavaFX Canvas.
 *
 * <p>CEF renders into a BGRA ByteBuffer via {@link #onPaint}; we forward
 * that buffer to {@link CefRendererFx}, which writes it into a
 * {@link javafx.scene.image.WritableImage} and draws it onto the Canvas on
 * the JavaFX Application Thread.</p>
 *
 * <p>Visibility is package-private. Use {@code CefBrowserFactory} to obtain
 * instances.</p>
 */
class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {

    private static class CanvasPane extends Pane {

        private Node canvas;

        public CanvasPane() {

        }

        public void setCanvas(Node canvas) {
            this.canvas = canvas;
            getChildren().add(canvas);
            if (canvas instanceof Canvas c) {
                c.widthProperty().bind(this.widthProperty());
                c.heightProperty().bind(this.heightProperty());
            }
        }
    }

    private static class DragDataSnapshot {
        final String fragmentText;
        final String linkMetadata;
        final String linkURL;
        final int mask;

        DragDataSnapshot(CefDragData data, int mask) {
            this.fragmentText = data.getFragmentText();
            this.linkMetadata = data.getLinkMetadata();
            this.linkURL = data.getLinkURL();
            this.mask = mask;
        }
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private CefRenderer renderer_;
    private CanvasPane pane = new CanvasPane();

    // resizing
    private final AtomicReference<Size> pendingSize = new AtomicReference<>();
    private final AtomicBoolean resizePending = new AtomicBoolean(false);

    /**
     * The rectangle that CEF considers the "view". Its size drives the
     * resolution at which CEF renders. Must never be 0×0 or CEF will
     * skip onPaint entirely.
     */
    private volatile BoundingBox browser_rect_ = new BoundingBox(0, 0, 1900, 720);

    private Point2D screenPoint_ = new Point2D(0, 0);
    private double scaleFactor_ = 1.0;
    private int depth = 32;
    private int depth_per_component = 8;
    private boolean isTransparent_;

    /** Whether the browser was freshly created and needs post-parent setup. */
    private boolean justCreated_ = false;

    private final CopyOnWriteArrayList<Consumer<CefPaintEvent>> onPaintListeners =
            new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<Object, Object> properties = new ConcurrentHashMap<>();

    private volatile boolean renderingEnabled = true;

    private final DropTargetListener dropTargetListener = new CefDropTargetListener(this);
//    private volatile CountDownLatch currentDragLatch = null;
//    private final AtomicReference<DragDataSnapshot> pendingDragSnapshot = new AtomicReference<>(null);


    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    CefBrowserOsr(CefClient client, String url, boolean transparent,
            CefRequestContext context, CefBrowserSettings settings) {
        this(client, url, transparent, context, null, null, settings);
    }

    private CefBrowserOsr(CefClient client, String url, boolean transparent,
            CefRequestContext context, CefBrowserOsr parent, Point2D inspectAt,
            CefBrowserSettings settings) {
        super(client, url, context, parent, inspectAt, settings);
        isTransparent_ = transparent;

        // Give the Canvas a concrete initial size so that browser_rect_ is
        // never 0×0 before the first layout pass.
        this.renderer_ = client.getRendererFactory().createRenderer();
        this.renderer_.setTransparent(transparent);
        this.pane.setCanvas(this.renderer_.getCanvas());

        setupNodeListeners();
        setupNodeHandlers();
    }

    // -----------------------------------------------------------------------
    // Canvas → CEF size binding
    // -----------------------------------------------------------------------

    /**
     * Wires up Canvas size changes and scene/window attachment events so
     * that CEF is informed whenever the renderable area changes.
     *
     * <p>CEF will never call {@link #onPaint} if {@link #getViewRect}
     * returns a zero-size rectangle, so keeping {@code browser_rect_}
     * in sync is critical.</p>
     */
    private void setupNodeListeners() {
        // --- Size changes ---------------------------------------------------
        pane.widthProperty().addListener((obs, oldVal, newVal) ->
                onCanvasResized((int) newVal.doubleValue(),
                                (int) pane.getHeight()));

        pane.heightProperty().addListener((obs, oldVal, newVal) ->
                onCanvasResized((int) pane.getWidth(),
                                (int) newVal.doubleValue()));

        // --- Scene / Window attachment ------------------------------------
        // The browser should be created once the Canvas is visible inside a
        // real window. We watch the scene→window chain to detect that moment.
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;

            if (newScene.getWindow() != null) {
                // Window already present – create browser immediately.
                CefApp.runLater(()-> createBrowserIfRequired(true));
            } else {
                // Window not yet attached – wait for it.
                newScene.windowProperty().addListener((wObs, oldWin, newWin) -> {
                    if (newWin != null) {
                        CefApp.runLater(()-> createBrowserIfRequired(true));
                    }
                });
            }
        });

        // If the Canvas is already in a scene+window at construction time,
        // kick off browser creation straight away.
        if (pane.getScene() != null && pane.getScene().getWindow() != null) {
            CefApp.runLater(() -> createBrowserIfRequired(true));
        }
    }

    private void setupNodeHandlers() {
        pane.canvas.addEventFilter(KeyEvent.ANY, event -> {
            event.consume();
            CefApp.runLater(() -> sendKeyEvent(event));
        });

        pane.canvas.addEventHandler(MouseEvent.ANY, event -> {
            pane.canvas.requestFocus();
            if (event.getEventType() == MouseEvent.DRAG_DETECTED) {
                // it will be handled in setOnDragDetected
                return;
            }
            CefApp.runLater(() -> sendMouseEvent(event));
        });

//        pane.canvas.setOnDragDetected(event -> {
//            // Create a new latch for this drag gesture
//            CountDownLatch latch = new CountDownLatch(1);
//            currentDragLatch = latch;
//            pendingDragSnapshot.set(null);
//            CefApp.runLater(() -> sendMouseEvent(event));
//
//            // Block the FX thread waiting for CEF to respond via startDragging().
//            // This is safe because we are still inside the DRAG_DETECTED handler,
//            // so JavaFX's internal dragDetected flag is still true.
//            // We use a timeout to avoid deadlock in case CEF never calls startDragging().
//            try {
//                boolean gotResponse = latch.await(2000, TimeUnit.MILLISECONDS);
//                DragDataSnapshot snapshot = pendingDragSnapshot.get();
//                if (gotResponse && snapshot != null) {
//                    // We are still inside DRAG_DETECTED - the flag is active, so
//                    // startDragAndDrop() is allowed to be called right now
//                    doDragAndDrop(snapshot);
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            } finally {
//                currentDragLatch = null;
//            }
//
//            event.consume();
//        });

        pane.canvas.addEventHandler(ScrollEvent.ANY, event -> {
            CefApp.runLater(() -> sendMouseWheelEvent(event));
        });
        pane.canvas.setOnDragEntered(event -> {
            dropTargetListener.dragEntered(event);
            event.consume();
        });
        pane.canvas.setOnDragExited(event -> {
            dropTargetListener.dragExited(event);
            event.consume();
        });
        pane.canvas.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY, TransferMode.MOVE, TransferMode.LINK);
            }
            dropTargetListener.dragOver(event);
            event.consume();
        });
        pane.canvas.setOnDragDropped(event -> {
            dropTargetListener.drop(event);
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Called whenever the Canvas width or height changes.
     * Updates {@code browser_rect_} and notifies CEF of the new size.
     *
     * <p>{@code wasResized} is a native CEF call; we run it on a daemon
     * thread to avoid blocking the JavaFX Application Thread.</p>
     */
    private void onCanvasResized(int width, int height) {
        if (width <= 0 || height <= 0) return;

        pendingSize.set(new Size(width, height));
        browser_rect_ = new BoundingBox(0, 0, width, height);

        if (resizePending.compareAndSet(false, true)) {
            scheduleResize();
        }
    }

    private void scheduleResize() {
        CefApp.runLater(() -> {
            Size s = pendingSize.get();
            try {
                wasResized(s.getWidth(), s.getHeight());
            } finally {
                resizePending.set(false);
                Size latest = pendingSize.get();
                if ((latest.getWidth() != s.getWidth() || latest.getHeight() != s.getHeight())
                        && resizePending.compareAndSet(false, true)) {
                    scheduleResize(); // recursive, max 1 level
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // CefBrowser_N overrides
    // -----------------------------------------------------------------------

    @Override
    public void createImmediately() {
        justCreated_ = true;
        createBrowserIfRequired(false);
    }

    /** Returns the JavaFX node that renders browser content. */
    @Override
    public Pane getPane() {
        return pane;
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    public synchronized void onBeforeClose() {
        super.onBeforeClose();
        this.renderer_.dispose();
    }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
            CefRequestContext context, CefBrowser_N parent, Point2D inspectAt) {
        return new CefBrowserOsr(client, url, isTransparent_, context,
                (CefBrowserOsr) this, inspectAt, null);
    }

    // -----------------------------------------------------------------------
    // CefRenderHandler implementation
    // -----------------------------------------------------------------------

    /**
     * CEF calls this to know the logical size of the browser view.
     * Returning 0×0 suppresses all onPaint calls.
     */
    @Override
    public BoundingBox getViewRect(CefBrowser browser) {
        return browser_rect_;
    }

    @Override
    public Point2D getScreenPoint(CefBrowser browser, Point2D viewPoint) {
        return screenPoint_.add(viewPoint);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        screenInfo.Set(scaleFactor_, depth, depth_per_component,
                false, browser_rect_, browser_rect_);
        return true;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        if (!show) {
            renderer_.clearPopupRects();
            invalidate();
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, BoundingBox size) {
        renderer_.onPopupSize(size);
    }

    /**
     * Main paint callback. Called by CEF on its render thread whenever
     * pixel content changes.
     *
     * <p>We copy the buffer immediately (CEF reclaims it after this method
     * returns) and hand it to {@link CefRendererFx}, which schedules the
     * actual Canvas update on the JavaFX Application Thread.</p>
     */
    @Override
    public void onPaint(CefBrowser browser, boolean popup, BoundingBox[] dirtyRects, ByteBuffer buffer,
            int width, int height) {
//        logger.debug("OnPaint request, dirtyRects length: {}, width: {}, height: {}",
//                dirtyRects == null ? 0 : dirtyRects.length, width, height);
        if (renderer_ == null || !this.renderingEnabled) {
            return;
        }
        renderer_.onPaint(popup, dirtyRects, buffer, width, height);

        if (!onPaintListeners.isEmpty()) {
            CefPaintEvent event =
                    new CefPaintEvent(browser, popup, dirtyRects, buffer, width, height);
            for (Consumer<CefPaintEvent> l : onPaintListeners) {
                l.accept(event);
            }
        }
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        // TODO: map cursorType to a JavaFX Cursor and set it on the scene.
        return true;
    }

    // -----------------------------------------------------------------------
    // Paint-listener API
    // -----------------------------------------------------------------------

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.add(listener);
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.clear();
        onPaintListeners.add(listener);
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
        onPaintListeners.remove(listener);
    }

    private TransferMode toTransferMode(int mask) {
        if ((mask & CefDragData.DragOperations.DRAG_OPERATION_COPY) != 0) {
            return TransferMode.COPY;
        }
        if ((mask & CefDragData.DragOperations.DRAG_OPERATION_MOVE) != 0) {
            return TransferMode.MOVE;
        }
        if ((mask & CefDragData.DragOperations.DRAG_OPERATION_LINK) != 0) {
            return TransferMode.LINK;
        }
        return TransferMode.COPY;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
//        CountDownLatch latch = currentDragLatch;
//        if (latch != null) {
//            // Copy all data immediately before CEF invalidates the native object
//            pendingDragSnapshot.set(new DragDataSnapshot(dragData, mask));
//            latch.countDown();
//        }
        return true;
    }

//    private void doDragAndDrop(DragDataSnapshot snapshot) {
//        // Must be called from within DRAG_DETECTED handler on FX thread
//        TransferMode mode = toTransferMode(snapshot.mask);
//        Dragboard db = pane.canvas.startDragAndDrop(mode);
//
//        ClipboardContent content = new ClipboardContent();
//
//        // Put fragment text if available
//        if (snapshot.fragmentText != null && !snapshot.fragmentText.isEmpty()) {
//            content.putString(snapshot.fragmentText);
//        }
//
//        // Put URL if available
//        if (snapshot.linkURL != null && !snapshot.linkURL.isEmpty()) {
//            content.putUrl(snapshot.linkURL);
//        }
//
//        // Parse DownloadURL from link metadata if available.
//        // Format: "mimetype:filename:url"
//        if (snapshot.linkMetadata != null && !snapshot.linkMetadata.isEmpty()) {
//            String[] parts = snapshot.linkMetadata.split(":", 3);
//            if (parts.length == 3) {
//                String mimeType = parts[0];
//                String fileName = parts[1];
//                String dataUrl = parts[2];
//                // Put file name as string if no other text was set
//                if (snapshot.fragmentText == null || snapshot.fragmentText.isEmpty()) {
//                    content.putString(fileName);
//                }
//            }
//        }
//
//        db.setContent(content);
//
//        pane.canvas.setOnDragDone(doneEvent -> {
//            pane.canvas.setOnDragDone(null);
//            Point2D pos = new Point2D(doneEvent.getX(), doneEvent.getY());
//            // Notify CEF that the drag gesture has ended
//            CefApp.runLater(() -> {
//                dragSourceEndedAt(pos, snapshot.mask);
//                dragSourceSystemDragEnded();
//            });
//        });
//    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        // TODO: update cursor based on drag operation
    }

    // -----------------------------------------------------------------------
    // Browser lifecycle helpers
    // -----------------------------------------------------------------------

    /**
     * Creates the native CEF browser if it has not been created yet.
     *
     * <p>In OSR mode the window handle is always 0; CEF does not need a
     * native window to render into because it pushes pixels via
     * {@link #onPaint}.</p>
     *
     * @param hasParent {@code true} when the Canvas is attached to a window
     *                  (used to trigger post-parent-change notifications).
     */
    private void createBrowserIfRequired(boolean hasParent) {
        if (getNativeRef("CefBrowser") == 0) {
            // Native browser not yet created.
            if (getParentBrowser() != null) {
                // DevTools window.
                createDevTools(getParentBrowser(), getClient(), 0, true, isTransparent_, getInspectAt());
            } else {
                createBrowser(getClient(), 0, getUrl(), true, isTransparent_, getRequestContext());
            }
        } else if (hasParent && justCreated_) {
            // Browser already exists but needs reparent notification.
            notifyAfterParentChanged();
            setFocus(true);
            justCreated_ = false;
        }
    }

    private void notifyAfterParentChanged() {
        // OSR has no native window to reparent, but the notification is still
        // required so that CefClient handlers fire correctly.
        getClient().onAfterParentChanged(this);
    }

    // -----------------------------------------------------------------------
    // Screenshot (not yet implemented for JavaFX)
    // -----------------------------------------------------------------------

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        // TODO: read pixels from WritableImage via PixelReader and convert to
        //       BufferedImage, applying scaleFactor_ if !nativeResolution.
        CompletableFuture<BufferedImage> f = new CompletableFuture<>();
        f.completeExceptionally(
                new UnsupportedOperationException("createScreenshot not yet implemented"));
        return f;
    }

    @Override
    public Map<Object, Object> getProperties() {
        return this.properties;
    }

    @Override
    public void setRenderingEnabled(boolean enabled) {
        this.renderingEnabled = enabled;
        if (enabled) {
            invalidate();
        }
    }
}