// Copyright (c) 2019 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.browser.CefFrame;
import com.techsenger.ceffx.core.callback.CefCallback;
import com.techsenger.ceffx.core.misc.BoolRef;
import com.techsenger.ceffx.core.misc.StringRef;
import com.techsenger.ceffx.core.network.CefRequest;
import com.techsenger.ceffx.core.network.CefResponse;
import com.techsenger.ceffx.core.network.CefURLRequest;

/**
 * An abstract adapter class for receiving browser request events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefResourceRequestHandlerAdapter implements CefResourceRequestHandler {
    @Override
    public CefCookieAccessFilter getCookieAccessFilter(
            CefBrowser browser, CefFrame frame, CefRequest request) {
        return null;
    }

    @Override
    public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
        return false;
    }

    @Override
    public CefResourceHandler getResourceHandler(
            CefBrowser browser, CefFrame frame, CefRequest request) {
        return null;
    }

    @Override
    public void onResourceRedirect(CefBrowser browser, CefFrame frame, CefRequest request,
            CefResponse response, StringRef new_url) {}

    @Override
    public boolean onResourceResponse(
            CefBrowser browser, CefFrame frame, CefRequest request, CefResponse response) {
        return false;
    }

    @Override
    public void onResourceLoadComplete(CefBrowser browser, CefFrame frame, CefRequest request,
            CefResponse response, CefURLRequest.Status status, long receivedContentLength) {}

    @Override
    public void onProtocolExecution(
            CefBrowser browser, CefFrame frame, CefRequest request, BoolRef allowOsExecution) {}
}
