/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.tab;

import com.techsenger.tabshell.core.tab.TabView;
import javafx.scene.Cursor;

/**
 *
 * @author Pavel Castornii
 */
public interface BrowserTabView extends TabView {

    void setAddress(String url);

    void transferFocusFromBrowser();

    void setCursor(Cursor cursor);
}
