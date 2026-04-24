// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.callback.CefBeforeDownloadCallback;
import com.techsenger.ceffx.core.callback.CefDownloadItem;
import com.techsenger.ceffx.core.callback.CefDownloadItemCallback;

/**
 * An abstract adapter class for receiving download events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefDownloadHandlerAdapter implements CefDownloadHandler {
    @Override
    public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
            String suggestedName, CefBeforeDownloadCallback callback) {
        return false;
    }

    @Override
    public void onDownloadUpdated(
            CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback) {}
}
