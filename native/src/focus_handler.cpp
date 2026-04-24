// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "focus_handler.h"

#include "include/base/cef_callback.h"
#include "include/wrapper/cef_closure_task.h"

#include "client_handler.h"
#include "jni_util.h"

FocusHandler::FocusHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

void FocusHandler::OnTakeFocus(CefRefPtr<CefBrowser> browser, bool next) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "onTakeFocus",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Z)V", jbrowser.get(),
                       (jboolean)next);
}

bool FocusHandler::OnSetFocus(CefRefPtr<CefBrowser> browser,
                              FocusSource source) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  jboolean jreturn = JNI_FALSE;
  ScopedJNIObjectResult jsource(env);
  switch (source) {
    JNI_CASE(env, "com/techsenger/ceffx/core/handler/CefFocusHandler$FocusSource",
             FOCUS_SOURCE_NAVIGATION, jsource);
    default:
      JNI_CASE(env, "com/techsenger/ceffx/core/handler/CefFocusHandler$FocusSource",
               FOCUS_SOURCE_SYSTEM, jsource);
  }

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_METHOD(env, handle_, "onSetFocus",
                  "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Lcom/techsenger/ceffx/core/handler/"
                  "CefFocusHandler$FocusSource;)Z",
                  Boolean, jreturn, jbrowser.get(), jsource.get());
  return (jreturn != JNI_FALSE);
}

void FocusHandler::OnGotFocus(CefRefPtr<CefBrowser> browser) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "onGotFocus",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;)V", jbrowser.get());
}
