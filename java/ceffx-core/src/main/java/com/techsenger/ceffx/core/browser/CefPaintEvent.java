// Copyright (c) 2024 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import java.nio.ByteBuffer;
import javafx.geometry.BoundingBox;

public class CefPaintEvent {
    private final CefBrowser browser;
    private final boolean popup;
    private final BoundingBox[] dirtyRects;
    private final ByteBuffer renderedFrame;
    private final int width;
    private final int height;

    public CefPaintEvent(CefBrowser browser, boolean popup, BoundingBox[] dirtyRects,
            ByteBuffer renderedFrame, int width, int height) {
        this.browser = browser;
        this.popup = popup;
        this.dirtyRects = dirtyRects;
        this.renderedFrame = renderedFrame;
        this.width = width;
        this.height = height;
    }

    public CefBrowser getBrowser() {
        return browser;
    }

    public boolean getPopup() {
        return popup;
    }

    public BoundingBox[] getDirtyRects() {
        return dirtyRects;
    }

    public ByteBuffer getRenderedFrame() {
        return renderedFrame;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}