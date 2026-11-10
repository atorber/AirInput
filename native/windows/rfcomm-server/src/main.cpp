#include "rfcomm_server.hpp"

#include <iostream>
#include <string>

#ifdef _WIN32

int main(int argc, char** argv) {
  std::wstring uuid = L"8d53dc1d-1db7-4cd3-868b-8a527460aa84";
  for (int i = 1; i + 1 < argc; ++i) {
    if (std::string(argv[i]) == "--uuid") {
      // Accept ASCII UUID string.
      std::string u = argv[i + 1];
      uuid.assign(u.begin(), u.end());
    }
  }

  airinput::win::RfcommServer server(uuid);
  if (!server.start()) {
    std::cerr << "RFCOMM server failed to start\n";
    return 1;
  }
  std::cerr << "AirInput Windows RFCOMM server running\n";
  server.runControlLoop();
  server.stop();
  return 0;
}

#else

int main() {
  std::cerr << "Windows RFCOMM server must be built on Windows with MSVC.\n";
  return 1;
}

#endif
