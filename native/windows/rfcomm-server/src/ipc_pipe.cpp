#include "ipc_pipe.hpp"

#ifdef _WIN32
#include <windows.h>
#endif

namespace airinput::win {

bool IpcPipe::create(const std::wstring& name, bool binary) {
  binary_ = binary;
#ifdef _WIN32
  HANDLE h = CreateNamedPipeW(
      name.c_str(),
      PIPE_ACCESS_OUTBOUND,
      binary ? (PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT)
             : (PIPE_TYPE_MESSAGE | PIPE_READMODE_MESSAGE | PIPE_WAIT),
      1,
      1 << 20,
      1 << 16,
      0,
      nullptr);
  if (h == INVALID_HANDLE_VALUE) {
    return false;
  }
  handle_ = h;
  return true;
#else
  (void)name;
  return false;
#endif
}

bool IpcPipe::writeLine(const std::string& line) {
#ifdef _WIN32
  if (!handle_) return false;
  DWORD written = 0;
  std::string payload = line;
  if (payload.empty() || payload.back() != '\n') payload.push_back('\n');
  return WriteFile(static_cast<HANDLE>(handle_), payload.data(),
                   static_cast<DWORD>(payload.size()), &written, nullptr) == TRUE;
#else
  (void)line;
  return false;
#endif
}

bool IpcPipe::writeBinary(const uint8_t* data, size_t len) {
#ifdef _WIN32
  if (!handle_ || !data || len == 0) return false;
  DWORD written = 0;
  return WriteFile(static_cast<HANDLE>(handle_), data, static_cast<DWORD>(len), &written,
                   nullptr) == TRUE;
#else
  (void)data;
  (void)len;
  return false;
#endif
}

void IpcPipe::close() {
#ifdef _WIN32
  if (handle_) {
    CloseHandle(static_cast<HANDLE>(handle_));
    handle_ = nullptr;
  }
#endif
}

}  // namespace airinput::win
