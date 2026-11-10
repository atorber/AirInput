#pragma once
#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

// Preallocated PCM s16le ring buffer for AudioServerPlugIn IOProc.
// No heap allocation in the audio callback path after construction.
class PcmRingBuffer {
 public:
  explicit PcmRingBuffer(size_t capacityBytes)
      : buffer_(capacityBytes), capacity_(capacityBytes) {}

  size_t write(const int16_t* samples, size_t count) {
    const size_t bytes = count * sizeof(int16_t);
    size_t written = 0;
    while (written < bytes) {
      const size_t w = writeIndex_.load(std::memory_order_relaxed);
      const size_t r = readIndex_.load(std::memory_order_acquire);
      const size_t used = w - r;
      if (used >= capacity_) break;  // overflow: drop
      const size_t pos = w % capacity_;
      const size_t chunk = std::min(bytes - written, capacity_ - pos);
      std::memcpy(buffer_.data() + pos, reinterpret_cast<const uint8_t*>(samples) + written, chunk);
      writeIndex_.store(w + chunk, std::memory_order_release);
      written += chunk;
    }
    return written / sizeof(int16_t);
  }

  // Underflow fills silence.
  void readOrSilence(int16_t* out, size_t count) {
    const size_t bytes = count * sizeof(int16_t);
    size_t copied = 0;
    while (copied < bytes) {
      const size_t w = writeIndex_.load(std::memory_order_acquire);
      const size_t r = readIndex_.load(std::memory_order_relaxed);
      if (r >= w) break;
      const size_t pos = r % capacity_;
      const size_t chunk = std::min(bytes - copied, std::min(w - r, capacity_ - pos));
      std::memcpy(reinterpret_cast<uint8_t*>(out) + copied, buffer_.data() + pos, chunk);
      readIndex_.store(r + chunk, std::memory_order_release);
      copied += chunk;
    }
    if (copied < bytes) {
      std::memset(reinterpret_cast<uint8_t*>(out) + copied, 0, bytes - copied);
    }
  }

 private:
  std::vector<uint8_t> buffer_;
  size_t capacity_;
  std::atomic<size_t> writeIndex_{0};
  std::atomic<size_t> readIndex_{0};
};
