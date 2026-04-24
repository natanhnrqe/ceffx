// Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.browser;

import com.techsenger.ceffx.core.callback.CefPdfPrintCallback;
import com.techsenger.ceffx.core.callback.CefRunFileDialogCallback;
import com.techsenger.ceffx.core.callback.CefStringVisitor;
import com.techsenger.ceffx.core.handler.CefDialogHandler;
import com.techsenger.ceffx.core.misc.CefPdfPrintSettings;
import com.techsenger.ceffx.core.network.CefRequest;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for a CEF browser instance, providing core browser functionality
 * independent of any UI representation.
 *
 * <p>This interface defines the fundamental browser operations such as navigation,
 * JavaScript execution, zoom control, and browser lifecycle management — without
 * any dependency on a visual component or rendering layer.
 *
 * <p>Implementations of this interface can be used in non-visual contexts, making
 * it suitable as a foundation for MVP, MVVM, or other architectural patterns where
 * browser logic must be decoupled from the view layer.
 *
 * @author Pavel Castornii
 */
public interface CefBrowserBase {

    /**
     * Retrieves the request context used by this browser instance. May be the
     * global request context if this browser does not have a specific request
     * context.
     */
    public CefRequestContext getRequestContext();

    /**
     * Call to immediately create the underlying browser object. By default the
     * browser object will be created when the parent container is displayed for
     * the first time.
     */
    public void createImmediately();

    //
    // The following methods are forwarded to CefBrowser.
    //

    /**
     * Tests if the browser can navigate backwards.
     * @return true if the browser can navigate backwards.
     */
    public boolean canGoBack();

    /**
     * Go back.
     */
    public void goBack();

    /**
     * Tests if the browser can navigate forwards.
     * @return true if the browser can navigate forwards.
     */
    public boolean canGoForward();

    /**
     * Go forward.
     */
    public void goForward();

    /**
     * Tests if the browser is currently loading.
     * @return true if the browser is currently loading.
     */
    public boolean isLoading();

    /**
     * Reload the current page.
     */
    public void reload();

    /**
     * Reload the current page ignoring any cached data.
     */
    public void reloadIgnoreCache();

    /**
     * Stop loading the page.
     */
    public void stopLoad();

    /**
     * Returns the unique browser identifier.
     * @return The browser identifier
     */
    public int getIdentifier();

    /**
     * Returns the main (top-level) frame for the browser window.
     * @return The main frame
     */
    public CefFrame getMainFrame();

    /**
     * Returns the focused frame for the browser window.
     * @return The focused frame
     */
    public CefFrame getFocusedFrame();

    /**
     * Returns the frame with the specified identifier, or NULL if not found.
     * @param identifier The unique frame identifier
     * @return The frame or NULL if not found
     */
    public CefFrame getFrameByIdentifier(String identifier);

    /**
     * Returns the frame with the specified name, or NULL if not found.
     * @param name The specified name
     * @return The frame or NULL if not found
     */
    public CefFrame getFrameByName(String name);

    /**
     * Returns the identifiers of all existing frames.
     * @return All identifiers of existing frames.
     */
    public Vector<String> getFrameIdentifiers();

    /**
     * Returns the names of all existing frames.
     * @return The names of all existing frames.
     */
    public Vector<String> getFrameNames();

    /**
     * Returns the number of frames that currently exist.
     * @return The number of frames
     */
    public int getFrameCount();

    /**
     * Tests if the window is a popup window.
     * @return true if the window is a popup window.
     */
    public boolean isPopup();

    /**
     * Tests if a document has been loaded in the browser.
     * @return true if a document has been loaded in the browser.
     */
    public boolean hasDocument();

    //
    // The following methods are forwarded to the mainFrame.
    //

    /**
     * Save this frame's HTML source to a temporary file and open it in the
     * default text viewing application. This method can only be called from the
     * browser process.
     */
    public void viewSource();

    /**
     * Retrieve this frame's HTML source as a string sent to the specified
     * visitor.
     *
     * @param visitor
     */
    public void getSource(CefStringVisitor visitor);

    /**
     * Retrieve this frame's display text as a string sent to the specified
     * visitor.
     *
     * @param visitor
     */
    public void getText(CefStringVisitor visitor);

    /**
     * Load the request represented by the request object.
     *
     * @param request The request object.
     */
    public void loadRequest(CefRequest request);

    /**
     * Load the specified URL in the main frame.
     * @param url The URL to load.
     */
    public void loadURL(String url);

    /**
     * Execute a string of JavaScript code in this frame. The url
     * parameter is the URL where the script in question can be found, if any.
     * The renderer may request this URL to show the developer the source of the
     * error. The line parameter is the base line number to use for error
     * reporting.
     *
     * @param code The code to be executed.
     * @param url The URL where the script in question can be found.
     * @param line The base line number to use for error reporting.
     */
    public void executeJavaScript(String code, String url, int line);

    /**
     * Emits the URL currently loaded in this frame.
     * @return the URL currently loaded in this frame.
     */
    public String getURL();

    // The following methods are forwarded to CefBrowserHost.

    /**
     * Request that the browser close.
     * @param force force the close.
     */
    public void close(boolean force);

    /**
     * Allow the browser to close.
     */
    public void setCloseAllowed();

    /**
     * Called from CefClient.doClose.
     */
    public boolean doClose();

    /**
     * Called from CefClient.onBeforeClose.
     */
    public void onBeforeClose();

    /**
     * Set or remove keyboard focus to/from the browser window.
     * @param enable set to true to give the focus to the browser
     **/
    public void setFocus(boolean enable);

    /**
     * Set whether the window containing the browser is visible
     * (minimized/unminimized, app hidden/unhidden, etc). Only used on Mac OS X.
     * @param visible
     */
    public void setWindowVisibility(boolean visible);

    /**
     * Get the current zoom level. The default zoom level is 0.0.
     * @return The current zoom level.
     */
    public double getZoomLevel();

    /**
     * Change the zoom level to the specified value. Specify 0.0 to reset the
     * zoom level.
     *
     * @param zoomLevel The zoom level to be set.
     */
    public void setZoomLevel(double zoomLevel);

    /**
     * Call to run a file chooser dialog. Only a single file chooser dialog may be
     * pending at any given time.The dialog will be initiated asynchronously on
     * the CEF main thread.
     *
     * @param mode represents the type of dialog to display.
     * @param title  to be used for the dialog and may be empty to show the
     * default title ("Open" or "Save" depending on the mode).
     * @param defaultFilePath is the path with optional directory and/or file name
     * component that should be initially selected in the dialog.
     * @param acceptFilters are used to restrict the selectable file types and may
     * any combination of (a) valid lower-cased MIME types (e.g. "text/*" or
     * "image/*"), (b) individual file extensions (e.g. ".txt" or ".png"), or (c)
     * combined description and file extension delimited using "|" and ";" (e.g.
     * "Image Types|.png;.gif;.jpg").
     * @param selectedAcceptFilter is the 0-based index of the filter that should
     * be selected by default.
     * @param callback will be executed after the dialog is dismissed or
     * immediately if another dialog is already pending.
     */
    public void runFileDialog(CefDialogHandler.FileDialogMode mode, String title, String defaultFilePath,
            Vector<String> acceptFilters, int selectedAcceptFilter,
            CefRunFileDialogCallback callback);

    /**
     * Download the file at url using CefDownloadHandler.
     *
     * @param url URL to download that file.
     */
    public void startDownload(String url);

    /**
     * Print the current browser contents.
     */
    public void print();

    /**
     * Print the current browser contents to a PDF.
     *
     * @param path The path of the file to write to (will be overwritten if it
     *      already exists).  Cannot be null.
     * @param settings The pdf print settings to use.  If null then defaults
     *      will be used.
     * @param callback Called when the pdf print job has completed.
     */
    public void printToPDF(String path, CefPdfPrintSettings settings, CefPdfPrintCallback callback);

    /**
     * Search for some kind of text on the page.
     *
     * @param searchText to be searched for.
     * @param forward indicates whether to search forward or backward within the page.
     * @param matchCase indicates whether the search should be case-sensitive.
     * @param findNext indicates whether this is the first request or a follow-up.
     */
    public void find(String searchText, boolean forward, boolean matchCase, boolean findNext);

    /**
     * Cancel all searches that are currently going on.
     * @param clearSelection Set to true to reset selection.
     */
    public void stopFinding(boolean clearSelection);

    /**
     * Get an instance of the DevTools to be displayed in its own window.
     */
    public void openDevTools();

    /**
     * Opens an instance of the DevTools window.
     *
     * <p>The DevTools will be created as a separate window and attached to this browser instance.
     * The {@code inspectAt} point defines a position in the browser view that should be used
     * as the initial inspection anchor (for example, where the element inspector is focused).</p>
     *
     * <p>Coordinates are in JavaFX view space (browser-local coordinates), not screen coordinates.</p>
     *
     * @param x the x-coordinate in browser view space to inspect
     * @param y the y-coordinate in browser view space to inspect
     */
    public void openDevTools(double x, double y);

    /**
     * Close the DevTools.
     */
    public void closeDevTools();

    /**
     * Get an instance of a client that can be used to leverage the DevTools
     * protocol. Only one instance per browser is available.
     *
     * @see {@link CefDevToolsClient}
     * @return DevTools client, or null if this browser is not yet created
     *   or if it is closed or closing
     */
    public CefDevToolsClient getDevToolsClient();

    /**
     * If a misspelled word is currently selected in an editable node calling
     * this method will replace it with the specified |word|.
     *
     * @param word replace selected word with this word.
     */
    public void replaceMisspelling(String word);

    /**
     * Captures a screenshot-like image of the currently displayed content and returns it.
     * <p>
     * If executed on the AWT Event Thread, this returns an immediately resolved {@link
     * java.util.concurrent.CompletableFuture}. If executed from another thread, the {@link
     * java.util.concurrent.CompletableFuture} returned is resolved as soon as the screenshot
     * has been taken (which must happen on the event thread).
     * <p>
     * The generated screenshot can either be returned as-is, containing all natively-rendered
     * pixels, or it can be scaled to match the logical width and height of the window.
     * This distinction is only relevant in case of differing logical and physical resolutions
     * (for example with HiDPI/Retina displays, which have a scaling factor of for example 2
     * between the logical width of a window (ex. 400px) and the actual number of pixels in
     * each row (ex. 800px with a scaling factor of 2)).
     *
     * @param nativeResolution whether to return an image at full native resolution (true)
     *      or a scaled-down version whose width and height are equal to the logical size
     *      of the screenshotted browser window
     * @return the screenshot image
     * @throws UnsupportedOperationException if not supported
     */
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution);

    /**
     * Set the maximum rate in frames per second (fps) that {@code CefRenderHandler::onPaint}
     * will be called for a windowless browser. The actual fps may be
     * lower if the browser cannot generate frames at the requested rate. The
     * minimum value is 1, and the maximum value is 60 (default 30).
     *
     * @param frameRate the maximum frame rate
     * @throws UnsupportedOperationException if not supported
     */
    public void setWindowlessFrameRate(int frameRate);

    /**
     * Returns the maximum rate in frames per second (fps) that {@code CefRenderHandler::onPaint}
     * will be called for a windowless browser. The actual fps may be lower if the browser cannot
     * generate frames at the requested rate. The minimum value is 1, and the maximum value is 60
     * (default 30).
     *
     * @return the framerate, 0 if an error occurs
     * @throws UnsupportedOperationException if not supported
     */
    public CompletableFuture<Integer> getWindowlessFrameRate();

    /**
     * Returns a thread-safe map for storing arbitrary user properties associated with this browser instance.
     * <p>
     * This map can be used to attach custom metadata, caches, or integration-specific state
     * that needs to travel with the browser instance across Java ↔ native (CEF) boundaries.
     * </p>
     *
     * <p><b>Thread safety:</b> This map is backed by {@link java.util.concurrent.ConcurrentHashMap}
     * and is safe to access from multiple threads, including CEF callback threads.</p>
     *
     * <p><b>Important:</b> Values stored in this map are not automatically synchronized
     * with the native CEF layer. It is purely a Java-side storage mechanism.</p>
     *
     * @return a concurrent map of user-defined properties associated with this browser instance
     */
   public Map<Object, Object> getProperties();

    /**
     * Enables or disables rendering of this browser.
     *
     * <p>When rendering is disabled, the CEF rendering pipeline will not produce or submit
     * new frames for this browser instance. This is useful when the browser (or tab) is not
     * visible and there is no need to waste CPU/GPU resources on painting.</p>
     *
     * <p>This is especially important in cases where the browser may display
     * resource-intensive content such as video, animations, or continuously updating
     * web pages. Disabling rendering in invisible tabs can significantly reduce
     * CPU/GPU usage and power consumption.</p>
     *
     * <p>Note: This does NOT stop the browser from running. JavaScript execution,
     * network activity, timers, and internal page logic continue to run normally.
     * Only the rendering/output stage is affected.</p>
     *
     * @param enabled {@code true} to allow rendering, {@code false} to suspend rendering
     *                when the browser is not visible.
     */
    public void setRenderingEnabled(boolean enabled);
}
