// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "render_handler.h"

#include <algorithm>
#include <climits>
#include <memory>

#include "client_handler.h"
#include "jni_util.h"

#if defined(OS_WIN)
#include <windows.h>

#include "d3d11_texture_pool.h"
#endif

namespace {

// Create a new javafx.geometry.BoundingBox from CefRect.
jobject NewJNIRect(JNIEnv* env, const CefRect& rect) {
  jclass cls = env->FindClass("javafx/geometry/BoundingBox");
  if (!cls)
    return nullptr;

  jmethodID ctor = env->GetMethodID(
      cls,
      "<init>",
      "(DDDD)V"
  );

  if (!ctor)
    return nullptr;

  jobject obj = env->NewObject(
      cls,
      ctor,
      static_cast<jdouble>(rect.x),
      static_cast<jdouble>(rect.y),
      static_cast<jdouble>(rect.width),
      static_cast<jdouble>(rect.height)
  );

  return obj;
}

jobject NewJNIScreenInfo(JNIEnv* env, CefScreenInfo& screenInfo) {
  ScopedJNIClass cls(env, "com/techsenger/ceffx/core/handler/CefScreenInfo");
  if (!cls) {
    return nullptr;
  }

  ScopedJNIObjectLocal obj(env, NewJNIObject(env, cls));
  if (!obj) {
    return nullptr;
  }

  if (SetJNIFieldDouble(env, cls, obj, "device_scale_factor",
                        (double)screenInfo.device_scale_factor) &&
      SetJNIFieldInt(env, cls, obj, "depth", screenInfo.depth) &&
      SetJNIFieldInt(env, cls, obj, "depth_per_component",
                     screenInfo.depth_per_component) &&
      SetJNIFieldBoolean(env, cls, obj, "is_monochrome",
                         screenInfo.is_monochrome) &&
      SetJNIFieldInt(env, cls, obj, "x", screenInfo.rect.x) &&
      SetJNIFieldInt(env, cls, obj, "y", screenInfo.rect.y) &&
      SetJNIFieldInt(env, cls, obj, "width", screenInfo.rect.width) &&
      SetJNIFieldInt(env, cls, obj, "height", screenInfo.rect.height) &&
      SetJNIFieldInt(env, cls, obj, "available_x",
                     screenInfo.available_rect.x) &&
      SetJNIFieldInt(env, cls, obj, "available_y",
                     screenInfo.available_rect.y) &&
      SetJNIFieldInt(env, cls, obj, "available_width",
                     screenInfo.available_rect.width) &&
      SetJNIFieldInt(env, cls, obj, "available_height",
                     screenInfo.available_rect.height)) {
    return obj.Release();
  }

  return nullptr;
}

bool GetJNIScreenInfo(JNIEnv* env, jobject jScreenInfo, CefScreenInfo& dest) {
  ScopedJNIClass cls(env, "com/techsenger/ceffx/core/handler/CefScreenInfo");
  if (!cls) {
    return false;
  }

  ScopedJNIObjectLocal obj(env, jScreenInfo);
  if (!obj) {
    return false;
  }
  double tmp;
  if (!GetJNIFieldDouble(env, cls, obj, "device_scale_factor", &tmp)) {
    return false;
  }
  dest.device_scale_factor = (float)tmp;

  if (GetJNIFieldInt(env, cls, obj, "depth", &(dest.depth)) &&
      GetJNIFieldInt(env, cls, obj, "depth_per_component",
                     &(dest.depth_per_component)) &&
      GetJNIFieldBoolean(env, cls, obj, "is_monochrome",
                         &(dest.is_monochrome)) &&
      GetJNIFieldInt(env, cls, obj, "x", &(dest.rect.x)) &&
      GetJNIFieldInt(env, cls, obj, "y", &(dest.rect.y)) &&
      GetJNIFieldInt(env, cls, obj, "width", &(dest.rect.width)) &&
      GetJNIFieldInt(env, cls, obj, "height", &(dest.rect.height)) &&
      GetJNIFieldInt(env, cls, obj, "available_x", &(dest.available_rect.x)) &&
      GetJNIFieldInt(env, cls, obj, "available_y", &(dest.available_rect.y)) &&
      GetJNIFieldInt(env, cls, obj, "available_width",
                     &(dest.available_rect.width)) &&
      GetJNIFieldInt(env, cls, obj, "available_height",
                     &(dest.available_rect.height))

  ) {
    return true;
  } else {
    return false;
  }
}

// create a new array of javafx.geometry.BoundingBox
jobjectArray NewJNIRectArray(JNIEnv* env, const std::vector<CefRect>& vals) {
  if (vals.empty())
    return nullptr;

  ScopedJNIClass cls(env, "javafx/geometry/BoundingBox");
  if (!cls)
    return nullptr;

  const jsize size = static_cast<jsize>(vals.size());
  jobjectArray arr = env->NewObjectArray(size, cls, nullptr);

  for (jsize i = 0; i < size; i++) {
    ScopedJNIObjectLocal rect_obj(env, NewJNIRect(env, vals[i]));
    env->SetObjectArrayElement(arr, i, rect_obj);
  }

  return arr;
}

// Create a new javafx.geometry.Point2D.
jobject NewJNIPoint2D(JNIEnv* env, double x, double y) {
  ScopedJNIClass cls(env, "javafx/geometry/Point2D");
  if (!cls)
    return nullptr;

  jmethodID ctor = env->GetMethodID(cls, "<init>", "(DD)V");
  if (!ctor)
    return nullptr;

  jobject obj = env->NewObject(cls, ctor, x, y);
  if (!obj)
    return nullptr;

  // Return local reference (caller owns it or wraps it)
  return obj;
}

}  // namespace

RenderHandler::RenderHandler(JNIEnv* env, jobject handler)
    : handle_(env, handler) {}

RenderHandler::~RenderHandler() {
#if defined(OS_WIN)
  // Destroy the D3D11 device / local texture before the JNI handle goes away.
  // We deliberately do NOT call back into Java from here (the CefBrowser is
  // already gone by the time this destructor runs).
  texture_pool_.reset();
#endif
}

bool RenderHandler::GetRootScreenRect(CefRefPtr<CefBrowser> browser,
                                      CefRect& rect) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  bool result = GetViewRect(jbrowser, rect);
  return result;
}

void RenderHandler::GetViewRect(CefRefPtr<CefBrowser> browser, CefRect& rect) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  if (!GetViewRect(jbrowser, rect)) {
    rect = CefRect(0, 0, 1, 1);
  }
}

///
// Called to allow the client to fill in the CefScreenInfo object with
// appropriate values. Return true if the |screen_info| structure has been
// modified.
//
// If the screen info rectangle is left empty the rectangle from GetViewRect
// will be used. If the rectangle is still empty or invalid popups may not be
// drawn correctly.
///
/*--cef()--*/
bool RenderHandler::GetScreenInfo(CefRefPtr<CefBrowser> browser,
                                  CefScreenInfo& screen_info) {
  ScopedJNIEnv env;
  if (!env) {
    return false;
  }

  ScopedJNIObjectLocal jScreenInfo(env, NewJNIScreenInfo(env, screen_info));
  if (!jScreenInfo) {
    return false;
  }
  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jresult = 0;

  JNI_CALL_BOOLEAN_METHOD(
      jresult, env, jbrowser.get(), "getScreenInfo",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Lcom/techsenger/ceffx/core/handler/CefScreenInfo;)Z",
      jbrowser.get(), jScreenInfo.get());

  if (jresult) {
    if (GetJNIScreenInfo(env, jScreenInfo.get(), screen_info)) {
      return true;
    }
  }

  return false;
}

bool RenderHandler::GetScreenPoint(CefRefPtr<CefBrowser> browser,
                                   int viewX,
                                   int viewY,
                                   int& screenX,
                                   int& screenY) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  return GetScreenPoint(jbrowser, viewX, viewY, screenX, screenY);
}

void RenderHandler::OnPopupShow(CefRefPtr<CefBrowser> browser, bool show) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "onPopupShow",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Z)V", jbrowser.get(),
                       (jboolean)show);
}

void RenderHandler::OnPopupSize(CefRefPtr<CefBrowser> browser,
                                const CefRect& rect) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIObjectLocal jrect(env, NewJNIRect(env, rect));
  if (!jrect)
    return;

  ScopedJNIBrowser jbrowser(env, browser);

  JNI_CALL_VOID_METHOD(
      env,
      handle_,
      "onPopupSize",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Ljavafx/geometry/BoundingBox;)V",
      jbrowser.get(),
      jrect.get());
}

void RenderHandler::OnPaint(CefRefPtr<CefBrowser> browser,
                            PaintElementType type,
                            const RectList& dirtyRects,
                            const void* buffer,
                            int width,
                            int height) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jtype = type == PET_VIEW ? JNI_FALSE : JNI_TRUE;
  ScopedJNIObjectLocal jrectArray(env, NewJNIRectArray(env, dirtyRects));
  ScopedJNIObjectLocal jdirectBuffer(
      env,
      env->NewDirectByteBuffer(const_cast<void*>(buffer), width * height * 4));
  JNI_CALL_VOID_METHOD(
      env,
      handle_,
      "onPaint",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Z[Ljavafx/geometry/BoundingBox;Ljava/nio/ByteBuffer;II)V",
      jbrowser.get(),
      jtype,
      jrectArray.get(),
      jdirectBuffer.get(),
      width,
      height);
}

void RenderHandler::OnAcceleratedPaint(CefRefPtr<CefBrowser> browser,
                                       PaintElementType type,
                                       const RectList& dirtyRects,
                                       const CefAcceleratedPaintInfo& info) {
#if defined(OS_WIN)
  // The shared texture handle from Chromium is an NT HANDLE to a D3D11
  // (Windows) texture. Per CEF documentation it is recycled by Chromium's
  // frame pool THE MOMENT this callback returns, so we must copy its contents
  // into our own texture before returning.
  if (!info.shared_texture_handle || info.shared_texture_handle == INVALID_HANDLE_VALUE)
    return;

  // Lazily instantiate per-browser D3D11 device + local texture pool.
  if (!texture_pool_)
    texture_pool_.reset(new D3D11TexturePool());

  // Sufficient dimensions to recreate the local texture. Use the full buffer
  // size, not the dirty union (textures are re-created only when size changes,
  // so per-frame allocation cost is amortized to nearly zero after warmup).
  int width = 0, height = 0;
  if (!dirtyRects.empty()) {
    // Compute bounding box of dirty rects as a fallback lower bound; CEF
    // always emits the *full* buffer dimensions in info when the underlying
    // frame is new (the dirty rects simply describe what changed). We
    // therefore use the union as a proxy only when no other source is
    // available; in practice CEF also keeps the texture at |GetViewRect| size
    // which is what we want.
    int min_x = INT_MAX, min_y = INT_MAX, max_x = INT_MIN, max_y = INT_MIN;
    for (const CefRect& r : dirtyRects) {
      min_x = (std::min)(min_x, r.x);
      min_y = (std::min)(min_y, r.y);
      max_x = (std::max)(max_x, r.x + r.width);
      max_y = (std::max)(max_y, r.y + r.height);
    }
    width = max_x - min_x;
    height = max_y - min_y;
  }
  if (width <= 0 || height <= 0)
    return;

  if (!texture_pool_->CopyFrame(info.shared_texture_handle, width, height))
    return;

  HANDLE local_handle = texture_pool_->GetSharedHandle();
  if (!local_handle || local_handle == INVALID_HANDLE_VALUE)
    return;

  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  jboolean jtype = type == PET_VIEW ? JNI_FALSE : JNI_TRUE;
  ScopedJNIObjectLocal jrectArray(env, NewJNIRectArray(env, dirtyRects));

  // Reinterpret the NT HANDLE as a jlong for transport across JNI. The JavaFX
  // side reopens it via ID3D11Device1::OpenSharedResource1 on Prism's device.
  jlong jhandle = static_cast<jlong>(reinterpret_cast<intptr_t>(local_handle));

  // Note the distinct Java method name "onAcceleratedPaint" with signature
  // (CefBrowser, boolean, BoundingBox[], long, int, int). The backend
  // CefRenderer is responsible for forwarding this to the D3D-aware renderer
  // implementation and falling back to onPaint (software) if Prism refuses
  // the texture.
  JNI_CALL_VOID_METHOD(
      env,
      handle_,
      "onAcceleratedPaint",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Z[Ljavafx/geometry/BoundingBox;JII)V",
      jbrowser.get(),
      jtype,
      jrectArray.get(),
      jhandle,
      texture_pool_->width(),
      texture_pool_->height());
#else
  // On non-Windows platforms accelerated painting of this kind is not
  // supported by this mod; OnPaint (software) remains the only path.
  (void)browser;
  (void)type;
  (void)dirtyRects;
  (void)info;
#endif
}

bool RenderHandler::StartDragging(CefRefPtr<CefBrowser> browser,
                                  CefRefPtr<CefDragData> drag_data,
                                  DragOperationsMask allowed_ops,
                                  int x,
                                  int y) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIBrowser jbrowser(env, browser);
  ScopedJNIDragData jdragdata(env, drag_data);
  jdragdata.SetTemporary();
  jboolean jresult = JNI_FALSE;
  JNI_CALL_METHOD(
      env, handle_, "startDragging",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Lcom/techsenger/ceffx/core/callback/CefDragData;III)Z",
      Boolean, jresult, jbrowser.get(), jdragdata.get(), (jint)allowed_ops,
      (jint)x, (jint)y);

  return (jresult != JNI_FALSE);
}

void RenderHandler::UpdateDragCursor(CefRefPtr<CefBrowser> browser,
                                     DragOperation operation) {
  ScopedJNIEnv env;
  if (!env)
    return;

  ScopedJNIBrowser jbrowser(env, browser);
  JNI_CALL_VOID_METHOD(env, handle_, "updateDragCursor",
                       "(Lcom/techsenger/ceffx/core/browser/CefBrowser;I)V", jbrowser.get(),
                       (jint)operation);
}

bool RenderHandler::GetViewRect(jobject browser, CefRect& rect) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  ScopedJNIObjectResult jreturn(env);

  JNI_CALL_METHOD(
      env,
      handle_,
      "getViewRect",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;)Ljavafx/geometry/BoundingBox;",
      Object,
      jreturn,
      browser);

  if (jreturn) {
    rect = GetJNIRect(env, jreturn);
    return true;
  }

  return false;
}

bool RenderHandler::GetScreenPoint(jobject browser,
                                   int viewX,
                                   int viewY,
                                   int& screenX,
                                   int& screenY) {
  ScopedJNIEnv env;
  if (!env)
    return false;

  // 1. Create JavaFX Point2D(viewX, viewY)
  ScopedJNIObjectLocal jpoint(
      env,
      NewJNIPoint2D(env, static_cast<double>(viewX), static_cast<double>(viewY)));
  if (!jpoint)
    return false;

  // 2. Call Java method returning Point2D
  ScopedJNIObjectResult jreturn(env);

  JNI_CALL_METHOD(
      env,
      handle_,
      "getScreenPoint",
      "(Lcom/techsenger/ceffx/core/browser/CefBrowser;Ljavafx/geometry/Point2D;)Ljavafx/geometry/Point2D;",
      Object,
      jreturn,
      browser,
      jpoint.get());

  if (!jreturn)
    return false;

  // 3. Extract x/y from Point2D
  jclass pointCls = env->GetObjectClass(jreturn.get());
  if (!pointCls)
    return false;

  jmethodID getX = env->GetMethodID(pointCls, "getX", "()D");
  jmethodID getY = env->GetMethodID(pointCls, "getY", "()D");

  if (!getX || !getY)
    return false;

  jdouble sx = env->CallDoubleMethod(jreturn.get(), getX);
  jdouble sy = env->CallDoubleMethod(jreturn.get(), getY);

  screenX = static_cast<int>(sx);
  screenY = static_cast<int>(sy);

  return true;
}
