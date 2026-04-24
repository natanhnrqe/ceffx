/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.core.browser;

import javafx.scene.input.DragEvent;

/**
 *
 * @author Pavel Castornii
 */
public interface DropTargetListener {

    void dragEntered(DragEvent event);

    void dragExited(DragEvent event);

    void dragOver(DragEvent event);

    void drop(DragEvent event);
}