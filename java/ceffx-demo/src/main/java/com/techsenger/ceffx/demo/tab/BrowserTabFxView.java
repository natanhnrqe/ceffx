/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo.tab;

import com.techsenger.tabshell.core.ShellFxView;
import com.techsenger.tabshell.core.history.HistoryManager;
import com.techsenger.tabshell.core.settings.ShellSettings;
import com.techsenger.tabshell.core.tab.AbstractTabFxView;
import com.techsenger.tabshell.devtools.DevToolsHostType;
import com.techsenger.tabshell.devtools.DevToolsTabDockFxView;
import com.techsenger.tabshell.devtools.DevToolsTabDockParams;
import com.techsenger.tabshell.devtools.DevToolsTabDockPresenter;
import com.techsenger.tabshell.material.style.StyleClasses;
import com.techsenger.toolkit.fx.utils.NodeUtils;
import javafx.geometry.Orientation;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

/**
 *
 * @author Pavel Castornii
 */
public class BrowserTabFxView extends AbstractTabFxView<BrowserTabPresenter> implements BrowserTabView {

    public class Composer extends AbstractTabFxView<BrowserTabPresenter>.Composer implements BrowserTabView.Composer {

        private final BrowserTabFxView view = BrowserTabFxView.this;

        private DevToolsTabDockFxView<?> devTools;

        public void addDevTools(ShellSettings settings, HistoryManager historyManager) {
            if (devTools != null) {
                return;
            }
            devTools = new DevToolsTabDockFxView<>(getShell(), getShell()) {

                @Override
                protected void addHandlers() {
                    super.addHandlers();
                    getCloseButton().setOnAction(e -> view.getPresenter().closeDevTools());
                }
            };
            var params = new DevToolsTabDockParams(DevToolsHostType.OTHER, settings, historyManager);
            var presenter = new DevToolsTabDockPresenter<>(devTools, params);
            presenter.initialize();
            getModifiableChildren().add(devTools);
            var slplitPane = new SplitPane(content, devTools.getNode());
            slplitPane.setOrientation(Orientation.VERTICAL);
            slplitPane.setDividerPositions(0.75);
            getContentBox().getChildren().set(0, slplitPane);
        }

        public void removeDevTools() {
            getModifiableChildren().remove(devTools);
            getContentBox().getChildren().set(0, content);
            devTools.getPresenter().deinitialize();
            this.devTools = null;
        }
    }

    private final Button backButton = new Button(null, new FontIcon(MaterialDesignA.ARROW_LEFT));

    private final Button forwardButton = new Button(null, new FontIcon(MaterialDesignA.ARROW_RIGHT));

    private final TextField addressTextField = new TextField();

    private final Button devToolsButton = new Button(null, new FontIcon(MaterialDesignT.TOOLS));

    private final ToolBar toolBar = new ToolBar(backButton, forwardButton, addressTextField, 
            devToolsButton);

    private final VBox content = new VBox(toolBar);

    private final Pane browserPane;

    public BrowserTabFxView(ShellFxView<?> shell, Pane browserPane) {
        super(shell);
        this.browserPane = browserPane;
    }

    @Override
    public void requestFocus() {
        NodeUtils.requestFocus(addressTextField);
    }

    @Override
    public Composer getComposer() {
        return (Composer) super.getComposer();
    }

    @Override
    public void setAddress(String url) {
        this.addressTextField.setText(url);
    }

    @Override
    public void transferFocusFromBrowser() {
        this.addressTextField.requestFocus();
    }

    @Override
    public void setCursor(Cursor cursor) {
        this.browserPane.setCursor(cursor);
    }

    @Override
    protected void build() {
        super.build();
        forwardButton.getStyleClass().addAll(StyleClasses.ICON_BUTTON, StyleClasses.SIZE_L);
        backButton.getStyleClass().addAll(StyleClasses.ICON_BUTTON, StyleClasses.SIZE_L);
        devToolsButton.getStyleClass().addAll(StyleClasses.ICON_BUTTON, StyleClasses.SIZE_L);
        VBox.setVgrow(browserPane, Priority.ALWAYS);
        HBox.setHgrow(addressTextField, Priority.ALWAYS);
        content.getChildren().add(browserPane);
        VBox.setVgrow(content, Priority.ALWAYS);
        getContentBox().getChildren().add(content);
    }

    @Override
    protected void addListeners() {
        super.addListeners();
        addressTextField.textProperty()
                .addListener((ov, oldV, newV) -> getPresenter().onAddressChanged(newV, ChangeSource.VIEW));
    }

    @Override
    protected void addHandlers() {
        super.addHandlers();
        addressTextField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                getPresenter().onAddressSubmitted();
            }
        });
        forwardButton.setOnAction(e -> getPresenter().onForward());
        backButton.setOnAction(e -> getPresenter().onBack());
        devToolsButton.setOnAction(e -> getPresenter().openDevTools());
    }

    @Override
    protected Composer createComposer() {
        return new BrowserTabFxView.Composer();
    }

    private MenuButton createMenuButton() {
        var openDevToolsItem = new MenuItem("Open DevTools");
        openDevToolsItem.setOnAction(e -> getPresenter().onBrowserDevTools());
        var printItem = new MenuItem("Print");
        printItem.setOnAction(e -> getPresenter().onPrint());
        var resetZoomItem = new MenuItem("Reset Zoom");
        resetZoomItem.setOnAction(e -> getPresenter().onResetZoom());
        var dndZoomItem = new MenuItem("Drag And Drop");
        dndZoomItem.setOnAction(e -> getPresenter().onDragAndDrop());
        var themeItem = new MenuItem("Toggle Theme");
        themeItem.setOnAction(e -> getPresenter().onToggleTheme());
        MenuButton menuButton = new MenuButton("Tests", null, openDevToolsItem, printItem, resetZoomItem,
                dndZoomItem, themeItem);
        return menuButton;
    }
}
