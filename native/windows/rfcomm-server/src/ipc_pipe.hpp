#pragma once
#include <cstddef>
#include <cstdint>
#include <string>

namespace airinput::win {

class IpcPipe {
 public:
  bool create(const std::wstring& name, bool binary);
  bool writeLine(const std::string& line);
  bool writeBinary(const uint8_t* data, size_t len);
  void close();

 private:
#ifdef _WIN32
  void* handle_ = nullptr;  // HANDLE
#endif
  bool binary_ = false;
};

}  // namespace airinput::win
