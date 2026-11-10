#include "shared_ring.hpp"

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

using airinput::vmic::SharedRingClose;
using airinput::vmic::SharedRingCreate;
using airinput::vmic::SharedRingView;
using airinput::vmic::SharedRingWriteS16;
using airinput::vmic::kDefaultRingCapacityBytes;
using airinput::vmic::kSharedRingPath;

int main(int argc, char** argv) {
  const char* mode = "tone";
  double seconds = 3.0;
  double hz = 440.0;
  const char* raw_path = nullptr;

  for (int i = 1; i < argc; ++i) {
    if (std::strcmp(argv[i], "--tone") == 0) {
      mode = "tone";
    } else if (std::strcmp(argv[i], "--seconds") == 0 && i + 1 < argc) {
      seconds = std::atof(argv[++i]);
    } else if (std::strcmp(argv[i], "--hz") == 0 && i + 1 < argc) {
      hz = std::atof(argv[++i]);
    } else if (std::strcmp(argv[i], "--raw") == 0 && i + 1 < argc) {
      mode = "raw";
      raw_path = argv[++i];
    } else if (std::strcmp(argv[i], "--help") == 0) {
      std::printf("Usage: airinput_pcm_inject [--tone --seconds N --hz F] | [--raw file.s16le]\n");
      std::printf("Writes PCM into %s for AirInput Microphone plugin.\n", kSharedRingPath);
      return 0;
    }
  }

  SharedRingView ring{};
  if (!SharedRingCreate(&ring, kDefaultRingCapacityBytes, true)) {
    std::fprintf(stderr, "failed to create shared ring at %s\n", kSharedRingPath);
    return 1;
  }

  if (std::strcmp(mode, "raw") == 0) {
    FILE* f = std::fopen(raw_path, "rb");
    if (!f) {
      std::perror("fopen");
      SharedRingClose(&ring);
      return 1;
    }
    std::vector<int16_t> buf(4096);
    size_t total = 0;
    while (true) {
      const size_t n = std::fread(buf.data(), sizeof(int16_t), buf.size(), f);
      if (n == 0) break;
      size_t offset = 0;
      while (offset < n) {
        const size_t w = SharedRingWriteS16(&ring, buf.data() + offset, n - offset);
        if (w == 0) continue;
        offset += w;
        total += w;
      }
    }
    std::fclose(f);
    std::printf("injected %zu frames from %s\n", total, raw_path);
  } else {
    const size_t total_frames = static_cast<size_t>(48000 * seconds);
    std::vector<int16_t> frame(960);
    size_t written = 0;
    while (written < total_frames) {
      const size_t n = std::min(frame.size(), total_frames - written);
      for (size_t i = 0; i < n; ++i) {
        const double t = static_cast<double>(written + i) / 48000.0;
        frame[i] = static_cast<int16_t>(std::sin(2.0 * M_PI * hz * t) * 0.2 * 32767.0);
      }
      size_t offset = 0;
      while (offset < n) {
        const size_t w = SharedRingWriteS16(&ring, frame.data() + offset, n - offset);
        if (w == 0) continue;
        offset += w;
        written += w;
      }
    }
    std::printf("injected %.2fs tone %.1f Hz (%zu frames)\n", seconds, hz, written);
  }

  // Keep the ring file for the plugin; don't unlink on close.
  ring.owner = false;
  SharedRingClose(&ring);
  return 0;
}
