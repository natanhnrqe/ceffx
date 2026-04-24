// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.callback.CefNativeAdapter;
import com.techsenger.ceffx.core.callback.CefPrintDialogCallback;
import com.techsenger.ceffx.core.callback.CefPrintJobCallback;
import com.techsenger.ceffx.core.misc.CefPrintSettings;
import javafx.geometry.Dimension2D;

/**
 * An abstract adapter class for receiving print events on Linux.
 * The methods in this class are empty.
 * This class exists as convenience for creating handler objects.
 */
public abstract class CefPrintHandlerAdapter extends CefNativeAdapter implements CefPrintHandler {
    @Override
    public void onPrintStart(CefBrowser browser) {
        // The default implementation does nothing
    }

    @Override
    public void onPrintSettings(
            CefBrowser browser, CefPrintSettings settings, boolean getDefaults) {
        // The default implementation does nothing
    }

    @Override
    public boolean onPrintDialog(
            CefBrowser browser, boolean hasSelection, CefPrintDialogCallback callback) {
        // The default implementation does nothing
        return false;
    }

    @Override
    public boolean onPrintJob(CefBrowser browser, String documentName, String pdfFilePath,
            CefPrintJobCallback callback) {
        // The default implementation does nothing
        return false;
    }

    @Override
    public void onPrintReset(CefBrowser browser) {
        // The default implementation does nothing
    }

    @Override
    public Dimension2D getPdfPaperSize(CefBrowser browser, int deviceUnitsPerInch) {
        // default implementation is A4 letter size
        // @ 300 DPI, A4 is 2480 x 3508
        // @ 150 DPI, A4 is 1240 x 1754
        double scale = deviceUnitsPerInch / 300.0;
        double adjustedWidth = 2480.0 * scale;
        double adjustedHeight = 3508.0 * scale;
        return new Dimension2D(adjustedWidth, adjustedHeight);
    }
}
