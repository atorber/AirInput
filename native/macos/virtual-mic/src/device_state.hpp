#pragma once

#include "shared_ring.hpp"

#include <CoreAudio/AudioServerPlugIn.h>

#include <atomic>
#include <mutex>

namespace airinput::vmic {

enum ObjectID : AudioObjectID {
  kObjectID_PlugIn = kAudioObjectPlugInObject,
  kObjectID_Box = 2,
  kObjectID_Device = 3,
  kObjectID_Stream_Input = 4,
  kObjectID_Volume_Input = 5,
  kObjectID_Mute_Input = 6,
};

constexpr Float64 kSampleRate = 48000.0;
constexpr UInt32 kChannelCount = 1;

struct DeviceState {
  std::mutex mutex;
  AudioServerPlugInHostRef host = nullptr;

  std::atomic<UInt32> io_running{0};
  std::atomic<Float64> host_ticks_per_frame{0.0};
  std::atomic<UInt64> number_time_stamps{0};
  std::atomic<UInt64> anchor_host_time{0};
  std::atomic<UInt64> anchor_sample_time{0};

  std::atomic<bool> box_acquired{true};
  std::atomic<bool> device_muted{false};
  std::atomic<Float32> volume{1.0f};

  SharedRingView ring{};
  bool ring_ready = false;
};

DeviceState& State();

bool EnsureRingMapped();

}  // namespace airinput::vmic
