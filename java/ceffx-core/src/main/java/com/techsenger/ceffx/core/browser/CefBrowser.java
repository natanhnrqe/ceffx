// Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import com.techsenger.ceffx.core.CefClient;
import com.techsenger.ceffx.core.handler.CefRenderHandler;
import com.techsenger.ceffx.core.handler.CefWindowHandler;
import javafx.scene.layout.Pane;

/**
 * Extends {@link CefBrowserBase} with UI-related functionality for JavaFX integration.
 *
 * <p>This interface adds visual representation and rendering capabilities to the
 * core browser functionality, binding the browser instance to a JavaFX {@link Pane}
 * that serves as the rendering container for the browser content.
 *
 * <p>Use this interface when you need direct access to the browser's visual
 * component. For UI-independent browser logic, prefer {@link CefBrowserBase}.
 *
 * @see CefBrowserBase
 */
public interface CefBrowser extends CefBrowserBase {

    /**
     * Get the pane containing the rendered browser content.
     * @return The JavaFX pane used as the browser rendering container.
     */
    public Pane getPane();

    /**
     * Get the client associated with this browser.
     * @return The browser client.
     */
    public CefClient getClient();

    /**
     * Get an implementation of CefRenderHandler if any.
     * @return An instance of CefRenderHandler or null.
     */
    public CefRenderHandler getRenderHandler();

    /**
     * Get an implementation of CefWindowHandler if any.
     * @return An instance of CefWindowHandler or null.
     */
    public CefWindowHandler getWindowHandler();
}
