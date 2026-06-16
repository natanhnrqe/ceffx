/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.demo;

import atlantafx.base.theme.Styles;
import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.CefBrowserSettings;
import com.techsenger.ceffx.core.CefClient;
import com.techsenger.ceffx.core.CefSettings;
import com.techsenger.ceffx.core.browser.CefBrowser;
import com.techsenger.ceffx.core.browser.CefBrowserFactory;
import com.techsenger.ceffx.core.browser.CefFrame;
import com.techsenger.ceffx.core.callback.CefCommandLine;
import com.techsenger.ceffx.core.handler.CefAppHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefCursorUtils;
import com.techsenger.ceffx.core.handler.CefDisplayHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefFocusHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefLifeSpanHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefLoadHandlerAdapter;
import com.techsenger.ceffx.core.handler.CefPrintHandlerAdapter;
import com.techsenger.ceffx.core.misc.CefPrintSettings;
import com.techsenger.ceffx.core.network.CefRequest;
import com.techsenger.ceffx.demo.menu.BookmarkMenuRegistrar;
import com.techsenger.ceffx.demo.menu.FileMenuRegistrar;
import com.techsenger.ceffx.demo.tab.BrowserTabFxView;
import com.techsenger.ceffx.demo.tab.BrowserTabParams;
import com.techsenger.ceffx.demo.tab.BrowserTabPort;
import com.techsenger.ceffx.demo.tab.BrowserTabPresenter;
import com.techsenger.ceffx.demo.tab.ChangeSource;
import com.techsenger.ceffx.natives.NativeExtractor;
import com.techsenger.shellfx.core.DefaultShellContext;
import com.techsenger.shellfx.core.DefaultShellFxView;
import com.techsenger.shellfx.core.DefaultShellParams;
import com.techsenger.shellfx.core.DefaultShellPresenter;
import com.techsenger.shellfx.core.ShellFxView;
import com.techsenger.shellfx.core.area.AreaParams;
import com.techsenger.shellfx.core.history.InMemoryHistoryManager;
import com.techsenger.shellfx.core.registry.ControlRegistry;
import com.techsenger.shellfx.core.settings.Density;
import com.techsenger.shellfx.core.settings.ShellSettings;
import com.techsenger.shellfx.core.tab.TabContainerFxView;
import com.techsenger.shellfx.icons.IconStylesheetFactory;
import com.techsenger.shellfx.layout.tabhost.TabHostFxView;
import com.techsenger.shellfx.layout.tabhost.TabHostPresenter;
import com.techsenger.shellfx.material.style.StyleClasses;
import com.techsenger.shellfx.material.style.Stylesheet;
import com.techsenger.shellfx.material.theme.AtlantaFxTheme;
import com.techsenger.tabpanepro.core.TabPanePro;
import com.techsenger.tabpanepro.core.skin.TabHeaderAreaPolicy;
import com.techsenger.tabpanepro.core.skin.TabPaneProSkin;
import com.techsenger.tabpanepro.core.skin.TabPaneProSkin.TabHeaderArea;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

/**
 * Demo application.
 *
 * <p>The closing process follows these steps:
 *
 * <p><b>If there are open tabs:</b>
 * <ol>
 *     <li>A window close is triggered.</li>
 *     <li>{@code shellPresenter.getOnCloseRequest()} is invoked.</li>
 *     <li>{@code shellPresenter.closeSafely()} is called.</li>
 *     <li>All tabs are deinitialized.</li>
 *     <li>Each tab performs {@code postDeinitialize()}, which calls {@code browser.close(boolean)}.</li>
 *     <li>{@code CefLifeSpanHandler.onBeforeClose()} is invoked.</li>
 *     <li>{@code CefApp.dispose()} is invoked.</li>
 * </ol>
 *
 * <p><b>If there are no open tabs:</b>
 * <ol>
 *     <li>A window close is triggered.</li>
 *     <li>{@code shellPresenter.getOnCloseRequest()} is invoked.</li>
 *     <li>{@code shellPresenter.closeSafely()} is called.</li>
 *     <li>{@code shellPresenter.getOnClosed()} is invoked.</li>
 *     <li>{@code CefApp.dispose()} is invoked.</li>
 * </ol>
 *
 * @author Pavel Castornii
 */
public class Demo extends Application {

    private static final Object TAB_COMPONENT = new Object();

    public static void main(String[] args) throws IOException {
        // #1 Cef
        var libPath = System.getProperty("java.library.path");
        // extract native libraries from ceffx-natives
        NativeExtractor.extract(Path.of(libPath));
        CefApp.startup(args);
        CefApp.addAppHandler(new CefAppHandlerAdapter(null) {
            @Override
            public void onBeforeCommandLineProcessing(String processType, CefCommandLine commandLine) {
//                commandLine.appendSwitchWithValue("force-dark-mode", "1");
                commandLine.appendSwitchWithValue("enable-features", "WebUIDarkMode,ForceDarkMode");
            }
        });
        var cefApp = CefApp.getInstance(createCefSettings());

        // #2 JavaFX
        Application.launch(Demo.class);
    }

    private static CefSettings createCefSettings() {
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.multi_threaded_message_loop = true;
        settings.external_message_pump = false;
        settings.command_line_args_disabled = false;
        return settings;
    }

    private static ShellSettings createShellSettings() {
        var settings = new DemoSettings();
        var appearance = settings.getAppearance();
        appearance.setRegularFont(Font.font("System", 14));
        appearance.setMonospaceFont(Font.font("Monospace", 14));
        appearance.setTheme(AtlantaFxTheme.CUPERTINO_DARK);
        appearance.setDensity(Density.S);
        return settings;
    }

    private static BrowserTabPort getTabPort(CefBrowser browser) {
        BrowserTabPort tabPort = (BrowserTabPort) browser.getProperties().get(TAB_COMPONENT);
        return tabPort;
    }

    private final AtomicInteger browserCount = new AtomicInteger();

    private ShellFxView<?> shell;

    private TabContainerFxView<?> workspace;

    private CefClient client;

    private volatile boolean isExit;

    @Override
    public void start(Stage stage) throws Exception {
        createShell(stage);
        createWorkspace();
        createMainMenu();

        CefApp.runLater(() -> {
            // one client for all tabs
            client = CefApp.getInstance().createClient();

            client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
                @Override
                public void onBeforeClose(CefBrowser browser) {
                    if (browserCount.decrementAndGet() == 0 && isExit) {
                        CefApp.runLater(() -> CefApp.getInstance().dispose());
                    }
                }
            });
            client.addDisplayHandler(new CefDisplayHandlerAdapter() {
                @Override
                public void onTitleChange(CefBrowser browser, String title) {
                    super.onTitleChange(browser, title);
                    var tabPort = getTabPort(browser);
                    Platform.runLater(() -> tabPort.onTitleChanged(title));
                }

                @Override
                public void onFaviconURLChange(CefBrowser browser, String[] iconUrls) {
                    super.onFaviconURLChange(browser, iconUrls);
                    var tabPort = getTabPort(browser);
                    Platform.runLater(() -> tabPort.onFaviconUrlChanged(iconUrls));
                }

                @Override
                public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                    var tabPort = getTabPort(browser);
                    Platform.runLater(() -> tabPort.onAddressChanged(url, ChangeSource.BROWSER));
                }

                @Override
                public boolean onCursorChange(CefBrowser browser, int cursorType) {
                    var tabPort = getTabPort(browser);
                    Platform.runLater(() -> tabPort.onCursorChanged(CefCursorUtils.getCursor(cursorType)));
                    return true;
                }
            });
            client.addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadStart(CefBrowser browser, CefFrame frame,
                                        CefRequest.TransitionType transitionType) {
                    if (frame.isMain()) {
                        CefApp.runLater(() -> {
                            var devToolsClient = browser.getDevToolsClient();
                            if (devToolsClient != null) {
                                devToolsClient.executeDevToolsMethod("Emulation.setAutoDarkModeOverride",
                                    "{ \"enabled\": true }"
                                );
                            }
                        });
                    }
                }
            });
            client.addFocusHandler(new CefFocusHandlerAdapter() {
                @Override
                public void onTakeFocus(CefBrowser browser, boolean next) {
                    super.onTakeFocus(browser, next);
                    var tabPort = getTabPort(browser);
                    Platform.runLater(() -> tabPort.onTakeFocusFromBrowser());
                }
            });
            client.addPrintHandler(new CefPrintHandlerAdapter() {
                @Override
                public void onPrintSettings(CefBrowser browser, CefPrintSettings settings, boolean getDefaults) {
                    // settings.setDeviceName(...);
                    settings.setPrinterPrintableArea(
                        new Dimension2D(300, 300),
                        new BoundingBox(0, 0, 300, 300),
                        false
                    );
                }
            });
        });

        stage.show();
    }

    private void createShell(Stage stage) {
        var stylesheets = new ArrayList<>(IconStylesheetFactory.forAll());
        stylesheets.add(new Stylesheet(Demo.class.getResource("demo.css")));
        var shellView = new DefaultShellFxView<>(this, stage, stylesheets, new ControlRegistry());
        this.shell = shellView;
        var settings = createShellSettings();
        var context = new DefaultShellContext(settings, new InMemoryHistoryManager(), getHostServices());
        var shellParams = new DefaultShellParams(context);
        var shellPresenter = new DefaultShellPresenter<>(shellView, shellParams);
        shellPresenter.initialize();
        shellView.getStage().getScene().getRoot().getStyleClass().add(StyleClasses.DENSITY_S);
        shellPresenter.setTitle("CEFFX Demo");
        shellPresenter.setOnCloseRequest(() -> {
            if (workspace.getComposer().getTabs().isEmpty()) {
                shellPresenter.setOnClosed(() -> {
                    CefApp.runLater(() -> CefApp.getInstance().dispose());
                });
            } else {
                isExit = true;
            }
            shellPresenter.closeSafely();
        });
    }

    private void createWorkspace() {
        var workspaceView = new TabHostFxView<>(true);
        this.workspace = workspaceView;
        TabPanePro tabPane = workspaceView.getNode();
        tabPane.setTabMaxWidth(220);
        TabPaneProSkin skin = (TabPaneProSkin) tabPane.getSkin();
        TabHeaderArea tabHeaderArea = skin.getTabHeaderArea();
        tabHeaderArea.setPolicy(TabHeaderAreaPolicy.ALWAYS_VISIBLE);
        StackPane stickyArea = tabHeaderArea.getStickyArea();
        stickyArea.setPadding(new Insets(2, 0, 0, 0));
        var newTabButton = new Button(null, new FontIcon(MaterialDesignP.PLUS));
        newTabButton.getStyleClass().addAll(Styles.FLAT, StyleClasses.ICON_BUTTON);
        newTabButton.setOnAction((e) -> onNewTab(null));
        stickyArea.getChildren().add(newTabButton);
        var workspacePresenter = new TabHostPresenter<>(workspaceView, new AreaParams());
        workspacePresenter.initialize();
        shell.getComposer().addWorkspace(workspaceView);
    }

    private void createMainMenu() {
        var controlRegistry = shell.getControlRegistry();
        var bookMarkRegistrar = new BookmarkMenuRegistrar(controlRegistry, shell, this::onNewTab);
        bookMarkRegistrar.register();
        var fileRegistrar = new FileMenuRegistrar(controlRegistry, shell);
        fileRegistrar.register();
        shell.upgradeMenuBar();
    }

    private void onNewTab(String url) {
        CefApp.runLater(()-> {
            try {
                var browserSettings = new CefBrowserSettings();
                var browser = CefBrowserFactory.create(client, url, true, false, null, browserSettings);
                browser.setWindowlessFrameRate(60);
                browserCount.incrementAndGet();
                Platform.runLater(() -> {
                    var tabView = new BrowserTabFxView(shell, browser.getPane());
                    var tabParams = new BrowserTabParams(shell.getPresenter().getContext(), browser);
                    var tabPresenter = new BrowserTabPresenter(tabView, tabParams);
                    tabPresenter.initialize();
                    browser.getProperties().put(TAB_COMPONENT, tabPresenter);
                    workspace.getComposer().addTab(tabView);
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
    }
}
