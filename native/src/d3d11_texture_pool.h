// Copyright (c) 2026 CEFFX contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CEFFX_NATIVE_D3D11_TEXTURE_POOL_H_
#define CEFFX_NATIVE_D3D11_TEXTURE_POOL_H_
#pragma once

#include "include/base/cef_build.h"

#if defined(OS_WIN)

#include <d3d11.h>
#include <d3d11_1.h>
#include <dxgi1_2.h>
#include <wrl/client.h>

#include <mutex>

///
/// Owns a private Direct3D 11 device and a single local "staging" texture that
/// mirrors the latest frame produced by the Chromium GPU process.
///
/// The local texture is created with D3D11_RESOURCE_MISC_SHARED so its
/// underlying IDXGIResource can be re-exported as a stable HANDLE that the
/// JavaFX Prism pipeline reopens on its own D3D11 device via
/// ID3D11Device1::OpenSharedResource1.
///
/// Thread-safety: all public methods are guarded by an internal mutex so
/// OnAcceleratedPaint (called on the CEF render thread) can run concurrently
/// with whatever calls GetSharedHandle() from other threads.
///
/// Lifecycle: the pool is lazily constructed the first time a browser needs
/// accelerated paint. It is destroyed when the owning RenderHandler is torn
/// down (browser close). Each instance is per-RenderHandler, i.e. per-browser.
///
class D3D11TexturePool {
 public:
  D3D11TexturePool();
  ~D3D11TexturePool();

  D3D11TexturePool(const D3D11TexturePool&) = delete;
  D3D11TexturePool& operator=(const D3D11TexturePool&) = delete;

  ///
  /// Idempotently initializes the D3D11 device, immediate context and feature
  /// level 11.0+ device1 pointer. Returns true on success or if already
  /// initialized. Returns false (and logs to stderr) on hard failure.
  ///
  bool EnsureDevice();

  ///
  /// Copies the latest frame from the Chromium shared texture (identified by
  /// |cef_shared_handle|, BGRA8 / R8G8B8A8_UNORM, |width|×|height|) into the
  /// local texture, recreating the local texture when dimensions change.
  ///
  /// After a successful call the local texture contains a private copy of the
  /// frame, decoupled from the Chromium texture pool's recycling.
  ///
  /// Returns true on success. On failure the previous local texture (if any)
  /// is preserved so a stale frame keeps being shown.
  ///
  bool CopyFrame(HANDLE cef_shared_handle, int width, int height);

  ///
  /// Returns the shared HANDLE of the local texture. The handle is stable for
  /// the lifetime of this pool (it does not change between frames, only the
  /// texture contents do). Returns nullptr if no frame has been copied yet
  /// or if the texture could not be made shareable.
  ///
  /// The returned handle is owned by this pool; the caller must NOT CloseHandle
  /// it. Consumers must reopen it via ID3D11Device1::OpenSharedResource1 on
  /// their own device and release their view before calling CopyFrame again
  /// if they want to read the new contents (the underlying texture object
  /// identity is reused across frames).
  ///
  HANDLE GetSharedHandle();

  ///
  /// Returns the pixel width / height of the local texture. Returns 0 if no
  /// frame has been copied yet.
  ///
  int width() const { return width_; }
  int height() const { return height_; }

 private:
  // Creates or recreates |local_texture_| at the given dimensions. Also
  // re-extracts its shared handle. Requires the device to be initialized.
  bool RecreateLocalTexture(int width, int height);

  std::mutex lock_;

  Microsoft::WRL::ComPtr<ID3D11Device> device_;
  Microsoft::WRL::ComPtr<ID3D11DeviceContext> context_;
  Microsoft::WRL::ComPtr<ID3D11Device1> device1_;

  Microsoft::WRL::ComPtr<ID3D11Texture2D> local_texture_;
  Microsoft::WRL::ComPtr<IDXGIResource> dxgi_resource_;
  HANDLE local_shared_handle_ = nullptr;

  int width_ = 0;
  int height_ = 0;
};

#endif  // defined(OS_WIN)

#endif  // CEFFX_NATIVE_D3D11_TEXTURE_POOL_H_
