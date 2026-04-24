/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.tab;

import com.techsenger.tabshell.core.history.HistoryManager;
import com.techsenger.tabshell.core.settings.Settings;
import com.techsenger.tabshell.core.tab.TabComposer;

/**
 *
 * @author Pavel Castornii
 */
public interface BrowserTabComposer extends TabComposer {

    void addDevTools(Settings settings, HistoryManager historyManager);

    void removeDevTools();
}
