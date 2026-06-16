/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.menu;

import com.techsenger.shellfx.material.menu.DefaultMenuGroupName;
import com.techsenger.shellfx.material.menu.DefaultMenuName;
import com.techsenger.shellfx.material.menu.MenuGroupName;
import com.techsenger.shellfx.material.menu.MenuName;

/**
 *
 * @author Pavel Castornii
 */
public final class FileMenu {

    public static final MenuName NAME = new DefaultMenuName();

    public static final MenuGroupName GROUP = new DefaultMenuGroupName("Group");

    private FileMenu() {
        // empty
    }
}
