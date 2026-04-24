/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.tab;

import javafx.scene.Cursor;

/**
 *
 * @author Pavel Castornii
 */
public interface BrowserTabPort {

    void onTitleChanged(String title);

    void onFaviconUrlChanged(String[] iconUrls);

    void onAddressChanged(String address, ChangeSource source);

    void onTakeFocusFromBrowser();

    void onCursorChanged(Cursor cursor);
}
