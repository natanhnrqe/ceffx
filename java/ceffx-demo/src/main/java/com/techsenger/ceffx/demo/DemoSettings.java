/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo;

import com.techsenger.tabshell.core.settings.AppearanceSettings;
import com.techsenger.tabshell.core.settings.DefaultAppearanceSettings;
import com.techsenger.tabshell.core.settings.ShellSettings;

/**
 *
 * @author Pavel Castornii
 */
public class DemoSettings implements ShellSettings {

    private final AppearanceSettings appearance = new DefaultAppearanceSettings();

    @Override
    public AppearanceSettings getAppearance() {
        return appearance;
    }

}
