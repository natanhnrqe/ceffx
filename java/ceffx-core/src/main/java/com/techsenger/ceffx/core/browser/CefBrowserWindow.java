// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import javafx.stage.Stage;

/**
 * Interface representing system dependent methods for the browser.
 * In OSR (off-screen rendering) mode this interface is not used since CEF
 * renders into an offscreen buffer and does not need to embed into a native window.
 */
public interface CefBrowserWindow {
    /**
     * Get the native window handle for the given JavaFX stage.
     * Not applicable in OSR mode - return 0.
     *
     * @param stage a JavaFX stage
     * @return a native window handle, or 0 if not applicable
     */
    long getWindowHandle(Stage stage);
}
