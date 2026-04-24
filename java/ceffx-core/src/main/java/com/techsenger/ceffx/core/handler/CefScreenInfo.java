// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package com.techsenger.ceffx.core.handler;

import javafx.geometry.BoundingBox;

/**
 *
 * @author shannah
 */
public class CefScreenInfo {
    public double device_scale_factor;
    public int depth;
    public int depth_per_component;
    public boolean is_monochrome;

    public int x, y, width, height;
    public int available_x, available_y, available_width, available_height;

    public void Set(
            double device_scale_factor,
            int depth,
            int depth_per_component,
            boolean is_monochrome,
            BoundingBox rect,
            BoundingBox availableRect) {

        this.device_scale_factor = device_scale_factor;
        this.depth = depth;
        this.depth_per_component = depth_per_component;
        this.is_monochrome = is_monochrome;

        this.x = (int) Math.round(rect.getMinX());
        this.y = (int) Math.round(rect.getMinY());
        this.width = (int) Math.round(rect.getWidth());
        this.height = (int) Math.round(rect.getHeight());

        this.available_x = (int) Math.round(availableRect.getMinX());
        this.available_y = (int) Math.round(availableRect.getMinY());
        this.available_width = (int) Math.round(availableRect.getWidth());
        this.available_height = (int) Math.round(availableRect.getHeight());
    }
}
