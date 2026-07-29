// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#ifndef CEFFX_NATIVE_RENDER_HANDLER_H_
#define CEFFX_NATIVE_RENDER_HANDLER_H_
#pragma once

#include <jni.h>
#include <memory>

#include "include/cef_render_handler.h"

#include "jni_scoped_helpers.h"

#if defined(OS_WIN)
class D3D11TexturePool;
#endif

// RenderHandler implementation.
class RenderHandler : public CefRenderHandler {
 public:
  RenderHandler(JNIEnv* env, jobject handler);
  ~RenderHandler() override;

  // CefRenderHandler methods
  virtual bool GetRootScreenRect(CefRefPtr<CefBrowser> browser,
                                 CefRect& rect) override;
  virtual void GetViewRect(CefRefPtr<CefBrowser> browser,
                           CefRect& rect) override;

  virtual bool GetScreenInfo(CefRefPtr<CefBrowser> browser,
                             CefScreenInfo& screen_info) override;

  virtual bool GetScreenPoint(CefRefPtr<CefBrowser> browser,
                              int viewX,
                              int viewY,
                              int& screenX,
                              int& screenY) override;
  virtual void OnPopupShow(CefRefPtr<CefBrowser> browser, bool show) override;
  virtual void OnPopupSize(CefRefPtr<CefBrowser> browser,
                           const CefRect& rect) override;
  virtual void OnPaint(CefRefPtr<CefBrowser> browser,
                       PaintElementType type,
                       const RectList& dirtyRects,
                       const void* buffer,
                       int width,
                       int height) override;

  ///
  /// Accelerated paint path. CEF calls this instead of OnPaint when
  /// CefWindowInfo::shared_texture_enabled is true. |info.shared_texture_handle|
  /// is an NT handle to a D3D11/D3D12 texture owned by the Chromium GPU
  /// process and is recycled by the Chromium pool the moment this callback
  /// returns, so we MUST copy its contents into a private texture before
  /// returning. The copied texture's shared handle is then passed to Java
  /// for consumption by the JavaFX Prism pipeline.
  ///
  virtual void OnAcceleratedPaint(CefRefPtr<CefBrowser> browser,
                                  PaintElementType type,
                                  const RectList& dirtyRects,
                                  const CefAcceleratedPaintInfo& info) override;

  virtual bool StartDragging(CefRefPtr<CefBrowser> browser,
                             CefRefPtr<CefDragData> drag_data,
                             DragOperationsMask allowed_ops,
                             int x,
                             int y) override;
  virtual void UpdateDragCursor(CefRefPtr<CefBrowser> browser,
                                DragOperation operation) override;

  bool GetViewRect(jobject browser, CefRect& rect);
  bool GetScreenPoint(jobject browser,
                      int viewX,
                      int viewY,
                      int& screenX,
                      int& screenY);

 protected:
  ScopedJNIObjectGlobal handle_;

#if defined(OS_WIN)
  // Lazily constructed on the first OnAcceleratedPaint call. Owned by this
  // RenderHandler (one per browser).
  std::unique_ptr<D3D11TexturePool> texture_pool_;
#endif

  // Include the default reference counting implementation.
  IMPLEMENT_REFCOUNTING(RenderHandler);
};

#endif  // CEFFX_NATIVE_RENDER_HANDLER_H_
