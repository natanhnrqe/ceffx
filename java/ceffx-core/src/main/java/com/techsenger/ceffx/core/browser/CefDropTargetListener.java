// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.callback.CefDragData;
import com.techsenger.ceffx.core.misc.EventFlags;
import java.io.File;
import javafx.geometry.Point2D;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import static javafx.scene.input.TransferMode.COPY;
import static javafx.scene.input.TransferMode.LINK;
import static javafx.scene.input.TransferMode.MOVE;

class CefDropTargetListener implements DropTargetListener {

    private static CefDragData doCreateDragData(DragEvent event) {
        CefDragData dragData = CefDragData.create();

        var dragboard = event.getDragboard();

        if (dragboard.hasString()) {
            dragData.setFragmentText(dragboard.getString());
        }

        if (dragboard.hasFiles()) {
            for (File file : dragboard.getFiles()) {
                dragData.addFile(file.getAbsolutePath(), file.getName());
            }
        }

        if (dragboard.hasHtml()) {
            dragData.setFragmentHtml(dragboard.getHtml());
        }

        if (dragboard.hasUrl()) {
            dragData.setLinkURL(dragboard.getUrl());
        }

        return dragData;
    }

    private CefBrowser_N browser_;
    private CefDragData dragData_ = null;
    private int dragOperations_ = CefDragData.DragOperations.DRAG_OPERATION_COPY;
    private int dragModifiers_ = EventFlags.EVENTFLAG_NONE;
    
    CefDropTargetListener(CefBrowser_N browser) {
        browser_ = browser;
    }

    @Override
    public void dragEntered(DragEvent event) {
        createDragData(event);
        var data = dragData_;
        var point = createPoint(event);
        var modifiers = dragModifiers_;
        var operations = dragOperations_;
        CefApp.runLater(() -> browser_.dragTargetDragEnter(data, point, modifiers, operations));
    }

    @Override
    public void dragExited(DragEvent event) {
        assertDragData();
        CefApp.runLater(() -> browser_.dragTargetDragLeave());
        clearDragData();
    }

    @Override
    public void dragOver(DragEvent event) {
        updateDragData(event);
        var point = createPoint(event);
        var modifiers = dragModifiers_;
        var operations = dragOperations_;
        CefApp.runLater(() -> browser_.dragTargetDragOver(point, modifiers, operations));
    }

    @Override
    public void drop(DragEvent event) {
        assertDragData();
        var point = createPoint(event);
        var modifiers = dragModifiers_;
        CefApp.runLater(() -> browser_.dragTargetDrop(point, modifiers));
        event.setDropCompleted(true);
        clearDragData();
        event.consume();
    }

    private Point2D createPoint(DragEvent event) {
        return new Point2D(event.getX(), event.getY());
    }

    private void createDragData(DragEvent event) {
        assert dragData_ == null;
        dragData_ = doCreateDragData(event);
        updateDragData(event);
    }

    private void assertDragData() {
        assert dragData_ != null;
    }

    private void updateDragData(DragEvent event) {
        assertDragData();
        TransferMode mode = event.getTransferMode();
        if (mode == null) {
            dragOperations_ = CefDragData.DragOperations.DRAG_OPERATION_COPY;
            dragModifiers_ = EventFlags.EVENTFLAG_NONE;
            return;
        }

        switch (mode) {
            case LINK:
                dragOperations_ = CefDragData.DragOperations.DRAG_OPERATION_LINK;
                dragModifiers_ = EventFlags.EVENTFLAG_CONTROL_DOWN | EventFlags.EVENTFLAG_SHIFT_DOWN;
                break;
            case COPY:
                dragOperations_ = CefDragData.DragOperations.DRAG_OPERATION_COPY;
                dragModifiers_ = EventFlags.EVENTFLAG_CONTROL_DOWN;
                break;
            case MOVE:
                dragOperations_ = CefDragData.DragOperations.DRAG_OPERATION_MOVE;
                dragModifiers_ = EventFlags.EVENTFLAG_SHIFT_DOWN;
                break;
            default:
                // fallback
                dragOperations_ = CefDragData.DragOperations.DRAG_OPERATION_COPY;
                dragModifiers_ = EventFlags.EVENTFLAG_NONE;
                break;
        }
    }

    private void clearDragData() {
        dragData_ = null;
    }
}
