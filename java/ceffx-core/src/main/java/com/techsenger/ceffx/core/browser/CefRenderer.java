/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.core.browser;

import java.nio.ByteBuffer;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;

/**
 *
 * @author Pavel Castornii
 */
public interface CefRenderer {

    /**
     * Sets whether the renderer should use a transparent background.
     * This method is called immediately after instantiation.
     *
     * @param transparent {@code true} to enable transparency, {@code false} otherwise
     */
    void setTransparent(boolean transparent);

    /**
     * Returns JavaFX canvas.
     *
     * @return
     */
    Node getCanvas();

    /**
     * Called by CEF on the render thread whenever a new frame is ready. The buffer is copied immediately because
     * CEF reclaims it after this method returns.
     */
    void onPaint(boolean popup, BoundingBox[] dirtyRects, ByteBuffer buffer, int width, int height);

    /**
     * Accelerated paint path. Called by CEF on the render thread when a native
     * Direct3D 11 shared texture is available instead of a CPU pixel buffer.
     *
     * <p>{@code sharedTextureHandle} is a native Windows HANDLE reinterpreted
     * as a {@code long}. It is owned and lifecycle-managed by the native CEFFX
     * library; the Java side must reopen it on Prism's Direct3D device each
     * time it wants to sample a new frame.</p>
     *
     * <p>The default implementation is a no-op so renderers without GPU
     * support continue to work; CEF will fall back to {@link #onPaint} on
     * hardware that cannot provide shared textures.</p>
     */
    default void onAcceleratedPaint(boolean popup, BoundingBox[] dirtyRects,
            long sharedTextureHandle, int width, int height) {
        // No-op default; accelerated renderers override this.
    }

    /**
     * Informs the renderer about popup position and size. Must be called before the popup onPaint.
     */
    void onPopupSize(BoundingBox size);

    /**
     * Hides the popup overlay.
     */
    void clearPopupRects();

    /**
     * Releases all native and graphics resources held by this renderer.
     *
     * <p>Must be called when the browser is closed or the renderer is no longer needed.
     * After this method returns, the renderer must not be used.
     */
    void dispose();
}
