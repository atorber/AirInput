#pragma once

#include <atomic>
#include <cstddef>
#include <cstdint>

namespace airinput::vmic {

constexpr uint32_t kSharedRingMagic = 0x41494D43;  // 'AIMC'
constexpr uint32_t kSharedRingVersion = 1;
constexpr const char* kSharedRingPath = "/tmp/airinput-vmic.ring";
constexpr size_t kDefaultRingCapacityBytes = 1u << 20;  // 1 MiB ≈ 10.9s mono s16 @ 48k

// File-backed shared ring for Helper → AudioServerPlugIn.
// Layout is POD; all multi-byte fields little-endian (same process endianness on Apple Silicon/Intel).
struct SharedRingHeader {
  uint32_t magic;
  uint32_t version;
  uint32_t sample_rate;
  uint32_t channels;
  uint32_t capacity_bytes;
  uint32_t reserved;
  std::atomic<uint64_t> write_bytes;
  std::atomic<uint64_t> read_bytes;
};

struct SharedRingView {
  SharedRingHeader* header = nullptr;
  uint8_t* data = nullptr;
  size_t map_size = 0;
  int fd = -1;
  bool owner = false;
};

// Create or open the shared ring. capacity_bytes must be power-of-two for fast mask.
bool SharedRingCreate(SharedRingView* out, size_t capacity_bytes, bool create);
void SharedRingClose(SharedRingView* view);

// Producer: write interleaved s16le frames. Drops on overflow. Returns frames written.
size_t SharedRingWriteS16(SharedRingView* view, const int16_t* frames, size_t frame_count);

// Consumer (IO thread): read frames as float32 -1..1. Underflow fills silence.
void SharedRingReadF32(SharedRingView* view, float* out, size_t frame_count);

}  // namespace airinput::vmic
