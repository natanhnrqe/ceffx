/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.menu;

import com.techsenger.tabshell.core.ShellFxView;
import com.techsenger.tabshell.core.menu.AbstractMenuItemHandler;
import com.techsenger.tabshell.core.menu.MenuItemHandler;
import com.techsenger.tabshell.core.registry.AbstractControlRegistrar;
import com.techsenger.tabshell.core.registry.ControlFactory;
import com.techsenger.tabshell.core.registry.ControlRegistry;
import com.techsenger.tabshell.material.menu.ManagedMenu;
import com.techsenger.tabshell.material.menu.ManagedMenuGroup;
import com.techsenger.tabshell.material.menu.ManagedMenuItem;

/**
 *
 * @author Pavel Castornii
 */
public class FileMenuRegistrar extends AbstractControlRegistrar {

    private final ShellFxView<?> shell;

    public FileMenuRegistrar(ControlRegistry registry, ShellFxView<?> shell) {
        super(registry);
        this.shell = shell;
    }

    @Override
    public void register() {
        registerMenu();
        registerGroup();
        registerExitItem();
    }

    protected void registerMenu() {
        ControlFactory<ShellFxView<?>, ManagedMenu> f = (v) -> {
            var menu = new ManagedMenu(FileMenu.NAME, "_File", 0);
            return menu;
        };
        addRegistration(getRegistry().mainMenu().registerMenu(null, f));
    }

    protected void registerGroup() {
        ControlFactory<ShellFxView<?>, ManagedMenuGroup> f = (v) -> {
            return new ManagedMenuGroup(FileMenu.GROUP, 0);
        };
        addRegistration(getRegistry().mainMenu().registerMenuGroup(FileMenu.NAME, f));
    }

    protected void registerExitItem() {
        ControlFactory<ShellFxView<?>, ManagedMenuItem> f = (v) -> {
            var item = new ManagedMenuItem("E_xit", 1000);
            var handler = new AbstractMenuItemHandler<ShellFxView<?>>(item, shell) {
                @Override
                public void onAction() {
                    shell.getPresenter().requestClose();
                }
            };
            MenuItemHandler.setHandler(item, handler);
            return item;
        };
        addRegistration(getRegistry().mainMenu().registerMenuItem(FileMenu.GROUP, f));
    }
}
