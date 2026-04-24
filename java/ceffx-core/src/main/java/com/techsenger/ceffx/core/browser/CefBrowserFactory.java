// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import com.techsenger.ceffx.core.CefBrowserSettings;
import com.techsenger.ceffx.core.CefClient;

/**
 * Creates a new instance of CefBrowser according the passed values
 */
public class CefBrowserFactory {
    public static CefBrowser create(CefClient client, String url, boolean isOffscreenRendered,
            boolean isTransparent, CefRequestContext context, CefBrowserSettings settings) {
        if (isOffscreenRendered)
            return new CefBrowserOsr(client, url, isTransparent, context, settings);
//        return new CefBrowserWr(client, url, context, settings);
        return null;
    }
}
