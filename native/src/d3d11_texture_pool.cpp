// Copyright (c) 2026 CEFFX contributors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "d3d11_texture_pool.h"

#if defined(OS_WIN)

#include <d3d11.h>
#include <d3d11_1.h>
#include <dxgi1_2.h>
#include <windows.h>
#include <cstdio>

namespace {

void LogError(const char* what, HRESULT hr) {
  std::fprintf(stderr, "[ceffx-d3d11] %s failed: hr=0x%08lX\n", what,
               static_cast<unsigned long>(hr));
}

}  // namespace

D3D11TexturePool::D3D11TexturePool() = default;

D3D11TexturePool::~D3D11TexturePool() {
  std::lock_guard<std::mutex> guard(lock_);

  // Closing the shared handle does NOT destroy the underlying texture; the
  // texture is released when |local_texture_| is released by the ComPtr.
  if (local_shared_handle_) {
    ::CloseHandle(local_shared_handle_);
    local_shared_handle_ = nullptr;
  }
  local_texture_.Reset();
  dxgi_resource_.Reset();
  device1_.Reset();
  context_.Reset();
  device_.Reset();
}

bool D3D11TexturePool::EnsureDevice() {
  std::lock_guard<std::mutex> guard(lock_);
  if (device_)
    return true;

  // Create a WARP-only device for software fallback safety? No: the goal is
  // GPU->GPU sharing with Chromium, so we explicitly request the hardware
  // driver. Chromium's GPU process opens its textures as cross-process
  // shareable NT handles; any D3D11 feature level >= 11.0 device can reopen
  // them via OpenSharedResource1.
  D3D_FEATURE_LEVEL requested_levels[] = {
      D3D_FEATURE_LEVEL_11_1,
      D3D_FEATURE_LEVEL_11_0,
  };
  D3D_FEATURE_LEVEL obtained_level = D3D_FEATURE_LEVEL_11_0;

  HRESULT hr = ::D3D11CreateDevice(
      /*adapter*/ nullptr,
      /*driver_type*/ D3D_DRIVER_TYPE_HARDWARE,
      /*software*/
      nullptr,
      /*flags*/ D3D11_CREATE_DEVICE_BGRA_SUPPORT,
      /*feature_levels*/ requested_levels,
      static_cast<UINT>(sizeof(requested_levels) / sizeof(requested_levels[0])),
      /*sdk_version*/ D3D11_SDK_VERSION,
      /*device*/ &device_,
      /*feature_level*/ &obtained_level,
      /*context*/
      &context_);
  if (FAILED(hr)) {
    LogError("D3D11CreateDevice(Hardware)", hr);
    // Last-ditch WARP fallback so we can still resolve something on VMs /
    // headless RDP. Texture contents will still be shared correctly even
    // across WARP <-> Hardware boundaries (shared resources cross drivers).
    hr = ::D3D11CreateDevice(
        nullptr, D3D_DRIVER_TYPE_WARP, nullptr,
        D3D11_CREATE_DEVICE_BGRA_SUPPORT, requested_levels,
        static_cast<UINT>(sizeof(requested_levels) / sizeof(requested_levels[0])),
        D3D11_SDK_VERSION, &device_, &obtained_level, &context_);
    if (FAILED(hr)) {
      LogError("D3D11CreateDevice(WARP)", hr);
      return false;
    }
  }

  // Need Device1 for OpenSharedResource1 (NT handle path). All D3D11 devices
  // support this; the cast is essentially free.
  hr = device_.As(&device1_);
  if (FAILED(hr)) {
    LogError("QueryInterface(ID3D11Device1)", hr);
    return false;
  }

  return true;
}

bool D3D11TexturePool::RecreateLocalTexture(int width, int height) {
  // Assumes the lock is held.
  if (!device_)
    return false;

  // Release any previous handle BEFORE releasing the texture so the OS
  // ref-counting doesn't leave dangling handles mapped in the JavaFX side.
  if (local_shared_handle_) {
    ::CloseHandle(local_shared_handle_);
    local_shared_handle_ = nullptr;
  }
  dxgi_resource_.Reset();
  local_texture_.Reset();

  D3D11_TEXTURE2D_DESC desc = {};
  desc.Width = static_cast<UINT>(width);
  desc.Height = static_cast<UINT>(height);
  desc.MipLevels = 1;
  desc.ArraySize = 1;
  desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;  // matches CEF BGRA output
  desc.SampleDesc.Count = 1;
  desc.Usage = D3D11_USAGE_DEFAULT;
  // D3D11_RESOURCE_MISC_SHARED_NTHANDLE | KEYED_MUTEX gives us the cross-
  // process NT handle path used by Chromium; KEYED_MUTEX also lets us
  // synchronize access between our copy step and JavaFX's read step.
  desc.MiscFlags = D3D11_RESOURCE_MISC_SHARED_NTHANDLE |
                   D3D11_RESOURCE_MISC_SHARED_KEYEDMUTEX;

  HRESULT hr = device_->CreateTexture2D(&desc, /*initial_data*/ nullptr,
                                        &local_texture_);
  if (FAILED(hr)) {
    LogError("CreateTexture2D(local)", hr);
    return false;
  }

  // Extract the shareable NT handle. GetSharedHandle on IDXGIResource1 is the
  // non-keyed-mutex variant; NTHANDLE resources expose their handle here.
  Microsoft::WRL::ComPtr<IDXGIResource1> res1;
  hr = local_texture_.As(&res1);
  if (FAILED(hr)) {
    LogError("QueryInterface(IDXGIResource1)", hr);
    return false;
  }
  hr = res1->CreateSharedHandle(
      /*attributes*/ nullptr,
      /*access*/ DXGI_SHARED_RESOURCE_READ | DXGI_SHARED_RESOURCE_WRITE,
      /*name*/ nullptr,
      &local_shared_handle_);
  if (FAILED(hr)) {
    LogError("CreateSharedHandle", hr);
    return false;
  }

  // Stash IDXGIResource for completeness / potential future queries.
  local_texture_.As(&dxgi_resource_);

  width_ = width;
  height_ = height;
  return true;
}

bool D3D11TexturePool::CopyFrame(HANDLE cef_shared_handle, int width,
                                  int height) {
  if (!EnsureDevice())
    return false;

  std::lock_guard<std::mutex> guard(lock_);

  if (width <= 0 || height <= 0 || cef_shared_handle == nullptr ||
      cef_shared_handle == INVALID_HANDLE_VALUE) {
    return false;
  }

  // Recreate the local texture when size changes (resize / DPI change / swap).
  if (!local_texture_ || width_ != width || height_ != height) {
    if (!RecreateLocalTexture(width, height))
      return false;
  }

  // Open the Chromium texture into our device for the duration of this call.
  Microsoft::WRL::ComPtr<ID3D11Texture2D> cef_tex;
  HRESULT hr = device1_->OpenSharedResource1(
      cef_shared_handle, IID_PPV_ARGS(&cef_tex));
  if (FAILED(hr)) {
    LogError("OpenSharedResource1(cef)", hr);
    return false;
  }

  // Synchronize with Chromium's writer key. Chromium hands us the texture
  // "owned" by key 0 (writer passed ownership). We acquire with 0, do the
  // copy, and release back to Chromium with key 0 so its pool can recycle it.
  Microsoft::WRL::ComPtr<IDXGIKeyedMutex> cef_mutex;
  hr = cef_tex.As(&cef_mutex);
  if (SUCCEEDED(hr)) {
    // Acquire with key 0 (polling up to ~500ms; anything more would block the
    // CEF render thread which is undesirable and we'd rather drop the frame).
    hr = cef_mutex->AcquireSync(0, 500);
    if (hr != S_OK) {
      // Timed out / abandoned; let Chromium recycle the frame and drop ours.
      return false;
    }
  }

  // The actual GPU copy. Both textures are D3D11_USAGE_DEFAULT GPU-only.
  context_->CopyResource(local_texture_.Get(), cef_tex.Get());

  if (cef_mutex) {
    cef_mutex->ReleaseSync(0);
  }
  return true;
}

HANDLE D3D11TexturePool::GetSharedHandle() {
  std::lock_guard<std::mutex> guard(lock_);
  return local_shared_handle_;
}

#endif  // defined(OS_WIN)
