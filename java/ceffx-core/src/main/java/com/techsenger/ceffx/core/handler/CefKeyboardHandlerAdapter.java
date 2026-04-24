// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.misc.BoolRef;

/**
 * An abstract adapter class for receiving keyboard events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefKeyboardHandlerAdapter implements CefKeyboardHandler {
    @Override
    public boolean onPreKeyEvent(
            CefBrowser browser, CefKeyEvent event, BoolRef is_keyboard_shortcut) {
        return false;
    }

    @Override
    public boolean onKeyEvent(CefBrowser browser, CefKeyEvent event) {
        return false;
    }
}
