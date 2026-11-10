#pragma once
#include <string>

namespace airinput::win {

class RfcommServer {
 public:
  explicit RfcommServer(std::wstring serviceUuid);
  bool start();
  void runControlLoop();
  void stop();

 private:
  std::wstring serviceUuid_;
  bool running_ = false;
};

}  // namespace airinput::win
