/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

module com.techsenger.ceffx.core {
    requires org.slf4j;
    requires java.desktop;
    requires java.logging;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;

    exports com.techsenger.ceffx.core;
    exports com.techsenger.ceffx.core.browser;
    exports com.techsenger.ceffx.core.callback;
    exports com.techsenger.ceffx.core.handler;
    exports com.techsenger.ceffx.core.misc;
    exports com.techsenger.ceffx.core.network;

    //
    // The accelerated renderer (CefRendererD3D) uses reflection to reach the
    // internal JavaFX Prism Direct3D pipeline classes located in the package
    // com.sun.prism.d3d, which is NOT exported by the javafx.graphics module.
    //
    // At runtime launch the JVM must be told to open that package to the
    // ceffx-core module (or to ALL-UNNAMED if used from the classpath):
    //
    //   --add-opens javafx.graphics/com.sun.prism.d3d=com.techsenger.ceffx.core
    //   --add-opens javafx.graphics/com.sun.javafx.graphics=com.techsenger.ceffx.core
    //   --add-opens javafx.graphics/com.sun.glass.ui=com.techsenger.ceffx.core
    //   --add-opens javafx.graphics/com.sun.prism=com.techsenger.ceffx.core
    //
    // No replacement can be expressed in this module-info alone because the
    // package is in a DIFFERENT module; the flags above are mandatory when
    // the accelerated renderer is used on Windows.
    //
}
