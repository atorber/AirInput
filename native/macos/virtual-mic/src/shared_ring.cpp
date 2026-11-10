#include "shared_ring.hpp"

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace airinput::vmic {
namespace {

constexpr size_t align_up(size_t v, size_t a) { return (v + a - 1) & ~(a - 1); }

bool is_pow2(size_t v) { return v && ((v & (v - 1)) == 0); }

}  // namespace

bool SharedRingCreate(SharedRingView* out, size_t capacity_bytes, bool create) {
  if (!out || capacity_bytes < 4096 || !is_pow2(capacity_bytes)) {
    return false;
  }
  const size_t header_bytes = align_up(sizeof(SharedRingHeader), 64);
  const size_t map_size = header_bytes + capacity_bytes;

  int fd = -1;
  if (create) {
    fd = ::open(kSharedRingPath, O_RDWR | O_CREAT | O_TRUNC, 0666);
    if (fd < 0) return false;
    if (::ftruncate(fd, static_cast<off_t>(map_size)) != 0) {
      ::close(fd);
      return false;
    }
  } else {
    fd = ::open(kSharedRingPath, O_RDWR);
    if (fd < 0) return false;
  }

  void* mem = ::mmap(nullptr, map_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
  if (mem == MAP_FAILED) {
    ::close(fd);
    return false;
  }

  auto* header = reinterpret_cast<SharedRingHeader*>(mem);
  uint8_t* data = reinterpret_cast<uint8_t*>(mem) + header_bytes;

  if (create) {
    std::memset(mem, 0, map_size);
    header->magic = kSharedRingMagic;
    header->version = kSharedRingVersion;
    header->sample_rate = 48000;
    header->channels = 1;
    header->capacity_bytes = static_cast<uint32_t>(capacity_bytes);
    header->write_bytes.store(0, std::memory_order_relaxed);
    header->read_bytes.store(0, std::memory_order_relaxed);
  } else {
    if (header->magic != kSharedRingMagic || header->version != kSharedRingVersion ||
        header->capacity_bytes != capacity_bytes) {
      ::munmap(mem, map_size);
      ::close(fd);
      return false;
    }
  }

  out->header = header;
  out->data = data;
  out->map_size = map_size;
  out->fd = fd;
  out->owner = create;
  return true;
}

void SharedRingClose(SharedRingView* view) {
  if (!view) return;
  if (view->header && view->map_size) {
    ::munmap(view->header, view->map_size);
  }
  if (view->fd >= 0) {
    ::close(view->fd);
  }
  if (view->owner) {
    ::unlink(kSharedRingPath);
  }
  *view = SharedRingView{};
}

size_t SharedRingWriteS16(SharedRingView* view, const int16_t* frames, size_t frame_count) {
  if (!view || !view->header || !frames || frame_count == 0) return 0;
  const size_t channels = view->header->channels ? view->header->channels : 1;
  const size_t bytes = frame_count * channels * sizeof(int16_t);
  const size_t capacity = view->header->capacity_bytes;
  const size_t mask = capacity - 1;

  uint64_t w = view->header->write_bytes.load(std::memory_order_relaxed);
  const uint64_t r = view->header->read_bytes.load(std::memory_order_acquire);
  const size_t used = static_cast<size_t>(w - r);
  if (used >= capacity) return 0;

  const size_t writable = std::min(bytes, capacity - used);
  const size_t pos = static_cast<size_t>(w) & mask;
  const size_t first = std::min(writable, capacity - pos);
  std::memcpy(view->data + pos, frames, first);
  if (writable > first) {
    std::memcpy(view->data, reinterpret_cast<const uint8_t*>(frames) + first, writable - first);
  }
  view->header->write_bytes.store(w + writable, std::memory_order_release);
  return writable / (channels * sizeof(int16_t));
}

void SharedRingReadF32(SharedRingView* view, float* out, size_t frame_count) {
  if (!out || frame_count == 0) return;
  if (!view || !view->header) {
    std::memset(out, 0, frame_count * sizeof(float));
    return;
  }

  const size_t channels = view->header->channels ? view->header->channels : 1;
  const size_t need_bytes = frame_count * channels * sizeof(int16_t);
  const size_t capacity = view->header->capacity_bytes;
  const size_t mask = capacity - 1;

  uint64_t r = view->header->read_bytes.load(std::memory_order_relaxed);
  const uint64_t w = view->header->write_bytes.load(std::memory_order_acquire);
  const size_t available = static_cast<size_t>(w - r);
  const size_t readable = std::min(need_bytes, available);

  size_t got = 0;
  if (readable > 0) {
    const size_t pos = static_cast<size_t>(r) & mask;
    const size_t first = std::min(readable, capacity - pos);
    auto* src = reinterpret_cast<const int16_t*>(view->data + pos);
    const size_t first_samples = first / sizeof(int16_t);
    for (size_t i = 0; i < first_samples; ++i) {
      out[i] = static_cast<float>(src[i]) / 32768.0f;
    }
    if (readable > first) {
      auto* src2 = reinterpret_cast<const int16_t*>(view->data);
      const size_t second_samples = (readable - first) / sizeof(int16_t);
      for (size_t i = 0; i < second_samples; ++i) {
        out[first_samples + i] = static_cast<float>(src2[i]) / 32768.0f;
      }
    }
    got = readable / sizeof(int16_t);
    view->header->read_bytes.store(r + readable, std::memory_order_release);
  }

  const size_t total_samples = frame_count * channels;
  if (got < total_samples) {
    std::memset(out + got, 0, (total_samples - got) * sizeof(float));
  }
}

}  // namespace airinput::vmic
