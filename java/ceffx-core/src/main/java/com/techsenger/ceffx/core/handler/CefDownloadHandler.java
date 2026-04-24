// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.callback.CefBeforeDownloadCallback;
import com.techsenger.ceffx.core.callback.CefDownloadItem;
import com.techsenger.ceffx.core.callback.CefDownloadItemCallback;

/**
 * Implement this interface to handle file downloads. The methods of this class
 * will called on the browser process CEF main thread.
 */
public interface CefDownloadHandler {
    /**
     * Called before a download begins. Return true and execute |callback| either
     * asynchronously or in this method to continue or cancel the download.
     * Return false to proceed with default handling (cancel with Alloy style,
     * download shelf with Chrome style). Do not keep a reference to
     * downloadItem outside of this method.
     *
     * @param browser The desired browser.
     * @param downloadItem The item to be downloaded. Do not keep a reference to it outside this
     * method.
     * @param suggestedName is the suggested name for the download file.
     * @param callback start the download by calling the Continue method
     */
    public boolean onBeforeDownload(CefBrowser browser, CefDownloadItem downloadItem,
            String suggestedName, CefBeforeDownloadCallback callback);

    /**
     * Called when a download's status or progress information has been updated.
     * @param browser The desired browser.
     * @param downloadItem The downloading item.
     * @param callback Execute callback to cancel the download
     */
    public void onDownloadUpdated(
            CefBrowser browser, CefDownloadItem downloadItem, CefDownloadItemCallback callback);
}
