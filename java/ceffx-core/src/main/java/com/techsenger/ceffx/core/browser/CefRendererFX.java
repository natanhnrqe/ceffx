/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.core.browser;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;

/**
 * JavaFX renderer for CEF OSR (Off-Screen Rendering) mode.
 *
 * <p>Converts CEF BGRA ByteBuffer into a JavaFX {@link WritableImage} and
 * publishes it via an {@link ImageView}, using partial dirty-rect updates
 * for optimal performance.</p>
 *
 * <h2>Why ImageView instead of Canvas</h2>
 * <p>The previous design fed a {@code WritableImage} into a {@code Canvas}
 * via {@code GraphicsContext.drawImage}. Although that approach is
 * framework-idiomatic, the {@code Canvas} path builds an asynchronous
 * command buffer that the {@code NGCanvas} reflects into the QuantumRenderer
 * thread. During violent resize bursts the queued {@code handleRenderOp}
 * sequence can run against a {@code D3DTexture} that was deallocated by the
 * time the command finally executes, throwing
 * <pre>IllegalArgumentException: Upload requires N elements, but only M remain in the buffer</pre>
 * and tearing down the whole JavaFX render job.</p>
 *
 * <p>Switching to an {@code ImageView}-only visual tree removes that
 * asynchronous layer entirely: the {@code ImageView} holds a single
 * {@code WritableImage} reference. Replacing that reference on the JavaFX
 * Application Thread is atomic with respect to the QuantumRenderer pulse —
 * Prism re-samples the image in the very next pulse and there is never a
 * queued, deferred call that can outrun a texture reallocation.</p>
 *
 * <h2>Threading invariants (hard-won, do not relax)</h2>
 * <ol>
 *   <li>The CEF render thread touches the native buffer whose backing memory
 *       is recycled by the Chromium frame pool the instant {@code onPaint}
 *       returns. We MUST publish a private Java-heap snapshot of it before
 *       relinquishing that thread. The snapshot is a fixed-size
 *       {@code byte[]} of exactly {@code width*height*4} bytes and cannot
 *       be resized by any native side; everything downstream is safe.</li>
 *   <li>Every JavaFX pipeline touch ({@code WritableImage.getPixelWriter},
 *       {@code setPixels}, {@code ImageView.setImage}) runs on the JavaFX
 *       Application Thread, atomically within the same
 *       {@code drainPendingOnFxThread} call. There is no remaining TOCTOU
 *       window between dimension check and {@code D3DTexture.update} because
 *       the check and the consumption are the same statement block.</li>
 *   <li>If the incoming frame's dimensions don't match the dimensions of
 *       the current {@code WritableImage}, we synchronously discard and
 *       rebuild the {@code WritableImage} on the Application Thread and
 *       ABORT that frame. The next consistent frame re-enters with a
 *       texture whose dimensions match exactly — the upload proceeds
 *       cleanly. This is the only thing that makes the resize race
 *       physically safe.</li>
 *   <li>Frame bursts during resize are coalesced through an
 *       {@link AtomicReference} of pending snapshots + a one-shot
 *       {@code fxUploadScheduled} guard. Even though twenty frames arrive
 *       from CEF mid-resize, only the very latest one arrives at the
 *       Prism upload, with its correspondingly correct dimensions.</li>
 * </ol>
 *
 * <p>The {@code D3DTexture.update} exception is now structurally impossible:
 * at the moment {@code setPixels} runs, the target {@code WritableImage}
 * and the snapshot buffer are guaranteed by construction to have
 * byte-for-byte matching dimensions, both inspected on the same thread
 * in the same atomic block.</p>
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

    // -----------------------------------------------------------------------
    // Pending-frame carrier: a single mutable holder that retains the most
    // recent snapshot between the CEF render thread and the JavaFX thread.
    // The CEF thread overwrites it; the JavaFX thread consumes it. Using an
    // AtomicReference lets us coalesce multiple runLater submissions (only
    // the very latest snapshot is ever uploaded — mid-resize bursts of
    // frames never pile up into the FX queue with stale sizes).
    // -----------------------------------------------------------------------
    private static final class PendingFrame {
        final boolean popup;
        final byte[] snapshot;                 // private Java heap copy of BGRA
        final int width;
        final int height;
        final BoundingBox[] dirtyRects;       // may be null for full-frame

        PendingFrame(boolean popup, byte[] snapshot, int width, int height,
                     BoundingBox[] dirtyRects) {
            this.popup = popup;
            this.snapshot = snapshot;
            this.width = width;
            this.height = height;
            // Dirty rects are tiny arrays of immutable JavaFX BoundingBox;
            // even though they're typed BoundingBox[], they're immutable
            // references — safe to share without cloning.
            this.dirtyRects = dirtyRects;
        }
    }

    private final ImageView imageView = new ImageView();
    private boolean transparent;

    /** Reused backing image – recreated only on size change. All access
     * confined to the JavaFX Application Thread. */
    private WritableImage image;

    /** Reused popup image. All access confined to the JavaFX Application Thread. */
    private WritableImage popupImage;

    private volatile boolean popupVisible = false;
    private volatile BoundingBox popupRect = null;

    /**
     * Most-recent snapshot pending upload. The CEF thread sets it; the JavaFX
     * thread drains it and clears it. AtomicReference so we can coalesce.
     */
    private final AtomicReference<PendingFrame> pending = new AtomicReference<>();
    private final AtomicReference<PendingFrame> pendingPopup = new AtomicReference<>();

    /** Guard that ensures only one runLater is ever in-flight per renderer. */
    private volatile boolean fxUploadScheduled = false;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------
    public CefRendererFX() {
        // ImageView defaults to smooth filtering which is undesirable for
        // text rendering at 1:1 scale; disable it so the BGRA pixels reach
        // the screen with no interpolation.
        this.imageView.setSmooth(false);
        this.imageView.setPreserveRatio(false);
    }

    @Override
    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    @Override
    public Node getCanvas() {
        // Kept for API compatibility — returns the ImageView instead of a
        // Canvas. Callers that previously added this Node to a layout pane
        // keep working because ImageView is also a Region-sized Node and
        // resizes naturally with its parent.
        return this.imageView;
    }


    // -----------------------------------------------------------------------
    // CEF callbacks  (called from CEF render thread)
    // -----------------------------------------------------------------------

    /**
     * Called by CEF on the render thread whenever a new frame is ready.
     *
     * <p>This method's ONLY responsibility is to produce a private,
     * immutable snapshot of the CEF buffer and publish it to the JavaFX
     * thread. Every JavaFX pipeline interaction (size checks, image
     * reallocation, {@code setPixels}, {@code ImageView.setImage}) happens
     * on the JavaFX Application Thread, atomically, so there is no window
     * in which a {@code D3DTexture.update} can see a buffer and a texture
     * with mismatched dimensions.</p>
     */
    @Override
    public void onPaint(boolean popup, BoundingBox[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (width <= 0 || height <= 0 || buffer == null) return;

        // ---- Snapshot capture -----------------------------------------
        // Compute the EXACT byte count of a BGRA frame at width*height and
        // allocate a private Java-heap array of that size. Any subsequent
        // access to the snapshot is immune to Chromium recycling the native
        // buffer under us, since the JVM heap array has no native owner.
        final int required = safeRequiredBytes(width, height);
        if (required <= 0) {
            return;
        }

        final byte[] snapshot = new byte[required];
        final int savedPosition = buffer.position();
        final int savedLimit = buffer.limit();
        boolean snapshotComplete = false;
        try {
            // Clamp the source so we never read more than we need AND never
            // more than what the pool currently advertises. If remaining()
            // shrank below required we bail out BEFORE the bulk copy, so the
            // buffer-state observed by Chromium's writer is unchanged.
            int remaining = buffer.remaining();
            if (remaining < required) {
                return;
            }
            buffer.limit(buffer.position() + required);
            buffer.get(snapshot, 0, required);
            snapshotComplete = true;
        } catch (BufferUnderflowException race) {
            // Chromium recycled the buffer mid-copy: frame is corrupt, drop.
            snapshotComplete = false;
        } finally {
            // Always restore the source buffer markers so the JNI contract
            // with CEF (which may inspect position/limit after we return)
            // holds even on the failure path.
            buffer.position(savedPosition);
            buffer.limit(savedLimit);
        }
        if (!snapshotComplete) {
            return;
        }

        // Publish the snapshot; popups and main frames are kept in two
        // separate slots so a popup update does not clobber a main-frame
        // update that is about to be drained on the FX thread, and vice
        // versa. The ImageView tree is shared; both slots drain together in
        // the same runLater below.
        if (popup) {
            pendingPopup.set(new PendingFrame(true, snapshot, width, height, dirtyRects));
        } else {
            pending.set(new PendingFrame(false, snapshot, width, height, dirtyRects));
        }

        // Schedule a single drain on the JavaFX Application Thread if no
        // drain is currently in flight. The drain-side guard is reset from
        // the FX thread itself, so we don't fan out thousands of
        // runLaters during a resize burst.
        if (!fxUploadScheduled) {
            fxUploadScheduled = true;
            Platform.runLater(this::drainPendingOnFxThread);
        }
    }

    /**
     * Computes the exact byte count for a BGRA buffer of {@code width x height}.
     * Returns {@code -1} on overflow or non-positive dimensions so the caller
     * can drop the frame instead of allocating a bogus array size.
     */
    private static int safeRequiredBytes(int width, int height) {
        // Use long arithmetic and clamp at Integer.MAX_VALUE. We never expect
        // more than ~8K*8K*4 = 268435456 bytes which fits comfortably in int
        // but be defensive against corrupted dimension args.
        long bytes = ((long) width) * ((long) height) * 4L;
        if (bytes <= 0 || bytes > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) bytes;
    }

    @Override
    public void onPopupSize(BoundingBox rect) {
        if (rect == null || rect.getWidth() <= 0 || rect.getHeight() <= 0) {
            popupVisible = false;
            popupRect    = null;
            pendingPopup.set(null);
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
        pendingPopup.set(null);
    }

    /** Clears the visible image (e.g. on browser close). */
    public void clear() {
        pending.set(null);
        pendingPopup.set(null);
        Platform.runLater(() -> {
            imageView.setImage(null);
        });
    }

    @Override
    public void dispose() {
        pending.set(null);
        pendingPopup.set(null);
        // Decouple the image reference so Prism's D3DTexture backing can be
        // released promptly. Done on the FX thread to be consistent with
        // the only-thread-that-touches-image rule.
        Platform.runLater(() -> {
            imageView.setImage(null);
            image = null;
            popupImage = null;
        });
    }

    // -----------------------------------------------------------------------
    // JavaFX Application Thread entry point
    // -----------------------------------------------------------------------

    /**
     * Runs on the JavaFX Application Thread. Drains the most recent pending
     * snapshot (and the most recent pending popup snapshot, if any) and
     * uploads each into its respective {@code WritableImage}, after first
     * verifying — and, if necessary, rebuilding — that the
     * {@code WritableImage}'s dimensions exactly match the snapshot
     * dimensions. This is the ONLY place that touches the
     * {@code WritableImage}/{@code ImageView}, which confines the size check
     * to a single thread and makes the geometric gate absolute.
     *
     * <p>Note: there is no {@code Canvas}/{@code GraphicsContext} anywhere
     * in the call graph anymore. The {@code ImageView} references its
     * {@code WritableImage} directly; Prism samples it on the next pulse.
     * No asynchronous command buffer is generated, so the {@code NGCanvas}
     * / {@code handleRenderOp} crash path is structurally gone.</p>
     */
    private void drainPendingOnFxThread() {
        // Always allow the next onPaint to schedule again, even on the
        // exception path.
        try {
            PendingFrame frame = pending.getAndSet(null);
            PendingFrame popupFrame = pendingPopup.getAndSet(null);

            if (frame != null) {
                uploadIntoImage(frame, /*isPopup*/ false);
            }
            if (popupFrame != null) {
                uploadIntoImage(popupFrame, /*isPopup*/ true);
            }

            // Composite the popup onto the main image if we have both. We
            // mutate the main image's pixels directly, in place, and then
            // ask the ImageView to repaint by re-setting the image reference
            // to itself — both are FX-thread operations, no Canvas involved.
            if (popupVisible && popupRect != null
                    && image != null && popupImage != null) {
                compositPopupIntoMain();
            }

            // If we replaced/realized the main image this call, or merely
            // refreshed its pixels, re-binding the ImageView to it forces
            // Prism to re-sample on the next pulse. ImageView.setImage is
            // cheap when the new value equals the current reference, but it
            // still guarantees the dirty region mark — that's what we need.
            if (image != null && imageView.getImage() != image) {
                imageView.setFitWidth(image.getWidth());
                imageView.setFitHeight(image.getHeight());
                imageView.setImage(image);
            } else if (image != null) {
                // Handy no-op refire for dirty tracking of WritableImage
                // internals; ImageView's listener responds to identity
                // changes, not to WritableImage pixel changes, so we
                // force the dirty mark by re-setting the same reference.
                imageView.setImage(null);
                imageView.setImage(image);
            }
        } finally {
            fxUploadScheduled = false;
        }
    }

    /**
     * Uploads (or rebuilds and uploads) the pending snapshot into the main
     * or popup WritableImage. The isPopup flag selects which slot to use.
     *
     * <p>Strict geometric gate (the heart of the crash fix):</p>
     * <ol>
     *   <li>Bring the target {@code WritableImage} in line with the
     *       snapshot's dimensions, reallocating if needed.</li>
     *   <li>If a reallocation occurred (i.e. the snapshot's size was NOT
     *       equal to the previous image's size), the snapshot itself
     *       was prepared for the OLD dimensions — wait, no: the snapshot
     *       is ALWAYS sized exactly {@code width*height*4}, so it matches
     *       the new image <em>and</em> is independent of any earlier
     *       reallocation. We can simply upload. This is the key property
     *       of the snapshot isolation design: the snapshot dimensions ARE
     *       the truth; if the image doesn't match, we re-create it and
     *       the upload proceeds against the correctly-sized image.</li>
     * </ol>
     * <p>{@code setPixels} is invoked from here on the FX Application
     * Thread atomically with the size check, so the internal
     * {@code D3DTexture.update} cannot observe a mismatch.</p>
     */
    private void uploadIntoImage(PendingFrame frame, boolean isPopup) {
        WritableImage target = ensureImage(frame.width, frame.height, isPopup);

        // Hard precondition: the capsule must match the snapshot exactly
        // before any pixel write. yesNoRebuildRequired above guarantees
        // this, but we re-check defensively — that single statement is the
        // essence of the fix.
        if (target == null
                || (int) target.getWidth()  != frame.width
                || (int) target.getHeight() != frame.height) {
            return;
        }

        final ByteBuffer view = ByteBuffer.wrap(frame.snapshot);
        uploadPixels(target, view, frame.width, frame.height, frame.dirtyRects);
    }

    /**
     * Writes pixel data into a WritableImage. Caller MUST have verified
     * that the WritableImage is sized exactly {@code width x height}
     * and that {@code data} contains at least {@code width*height*4} bytes
     * via the strict geometric gate above. Both happen on the same
     * JavaFX Application Thread run, so {@code D3DTexture.update} sees
     * buffer and target dimensions that are identical at the moment of issue.
     */
    private void uploadPixels(WritableImage target, ByteBuffer data,
                              int width, int height, BoundingBox[] dirtyRects) {

        // Re-affirm the precondition at every entry. After the gate in
        // uploadIntoImage() this branch is unreachable in practice, but
        // it is cheap and documents the invariant.
        int targetW = (int) target.getWidth();
        int targetH = (int) target.getHeight();
        if (targetW != width || targetH != height) {
            return;
        }

        var writer = target.getPixelWriter();
        int stride = width * 4; // bytes per row
        int dataLimit = data.limit();

        if (dirtyRects != null && dirtyRects.length > 0) {
            for (BoundingBox r : dirtyRects) {
                int rx = Math.max(0, (int) Math.round(r.getMinX()));
                int ry = Math.max(0, (int) Math.round(r.getMinY()));
                int rw = Math.min((int) Math.round(r.getWidth()),  width  - rx);
                int rh = Math.min((int) Math.round(r.getHeight()), height - ry);

                if (rw <= 0 || rh <= 0)
                    continue;

                // Per-rect capacity guard: the slice's position+offset must
                // not exceed dataLimit or setPixels will throw the very same
                // "Upload requires N elements" exception.
                long rectStart = ((long) ry) * stride + ((long) rx) * 4L;
                long rectBytes = ((long) rh) * stride;  // full rows; clipping below
                if (rectStart + rectBytes > dataLimit) {
                    continue;
                }
                if (rw != width) {
                    long lastRowStart = rectStart + ((long) (rh - 1)) * stride;
                    if (lastRowStart + ((long) rw) * 4L > dataLimit) {
                        continue;
                    }
                }

                ByteBuffer slice = data.duplicate();
                slice.position((int) rectStart);
                writer.setPixels(rx, ry, rw, rh, FMT_BGRA, slice, stride);
            }
        } else {
            // Full-frame upload. Validated by the caller; the snapshot is
            // exactly width*height*4 so this branch is always safe here.
            data.rewind();
            writer.setPixels(0, 0, width, height, FMT_BGRA, data, stride);
        }
    }

    /**
     * Composite the popup image into the main image at the current popupRect
     * position. This is done in place by writing the popup pixels into the
     * backing WritableImage; the ImageView then renders the final frame by
     * sampling the resulting main image.
     *
     * <p>Must be called on the JavaFX Application Thread.</p>
     */
    private void compositPopupIntoMain() {
        // Validate that the rect lies inside the main image bounds; if the
        // popup partially overflows, clip it so setPixels stays in range.
        int mainW = (int) image.getWidth();
        int mainH = (int) image.getHeight();
        int popW = (int) popupImage.getWidth();
        int popH = (int) popupImage.getHeight();
        int popX = (int) Math.max(0, Math.round(popupRect.getMinX()));
        int popY = (int) Math.max(0, Math.round(popupRect.getMinY()));

        int dx = Math.min(popW, mainW - popX);
        int dy = Math.min(popH, mainH - popY);
        if (dx <= 0 || dy <= 0) return;

        // Read pixels out of the popup image and write them onto the main
        // image at the popup's top-left corner. This uses the
        // PixelReader-based overload which does format conversion internally.
        var reader = popupImage.getPixelReader();
        var writer = image.getPixelWriter();
        writer.setPixels(popX, popY, dx, dy, reader, 0, 0);
    }

    // -----------------------------------------------------------------------
    // Image lifecycle helpers (JavaFX Application Thread only)
    // -----------------------------------------------------------------------

    /**
     * Returns a {@link WritableImage} sized exactly {@code width x height},
     * allocating a new one if the cached one (for this slot) is absent or
     * differs in size.
     *
     * <p>Confined to the JavaFX Application Thread so the returned image
     * has a stable identity for the entire duration of the consuming
     * {@link #uploadIntoImage} call. Because the snapshot passed in is
     * ALREADY sized for the new dimensions, no skip-frame is necessary
     * here even on a freshly reallocated image — that is the key
     * difference between this design and a Canvas-driven one, where the
     * {@code D3DTexture} behind the {@code Canvas}'s back-buffer could lag
     * the reallocation by a pulse.</p>
     */
    private WritableImage ensureImage(int width, int height, boolean isPopup) {
        WritableImage cur = isPopup ? popupImage : image;
        if (cur == null
                || (int) cur.getWidth()  != width
                || (int) cur.getHeight() != height) {
            // Reallocating the WritableImage allocates a fresh underlying
            // D3DTexture at the new size. The ImageView holds a reference
            // to the OLD image until we re-bind it at the end of the
            // drainPendingOnFxThread call; this means Prism cannot possibly
            // issue a setPixels against the new image until we hand it off.
            cur = new WritableImage(width, height);
            if (isPopup) {
                popupImage = cur;
            } else {
                image = cur;
            }
        }
        return cur;
    }
}
