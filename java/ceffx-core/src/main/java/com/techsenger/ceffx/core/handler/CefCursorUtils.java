/*
 * Copyright 2026 Pavel Castornii.
 *
 * Licensed under the BSD 3-Clause License. See LICENSE file for details.
 */

package com.techsenger.ceffx.core.handler;

import javafx.scene.Cursor;

/**
 *
 * @author Pavel Castornii
 */
public final class CefCursorUtils {

    public static Cursor getCursor(int cefCursorType) {
        switch (cefCursorType) {
            case 0: // CT_POINTER
                return Cursor.DEFAULT;

            case 1: // CT_CROSS
                return Cursor.CROSSHAIR;

            case 2: // CT_HAND
                return Cursor.HAND;

            case 3: // CT_IBEAM
                return Cursor.TEXT;

            case 4: // CT_WAIT
                return Cursor.WAIT;

            case 5: // CT_HELP
                return Cursor.DEFAULT; // нет аналога

            case 6: // CT_EASTRESIZE
                return Cursor.E_RESIZE;

            case 7: // CT_NORTHRESIZE
                return Cursor.N_RESIZE;

            case 8: // CT_NORTHEASTRESIZE
                return Cursor.NE_RESIZE;

            case 9: // CT_NORTHWESTRESIZE
                return Cursor.NW_RESIZE;

            case 10: // CT_SOUTHRESIZE
                return Cursor.S_RESIZE;

            case 11: // CT_SOUTHEASTRESIZE
                return Cursor.SE_RESIZE;

            case 12: // CT_SOUTHWESTRESIZE
                return Cursor.SW_RESIZE;

            case 13: // CT_WESTRESIZE
                return Cursor.W_RESIZE;

            case 14: // CT_NORTHSOUTHRESIZE
                return Cursor.V_RESIZE;

            case 15: // CT_EASTWESTRESIZE
                return Cursor.H_RESIZE;

            case 16: // CT_NORTHEASTSOUTHWESTRESIZE
                return Cursor.NE_RESIZE;

            case 17: // CT_NORTHWESTSOUTHEASTRESIZE
                return Cursor.NW_RESIZE;

            case 18: // CT_COLUMNRESIZE
                return Cursor.H_RESIZE;

            case 19: // CT_ROWRESIZE
                return Cursor.V_RESIZE;

            case 20: // CT_MIDDLEPANNING
            case 21: // CT_EASTPANNING
            case 22: // CT_NORTHPANNING
            case 23: // CT_NORTHEASTPANNING
            case 24: // CT_NORTHWESTPANNING
            case 25: // CT_SOUTHPANNING
            case 26: // CT_SOUTHEASTPANNING
            case 27: // CT_SOUTHWESTPANNING
            case 28: // CT_WESTPANNING
                return Cursor.MOVE;

            case 29: // CT_MOVE
                return Cursor.MOVE;

            case 30: // CT_VERTICALTEXT
                return Cursor.TEXT;

            case 31: // CT_CELL
                return Cursor.CROSSHAIR;

            case 32: // CT_CONTEXTMENU
                return Cursor.DEFAULT;

            case 33: // CT_ALIAS
            case 34: // CT_COPY
                return Cursor.HAND;

            case 35: // CT_NODROP
            case 36: // CT_NOTALLOWED
                return Cursor.DEFAULT;

            case 37: // CT_ZOOMIN
            case 38: // CT_ZOOMOUT
                return Cursor.DEFAULT;

            case 39: // CT_GRAB
            case 40: // CT_GRABBING
                return Cursor.MOVE;

            case 41: // CT_CUSTOM
                return Cursor.DEFAULT;

            default:
                return Cursor.DEFAULT;
        }
    }

    private CefCursorUtils() {
        // empty
    }
}
