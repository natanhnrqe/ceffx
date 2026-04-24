// Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.
#include "CefBrowser_N.h"
#include "include/base/cef_callback.h"
#include "include/cef_browser.h"
#include "include/cef_parser.h"
#include "include/cef_task.h"
#include "include/wrapper/cef_closure_task.h"
#include "browser_process_handler.h"
#include "client_handler.h"
#include "critical_wait.h"
#include "devtools_message_observer.h"
#include "int_callback.h"
#include "jni_util.h"
#include "life_span_handler.h"
#include "pdf_print_callback.h"
#include "render_handler.h"
#include "run_file_dialog_callback.h"
#include "string_visitor.h"
#include "temp_window.h"
#include "window_handler.h"
#if defined(OS_LINUX)
#define XK_3270  // for XK_3270_BackTab
#include <X11/XF86keysym.h>
#include <X11/keysym.h>
#include <memory>
#endif
#if defined(OS_MACOSX)
#include <Carbon/Carbon.h>
#include "util_mac.h"
#endif
#if defined(OS_WIN)
#include <memory>
#undef MOUSE_MOVED
#endif
namespace {
int GetCefModifiers(JNIEnv* env, jclass cls, int modifiers) {
  JNI_STATIC_DEFINE_INT_RV(env, cls, ALT_DOWN_MASK, 0);
  JNI_STATIC_DEFINE_INT_RV(env, cls, BUTTON1_DOWN_MASK, 0);
  JNI_STATIC_DEFINE_INT_RV(env, cls, BUTTON2_DOWN_MASK, 0);
  JNI_STATIC_DEFINE_INT_RV(env, cls, BUTTON3_DOWN_MASK, 0);
  JNI_STATIC_DEFINE_INT_RV(env, cls, CTRL_DOWN_MASK, 0);
  JNI_STATIC_DEFINE_INT_RV(env, cls, META_DOWN_MASK, 0);
  JNI_STATIC_DEFINE_INT_RV(env, cls, SHIFT_DOWN_MASK, 0);
  int cef_modifiers = 0;
  if (modifiers & JNI_STATIC(ALT_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_ALT_DOWN;
  if (modifiers & JNI_STATIC(BUTTON1_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_LEFT_MOUSE_BUTTON;
  if (modifiers & JNI_STATIC(BUTTON2_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_MIDDLE_MOUSE_BUTTON;
  if (modifiers & JNI_STATIC(BUTTON3_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_RIGHT_MOUSE_BUTTON;
  if (modifiers & JNI_STATIC(CTRL_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_CONTROL_DOWN;
  if (modifiers & JNI_STATIC(META_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_COMMAND_DOWN;
  if (modifiers & JNI_STATIC(SHIFT_DOWN_MASK))
    cef_modifiers |= EVENTFLAG_SHIFT_DOWN;
  return cef_modifiers;
}

int GetCefModifiersFromJavaFX(JNIEnv* env, jobject event) {
  int cef_modifiers = 0;

  jclass eventClass = env->GetObjectClass(event);
  if (!eventClass)
    return 0;

  jmethodID isCtrlDownMethod = env->GetMethodID(eventClass, "isControlDown", "()Z");
  jmethodID isShiftDownMethod = env->GetMethodID(eventClass, "isShiftDown", "()Z");
  jmethodID isAltDownMethod = env->GetMethodID(eventClass, "isAltDown", "()Z");
  jmethodID isMetaDownMethod = env->GetMethodID(eventClass, "isMetaDown", "()Z");

  if (isCtrlDownMethod && env->CallBooleanMethod(event, isCtrlDownMethod))
    cef_modifiers |= EVENTFLAG_CONTROL_DOWN;
  if (isShiftDownMethod && env->CallBooleanMethod(event, isShiftDownMethod))
    cef_modifiers |= EVENTFLAG_SHIFT_DOWN;
  if (isAltDownMethod && env->CallBooleanMethod(event, isAltDownMethod))
    cef_modifiers |= EVENTFLAG_ALT_DOWN;
  if (isMetaDownMethod && env->CallBooleanMethod(event, isMetaDownMethod))
    cef_modifiers |= EVENTFLAG_COMMAND_DOWN;

  return cef_modifiers;
}

int GetCefModifiersFromJavaFXMouse(JNIEnv* env, jobject event) {
  int cef_modifiers = 0;

  jclass eventClass = env->GetObjectClass(event);
  if (!eventClass)
    return 0;

  jmethodID isPrimaryDownMethod = env->GetMethodID(eventClass, "isPrimaryButtonDown", "()Z");
  jmethodID isSecondaryDownMethod = env->GetMethodID(eventClass, "isSecondaryButtonDown", "()Z");
  jmethodID isMiddleDownMethod = env->GetMethodID(eventClass, "isMiddleButtonDown", "()Z");

  jmethodID isCtrlDownMethod = env->GetMethodID(eventClass, "isControlDown", "()Z");
  jmethodID isShiftDownMethod = env->GetMethodID(eventClass, "isShiftDown", "()Z");
  jmethodID isAltDownMethod = env->GetMethodID(eventClass, "isAltDown", "()Z");
  jmethodID isMetaDownMethod = env->GetMethodID(eventClass, "isMetaDown", "()Z");

  if (isPrimaryDownMethod && env->CallBooleanMethod(event, isPrimaryDownMethod))
    cef_modifiers |= EVENTFLAG_LEFT_MOUSE_BUTTON;
  if (isSecondaryDownMethod && env->CallBooleanMethod(event, isSecondaryDownMethod))
    cef_modifiers |= EVENTFLAG_RIGHT_MOUSE_BUTTON;
  if (isMiddleDownMethod && env->CallBooleanMethod(event, isMiddleDownMethod))
    cef_modifiers |= EVENTFLAG_MIDDLE_MOUSE_BUTTON;

  if (isCtrlDownMethod && env->CallBooleanMethod(event, isCtrlDownMethod))
    cef_modifiers |= EVENTFLAG_CONTROL_DOWN;
  if (isShiftDownMethod && env->CallBooleanMethod(event, isShiftDownMethod))
    cef_modifiers |= EVENTFLAG_SHIFT_DOWN;
  if (isAltDownMethod && env->CallBooleanMethod(event, isAltDownMethod))
    cef_modifiers |= EVENTFLAG_ALT_DOWN;
  if (isMetaDownMethod && env->CallBooleanMethod(event, isMetaDownMethod))
    cef_modifiers |= EVENTFLAG_COMMAND_DOWN;

  return cef_modifiers;
}

static std::unordered_map<std::string, int> FX_TO_VK = {
    // letters
    {"A", 0x41}, {"B", 0x42}, {"C", 0x43}, {"D", 0x44},
    {"E", 0x45}, {"F", 0x46}, {"G", 0x47}, {"H", 0x48},
    {"I", 0x49}, {"J", 0x4A}, {"K", 0x4B}, {"L", 0x4C},
    {"M", 0x4D}, {"N", 0x4E}, {"O", 0x4F}, {"P", 0x50},
    {"Q", 0x51}, {"R", 0x52}, {"S", 0x53}, {"T", 0x54},
    {"U", 0x55}, {"V", 0x56}, {"W", 0x57}, {"X", 0x58},
    {"Y", 0x59}, {"Z", 0x5A},

    // numbers
    {"DIGIT0", 0x30}, {"DIGIT1", 0x31}, {"DIGIT2", 0x32},
    {"DIGIT3", 0x33}, {"DIGIT4", 0x34}, {"DIGIT5", 0x35},
    {"DIGIT6", 0x36}, {"DIGIT7", 0x37}, {"DIGIT8", 0x38},
    {"DIGIT9", 0x39},

    // arrows
    {"LEFT", 0x25},
    {"UP", 0x26},
    {"RIGHT", 0x27},
    {"DOWN", 0x28},

    // control keys
    {"ENTER", 0x0D},
    {"TAB", 0x09},
    {"ESCAPE", 0x1B},
    {"SPACE", 0x20},

    {"BACK_SPACE", 0x08},
    {"DELETE", 0x2E},

    {"HOME", 0x24},
    {"END", 0x23},
    {"PAGE_UP", 0x21},
    {"PAGE_DOWN", 0x22},

    // modifiers (НЕ обязательно, но пусть будут)
    {"SHIFT", 0x10},
    {"CONTROL", 0x11},
    {"ALT", 0x12},
    {"META", 0x5B},

    // function keys
    {"F1", 0x70}, {"F2", 0x71}, {"F3", 0x72}, {"F4", 0x73},
    {"F5", 0x74}, {"F6", 0x75}, {"F7", 0x76}, {"F8", 0x77},
    {"F9", 0x78}, {"F10", 0x79}, {"F11", 0x7A}, {"F12", 0x7B},

    // punctuation
    {"COMMA", 0xBC},
    {"PERIOD", 0xBE},
    {"SEMICOLON", 0xBA},
    {"SLASH", 0xBF},
    {"BACK_SLASH", 0xDC},
    {"OPEN_BRACKET", 0xDB},
    {"CLOSE_BRACKET", 0xDD},
    {"MINUS", 0xBD},
    {"EQUALS", 0xBB},
    {"GRAVE", 0xC0}
};

#if defined(OS_LINUX)
// From ui/events/keycodes/keyboard_codes_posix.h.
enum KeyboardCode {
  VKEY_BACK = 0x08,
  VKEY_TAB = 0x09,
  VKEY_BACKTAB = 0x0A,
  VKEY_CLEAR = 0x0C,
  VKEY_RETURN = 0x0D,
  VKEY_SHIFT = 0x10,
  VKEY_CONTROL = 0x11,
  VKEY_MENU = 0x12,
  VKEY_PAUSE = 0x13,
  VKEY_CAPITAL = 0x14,
  VKEY_KANA = 0x15,
  VKEY_HANGUL = 0x15,
  VKEY_JUNJA = 0x17,
  VKEY_FINAL = 0x18,
  VKEY_HANJA = 0x19,
  VKEY_KANJI = 0x19,
  VKEY_ESCAPE = 0x1B,
  VKEY_CONVERT = 0x1C,
  VKEY_NONCONVERT = 0x1D,
  VKEY_ACCEPT = 0x1E,
  VKEY_MODECHANGE = 0x1F,
  VKEY_SPACE = 0x20,
  VKEY_PRIOR = 0x21,
  VKEY_NEXT = 0x22,
  VKEY_END = 0x23,
  VKEY_HOME = 0x24,
  VKEY_LEFT = 0x25,
  VKEY_UP = 0x26,
  VKEY_RIGHT = 0x27,
  VKEY_DOWN = 0x28,
  VKEY_SELECT = 0x29,
  VKEY_PRINT = 0x2A,
  VKEY_EXECUTE = 0x2B,
  VKEY_SNAPSHOT = 0x2C,
  VKEY_INSERT = 0x2D,
  VKEY_DELETE = 0x2E,
  VKEY_HELP = 0x2F,
  VKEY_0 = 0x30,
  VKEY_1 = 0x31,
  VKEY_2 = 0x32,
  VKEY_3 = 0x33,
  VKEY_4 = 0x34,
  VKEY_5 = 0x35,
  VKEY_6 = 0x36,
  VKEY_7 = 0x37,
  VKEY_8 = 0x38,
  VKEY_9 = 0x39,
  VKEY_A = 0x41,
  VKEY_B = 0x42,
  VKEY_C = 0x43,
  VKEY_D = 0x44,
  VKEY_E = 0x45,
  VKEY_F = 0x46,
  VKEY_G = 0x47,
  VKEY_H = 0x48,
  VKEY_I = 0x49,
  VKEY_J = 0x4A,
  VKEY_K = 0x4B,
  VKEY_L = 0x4C,
  VKEY_M = 0x4D,
  VKEY_N = 0x4E,
  VKEY_O = 0x4F,
  VKEY_P = 0x50,
  VKEY_Q = 0x51,
  VKEY_R = 0x52,
  VKEY_S = 0x53,
  VKEY_T = 0x54,
  VKEY_U = 0x55,
  VKEY_V = 0x56,
  VKEY_W = 0x57,
  VKEY_X = 0x58,
  VKEY_Y = 0x59,
  VKEY_Z = 0x5A,
  VKEY_LWIN = 0x5B,
  VKEY_COMMAND = VKEY_LWIN,  // Provide the Mac name for convenience.
  VKEY_RWIN = 0x5C,
  VKEY_APPS = 0x5D,
  VKEY_SLEEP = 0x5F,
  VKEY_NUMPAD0 = 0x60,
  VKEY_NUMPAD1 = 0x61,
  VKEY_NUMPAD2 = 0x62,
  VKEY_NUMPAD3 = 0x63,
  VKEY_NUMPAD4 = 0x64,
  VKEY_NUMPAD5 = 0x65,
  VKEY_NUMPAD6 = 0x66,
  VKEY_NUMPAD7 = 0x67,
  VKEY_NUMPAD8 = 0x68,
  VKEY_NUMPAD9 = 0x69,
  VKEY_MULTIPLY = 0x6A,
  VKEY_ADD = 0x6B,
  VKEY_SEPARATOR = 0x6C,
  VKEY_SUBTRACT = 0x6D,
  VKEY_DECIMAL = 0x6E,
  VKEY_DIVIDE = 0x6F,
  VKEY_F1 = 0x70,
  VKEY_F2 = 0x71,
  VKEY_F3 = 0x72,
  VKEY_F4 = 0x73,
  VKEY_F5 = 0x74,
  VKEY_F6 = 0x75,
  VKEY_F7 = 0x76,
  VKEY_F8 = 0x77,
  VKEY_F9 = 0x78,
  VKEY_F10 = 0x79,
  VKEY_F11 = 0x7A,
  VKEY_F12 = 0x7B,
  VKEY_F13 = 0x7C,
  VKEY_F14 = 0x7D,
  VKEY_F15 = 0x7E,
  VKEY_F16 = 0x7F,
  VKEY_F17 = 0x80,
  VKEY_F18 = 0x81,
  VKEY_F19 = 0x82,
  VKEY_F20 = 0x83,
  VKEY_F21 = 0x84,
  VKEY_F22 = 0x85,
  VKEY_F23 = 0x86,
  VKEY_F24 = 0x87,
  VKEY_NUMLOCK = 0x90,
  VKEY_SCROLL = 0x91,
  VKEY_LSHIFT = 0xA0,
  VKEY_RSHIFT = 0xA1,
  VKEY_LCONTROL = 0xA2,
  VKEY_RCONTROL = 0xA3,
  VKEY_LMENU = 0xA4,
  VKEY_RMENU = 0xA5,
  VKEY_BROWSER_BACK = 0xA6,
  VKEY_BROWSER_FORWARD = 0xA7,
  VKEY_BROWSER_REFRESH = 0xA8,
  VKEY_BROWSER_STOP = 0xA9,
  VKEY_BROWSER_SEARCH = 0xAA,
  VKEY_BROWSER_FAVORITES = 0xAB,
  VKEY_BROWSER_HOME = 0xAC,
  VKEY_VOLUME_MUTE = 0xAD,
  VKEY_VOLUME_DOWN = 0xAE,
  VKEY_VOLUME_UP = 0xAF,
  VKEY_MEDIA_NEXT_TRACK = 0xB0,
  VKEY_MEDIA_PREV_TRACK = 0xB1,
  VKEY_MEDIA_STOP = 0xB2,
  VKEY_MEDIA_PLAY_PAUSE = 0xB3,
  VKEY_MEDIA_LAUNCH_MAIL = 0xB4,
  VKEY_MEDIA_LAUNCH_MEDIA_SELECT = 0xB5,
  VKEY_MEDIA_LAUNCH_APP1 = 0xB6,
  VKEY_MEDIA_LAUNCH_APP2 = 0xB7,
  VKEY_OEM_1 = 0xBA,
  VKEY_OEM_PLUS = 0xBB,
  VKEY_OEM_COMMA = 0xBC,
  VKEY_OEM_MINUS = 0xBD,
  VKEY_OEM_PERIOD = 0xBE,
  VKEY_OEM_2 = 0xBF,
  VKEY_OEM_3 = 0xC0,
  VKEY_OEM_4 = 0xDB,
  VKEY_OEM_5 = 0xDC,
  VKEY_OEM_6 = 0xDD,
  VKEY_OEM_7 = 0xDE,
  VKEY_OEM_8 = 0xDF,
  VKEY_OEM_102 = 0xE2,
  VKEY_OEM_103 = 0xE3,  // GTV KEYCODE_MEDIA_REWIND
  VKEY_OEM_104 = 0xE4,  // GTV KEYCODE_MEDIA_FAST_FORWARD
  VKEY_PROCESSKEY = 0xE5,
  VKEY_PACKET = 0xE7,
  VKEY_DBE_SBCSCHAR = 0xF3,
  VKEY_DBE_DBCSCHAR = 0xF4,
  VKEY_ATTN = 0xF6,
  VKEY_CRSEL = 0xF7,
  VKEY_EXSEL = 0xF8,
  VKEY_EREOF = 0xF9,
  VKEY_PLAY = 0xFA,
  VKEY_ZOOM = 0xFB,
  VKEY_NONAME = 0xFC,
  VKEY_PA1 = 0xFD,
  VKEY_OEM_CLEAR = 0xFE,
  VKEY_UNKNOWN = 0,
  // POSIX specific VKEYs. Note that as of Windows SDK 7.1, 0x97-9F, 0xD8-DA,
  // and 0xE8 are unassigned.
  VKEY_WLAN = 0x97,
  VKEY_POWER = 0x98,
  VKEY_BRIGHTNESS_DOWN = 0xD8,
  VKEY_BRIGHTNESS_UP = 0xD9,
  VKEY_KBD_BRIGHTNESS_DOWN = 0xDA,
  VKEY_KBD_BRIGHTNESS_UP = 0xE8,
  // Windows does not have a specific key code for AltGr. We use the unused 0xE1
  // (VK_OEM_AX) code to represent AltGr, matching the behaviour of Firefox on
  // Linux.
  VKEY_ALTGR = 0xE1,
  // Windows does not have a specific key code for Compose. We use the unused
  // 0xE6 (VK_ICO_CLEAR) code to represent Compose.
  VKEY_COMPOSE = 0xE6,
};
#endif  // defined(OS_LINUX)
#if defined(OS_MACOSX)
// A convenient array for getting symbol characters on the number keys.
const char kShiftCharsForNumberKeys[] = ")!@#$%^&*(";
// Convert an ANSI character to a Mac key code.
int GetMacKeyCodeFromChar(int key_char) {
  switch (key_char) {
    case ' ':
      return kVK_Space;
    case '0':
    case ')':
      return kVK_ANSI_0;
    case '1':
    case '!':
      return kVK_ANSI_1;
    case '2':
    case '@':
      return kVK_ANSI_2;
    case '3':
    case '#':
      return kVK_ANSI_3;
    case '4':
    case '$':
      return kVK_ANSI_4;
    case '5':
    case '%':
      return kVK_ANSI_5;
    case '6':
    case '^':
      return kVK_ANSI_6;
    case '7':
    case '&':
      return kVK_ANSI_7;
    case '8':
    case '*':
      return kVK_ANSI_8;
    case '9':
    case '(':
      return kVK_ANSI_9;
    case 'a':
    case 'A':
      return kVK_ANSI_A;
    case 'b':
    case 'B':
      return kVK_ANSI_B;
    case 'c':
    case 'C':
      return kVK_ANSI_C;
    case 'd':
    case 'D':
      return kVK_ANSI_D;
    case 'e':
    case 'E':
      return kVK_ANSI_E;
    case 'f':
    case 'F':
      return kVK_ANSI_F;
    case 'g':
    case 'G':
      return kVK_ANSI_G;
    case 'h':
    case 'H':
      return kVK_ANSI_H;
    case 'i':
    case 'I':
      return kVK_ANSI_I;
    case 'j':
    case 'J':
      return kVK_ANSI_J;
    case 'k':
    case 'K':
      return kVK_ANSI_K;
    case 'l':
    case 'L':
      return kVK_ANSI_L;
    case 'm':
    case 'M':
      return kVK_ANSI_M;
    case 'n':
    case 'N':
      return kVK_ANSI_N;
    case 'o':
    case 'O':
      return kVK_ANSI_O;
    case 'p':
    case 'P':
      return kVK_ANSI_P;
    case 'q':
    case 'Q':
      return kVK_ANSI_Q;
    case 'r':
    case 'R':
      return kVK_ANSI_R;
    case 's':
    case 'S':
      return kVK_ANSI_S;
    case 't':
    case 'T':
      return kVK_ANSI_T;
    case 'u':
    case 'U':
      return kVK_ANSI_U;
    case 'v':
    case 'V':
      return kVK_ANSI_V;
    case 'w':
    case 'W':
      return kVK_ANSI_W;
    case 'x':
    case 'X':
      return kVK_ANSI_X;
    case 'y':
    case 'Y':
      return kVK_ANSI_Y;
    case 'z':
    case 'Z':
      return kVK_ANSI_Z;
    // U.S. Specific mappings.  Mileage may vary.
    case ';':
    case ':':
      return kVK_ANSI_Semicolon;
    case '=':
    case '+':
      return kVK_ANSI_Equal;
    case ',':
    case '<':
      return kVK_ANSI_Comma;
    case '-':
    case '_':
      return kVK_ANSI_Minus;
    case '.':
    case '>':
      return kVK_ANSI_Period;
    case '/':
    case '?':
      return kVK_ANSI_Slash;
    case '`':
    case '~':
      return kVK_ANSI_Grave;
    case '[':
    case '{':
      return kVK_ANSI_LeftBracket;
    case '\\':
    case '|':
      return kVK_ANSI_Backslash;
    case ']':
    case '}':
      return kVK_ANSI_RightBracket;
    case '\'':
    case '"':
      return kVK_ANSI_Quote;
  }
  return -1;
}
#endif  // defined(OS_MACOSX)
struct JNIObjectsForCreate {
 public:
  ScopedJNIObjectGlobal jbrowser;
  ScopedJNIObjectGlobal jparentBrowser;
  ScopedJNIObjectGlobal jclientHandler;
  ScopedJNIObjectGlobal url;
  ScopedJNIObjectGlobal jcontext;
  ScopedJNIObjectGlobal jinspectAt;
  ScopedJNIObjectGlobal jbrowserSettings;
  JNIObjectsForCreate(JNIEnv* env,
                      jobject _jbrowser,
                      jobject _jparentBrowser,
                      jobject _jclientHandler,
                      jstring _url,
                      jobject _jcontext,
                      jobject _jinspectAt,
                      jobject _browserSettings)
      :
        jbrowser(env, _jbrowser),
        jparentBrowser(env, _jparentBrowser),
        jclientHandler(env, _jclientHandler),
        url(env, _url),
        jcontext(env, _jcontext),
        jinspectAt(env, _jinspectAt),
        jbrowserSettings(env, _browserSettings) {}
};

void create(std::shared_ptr<JNIObjectsForCreate> objs,
            jlong windowHandle,
            jboolean osr,
            jboolean transparent) {
  ScopedJNIEnv env;
  CefRefPtr<ClientHandler> clientHandler = GetCefFromJNIObject<ClientHandler>(
      env, objs->jclientHandler, "CefClientHandler");
  if (!clientHandler.get())
    return;
  CefRefPtr<LifeSpanHandler> lifeSpanHandler =
      (LifeSpanHandler*)clientHandler->GetLifeSpanHandler().get();
  if (!lifeSpanHandler.get())
    return;
  CefRefPtr<CefBrowser> parentBrowser =
      GetCefFromJNIObject<CefBrowser>(env, objs->jparentBrowser, "CefBrowser");
  CefWindowInfo windowInfo;
  CefBrowserSettings settings;
  // If parentBrowser is set, we want to show the DEV-Tools for that browser.
  // Since that cannot be an Alloy-style window, it cannot be integrated into
  // Java UI but must be opened as a pop-up.
  if (parentBrowser.get() != nullptr) {
    CefPoint inspectAt;
    if (objs->jinspectAt != nullptr) {
      int x, y;
      GetJNIPoint2D(env, objs->jinspectAt, &x, &y);
      inspectAt.Set(x, y);
    }
    parentBrowser->GetHost()->ShowDevTools(windowInfo, clientHandler.get(),
                                           settings, inspectAt);
    JNI_CALL_VOID_METHOD(env, objs->jbrowser, "notifyBrowserCreated", "()V");
    return;
  }
  if (osr == JNI_FALSE) {
    CefRect rect;
    CefRefPtr<WindowHandler> windowHandler =
        (WindowHandler*)clientHandler->GetWindowHandler().get();
    if (windowHandler.get()) {
      windowHandler->GetRect(objs->jbrowser, rect);
    }
#if defined(OS_WIN)
    CefWindowHandle parent = TempWindow::GetWindowHandle();
    if (windowHandle != 0) {
      parent = (CefWindowHandle)windowHandle;
    } else {
      // Do not activate hidden browser windows on creation.
      windowInfo.ex_style |= WS_EX_NOACTIVATE;
    }
    windowInfo.SetAsChild(parent, rect);
#elif defined(OS_MACOSX)
    NSWindow* parent = nullptr;
    if (windowHandle != 0) {
      parent = (NSWindow*)windowHandle;
    } else {
      parent = TempWindow::GetWindow();
    }
    CefWindowHandle browserContentView =
        util_mac::CreateBrowserContentView(parent, rect);
    windowInfo.SetAsChild(browserContentView, rect);
#elif defined(OS_LINUX)
    CefWindowHandle parent = TempWindow::GetWindowHandle();
    if (windowHandle != 0) {
      parent = (CefWindowHandle)windowHandle;
    }
    windowInfo.SetAsChild(parent, rect);
#endif
  } else {
    windowInfo.SetAsWindowless((CefWindowHandle)windowHandle);
  }
  if (transparent == JNI_FALSE) {
    // Specify an opaque background color (white) to disable transparency.
    settings.background_color = CefColorSetARGB(255, 255, 255, 255);
  }
  ScopedJNIClass cefBrowserSettings(env, "com/techsenger/ceffx/core/CefBrowserSettings");
  if (cefBrowserSettings != nullptr &&
      objs->jbrowserSettings != nullptr) {  // Dev-tools settings are null
    GetJNIFieldInt(env, cefBrowserSettings, objs->jbrowserSettings,
                   "windowless_frame_rate", &settings.windowless_frame_rate);
  }
  CefRefPtr<CefBrowser> browserObj;
  CefString strUrl = GetJNIString(env, static_cast<jstring>(objs->url.get()));
  CefRefPtr<CefRequestContext> context = GetCefFromJNIObject<CefRequestContext>(
      env, objs->jcontext, "CefRequestContext");
  // Add a global ref that will be released in LifeSpanHandler::OnAfterCreated.
  jobject globalRef = env->NewGlobalRef(objs->jbrowser);
  lifeSpanHandler->registerJBrowser(globalRef);
  CefRefPtr<CefDictionaryValue> extra_info;
  auto router_configs = BrowserProcessHandler::GetMessageRouterConfigs();
  if (router_configs) {
    // Send the message router config to CefHelperApp::OnBrowserCreated.
    extra_info = CefDictionaryValue::Create();
    extra_info->SetList("router_configs", router_configs);
  }
  // CEFFX requires Alloy runtime style for "normal" browsers in order for them
  // to be integratable into Java UI.
  windowInfo.runtime_style = CEF_RUNTIME_STYLE_ALLOY;
  bool result = CefBrowserHost::CreateBrowser(
      windowInfo, clientHandler.get(), strUrl, settings, extra_info, context);
  if (!result) {
    lifeSpanHandler->unregisterJBrowser(globalRef);
    env->DeleteGlobalRef(globalRef);
    return;
  }
  JNI_CALL_VOID_METHOD(env, objs->jbrowser, "notifyBrowserCreated", "()V");
}

void getZoomLevel(CefRefPtr<CefBrowserHost> host,
                  CriticalWait* waitCond,
                  double* result) {
  if (waitCond && result) {
    waitCond->lock()->Lock();
    *result = host->GetZoomLevel();
    waitCond->WakeUp();
    waitCond->lock()->Unlock();
  }
}
void executeDevToolsMethod(CefRefPtr<CefBrowserHost> host,
                           const CefString& method,
                           const CefString& parametersAsJson,
                           CefRefPtr<IntCallback> callback) {
  CefRefPtr<CefDictionaryValue> parameters = nullptr;
  if (!parametersAsJson.empty()) {
    CefRefPtr<CefValue> value = CefParseJSON(
        parametersAsJson, cef_json_parser_options_t::JSON_PARSER_RFC);
    if (!value || value->GetType() != VTYPE_DICTIONARY) {
      callback->onComplete(0);
      return;
    }
    parameters = value->GetDictionary();
  }
  callback->onComplete(host->ExecuteDevToolsMethod(0, method, parameters));
}
void OnAfterParentChanged(CefRefPtr<CefBrowser> browser) {
  if (!CefCurrentlyOn(TID_UI)) {
    CefPostTask(TID_UI, base::BindOnce(&OnAfterParentChanged, browser));
    return;
  }
  if (browser->GetHost()->GetClient()) {
    CefRefPtr<LifeSpanHandler> lifeSpanHandler =
        (LifeSpanHandler*)browser->GetHost()
            ->GetClient()
            ->GetLifeSpanHandler()
            .get();
    if (lifeSpanHandler) {
      lifeSpanHandler->OnAfterParentChanged(browser);
    }
  }
}
CefPdfPrintSettings GetJNIPdfPrintSettings(JNIEnv* env, jobject obj) {
  CefString tmp;
  CefPdfPrintSettings settings;
  if (!obj)
    return settings;
  ScopedJNIClass cls(env, "com/techsenger/ceffx/core/misc/CefPdfPrintSettings");
  if (!cls)
    return settings;
  GetJNIFieldBoolean(env, cls, obj, "landscape", &settings.landscape);
  GetJNIFieldBoolean(env, cls, obj, "print_background",
                     &settings.print_background);
  GetJNIFieldDouble(env, cls, obj, "scale", &settings.scale);
  GetJNIFieldDouble(env, cls, obj, "paper_width", &settings.paper_width);
  GetJNIFieldDouble(env, cls, obj, "paper_height", &settings.paper_height);
  GetJNIFieldBoolean(env, cls, obj, "prefer_css_page_size",
                     &settings.prefer_css_page_size);
  jobject obj_margin_type = nullptr;
  if (GetJNIFieldObject(env, cls, obj, "margin_type", &obj_margin_type,
                        "Lcom/techsenger/ceffx/core/misc/CefPdfPrintSettings$MarginType;")) {
    ScopedJNIObjectLocal margin_type(env, obj_margin_type);
    if (IsJNIEnumValue(env, margin_type,
                       "com/techsenger/ceffx/core/misc/CefPdfPrintSettings$MarginType",
                       "DEFAULT")) {
      settings.margin_type = PDF_PRINT_MARGIN_DEFAULT;
    } else if (IsJNIEnumValue(env, margin_type,
                              "com/techsenger/ceffx/core/misc/CefPdfPrintSettings$MarginType",
                              "NONE")) {
      settings.margin_type = PDF_PRINT_MARGIN_NONE;
    } else if (IsJNIEnumValue(env, margin_type,
                              "com/techsenger/ceffx/core/misc/CefPdfPrintSettings$MarginType",
                              "CUSTOM")) {
      settings.margin_type = PDF_PRINT_MARGIN_CUSTOM;
    }
  }
  GetJNIFieldDouble(env, cls, obj, "margin_top", &settings.margin_top);
  GetJNIFieldDouble(env, cls, obj, "margin_bottom", &settings.margin_bottom);
  GetJNIFieldDouble(env, cls, obj, "margin_right", &settings.margin_right);
  GetJNIFieldDouble(env, cls, obj, "margin_left", &settings.margin_left);
  if (GetJNIFieldString(env, cls, obj, "page_ranges", &tmp) && !tmp.empty()) {
    CefString(&settings.page_ranges) = tmp;
    tmp.clear();
  }
  GetJNIFieldBoolean(env, cls, obj, "display_header_footer",
                     &settings.display_header_footer);
  if (GetJNIFieldString(env, cls, obj, "header_template", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.header_template) = tmp;
    tmp.clear();
  }
  if (GetJNIFieldString(env, cls, obj, "footer_template", &tmp) &&
      !tmp.empty()) {
    CefString(&settings.footer_template) = tmp;
    tmp.clear();
  }
  GetJNIFieldBoolean(env, cls, obj, "generate_tagged_pdf",
                     &settings.generate_tagged_pdf);
  GetJNIFieldBoolean(env, cls, obj, "generate_document_outline",
                     &settings.generate_document_outline);
  return settings;
}
// JNI CefRegistration object.
class ScopedJNIRegistration : public ScopedJNIObject<CefRegistration> {
 public:
  ScopedJNIRegistration(JNIEnv* env, CefRefPtr<CefRegistration> obj)
      : ScopedJNIObject<CefRegistration>(env,
                                         obj,
                                         "com/techsenger/ceffx/core/browser/CefRegistration_N",
                                         "CefRegistration") {}
};
}  // namespace
JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1CreateBrowser(JNIEnv* env,
                                                    jobject jbrowser,
                                                    jobject jclientHandler,
                                                    jlong windowHandle,
                                                    jstring url,
                                                    jboolean osr,
                                                    jboolean transparent,
                                                    jobject jcontext,
                                                    jobject browserSettings) {
  std::shared_ptr<JNIObjectsForCreate> objs(
      new JNIObjectsForCreate(env, jbrowser, nullptr, jclientHandler, url,
                              jcontext, nullptr, browserSettings));
  if (CefCurrentlyOn(TID_UI)) {
    create(objs, windowHandle, osr, transparent);
  } else {
    CefPostTask(TID_UI,
                base::BindOnce(&create, objs, windowHandle, osr, transparent));
  }
  return JNI_FALSE;  // set asynchronously
}

JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1CreateDevTools(
    JNIEnv* env,
    jobject jbrowser,
    jobject jparent,
    jobject jclientHandler,
    jlong windowHandle,
    jboolean osr,
    jboolean transparent,
    jobject inspect) {

  std::shared_ptr<JNIObjectsForCreate> objs(
      new JNIObjectsForCreate(env,
                              jbrowser,
                              jparent,
                              jclientHandler,
                              nullptr,
                              nullptr,
                              inspect,
                              nullptr));

  if (CefCurrentlyOn(TID_UI)) {
    create(objs, windowHandle, osr, transparent);
  } else {
    CefPostTask(TID_UI,
                base::BindOnce(&create, objs, windowHandle, osr, transparent));
  }

  return JNI_FALSE;  // async operation
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1ExecuteDevToolsMethod(
    JNIEnv* env,
    jobject jbrowser,
    jstring method,
    jstring parametersAsJson,
    jobject jcallback) {
  CefRefPtr<IntCallback> callback = new IntCallback(env, jcallback);
  CefRefPtr<CefBrowser> browser = GetJNIBrowser(env, jbrowser);
  if (!browser.get()) {
    callback->onComplete(0);
    return;
  }
  CefString strMethod = GetJNIString(env, method);
  CefString strParametersAsJson = GetJNIString(env, parametersAsJson);
  if (CefCurrentlyOn(TID_UI)) {
    executeDevToolsMethod(browser->GetHost(), strMethod, strParametersAsJson,
                          callback);
  } else {
    CefPostTask(TID_UI,
                base::BindOnce(executeDevToolsMethod, browser->GetHost(),
                               strMethod, strParametersAsJson, callback));
  }
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1AddDevToolsMessageObserver(
    JNIEnv* env,
    jobject jbrowser,
    jobject jobserver) {
  CefRefPtr<CefBrowser> browser =
      JNI_GET_BROWSER_OR_RETURN(env, jbrowser, NULL);
  CefRefPtr<DevToolsMessageObserver> observer =
      new DevToolsMessageObserver(env, jobserver);
  CefRefPtr<CefRegistration> registration =
      browser->GetHost()->AddDevToolsMessageObserver(observer);
  ScopedJNIRegistration jregistration(env, registration);
  return jregistration.Release();
}
JNIEXPORT jlong JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetWindowHandle(JNIEnv* env,
                                                      jobject obj,
                                                      jlong displayHandle) {
  CefWindowHandle windowHandle = kNullWindowHandle;
#if defined(OS_WIN)
  windowHandle = ::WindowFromDC((HDC)displayHandle);
#elif defined(OS_LINUX)
  return displayHandle;
#elif defined(OS_MACOSX)
  ASSERT(util_mac::IsNSView((void*)displayHandle));
#endif
  return (jlong)windowHandle;
}
JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1CanGoBack(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser =
      JNI_GET_BROWSER_OR_RETURN(env, obj, JNI_FALSE);
  return browser->CanGoBack() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GoBack(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GoBack();
}
JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1CanGoForward(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser =
      JNI_GET_BROWSER_OR_RETURN(env, obj, JNI_FALSE);
  return browser->CanGoForward() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GoForward(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GoForward();
}
JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1IsLoading(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser =
      JNI_GET_BROWSER_OR_RETURN(env, obj, JNI_FALSE);
  return browser->IsLoading() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1Reload(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->Reload();
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1ReloadIgnoreCache(JNIEnv* env,
                                                        jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->ReloadIgnoreCache();
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1StopLoad(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->StopLoad();
}
JNIEXPORT jint JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetIdentifier(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, -1);
  return browser->GetIdentifier();
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetMainFrame(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, nullptr);
  CefRefPtr<CefFrame> frame = browser->GetMainFrame();
  if (!frame)
    return nullptr;
  ScopedJNIFrame jframe(env, frame);
  return jframe.Release();
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetFocusedFrame(JNIEnv* env,
                                                      jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, nullptr);
  CefRefPtr<CefFrame> frame = browser->GetFocusedFrame();
  if (!frame)
    return nullptr;
  ScopedJNIFrame jframe(env, frame);
  return jframe.Release();
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetFrameByIdentifier(JNIEnv* env,
                                                           jobject obj,
                                                           jstring identifier) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, nullptr);
  CefRefPtr<CefFrame> frame =
      browser->GetFrameByIdentifier(GetJNIString(env, identifier));
  if (!frame)
    return nullptr;
  ScopedJNIFrame jframe(env, frame);
  return jframe.Release();
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetFrameByName(JNIEnv* env,
                                                     jobject obj,
                                                     jstring name) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, nullptr);
  CefRefPtr<CefFrame> frame = browser->GetFrameByName(GetJNIString(env, name));
  if (!frame)
    return nullptr;
  ScopedJNIFrame jframe(env, frame);
  return jframe.Release();
}
JNIEXPORT jint JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetFrameCount(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, -1);
  return (jint)browser->GetFrameCount();
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetFrameIdentifiers(JNIEnv* env,
                                                          jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, nullptr);
  std::vector<CefString> identifiers;
  browser->GetFrameIdentifiers(identifiers);
  return NewJNIStringVector(env, identifiers);
}
JNIEXPORT jobject JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetFrameNames(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, nullptr);
  std::vector<CefString> names;
  browser->GetFrameNames(names);
  return NewJNIStringVector(env, names);
}
JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1IsPopup(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser =
      JNI_GET_BROWSER_OR_RETURN(env, obj, JNI_FALSE);
  return browser->IsPopup() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1HasDocument(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser =
      JNI_GET_BROWSER_OR_RETURN(env, obj, JNI_FALSE);
  return browser->HasDocument() ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1ViewSource(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  CefRefPtr<CefFrame> mainFrame = browser->GetMainFrame();
  CefPostTask(TID_UI, base::BindOnce(&CefFrame::ViewSource, mainFrame.get()));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetSource(JNIEnv* env,
                                                jobject obj,
                                                jobject jvisitor) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetMainFrame()->GetSource(new StringVisitor(env, jvisitor));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetText(JNIEnv* env,
                                              jobject obj,
                                              jobject jvisitor) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetMainFrame()->GetText(new StringVisitor(env, jvisitor));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1LoadRequest(JNIEnv* env,
                                                  jobject obj,
                                                  jobject jrequest) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  ScopedJNIRequest requestObj(env);
  requestObj.SetHandle(jrequest, false /* should_delete */);
  CefRefPtr<CefRequest> request = requestObj.GetCefObject();
  if (!request)
    return;
  browser->GetMainFrame()->LoadRequest(request);
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1LoadURL(JNIEnv* env,
                                              jobject obj,
                                              jstring url) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetMainFrame()->LoadURL(GetJNIString(env, url));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1ExecuteJavaScript(JNIEnv* env,
                                                        jobject obj,
                                                        jstring code,
                                                        jstring url,
                                                        jint line) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetMainFrame()->ExecuteJavaScript(GetJNIString(env, code),
                                             GetJNIString(env, url), line);
}
JNIEXPORT jstring JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetURL(JNIEnv* env, jobject obj) {
  jstring tmp = NewJNIString(env, "");
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, tmp);
  return NewJNIString(env, browser->GetMainFrame()->GetURL());
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1Close(JNIEnv* env,
                                            jobject obj,
                                            jboolean force) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  if (force != JNI_FALSE) {
    if (browser->GetHost()->IsWindowRenderingDisabled()) {
      browser->GetHost()->CloseBrowser(true);
    } else {
      // Destroy the native window representation.
      if (CefCurrentlyOn(TID_UI))
        util::DestroyCefBrowser(browser);
      else
        CefPostTask(TID_UI, base::BindOnce(&util::DestroyCefBrowser, browser));
    }
  } else {
    browser->GetHost()->CloseBrowser(false);
  }
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SetFocus(JNIEnv* env,
                                               jobject obj,
                                               jboolean enable) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->SetFocus(enable != JNI_FALSE);
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SetWindowVisibility(JNIEnv* env,
                                                          jobject obj,
                                                          jboolean visible) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
#if defined(OS_MACOSX)
  if (!browser->GetHost()->IsWindowRenderingDisabled()) {
    util_mac::SetVisibility(browser->GetHost()->GetWindowHandle(),
                            visible != JNI_FALSE);
  }
#endif
}
JNIEXPORT jdouble JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetZoomLevel(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj, 0.0);
  CefRefPtr<CefBrowserHost> host = browser->GetHost();
  double result = 0.0;
  if (CefCurrentlyOn(TID_UI))
    result = host->GetZoomLevel();
  else {
    CriticalLock lock;
    CriticalWait waitCond(&lock);
    lock.Lock();
    CefPostTask(TID_UI, base::BindOnce(getZoomLevel, host, &waitCond, &result));
    waitCond.Wait(1000);
    lock.Unlock();
  }
  return result;
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SetZoomLevel(JNIEnv* env,
                                                   jobject obj,
                                                   jdouble zoom) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->SetZoomLevel(zoom);
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1RunFileDialog(JNIEnv* env,
                                                    jobject obj,
                                                    jobject jmode,
                                                    jstring jtitle,
                                                    jstring jdefaultFilePath,
                                                    jobject jacceptFilters,
                                                    jint selectedAcceptFilter,
                                                    jobject jcallback) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  std::vector<CefString> accept_types;
  GetJNIStringVector(env, jacceptFilters, accept_types);
  CefBrowserHost::FileDialogMode mode;
  if (IsJNIEnumValue(env, jmode,
                     "com/techsenger/ceffx/core/handler/CefDialogHandler$FileDialogMode",
                     "FILE_DIALOG_OPEN")) {
    mode = FILE_DIALOG_OPEN;
  } else if (IsJNIEnumValue(env, jmode,
                            "com/techsenger/ceffx/core/handler/CefDialogHandler$FileDialogMode",
                            "FILE_DIALOG_OPEN_MULTIPLE")) {
    mode = FILE_DIALOG_OPEN_MULTIPLE;
  } else if (IsJNIEnumValue(env, jmode,
                            "com/techsenger/ceffx/core/handler/CefDialogHandler$FileDialogMode",
                            "FILE_DIALOG_OPEN_FOLDER")) {
    mode = FILE_DIALOG_OPEN_FOLDER;
  } else if (IsJNIEnumValue(env, jmode,
                            "com/techsenger/ceffx/core/handler/CefDialogHandler$FileDialogMode",
                            "FILE_DIALOG_SAVE")) {
    mode = FILE_DIALOG_SAVE;
  } else {
    mode = FILE_DIALOG_OPEN;
  }
  browser->GetHost()->RunFileDialog(
      mode, GetJNIString(env, jtitle), GetJNIString(env, jdefaultFilePath),
      accept_types, new RunFileDialogCallback(env, jcallback));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1StartDownload(JNIEnv* env,
                                                    jobject obj,
                                                    jstring url) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->StartDownload(GetJNIString(env, url));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1Print(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->Print();
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1PrintToPDF(JNIEnv* env,
                                                 jobject obj,
                                                 jstring jpath,
                                                 jobject jsettings,
                                                 jobject jcallback) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  CefPdfPrintSettings settings = GetJNIPdfPrintSettings(env, jsettings);
  browser->GetHost()->PrintToPDF(GetJNIString(env, jpath), settings,
                                 new PdfPrintCallback(env, jcallback));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1Find(JNIEnv* env,
                                           jobject obj,
                                           jstring searchText,
                                           jboolean forward,
                                           jboolean matchCase,
                                           jboolean findNext) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->Find(GetJNIString(env, searchText),
                           (forward != JNI_FALSE), (matchCase != JNI_FALSE),
                           (findNext != JNI_FALSE));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1StopFinding(JNIEnv* env,
                                                  jobject obj,
                                                  jboolean clearSelection) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->StopFinding(clearSelection != JNI_FALSE);
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1CloseDevTools(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->CloseDevTools();
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1ReplaceMisspelling(JNIEnv* env,
                                                         jobject obj,
                                                         jstring jword) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->ReplaceMisspelling(GetJNIString(env, jword));
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1WasResized(JNIEnv* env,
                                                 jobject obj,
                                                 jint width,
                                                 jint height) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  if (browser->GetHost()->IsWindowRenderingDisabled()) {
    browser->GetHost()->WasResized();
  }
#if (defined(OS_WIN) || defined(OS_LINUX))
  else {
    CefWindowHandle browserHandle = browser->GetHost()->GetWindowHandle();
    if (CefCurrentlyOn(TID_UI)) {
      util::SetWindowSize(browserHandle, width, height);
    } else {
      CefPostTask(TID_UI, base::BindOnce(util::SetWindowSize, browserHandle,
                                         (int)width, (int)height));
    }
  }
#endif
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1Invalidate(JNIEnv* env, jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->Invalidate(PET_VIEW);
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SendKeyEvent(
    JNIEnv* env,
    jobject obj,
    jobject key_event)
{
    CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
    jclass keyEventClass = env->GetObjectClass(key_event);
    if (!keyEventClass) return;

    // --- Resolve CEF event type from JavaFX event type name ---
    jmethodID getEventTypeMethod =
        env->GetMethodID(keyEventClass, "getEventType", "()Ljavafx/event/EventType;");
    if (!getEventTypeMethod) return;

    jobject eventType = env->CallObjectMethod(key_event, getEventTypeMethod);
    if (!eventType) return;

    jclass eventTypeClass = env->GetObjectClass(eventType);
    jmethodID getNameMethod =
        env->GetMethodID(eventTypeClass, "getName", "()Ljava/lang/String;");
    jstring eventTypeName = (jstring)env->CallObjectMethod(eventType, getNameMethod);
    const char* typeStr = env->GetStringUTFChars(eventTypeName, nullptr);

    cef_key_event_type_t event_type;
    if (strcmp(typeStr, "KEY_PRESSED") == 0) {
        event_type = KEYEVENT_RAWKEYDOWN;
    } else if (strcmp(typeStr, "KEY_RELEASED") == 0) {
        event_type = KEYEVENT_KEYUP;
    } else if (strcmp(typeStr, "KEY_TYPED") == 0) {
        event_type = KEYEVENT_CHAR;
    } else {
        env->ReleaseStringUTFChars(eventTypeName, typeStr);
        return;
    }
    env->ReleaseStringUTFChars(eventTypeName, typeStr);

    // --- Read Windows Virtual Key code directly from JavaFX KeyCode ---
    // JavaFX KeyCode.getCode() returns the Windows VK code, which maps 1:1 to CEF VKEY_* constants.
    jmethodID getCodeMethod =
        env->GetMethodID(keyEventClass, "getCode", "()Ljavafx/scene/input/KeyCode;");
    jobject keyCodeObj = env->CallObjectMethod(key_event, getCodeMethod);
    if (!keyCodeObj) return;

    jclass keyCodeClass = env->GetObjectClass(keyCodeObj);
    jmethodID getCodeIntMethod =
        env->GetMethodID(keyCodeClass, "getCode", "()I");
    jint windowsVkCode = env->CallIntMethod(keyCodeObj, getCodeIntMethod);

    // --- Read character from JavaFX KeyEvent ---
    jmethodID getCharMethod =
        env->GetMethodID(keyEventClass, "getCharacter", "()Ljava/lang/String;");
    jstring charStr = (jstring)env->CallObjectMethod(key_event, getCharMethod);
    CefString cefChar = GetJNIString(env, charStr);
    char16_t character = cefChar.length() > 0 ? cefChar.c_str()[0] : 0;

    // JavaFX uses 0xFFFF (CHAR_UNDEFINED) for non-printable keys (arrows, F-keys, etc.).
    // Drop KEY_TYPED events for such keys — CEF does not expect KEYEVENT_CHAR without a real character.
    if (event_type == KEYEVENT_CHAR && (character == 0xFFFF || character == 0)) {
        return;
    }

    // Normalize CHAR_UNDEFINED to 0 for RAWKEYDOWN/KEYUP — CEF expects 0 for non-printable keys.
    if (event_type != KEYEVENT_CHAR && character == 0xFFFF) {
        character = 0;
    }

    // --- Build and send CefKeyEvent ---
    CefKeyEvent cef_event;
    cef_event.type                 = event_type;
    cef_event.modifiers            = GetCefModifiersFromJavaFX(env, key_event);
    cef_event.windows_key_code     = windowsVkCode;
    cef_event.native_key_code      = 0;
    cef_event.is_system_key        = false;
    cef_event.character            = character;
    cef_event.unmodified_character = character;

    browser->GetHost()->SendKeyEvent(cef_event);
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SendMouseEvent(JNIEnv* env,
                                                     jobject obj,
                                                     jobject mouse_event) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);

  jclass mouseEventClass = env->GetObjectClass(mouse_event);
  if (!mouseEventClass)
    return;

  jmethodID getXMethod = env->GetMethodID(mouseEventClass, "getX", "()D");
  jmethodID getYMethod = env->GetMethodID(mouseEventClass, "getY", "()D");

  if (!getXMethod || !getYMethod)
    return;

  jdouble x = env->CallDoubleMethod(mouse_event, getXMethod);
  jdouble y = env->CallDoubleMethod(mouse_event, getYMethod);

  CefMouseEvent cef_event;
  cef_event.x = (int)x;
  cef_event.y = (int)y;
  cef_event.modifiers = GetCefModifiersFromJavaFXMouse(env, mouse_event);

  jclass eventClass = env->FindClass("javafx/event/Event");
  if (!eventClass)
    return;

  jmethodID getEventTypeMethod = env->GetMethodID(eventClass, "getEventType", "()Ljavafx/event/EventType;");
  if (!getEventTypeMethod)
    return;

  jobject eventType = env->CallObjectMethod(mouse_event, getEventTypeMethod);
  if (!eventType)
    return;

  jclass eventTypeClass = env->GetObjectClass(eventType);
  jmethodID toStringMethod = env->GetMethodID(eventTypeClass, "toString", "()Ljava/lang/String;");

  if (toStringMethod) {
    jstring eventTypeStr = (jstring)env->CallObjectMethod(eventType, toStringMethod);
    const char* eventTypeStrC = env->GetStringUTFChars(eventTypeStr, NULL);

    jmethodID isSecondaryDownMethod = env->GetMethodID(mouseEventClass, "isSecondaryButtonDown", "()Z");
    jmethodID isMiddleDownMethod = env->GetMethodID(mouseEventClass, "isMiddleButtonDown", "()Z");

    jboolean isSecondaryDown = isSecondaryDownMethod ? env->CallBooleanMethod(mouse_event, isSecondaryDownMethod) : JNI_FALSE;
    jboolean isMiddleDown = isMiddleDownMethod ? env->CallBooleanMethod(mouse_event, isMiddleDownMethod) : JNI_FALSE;

    CefBrowserHost::MouseButtonType button = MBT_LEFT;
    if (isSecondaryDown) {
      button = MBT_RIGHT;
    } else if (isMiddleDown) {
      button = MBT_MIDDLE;
    }

    if (strstr(eventTypeStrC, "MOUSE_PRESSED")) {
      browser->GetHost()->SendMouseClickEvent(cef_event, button, false, 1);
    } else if (strstr(eventTypeStrC, "MOUSE_RELEASED")) {
      browser->GetHost()->SendMouseClickEvent(cef_event, button, true, 1);
    } else {
      browser->GetHost()->SendMouseMoveEvent(cef_event, false);
    }

    env->ReleaseStringUTFChars(eventTypeStr, eventTypeStrC);
  }
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SendMouseWheelEvent(JNIEnv* env,
                                                        jobject obj,
                                                        jobject scroll_event) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);

  jclass scrollEventClass = env->GetObjectClass(scroll_event);
  if (!scrollEventClass)
    return;

  jmethodID getXMethod = env->GetMethodID(scrollEventClass, "getX", "()D");
  jmethodID getYMethod = env->GetMethodID(scrollEventClass, "getY", "()D");

  if (!getXMethod || !getYMethod)
    return;

  jdouble x = env->CallDoubleMethod(scroll_event, getXMethod);
  jdouble y = env->CallDoubleMethod(scroll_event, getYMethod);

  jmethodID getDeltaYMethod = env->GetMethodID(scrollEventClass, "getDeltaY", "()D");
  if (!getDeltaYMethod)
    return;

  jdouble deltaY = env->CallDoubleMethod(scroll_event, getDeltaYMethod);

  jmethodID getDeltaXMethod = env->GetMethodID(scrollEventClass, "getDeltaX", "()D");
  jdouble deltaX = getDeltaXMethod ? env->CallDoubleMethod(scroll_event, getDeltaXMethod) : 0.0;

  CefMouseEvent cef_event;
  cef_event.x = (int)x;
  cef_event.y = (int)y;
  cef_event.modifiers = GetCefModifiersFromJavaFXMouse(env, scroll_event);

  int wheel_delta_y = (int)(deltaY * 1.5);
  int wheel_delta_x = (int)(deltaX * 1.5);

  browser->GetHost()->SendMouseWheelEvent(cef_event, wheel_delta_x, wheel_delta_y);
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1DragTargetDragEnter(
    JNIEnv* env,
    jobject obj,
    jobject jdragData,
    jobject jpoint,
    jint jmodifiers,
    jint allowedOps) {

  CefRefPtr<CefDragData> drag_data =
      GetCefFromJNIObject<CefDragData>(env, jdragData, "CefDragData");

  if (!drag_data)
    return;

  CefMouseEvent cef_event;

  // JavaFX Point2D -> CefMouseEvent (double -> int)
  GetJNIPoint2D(env, jpoint, &cef_event.x, &cef_event.y);

  cef_event.modifiers = static_cast<int>(
      GetCefModifiers(env, nullptr, jmodifiers));

  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);

  browser->GetHost()->DragTargetDragEnter(
      drag_data,
      cef_event,
      static_cast<CefBrowserHost::DragOperationsMask>(allowedOps));
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1DragTargetDragOver(
    JNIEnv* env,
    jobject obj,
    jobject pos,
    jint jmodifiers,
    jint allowedOps) {

  CefMouseEvent cef_event;
  // JavaFX Point2D -> CefMouseEvent
  GetJNIPoint2D(env, pos, &cef_event.x, &cef_event.y);
  cef_event.modifiers = GetCefModifiers(env, nullptr, jmodifiers);
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->DragTargetDragOver(
      cef_event,
      static_cast<CefBrowserHost::DragOperationsMask>(allowedOps));
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1DragTargetDragLeave(JNIEnv* env,
                                                          jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->DragTargetDragLeave();
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1DragTargetDrop(
    JNIEnv* env,
    jobject obj,
    jobject pos,
    jint jmodifiers) {

  CefMouseEvent cef_event;
  // JavaFX Point2D -> CefMouseEvent
  GetJNIPoint2D(env, pos, &cef_event.x, &cef_event.y);
  cef_event.modifiers = GetCefModifiers(env, nullptr, jmodifiers);
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->DragTargetDrop(cef_event);
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1DragSourceEndedAt(
    JNIEnv* env,
    jobject obj,
    jobject pos,
    jint operation) {

  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  CefMouseEvent cef_event;
  // JavaFX Point2D -> CefMouseEvent
  GetJNIPoint2D(env, pos, &cef_event.x, &cef_event.y);
  browser->GetHost()->DragSourceEndedAt(
      cef_event.x,
      cef_event.y,
      static_cast<CefBrowserHost::DragOperationsMask>(operation));
}

JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1DragSourceSystemDragEnded(JNIEnv* env,
                                                                jobject obj) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  browser->GetHost()->DragSourceSystemDragEnded();
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1UpdateUI(JNIEnv* env,
                                               jobject obj,
                                               jobject jcontentRect,
                                               jobject jbrowserRect) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  CefRect contentRect = GetJNIRect(env, jcontentRect);
#if defined(OS_MACOSX)
  CefRect browserRect = GetJNIRect(env, jbrowserRect);
  util_mac::UpdateView(browser->GetHost()->GetWindowHandle(), contentRect,
                       browserRect);
#else
  CefWindowHandle windowHandle = browser->GetHost()->GetWindowHandle();
  if (CefCurrentlyOn(TID_UI)) {
    util::SetWindowBounds(windowHandle, contentRect);
  } else {
    CefPostTask(TID_UI, base::BindOnce(util::SetWindowBounds, windowHandle,
                                       contentRect));
  }
#endif
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SetParent(JNIEnv* env,
                                                jobject obj,
                                                jlong windowHandle) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  base::OnceClosure callback = base::BindOnce(&OnAfterParentChanged, browser);
#if defined(OS_MACOSX)
  util::SetParent(browser->GetHost()->GetWindowHandle(), windowHandle,
                  std::move(callback));
#else
  CefWindowHandle browserHandle = browser->GetHost()->GetWindowHandle();
  CefWindowHandle parentHandle =
      windowHandle ? (CefWindowHandle)windowHandle : kNullWindowHandle;
  if (CefCurrentlyOn(TID_UI)) {
    util::SetParent(browserHandle, parentHandle, std::move(callback));
  } else {
#if defined(OS_LINUX)
    CriticalLock lock;
    CriticalWait waitCond(&lock);
    lock.Lock();
    CefPostTask(TID_UI,
                base::BindOnce(util::SetParentSync, browserHandle, parentHandle,
                               &waitCond, std::move(callback)));
    waitCond.Wait(1000);
    lock.Unlock();
#else
    CefPostTask(TID_UI, base::BindOnce(util::SetParent, browserHandle,
                                       parentHandle, std::move(callback)));
#endif
  }
#endif
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1NotifyMoveOrResizeStarted(JNIEnv* env,
                                                                jobject obj) {
#if (defined(OS_WIN) || defined(OS_LINUX))
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, obj);
  if (!browser->GetHost()->IsWindowRenderingDisabled()) {
    browser->GetHost()->NotifyMoveOrResizeStarted();
  }
#endif
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1SetWindowlessFrameRate(JNIEnv* env,
                                                             jobject jbrowser,
                                                             jint frameRate) {
  CefRefPtr<CefBrowser> browser = JNI_GET_BROWSER_OR_RETURN(env, jbrowser);
  CefRefPtr<CefBrowserHost> host = browser->GetHost();
  host->SetWindowlessFrameRate(frameRate);
}
void getWindowlessFrameRate(CefRefPtr<CefBrowserHost> host,
                            CefRefPtr<IntCallback> callback) {
  callback->onComplete((jint)host->GetWindowlessFrameRate());
}
JNIEXPORT void JNICALL
Java_com_techsenger_ceffx_core_browser_CefBrowser_1N_N_1GetWindowlessFrameRate(
    JNIEnv* env,
    jobject jbrowser,
    jobject jintCallback) {
  CefRefPtr<IntCallback> callback = new IntCallback(env, jintCallback);
  CefRefPtr<CefBrowser> browser = GetJNIBrowser(env, jbrowser);
  if (!browser.get()) {
    callback->onComplete(0);
    return;
  }
  CefRefPtr<CefBrowserHost> host = browser->GetHost();
  if (CefCurrentlyOn(TID_UI)) {
    getWindowlessFrameRate(host, callback);
  } else {
    CefPostTask(TID_UI, base::BindOnce(getWindowlessFrameRate, host, callback));
  }
}