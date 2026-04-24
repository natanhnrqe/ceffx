// Copyright (c) 2015 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;

import javafx.geometry.BoundingBox;

/**
 * An abstract adapter class for receiving windowed render events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefWindowHandlerAdapter implements CefWindowHandler {

    @Override
    public BoundingBox getRect(CefBrowser browser) {
        return new BoundingBox(0, 0, 0, 0);
    }

    @Override
    public void onMouseEvent(
            CefBrowser browser, int event, int screenX, int screenY, int modifier, int button) {}
}
