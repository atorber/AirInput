#include "device_state.hpp"

using airinput::vmic::kDefaultRingCapacityBytes;

namespace airinput::vmic {

DeviceState& State() {
  static DeviceState state;
  return state;
}

bool EnsureRingMapped() {
  auto& s = State();
  std::lock_guard<std::mutex> lock(s.mutex);
  if (s.ring_ready) return true;
  // Prefer open existing ring created by injector/helper; if missing, create empty silence ring.
  if (SharedRingCreate(&s.ring, kDefaultRingCapacityBytes, false) ||
      SharedRingCreate(&s.ring, kDefaultRingCapacityBytes, true)) {
    s.ring_ready = true;
    return true;
  }
  return false;
}

}  // namespace airinput::vmic
