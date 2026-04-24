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
