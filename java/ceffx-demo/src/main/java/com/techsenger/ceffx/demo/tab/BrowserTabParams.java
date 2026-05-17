/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.tab;

import com.techsenger.ceffx.core.browser.CefBrowserBase;
import com.techsenger.tabshell.core.ShellContext;
import com.techsenger.tabshell.core.tab.TabParams;

/**
 *
 * @author Pavel Castornii
 */
public class BrowserTabParams extends TabParams {

    private final ShellContext context;

    private final CefBrowserBase browser;

    public BrowserTabParams(ShellContext context, CefBrowserBase browser) {
        this.context = context;
        this.browser = browser;
    }

    public ShellContext getContext() {
        return context;
    }

    public CefBrowserBase getBrowser() {
        return browser;
    }
}
