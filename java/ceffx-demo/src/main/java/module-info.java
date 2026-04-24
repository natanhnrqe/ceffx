/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

module com.techsenger.ceffx.demo {
    requires org.slf4j;
    requires atlantafx.base;
    requires java.desktop;
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires com.twelvemonkeys.imageio.bmp;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires com.techsenger.toolkit.fx;
    requires com.techsenger.patternfx.core;
    requires com.techsenger.patternfx.mvp;
    requires com.techsenger.tabpanepro.core;
    requires com.techsenger.tabshell.core;
    requires com.techsenger.tabshell.icons;
    requires com.techsenger.tabshell.material;
    requires com.techsenger.tabshell.layout;
    requires com.techsenger.tabshell.devtools;
    requires com.techsenger.ceffx.core;

    requires org.apache.logging.log4j.slf4j2.impl;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;

    exports com.techsenger.ceffx.demo to javafx.graphics;
}
