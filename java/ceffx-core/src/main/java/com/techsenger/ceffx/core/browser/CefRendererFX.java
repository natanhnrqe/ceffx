/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.core.browser;

import java.nio.ByteBuffer;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

/**
 * JavaFX renderer for CEF OSR (Off-Screen Rendering) mode.
 *
 * <p>Converts CEF BGRA ByteBuffer into a JavaFX WritableImage and draws it
 * onto a Canvas using partial dirty-rect updates for optimal performance.</p>
 *
 * <p>Thread-safety: {@link #onPaint} may be called from any thread (CEF render
 * thread). All Canvas/Image operations are dispatched to the JavaFX Application
 * Thread via {@link Platform#runLater}.</p>
 *
 * @author Pavel Castornii
 */
public class CefRendererFX implements CefRenderer {

    // -----------------------------------------------------------------------
    // JavaFX pixel format that matches CEF's native BGRA layout exactly.
    // Using a bulk-write pixel format avoids per-pixel byte-swapping loops.
    // -----------------------------------------------------------------------
    private static final WritablePixelFormat<ByteBuffer> FMT_BGRA =
            PixelFormat.getByteBgraPreInstance();


    /** Composites the main image (and popup if visible) onto the Canvas. */
    private static void draw(WritableImage image, Canvas canvas, boolean transparent, boolean popupVisible,
            WritableImage popupImage, BoundingBox popupRect) {
        if (image == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        double iw = image.getWidth();
        double ih = image.getHeight();
        // Skip frames whose size doesn't match the canvas — CEF is still
        // catching up after a resize. The previous frame stays on the canvas
        // as-is: no blank area, no stretch artifacts.
        if ((int) iw != (int) cw || (int) ih != (int) ch) {
            return;
        }
        if (transparent) {
            gc.clearRect(0, 0, cw, ch);
        }
        gc.drawImage(image, 0, 0);
        if (popupVisible && popupImage != null && popupRect != null) {
            gc.drawImage(popupImage,
                    popupRect.getMinX(), popupRect.getMinY(),
                    popupImage.getWidth(), popupImage.getHeight());
        }
    }

    private final Canvas canvas = new Canvas();
    private boolean transparent;

    /** Reused backing image – recreated only on size change. */
    private WritableImage image;

    /** Reused popup image. */
    private WritableImage popupImage;

    private volatile boolean popupVisible = false;
    private volatile BoundingBox popupRect = null;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------
    public CefRendererFX() {

    }

    @Override
    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    @Override
    public Node getCanvas() {
        return this.canvas;
    }


    // -----------------------------------------------------------------------
    // CEF callbacks  (called from CEF render thread)
    // -----------------------------------------------------------------------

    /**
     * Called by CEF on the render thread whenever a new frame is ready.
     * The buffer is copied immediately because CEF reclaims it after this
     * method returns. Rendering is then scheduled on the JavaFX thread.
     */
    @Override
    public void onPaint(boolean popup, BoundingBox[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (width <= 0 || height <= 0 || buffer == null) return;

        // Copy CEF buffer immediately — CEF reclaims it after onPaint returns.
        final ByteBuffer copy = ByteBuffer.allocateDirect(buffer.capacity());
        buffer.rewind();
        copy.put(buffer);
        copy.rewind();
        if (popup) {
            ensurePopupImage(width, height);
            paintFrame(popupImage, copy, width, height, dirtyRects);
            if (popupVisible && popupRect != null) {
                Platform.runLater(() -> draw(image, canvas, transparent, popupVisible, popupImage, popupRect));
            }
        } else {
            ensureImage(width, height);
            paintFrame(image, copy, width, height, dirtyRects);
            draw(image, canvas, transparent, popupVisible, popupImage, popupRect);
        }
    }

    @Override
    public void onPopupSize(BoundingBox rect) {
        if (rect == null || rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            popupVisible = false;
            popupRect    = null;
        } else {
            popupVisible = true;
            popupRect    = rect;
        }
    }

    @Override
    public void clearPopupRects() {
        popupVisible = false;
        popupRect    = null;
        popupImage   = null;
    }

    /** Clears the canvas (e.g. on browser close). */
    public void clear() {
        Platform.runLater(() -> {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        });
    }

    @Override
    public void dispose() {
        // empty
    }

    // -----------------------------------------------------------------------
    // Rendering  (JavaFX Application Thread only)
    // -----------------------------------------------------------------------

    /**
     * Writes pixel data into the target WritableImage.
     * Uses dirty-rect partial updates when available for performance.
     */
    private void paintFrame(WritableImage target, ByteBuffer data,
                             int width, int height, BoundingBox[] dirtyRects) {

        var writer = target.getPixelWriter();
        int stride = width * 4; // bytes per row

        if (dirtyRects != null && dirtyRects.length > 0) {
            for (BoundingBox r : dirtyRects) {

                int rx = Math.max(0, (int) Math.round(r.getMinX()));
                int ry = Math.max(0, (int) Math.round(r.getMinY()));
                int rw = Math.min((int) Math.round(r.getWidth()),  width  - rx);
                int rh = Math.min((int) Math.round(r.getHeight()), height - ry);

                if (rw <= 0 || rh <= 0)
                    continue;

                ByteBuffer slice = data.duplicate();
                slice.position(ry * stride + rx * 4);

                writer.setPixels(rx, ry, rw, rh, FMT_BGRA, slice, stride);
            }
        } else {
            data.rewind();
            writer.setPixels(0, 0, width, height, FMT_BGRA, data, stride);
        }
    }

    // -----------------------------------------------------------------------
    // Image lifecycle helpers
    // -----------------------------------------------------------------------

    private void ensureImage(int width, int height) {
        if (image == null
                || (int) image.getWidth()  != width
                || (int) image.getHeight() != height) {
            image = new WritableImage(width, height);
        }
    }

    private void ensurePopupImage(int width, int height) {
        if (popupImage == null
                || (int) popupImage.getWidth()  != width
                || (int) popupImage.getHeight() != height) {
            popupImage = new WritableImage(width, height);
        }
    }
}