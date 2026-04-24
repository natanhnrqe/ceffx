// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.callback.CefDragData;
import java.nio.ByteBuffer;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;

/**
 * An abstract adapter class for receiving render events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefRenderHandlerAdapter implements CefRenderHandler {
    @Override
    public BoundingBox getViewRect(CefBrowser browser) {
        return new BoundingBox(0, 0, 0, 0);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        return false;
    }

    @Override
    public Point2D getScreenPoint(CefBrowser browser, Point2D viewPoint) {
        return new Point2D(0, 0);
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {}

    @Override
    public void onPopupSize(CefBrowser browser, BoundingBox size) {}

    @Override
    public void onPaint(CefBrowser browser, boolean popup, BoundingBox[] dirtyRects,
            ByteBuffer buffer, int width, int height) {}

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        return false;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {}
}
