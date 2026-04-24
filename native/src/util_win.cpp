// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

#include "util.h"

#include <windows.h>

#include <tchar.h>
#include <tlhelp32.h>
#include <windows.h>
#include <iostream>
#include <map>
#include <sstream>

#ifdef USING_JAVA
#include "client_handler.h"
#include "jni_util.h"
#include "temp_window.h"
#endif

#include "include/base/cef_callback.h"
#include "include/cef_path_util.h"

#define XBUTTON1_HI (XBUTTON1 << 16)
#undef MOUSE_MOVED

namespace util {

int GetPid() {
  return (int)GetCurrentProcessId();
}

int GetParentPid() {
  DWORD pid = GetCurrentProcessId();
  int ppid = 0;
  HANDLE hProcess;
  PROCESSENTRY32 pe32;

  hProcess = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hProcess == INVALID_HANDLE_VALUE)
    return ppid;

  pe32.dwSize = sizeof(PROCESSENTRY32);
  if (!Process32First(hProcess, &pe32)) {
    CloseHandle(hProcess);
    return ppid;
  }

  do {
    if (pe32.th32ProcessID == pid) {
      ppid = (int)pe32.th32ParentProcessID;
      break;
    }
  } while (Process32Next(hProcess, &pe32));

  CloseHandle(hProcess);
  return ppid;
}

std::string GetTempFileName(const std::string& identifer, bool useParentId) {
  std::stringstream tmpName;
  CefString tmpPath;
  if (!CefGetPath(PK_DIR_TEMP, tmpPath)) {
    TCHAR lpPathBuffer[MAX_PATH];
    GetTempPath(MAX_PATH, lpPathBuffer);
    tmpPath.FromWString(lpPathBuffer);
  }
  tmpName << tmpPath.ToString().c_str() << "\\";
  tmpName << "ceffx-p" << (useParentId ? util::GetParentPid() : util::GetPid());
  tmpName << (identifer.empty() ? "" : "_") << identifer.c_str() << ".tmp";
  return tmpName.str();
}

#ifdef USING_JAVA

void AddCefBrowser(CefRefPtr<CefBrowser> browser) {
  if (!browser.get())
    return;
  CefWindowHandle browserHandle = browser->GetHost()->GetWindowHandle();
  if (!browserHandle)
    return;

  WaitForSingleObject(g_browsers_lock_, INFINITE);
  std::pair<CefWindowHandle, CefRefPtr<CefBrowser>> pair =
      std::make_pair(browserHandle, browser);
  g_browsers_.insert(pair);
  ReleaseMutex(g_browsers_lock_);

  if (g_mouse_monitor_ == NULL) {
    DWORD threadId = GetWindowThreadProcessId(browserHandle, NULL);
    g_mouse_monitor_ =
        SetWindowsHookEx(WH_MOUSE, util::MouseProc, NULL, threadId);
    g_mouse_monitor_refs_ = 1;
  } else {
    g_mouse_monitor_refs_++;
  }
}

void DestroyCefBrowser(CefRefPtr<CefBrowser> browser) {
  if (!browser.get())
    return;
  CefWindowHandle browserHandle = browser->GetHost()->GetWindowHandle();
  if (!browserHandle)
    return;

  WaitForSingleObject(g_browsers_lock_, INFINITE);
  size_t erased = g_browsers_.erase(browserHandle);
  DCHECK_EQ(1U, erased);
  ReleaseMutex(g_browsers_lock_);

  ::DestroyWindow(browserHandle);

  if (g_mouse_monitor_ == NULL)
    return;
  g_mouse_monitor_refs_--;
  if (g_mouse_monitor_refs_ <= 0) {
    UnhookWindowsHookEx(g_mouse_monitor_);
    g_mouse_monitor_ = NULL;
  }
}

void SetParent(CefWindowHandle browserHandle,
               CefWindowHandle parentHandle,
               base::OnceClosure callback) {
  if (parentHandle == kNullWindowHandle)
    parentHandle = TempWindow::GetWindowHandle();
  if (parentHandle != kNullWindowHandle && browserHandle != kNullWindowHandle)
    ::SetParent(browserHandle, parentHandle);
  std::move(callback).Run();
}

void SetWindowBounds(CefWindowHandle browserHandle,
                     const CefRect& contentRect) {
  HRGN contentRgn = CreateRectRgn(contentRect.x, contentRect.y,
                                  contentRect.x + contentRect.width,
                                  contentRect.y + contentRect.height);
  SetWindowRgn(GetParent(browserHandle), contentRgn, TRUE);
}

void SetWindowSize(CefWindowHandle browserHandle, int width, int height) {
  SetWindowPos(browserHandle, NULL, 0, 0, width, height,
               SWP_NOZORDER | SWP_NOMOVE);
}

#endif  // USING_JAVA

}  // namespace util
