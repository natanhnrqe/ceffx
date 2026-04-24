// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "display_handler.h"

#include "jni_util.h"

DisplayHandler::DisplayHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

void DisplayHandler::OnAddressChange(CefRefPtr<CefBrowser> browser,
                                     CefRefPtr<CefFrame> frame,
                                     const CefString& url) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIFrame jframe(env, frame);
  jframe.SetTemporary();
  ScopedJNIString jurl(env, url);

  JNI_CALL_VOID_METHOD(env, handle_, "onAddressChange",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Lcom/techsenger/ceffx/core/browser/"
                       "CefFrame;Ljava/lang/String;)V",
                       jbrowser.get(), jframe.get(), jurl.get());
}

void DisplayHandler::OnTitleChange(CefRefPtr<CefBrowser> browser,
                                   const CefString& title) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIString jtitle(env, title);

  JNI_CALL_VOID_METHOD(env, handle_, "onTitleChange",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Ljava/lang/String;)V",
                       jbrowser.get(), jtitle.get());
}

void DisplayHandler::OnFullscreenModeChange(CefRefPtr<CefBrowser> browser,
                                            bool fullscreen) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "onFullscreenModeChange",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Z)V", jbrowser.get(),
                       (jboolean)fullscreen);
}

bool DisplayHandler::OnTooltip(CefRefPtr<CefBrowser> browser, CefString& text) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIString jtext(env, text);
  jboolean jreturn = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "onTooltip",
                  "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Ljava/lang/String;)Z", Boolean,
                  jreturn, jbrowser.get(), jtext.get());

  return (jreturn != JNI_FALSE);
}

void DisplayHandler::OnStatusMessage(CefRefPtr<CefBrowser> browser,
                                     const CefString& value) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIString jvalue(env, value);

  JNI_CALL_VOID_METHOD(env, handle_, "onStatusMessage",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Ljava/lang/String;)V",
                       jbrowser.get(), jvalue.get());
}

bool DisplayHandler::OnConsoleMessage(CefRefPtr<CefBrowser> browser,
                                      cef_log_severity_t level,
                                      const CefString& message,
                                      const CefString& source,
                                      int line) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  jobject jlevel = nullptr;
  switch (level) {
    JNI_CASE(env, "com/techsenger/ceffx/core/CefSettings$LogSeverity", LOGSEVERITY_VERBOSE,
             jlevel);
    JNI_CASE(env, "com/techsenger/ceffx/core/CefSettings$LogSeverity", LOGSEVERITY_INFO, jlevel);
    JNI_CASE(env, "com/techsenger/ceffx/core/CefSettings$LogSeverity", LOGSEVERITY_WARNING,
             jlevel);
    JNI_CASE(env, "com/techsenger/ceffx/core/CefSettings$LogSeverity", LOGSEVERITY_ERROR, jlevel);
    JNI_CASE(env, "com/techsenger/ceffx/core/CefSettings$LogSeverity", LOGSEVERITY_FATAL, jlevel);
    JNI_CASE(env, "com/techsenger/ceffx/core/CefSettings$LogSeverity", LOGSEVERITY_DISABLE,
             jlevel);
    case LOGSEVERITY_DEFAULT:
      break;
  }

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIString jmessage(env, message);
  ScopedJNIString jsource(env, source);
  jboolean jreturn = JNI_FALSE;

  JNI_CALL_METHOD(
      env, handle_, "onConsoleMessage",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Lcom/techsenger/ceffx/core/CefSettings$LogSeverity;"
      "Ljava/lang/String;Ljava/lang/String;I)Z",
      Boolean, jreturn, jbrowser.get(), jlevel, jmessage.get(), jsource.get(),
      line);

  return (jreturn != JNI_FALSE);
}

// TODO(CEFFX): Expose all parameters.
bool DisplayHandler::OnCursorChange(CefRefPtr<CefBrowser> browser,
                                    CefCursorHandle cursor,
                                    cef_cursor_type_t type,
                                    const CefCursorInfo& custom_cursor_info) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jreturn = JNI_FALSE;

  JNI_CALL_METHOD(env, handle_, "onCursorChange",
                  "(Lcom/techsenger/ceffx/core/browser/CefBrowser;I)Z", Boolean, jreturn,
                  jbrowser.get(), (jint)type);

  return (jreturn != JNI_FALSE);
}

void DisplayHandler::OnFaviconURLChange(
    CefRefPtr<CefBrowser> browser,
    const std::vector<CefString>& icon_urls) {

  if (icon_urls.empty())
    return;

  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  jclass stringClass = env->FindClass("java/lang/String");
  if (!stringClass)
    return;

  const size_t size = icon_urls.size();

  jobjectArray jurls = env->NewObjectArray(
      (jsize)size,
      stringClass,
      nullptr);

  if (!jurls)
    return;

  for (jsize i = 0; i < (jsize)size; i++) {
    ScopedJNIString jurl(env, icon_urls[i]);
    env->SetObjectArrayElement(jurls, i, jurl.get());
  }

  JNI_CALL_VOID_METHOD(
      env,
      handle_,
      "onFaviconURLChange",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;[Ljava/lang/String;)V",
      jbrowser.get(),
      jurls);
}