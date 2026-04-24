// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.browser.CefFrame;
import com.techsenger.ceffx.core.callback.CefAuthCallback;
import com.techsenger.ceffx.core.callback.CefCallback;
import com.techsenger.ceffx.core.handler.CefLoadHandler.ErrorCode;
import com.techsenger.ceffx.core.misc.BoolRef;
import com.techsenger.ceffx.core.network.CefRequest;
import com.techsenger.ceffx.core.network.CefURLRequest;

/**
 * An abstract adapter class for receiving browser request events.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefRequestHandlerAdapter implements CefRequestHandler {
    @Override
    public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
            boolean user_gesture, boolean is_redirect) {
        return false;
    }

    @Override
    public boolean onOpenURLFromTab(
            CefBrowser browser, CefFrame frame, String target_url, boolean user_gesture) {
        return false;
    }

    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame,
            CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator,
            BoolRef disableDefaultHandling) {
        return null;
    }

    @Override
    public boolean getAuthCredentials(CefBrowser browser, String origin_url, boolean isProxy,
            String host, int port, String realm, String scheme, CefAuthCallback callback) {
        return false;
    }

    @Override
    public boolean onCertificateError(
            CefBrowser browser, ErrorCode cert_error, String request_url, CefCallback callback) {
        return false;
    }

    @Override
    public void onRenderProcessTerminated(
            CefBrowser browser, TerminationStatus status, int error_code, String error_string) {}
}
