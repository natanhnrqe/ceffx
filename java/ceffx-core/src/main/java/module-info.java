/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

module com.techsenger.ceffx.core {
    requires org.slf4j;
    requires java.desktop;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;

    exports com.techsenger.ceffx.core;
    exports com.techsenger.ceffx.core.browser;
    exports com.techsenger.ceffx.core.callback;
    exports com.techsenger.ceffx.core.handler;
    exports com.techsenger.ceffx.core.misc;
    exports com.techsenger.ceffx.core.network;
}
