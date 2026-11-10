#include "shared_ring.hpp"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <vector>

using namespace airinput::vmic;

int main() {
  SharedRingView ring{};
  assert(SharedRingCreate(&ring, kDefaultRingCapacityBytes, true));
  assert(ring.header);
  assert(ring.header->magic == kSharedRingMagic);
  assert(ring.header->sample_rate == 48000);
  assert(ring.header->channels == 1);

  std::vector<int16_t> in(960);
  for (size_t i = 0; i < in.size(); ++i) in[i] = static_cast<int16_t>(i);
  assert(SharedRingWriteS16(&ring, in.data(), in.size()) == in.size());

  std::vector<float> out(960);
  SharedRingReadF32(&ring, out.data(), out.size());
  for (size_t i = 0; i < out.size(); ++i) {
    const float expected = static_cast<float>(in[i]) / 32768.0f;
    assert(std::fabs(out[i] - expected) < 1e-6f);
  }

  // Underflow => silence
  SharedRingReadF32(&ring, out.data(), out.size());
  for (float v : out) assert(v == 0.0f);

  ring.owner = true;
  SharedRingClose(&ring);
  std::printf("shared_ring_tests OK\n");
  return 0;
}
