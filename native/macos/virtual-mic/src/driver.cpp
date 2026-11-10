#include "device_state.hpp"

#include <CoreAudio/AudioServerPlugIn.h>
#include <CoreFoundation/CFPlugInCOM.h>
#include <mach/mach_time.h>

#include <algorithm>
#include <cmath>
#include <cstring>

using airinput::vmic::EnsureRingMapped;
using airinput::vmic::State;
using airinput::vmic::kChannelCount;
using airinput::vmic::kObjectID_Box;
using airinput::vmic::kObjectID_Device;
using airinput::vmic::kObjectID_Mute_Input;
using airinput::vmic::kObjectID_PlugIn;
using airinput::vmic::kObjectID_Stream_Input;
using airinput::vmic::kObjectID_Volume_Input;
using airinput::vmic::kSampleRate;
using airinput::vmic::SharedRingReadF32;

namespace {

AudioServerPlugInDriverInterface gInterface{};
AudioServerPlugInDriverInterface* gInterfacePtr = &gInterface;
AudioServerPlugInDriverRef gDriverRef = &gInterfacePtr;

bool IsValidObject(AudioObjectID id) {
  switch (id) {
    case kObjectID_PlugIn:
    case kObjectID_Box:
    case kObjectID_Device:
    case kObjectID_Stream_Input:
    case kObjectID_Volume_Input:
    case kObjectID_Mute_Input:
      return true;
    default:
      return false;
  }
}

AudioStreamBasicDescription MakeASBD() {
  AudioStreamBasicDescription asbd{};
  asbd.mSampleRate = kSampleRate;
  asbd.mFormatID = kAudioFormatLinearPCM;
  asbd.mFormatFlags =
      kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian;
  asbd.mBytesPerPacket = sizeof(Float32) * kChannelCount;
  asbd.mFramesPerPacket = 1;
  asbd.mBytesPerFrame = sizeof(Float32) * kChannelCount;
  asbd.mChannelsPerFrame = kChannelCount;
  asbd.mBitsPerChannel = 32;
  return asbd;
}

HRESULT DriverQueryInterface(void* inDriver, REFIID iid, LPVOID* outInterface) {
  if (inDriver != gDriverRef || outInterface == nullptr) return E_POINTER;
  CFUUIDRef requested = CFUUIDCreateFromUUIDBytes(nullptr, iid);
  const Boolean match =
      requested &&
      (CFEqual(requested, IUnknownUUID) || CFEqual(requested, kAudioServerPlugInDriverInterfaceUUID));
  if (requested) CFRelease(requested);
  if (!match) {
    *outInterface = nullptr;
    return E_NOINTERFACE;
  }
  *outInterface = gDriverRef;
  return S_OK;
}

ULONG DriverAddRef(void*) { return 1; }
ULONG DriverRelease(void*) { return 1; }

OSStatus DriverInitialize(AudioServerPlugInDriverRef inDriver, AudioServerPlugInHostRef inHost) {
  if (inDriver != gDriverRef) return kAudioHardwareBadObjectError;
  auto& s = State();
  s.host = inHost;
  EnsureRingMapped();

  mach_timebase_info_data_t tb{};
  mach_timebase_info(&tb);
  const Float64 nanos_per_host = static_cast<Float64>(tb.numer) / static_cast<Float64>(tb.denom);
  const Float64 host_ticks_per_second = 1.0e9 / nanos_per_host;
  s.host_ticks_per_frame.store(host_ticks_per_second / kSampleRate, std::memory_order_relaxed);
  s.anchor_host_time.store(mach_absolute_time(), std::memory_order_relaxed);
  s.anchor_sample_time.store(0, std::memory_order_relaxed);
  s.number_time_stamps.store(0, std::memory_order_relaxed);
  return kAudioHardwareNoError;
}

OSStatus DriverCreateDevice(AudioServerPlugInDriverRef, CFDictionaryRef, const AudioServerPlugInClientInfo*,
                            AudioObjectID*) {
  return kAudioHardwareUnsupportedOperationError;
}

OSStatus DriverDestroyDevice(AudioServerPlugInDriverRef, AudioObjectID) {
  return kAudioHardwareUnsupportedOperationError;
}

OSStatus DriverAddDeviceClient(AudioServerPlugInDriverRef, AudioObjectID, const AudioServerPlugInClientInfo*) {
  return kAudioHardwareNoError;
}

OSStatus DriverRemoveDeviceClient(AudioServerPlugInDriverRef, AudioObjectID,
                                  const AudioServerPlugInClientInfo*) {
  return kAudioHardwareNoError;
}

OSStatus DriverPerformDeviceConfigurationChange(AudioServerPlugInDriverRef, AudioObjectID, UInt64, void*) {
  return kAudioHardwareNoError;
}

OSStatus DriverAbortDeviceConfigurationChange(AudioServerPlugInDriverRef, AudioObjectID, UInt64, void*) {
  return kAudioHardwareNoError;
}

Boolean DriverHasProperty(AudioServerPlugInDriverRef inDriver, AudioObjectID inObjectID, pid_t,
                          const AudioObjectPropertyAddress* inAddress) {
  if (inDriver != gDriverRef || !inAddress || !IsValidObject(inObjectID)) return false;
  switch (inObjectID) {
    case kObjectID_PlugIn:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
        case kAudioObjectPropertyClass:
        case kAudioObjectPropertyOwner:
        case kAudioObjectPropertyManufacturer:
        case kAudioObjectPropertyOwnedObjects:
        case kAudioPlugInPropertyBoxList:
        case kAudioPlugInPropertyTranslateUIDToBox:
        case kAudioPlugInPropertyDeviceList:
        case kAudioPlugInPropertyTranslateUIDToDevice:
        case kAudioPlugInPropertyResourceBundle:
          return true;
        default:
          return false;
      }
    case kObjectID_Box:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
        case kAudioObjectPropertyClass:
        case kAudioObjectPropertyOwner:
        case kAudioObjectPropertyName:
        case kAudioObjectPropertyManufacturer:
        case kAudioObjectPropertyOwnedObjects:
        case kAudioObjectPropertyIdentify:
        case kAudioBoxPropertyBoxUID:
        case kAudioBoxPropertyTransportType:
        case kAudioBoxPropertyHasAudio:
        case kAudioBoxPropertyHasVideo:
        case kAudioBoxPropertyHasMIDI:
        case kAudioBoxPropertyIsProtected:
        case kAudioBoxPropertyAcquired:
        case kAudioBoxPropertyAcquisitionFailed:
        case kAudioBoxPropertyDeviceList:
          return true;
        default:
          return false;
      }
    case kObjectID_Device:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
        case kAudioObjectPropertyClass:
        case kAudioObjectPropertyOwner:
        case kAudioObjectPropertyName:
        case kAudioObjectPropertyManufacturer:
        case kAudioObjectPropertyOwnedObjects:
        case kAudioDevicePropertyDeviceUID:
        case kAudioDevicePropertyModelUID:
        case kAudioDevicePropertyTransportType:
        case kAudioDevicePropertyRelatedDevices:
        case kAudioDevicePropertyClockDomain:
        case kAudioDevicePropertyDeviceIsAlive:
        case kAudioDevicePropertyDeviceIsRunning:
        case kAudioDevicePropertyDeviceCanBeDefaultDevice:
        case kAudioDevicePropertyDeviceCanBeDefaultSystemDevice:
        case kAudioDevicePropertyLatency:
        case kAudioDevicePropertyStreams:
        case kAudioObjectPropertyControlList:
        case kAudioDevicePropertyNominalSampleRate:
        case kAudioDevicePropertyAvailableNominalSampleRates:
        case kAudioDevicePropertyIsHidden:
        case kAudioDevicePropertyPreferredChannelsForStereo:
        case kAudioDevicePropertyPreferredChannelLayout:
          return true;
        default:
          return false;
      }
    case kObjectID_Stream_Input:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
        case kAudioObjectPropertyClass:
        case kAudioObjectPropertyOwner:
        case kAudioObjectPropertyOwnedObjects:
        case kAudioStreamPropertyIsActive:
        case kAudioStreamPropertyDirection:
        case kAudioStreamPropertyTerminalType:
        case kAudioStreamPropertyStartingChannel:
        case kAudioStreamPropertyLatency:
        case kAudioStreamPropertyVirtualFormat:
        case kAudioStreamPropertyAvailableVirtualFormats:
        case kAudioStreamPropertyPhysicalFormat:
        case kAudioStreamPropertyAvailablePhysicalFormats:
          return true;
        default:
          return false;
      }
    case kObjectID_Volume_Input:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
        case kAudioObjectPropertyClass:
        case kAudioObjectPropertyOwner:
        case kAudioObjectPropertyOwnedObjects:
        case kAudioControlPropertyScope:
        case kAudioControlPropertyElement:
        case kAudioLevelControlPropertyScalarValue:
        case kAudioLevelControlPropertyDecibelValue:
        case kAudioLevelControlPropertyDecibelRange:
        case kAudioLevelControlPropertyConvertScalarToDecibels:
        case kAudioLevelControlPropertyConvertDecibelsToScalar:
          return true;
        default:
          return false;
      }
    case kObjectID_Mute_Input:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
        case kAudioObjectPropertyClass:
        case kAudioObjectPropertyOwner:
        case kAudioObjectPropertyOwnedObjects:
        case kAudioControlPropertyScope:
        case kAudioControlPropertyElement:
        case kAudioBooleanControlPropertyValue:
          return true;
        default:
          return false;
      }
    default:
      return false;
  }
}

OSStatus DriverIsPropertySettable(AudioServerPlugInDriverRef inDriver, AudioObjectID inObjectID, pid_t,
                                  const AudioObjectPropertyAddress* inAddress, Boolean* outSettable) {
  if (!outSettable) return kAudioHardwareIllegalOperationError;
  *outSettable = false;
  if (!DriverHasProperty(inDriver, inObjectID, 0, inAddress)) return kAudioHardwareUnknownPropertyError;
  switch (inObjectID) {
    case kObjectID_Box:
      if (inAddress->mSelector == kAudioBoxPropertyAcquired) *outSettable = true;
      break;
    case kObjectID_Stream_Input:
      if (inAddress->mSelector == kAudioStreamPropertyIsActive ||
          inAddress->mSelector == kAudioStreamPropertyVirtualFormat ||
          inAddress->mSelector == kAudioStreamPropertyPhysicalFormat) {
        *outSettable = true;
      }
      break;
    case kObjectID_Volume_Input:
      if (inAddress->mSelector == kAudioLevelControlPropertyScalarValue ||
          inAddress->mSelector == kAudioLevelControlPropertyDecibelValue) {
        *outSettable = true;
      }
      break;
    case kObjectID_Mute_Input:
      if (inAddress->mSelector == kAudioBooleanControlPropertyValue) *outSettable = true;
      break;
    default:
      break;
  }
  return kAudioHardwareNoError;
}

OSStatus DriverGetPropertyDataSize(AudioServerPlugInDriverRef inDriver, AudioObjectID inObjectID, pid_t,
                                   const AudioObjectPropertyAddress* inAddress, UInt32, const void*,
                                   UInt32* outSize) {
  if (!outSize) return kAudioHardwareIllegalOperationError;
  if (!DriverHasProperty(inDriver, inObjectID, 0, inAddress)) return kAudioHardwareUnknownPropertyError;

  switch (inAddress->mSelector) {
    case kAudioObjectPropertyBaseClass:
    case kAudioObjectPropertyClass:
    case kAudioObjectPropertyOwner:
    case kAudioObjectPropertyIdentify:
    case kAudioBoxPropertyTransportType:  // same FourCC as kAudioDevicePropertyTransportType
    case kAudioBoxPropertyHasAudio:
    case kAudioBoxPropertyHasVideo:
    case kAudioBoxPropertyHasMIDI:
    case kAudioBoxPropertyIsProtected:
    case kAudioBoxPropertyAcquired:
    case kAudioBoxPropertyAcquisitionFailed:
    case kAudioDevicePropertyClockDomain:
    case kAudioDevicePropertyDeviceIsAlive:
    case kAudioDevicePropertyDeviceIsRunning:
    case kAudioDevicePropertyDeviceCanBeDefaultDevice:
    case kAudioDevicePropertyDeviceCanBeDefaultSystemDevice:
    case kAudioDevicePropertyLatency:  // same FourCC as kAudioStreamPropertyLatency
    case kAudioDevicePropertyIsHidden:
    case kAudioStreamPropertyIsActive:
    case kAudioStreamPropertyDirection:
    case kAudioStreamPropertyTerminalType:
    case kAudioStreamPropertyStartingChannel:
    case kAudioControlPropertyScope:
    case kAudioControlPropertyElement:
    case kAudioBooleanControlPropertyValue:
      *outSize = sizeof(UInt32);
      return noErr;
    case kAudioLevelControlPropertyScalarValue:
    case kAudioLevelControlPropertyDecibelValue:
      *outSize = sizeof(Float32);
      return noErr;
    case kAudioDevicePropertyNominalSampleRate:
      *outSize = sizeof(Float64);
      return noErr;
    case kAudioObjectPropertyManufacturer:
    case kAudioObjectPropertyName:
    case kAudioBoxPropertyBoxUID:
    case kAudioDevicePropertyDeviceUID:
    case kAudioDevicePropertyModelUID:
    case kAudioPlugInPropertyResourceBundle:
      *outSize = sizeof(CFStringRef);
      return noErr;
    case kAudioObjectPropertyOwnedObjects:
      if (inObjectID == kObjectID_Device) {
        *outSize = 3 * sizeof(AudioObjectID);
      } else {
        *outSize = sizeof(AudioObjectID);
      }
      return noErr;
    case kAudioPlugInPropertyBoxList:
    case kAudioPlugInPropertyDeviceList:
    case kAudioBoxPropertyDeviceList:
    case kAudioDevicePropertyRelatedDevices:
    case kAudioPlugInPropertyTranslateUIDToBox:
    case kAudioPlugInPropertyTranslateUIDToDevice:
      *outSize = sizeof(AudioObjectID);
      return noErr;
    case kAudioDevicePropertyStreams:
      *outSize = (inAddress->mScope == kAudioObjectPropertyScopeOutput) ? 0 : sizeof(AudioObjectID);
      return noErr;
    case kAudioObjectPropertyControlList:
      *outSize = 2 * sizeof(AudioObjectID);
      return noErr;
    case kAudioDevicePropertyAvailableNominalSampleRates:
    case kAudioLevelControlPropertyDecibelRange:
      *outSize = sizeof(AudioValueRange);
      return noErr;
    case kAudioDevicePropertyPreferredChannelsForStereo:
      *outSize = 2 * sizeof(UInt32);
      return noErr;
    case kAudioDevicePropertyPreferredChannelLayout:
      *outSize = static_cast<UInt32>(offsetof(AudioChannelLayout, mChannelDescriptions) +
                                     sizeof(AudioChannelDescription) * kChannelCount);
      return noErr;
    case kAudioStreamPropertyVirtualFormat:
    case kAudioStreamPropertyPhysicalFormat:
      *outSize = sizeof(AudioStreamBasicDescription);
      return noErr;
    case kAudioStreamPropertyAvailableVirtualFormats:
    case kAudioStreamPropertyAvailablePhysicalFormats:
      *outSize = sizeof(AudioStreamRangedDescription);
      return noErr;
    default:
      return kAudioHardwareUnknownPropertyError;
  }
}

OSStatus DriverGetPropertyData(AudioServerPlugInDriverRef inDriver, AudioObjectID inObjectID, pid_t,
                               const AudioObjectPropertyAddress* inAddress, UInt32 inQualifierDataSize,
                               const void*, UInt32 inDataSize, UInt32* outDataSize, void* outData) {
  UInt32 needed = 0;
  OSStatus err =
      DriverGetPropertyDataSize(inDriver, inObjectID, 0, inAddress, 0, nullptr, &needed);
  if (err) return err;
  if (!outData || !outDataSize) return kAudioHardwareIllegalOperationError;
  if (inDataSize < needed) return kAudioHardwareBadPropertySizeError;

  auto put_u32 = [&](UInt32 v) {
    *reinterpret_cast<UInt32*>(outData) = v;
    *outDataSize = sizeof(UInt32);
  };
  auto put_f32 = [&](Float32 v) {
    *reinterpret_cast<Float32*>(outData) = v;
    *outDataSize = sizeof(Float32);
  };
  auto put_f64 = [&](Float64 v) {
    *reinterpret_cast<Float64*>(outData) = v;
    *outDataSize = sizeof(Float64);
  };
  auto put_str = [&](CFStringRef s) {
    *reinterpret_cast<CFStringRef*>(outData) = s;
    *outDataSize = sizeof(CFStringRef);
  };

  switch (inObjectID) {
    case kObjectID_PlugIn:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
          put_u32(kAudioObjectClassID);
          return noErr;
        case kAudioObjectPropertyClass:
          put_u32(kAudioPlugInClassID);
          return noErr;
        case kAudioObjectPropertyOwner:
          put_u32(kAudioObjectUnknown);
          return noErr;
        case kAudioObjectPropertyManufacturer:
          put_str(CFSTR("AirInput"));
          return noErr;
        case kAudioObjectPropertyOwnedObjects:
        case kAudioPlugInPropertyBoxList:
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Box;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioPlugInPropertyDeviceList:
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Device;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioPlugInPropertyTranslateUIDToBox:
          if (inQualifierDataSize != sizeof(CFStringRef)) return kAudioHardwareBadPropertySizeError;
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Box;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioPlugInPropertyTranslateUIDToDevice:
          if (inQualifierDataSize != sizeof(CFStringRef)) return kAudioHardwareBadPropertySizeError;
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Device;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioPlugInPropertyResourceBundle:
          put_str(CFSTR(""));
          return noErr;
      }
      break;

    case kObjectID_Box:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
          put_u32(kAudioObjectClassID);
          return noErr;
        case kAudioObjectPropertyClass:
          put_u32(kAudioBoxClassID);
          return noErr;
        case kAudioObjectPropertyOwner:
          put_u32(kObjectID_PlugIn);
          return noErr;
        case kAudioObjectPropertyName:
          put_str(CFSTR("AirInput"));
          return noErr;
        case kAudioObjectPropertyManufacturer:
          put_str(CFSTR("AirInput"));
          return noErr;
        case kAudioObjectPropertyOwnedObjects:
        case kAudioBoxPropertyDeviceList:
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Device;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioObjectPropertyIdentify:
          put_u32(0);
          return noErr;
        case kAudioBoxPropertyBoxUID:
          put_str(CFSTR("com.airinput.virtualmic.box"));
          return noErr;
        case kAudioBoxPropertyTransportType:
          put_u32(kAudioDeviceTransportTypeVirtual);
          return noErr;
        case kAudioBoxPropertyHasAudio:
          put_u32(1);
          return noErr;
        case kAudioBoxPropertyHasVideo:
        case kAudioBoxPropertyHasMIDI:
        case kAudioBoxPropertyIsProtected:
        case kAudioBoxPropertyAcquisitionFailed:
          put_u32(0);
          return noErr;
        case kAudioBoxPropertyAcquired:
          put_u32(State().box_acquired.load() ? 1 : 0);
          return noErr;
      }
      break;

    case kObjectID_Device:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
          put_u32(kAudioObjectClassID);
          return noErr;
        case kAudioObjectPropertyClass:
          put_u32(kAudioDeviceClassID);
          return noErr;
        case kAudioObjectPropertyOwner:
          put_u32(kObjectID_Box);
          return noErr;
        case kAudioObjectPropertyName:
          put_str(CFSTR("AirInput Microphone"));
          return noErr;
        case kAudioObjectPropertyManufacturer:
          put_str(CFSTR("AirInput"));
          return noErr;
        case kAudioObjectPropertyOwnedObjects: {
          auto* ids = reinterpret_cast<AudioObjectID*>(outData);
          ids[0] = kObjectID_Stream_Input;
          ids[1] = kObjectID_Volume_Input;
          ids[2] = kObjectID_Mute_Input;
          *outDataSize = 3 * sizeof(AudioObjectID);
          return noErr;
        }
        case kAudioDevicePropertyDeviceUID:
          put_str(CFSTR("com.airinput.virtualmic.device"));
          return noErr;
        case kAudioDevicePropertyModelUID:
          put_str(CFSTR("com.airinput.virtualmic.model"));
          return noErr;
        case kAudioDevicePropertyTransportType:
          put_u32(kAudioDeviceTransportTypeVirtual);
          return noErr;
        case kAudioDevicePropertyRelatedDevices:
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Device;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioDevicePropertyClockDomain:
          put_u32(0);
          return noErr;
        case kAudioDevicePropertyDeviceIsAlive:
          put_u32(1);
          return noErr;
        case kAudioDevicePropertyDeviceIsRunning:
          put_u32(State().io_running.load() > 0 ? 1 : 0);
          return noErr;
        case kAudioDevicePropertyDeviceCanBeDefaultDevice:
        case kAudioDevicePropertyDeviceCanBeDefaultSystemDevice:
          put_u32(inAddress->mScope == kAudioObjectPropertyScopeInput ? 1 : 0);
          return noErr;
        case kAudioDevicePropertyLatency:
          put_u32(0);
          return noErr;
        case kAudioDevicePropertyStreams:
          if (inAddress->mScope == kAudioObjectPropertyScopeOutput) {
            *outDataSize = 0;
            return noErr;
          }
          *reinterpret_cast<AudioObjectID*>(outData) = kObjectID_Stream_Input;
          *outDataSize = sizeof(AudioObjectID);
          return noErr;
        case kAudioObjectPropertyControlList: {
          auto* ids = reinterpret_cast<AudioObjectID*>(outData);
          ids[0] = kObjectID_Volume_Input;
          ids[1] = kObjectID_Mute_Input;
          *outDataSize = 2 * sizeof(AudioObjectID);
          return noErr;
        }
        case kAudioDevicePropertyNominalSampleRate:
          put_f64(kSampleRate);
          return noErr;
        case kAudioDevicePropertyAvailableNominalSampleRates: {
          auto* range = reinterpret_cast<AudioValueRange*>(outData);
          range->mMinimum = kSampleRate;
          range->mMaximum = kSampleRate;
          *outDataSize = sizeof(AudioValueRange);
          return noErr;
        }
        case kAudioDevicePropertyIsHidden:
          put_u32(0);
          return noErr;
        case kAudioDevicePropertyPreferredChannelsForStereo: {
          auto* ch = reinterpret_cast<UInt32*>(outData);
          ch[0] = 1;
          ch[1] = 1;
          *outDataSize = 2 * sizeof(UInt32);
          return noErr;
        }
        case kAudioDevicePropertyPreferredChannelLayout: {
          auto* layout = reinterpret_cast<AudioChannelLayout*>(outData);
          std::memset(layout, 0, needed);
          layout->mChannelLayoutTag = kAudioChannelLayoutTag_Mono;
          layout->mNumberChannelDescriptions = 1;
          layout->mChannelDescriptions[0].mChannelLabel = kAudioChannelLabel_Mono;
          *outDataSize = needed;
          return noErr;
        }
      }
      break;

    case kObjectID_Stream_Input:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
          put_u32(kAudioObjectClassID);
          return noErr;
        case kAudioObjectPropertyClass:
          put_u32(kAudioStreamClassID);
          return noErr;
        case kAudioObjectPropertyOwner:
          put_u32(kObjectID_Device);
          return noErr;
        case kAudioObjectPropertyOwnedObjects:
          *outDataSize = 0;
          return noErr;
        case kAudioStreamPropertyIsActive:
          put_u32(1);
          return noErr;
        case kAudioStreamPropertyDirection:
          put_u32(1);
          return noErr;
        case kAudioStreamPropertyTerminalType:
          put_u32(kAudioStreamTerminalTypeMicrophone);
          return noErr;
        case kAudioStreamPropertyStartingChannel:
          put_u32(1);
          return noErr;
        case kAudioStreamPropertyLatency:
          put_u32(0);
          return noErr;
        case kAudioStreamPropertyVirtualFormat:
        case kAudioStreamPropertyPhysicalFormat:
          *reinterpret_cast<AudioStreamBasicDescription*>(outData) = MakeASBD();
          *outDataSize = sizeof(AudioStreamBasicDescription);
          return noErr;
        case kAudioStreamPropertyAvailableVirtualFormats:
        case kAudioStreamPropertyAvailablePhysicalFormats: {
          auto* desc = reinterpret_cast<AudioStreamRangedDescription*>(outData);
          desc->mFormat = MakeASBD();
          desc->mSampleRateRange.mMinimum = kSampleRate;
          desc->mSampleRateRange.mMaximum = kSampleRate;
          *outDataSize = sizeof(AudioStreamRangedDescription);
          return noErr;
        }
      }
      break;

    case kObjectID_Volume_Input:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
          put_u32(kAudioObjectClassID);
          return noErr;
        case kAudioObjectPropertyClass:
          put_u32(kAudioVolumeControlClassID);
          return noErr;
        case kAudioObjectPropertyOwner:
          put_u32(kObjectID_Device);
          return noErr;
        case kAudioObjectPropertyOwnedObjects:
          *outDataSize = 0;
          return noErr;
        case kAudioControlPropertyScope:
          put_u32(kAudioObjectPropertyScopeInput);
          return noErr;
        case kAudioControlPropertyElement:
          put_u32(kAudioObjectPropertyElementMain);
          return noErr;
        case kAudioLevelControlPropertyScalarValue:
          put_f32(State().volume.load());
          return noErr;
        case kAudioLevelControlPropertyDecibelValue: {
          const Float32 v = State().volume.load();
          put_f32(v <= 0.0f ? -96.0f : 20.0f * std::log10(v));
          return noErr;
        }
        case kAudioLevelControlPropertyDecibelRange: {
          auto* range = reinterpret_cast<AudioValueRange*>(outData);
          range->mMinimum = -96.0;
          range->mMaximum = 0.0;
          *outDataSize = sizeof(AudioValueRange);
          return noErr;
        }
        case kAudioLevelControlPropertyConvertScalarToDecibels: {
          const Float32 v = *reinterpret_cast<const Float32*>(outData);
          put_f32(v <= 0.0f ? -96.0f : 20.0f * std::log10(v));
          return noErr;
        }
        case kAudioLevelControlPropertyConvertDecibelsToScalar: {
          const Float32 db = *reinterpret_cast<const Float32*>(outData);
          put_f32(std::pow(10.0f, db / 20.0f));
          return noErr;
        }
      }
      break;

    case kObjectID_Mute_Input:
      switch (inAddress->mSelector) {
        case kAudioObjectPropertyBaseClass:
          put_u32(kAudioObjectClassID);
          return noErr;
        case kAudioObjectPropertyClass:
          put_u32(kAudioMuteControlClassID);
          return noErr;
        case kAudioObjectPropertyOwner:
          put_u32(kObjectID_Device);
          return noErr;
        case kAudioObjectPropertyOwnedObjects:
          *outDataSize = 0;
          return noErr;
        case kAudioControlPropertyScope:
          put_u32(kAudioObjectPropertyScopeInput);
          return noErr;
        case kAudioControlPropertyElement:
          put_u32(kAudioObjectPropertyElementMain);
          return noErr;
        case kAudioBooleanControlPropertyValue:
          put_u32(State().device_muted.load() ? 1 : 0);
          return noErr;
      }
      break;
  }

  return kAudioHardwareUnknownPropertyError;
}

OSStatus DriverSetPropertyData(AudioServerPlugInDriverRef inDriver, AudioObjectID inObjectID, pid_t,
                               const AudioObjectPropertyAddress* inAddress, UInt32, const void*,
                               UInt32 inDataSize, const void* inData) {
  Boolean settable = false;
  OSStatus err = DriverIsPropertySettable(inDriver, inObjectID, 0, inAddress, &settable);
  if (err) return err;
  if (!settable) return kAudioHardwareUnknownPropertyError;

  switch (inObjectID) {
    case kObjectID_Box:
      if (inAddress->mSelector == kAudioBoxPropertyAcquired && inDataSize == sizeof(UInt32)) {
        State().box_acquired.store(*reinterpret_cast<const UInt32*>(inData) != 0);
        return noErr;
      }
      break;
    case kObjectID_Stream_Input:
      if (inAddress->mSelector == kAudioStreamPropertyIsActive && inDataSize == sizeof(UInt32)) {
        return noErr;
      }
      if ((inAddress->mSelector == kAudioStreamPropertyVirtualFormat ||
           inAddress->mSelector == kAudioStreamPropertyPhysicalFormat) &&
          inDataSize == sizeof(AudioStreamBasicDescription)) {
        const auto* asbd = reinterpret_cast<const AudioStreamBasicDescription*>(inData);
        if (asbd->mSampleRate == kSampleRate && asbd->mChannelsPerFrame == kChannelCount &&
            asbd->mFormatID == kAudioFormatLinearPCM) {
          return noErr;
        }
        return kAudioDeviceUnsupportedFormatError;
      }
      break;
    case kObjectID_Volume_Input:
      if (inAddress->mSelector == kAudioLevelControlPropertyScalarValue &&
          inDataSize == sizeof(Float32)) {
        State().volume.store(std::clamp(*reinterpret_cast<const Float32*>(inData), 0.0f, 1.0f));
        return noErr;
      }
      if (inAddress->mSelector == kAudioLevelControlPropertyDecibelValue &&
          inDataSize == sizeof(Float32)) {
        const Float32 db = *reinterpret_cast<const Float32*>(inData);
        Float32 v = (db <= -96.0f) ? 0.0f : std::pow(10.0f, db / 20.0f);
        State().volume.store(std::clamp(v, 0.0f, 1.0f));
        return noErr;
      }
      break;
    case kObjectID_Mute_Input:
      if (inAddress->mSelector == kAudioBooleanControlPropertyValue && inDataSize == sizeof(UInt32)) {
        State().device_muted.store(*reinterpret_cast<const UInt32*>(inData) != 0);
        return noErr;
      }
      break;
  }
  return kAudioHardwareUnknownPropertyError;
}

OSStatus DriverStartIO(AudioServerPlugInDriverRef inDriver, AudioObjectID inDeviceObjectID, UInt32) {
  if (inDriver != gDriverRef || inDeviceObjectID != kObjectID_Device) return kAudioHardwareBadObjectError;
  EnsureRingMapped();
  auto& s = State();
  if (s.io_running.fetch_add(1) == 0) {
    s.anchor_host_time.store(mach_absolute_time(), std::memory_order_relaxed);
    s.anchor_sample_time.store(0, std::memory_order_relaxed);
  }
  return noErr;
}

OSStatus DriverStopIO(AudioServerPlugInDriverRef inDriver, AudioObjectID inDeviceObjectID, UInt32) {
  if (inDriver != gDriverRef || inDeviceObjectID != kObjectID_Device) return kAudioHardwareBadObjectError;
  auto& s = State();
  if (s.io_running.load() == 0) return kAudioHardwareIllegalOperationError;
  s.io_running.fetch_sub(1);
  return noErr;
}

OSStatus DriverGetZeroTimeStamp(AudioServerPlugInDriverRef inDriver, AudioObjectID inDeviceObjectID, UInt32,
                                Float64* outSampleTime, UInt64* outHostTime, UInt64* outSeed) {
  if (inDriver != gDriverRef || inDeviceObjectID != kObjectID_Device) return kAudioHardwareBadObjectError;
  auto& s = State();
  const UInt64 host = mach_absolute_time();
  const UInt64 anchor_host = s.anchor_host_time.load(std::memory_order_relaxed);
  const Float64 ticks_per_frame = s.host_ticks_per_frame.load(std::memory_order_relaxed);
  const Float64 frames = static_cast<Float64>(host - anchor_host) / ticks_per_frame;
  *outSampleTime = static_cast<Float64>(s.anchor_sample_time.load(std::memory_order_relaxed)) + frames;
  *outHostTime = host;
  *outSeed = 1;
  return noErr;
}

OSStatus DriverWillDoIOOperation(AudioServerPlugInDriverRef, AudioObjectID, UInt32, UInt32 inOperationID,
                                 Boolean* outWillDo, Boolean* outWillDoInPlace) {
  if (!outWillDo || !outWillDoInPlace) return kAudioHardwareIllegalOperationError;
  const Boolean yes = (inOperationID == kAudioServerPlugInIOOperationReadInput);
  *outWillDo = yes;
  *outWillDoInPlace = yes;
  return noErr;
}

OSStatus DriverBeginIOOperation(AudioServerPlugInDriverRef, AudioObjectID, UInt32, UInt32, UInt32,
                                const AudioServerPlugInIOCycleInfo*) {
  return noErr;
}

OSStatus DriverDoIOOperation(AudioServerPlugInDriverRef inDriver, AudioObjectID inDeviceObjectID,
                             AudioObjectID inStreamObjectID, UInt32, UInt32 inOperationID,
                             UInt32 inIOBufferFrameSize, const AudioServerPlugInIOCycleInfo*,
                             void* ioMainBuffer, void*) {
  if (inDriver != gDriverRef || inDeviceObjectID != kObjectID_Device ||
      inStreamObjectID != kObjectID_Stream_Input) {
    return kAudioHardwareBadObjectError;
  }
  if (inOperationID != kAudioServerPlugInIOOperationReadInput) return noErr;
  if (!ioMainBuffer) return kAudioHardwareIllegalOperationError;

  auto* out = reinterpret_cast<float*>(ioMainBuffer);
  auto& s = State();
  if (!s.ring_ready) EnsureRingMapped();

  if (s.device_muted.load(std::memory_order_relaxed)) {
    std::memset(out, 0, static_cast<size_t>(inIOBufferFrameSize) * kChannelCount * sizeof(float));
    return noErr;
  }

  SharedRingReadF32(&s.ring, out, inIOBufferFrameSize);
  const float vol = s.volume.load(std::memory_order_relaxed);
  if (vol != 1.0f) {
    const UInt32 n = inIOBufferFrameSize * kChannelCount;
    for (UInt32 i = 0; i < n; ++i) out[i] *= vol;
  }
  return noErr;
}

OSStatus DriverEndIOOperation(AudioServerPlugInDriverRef, AudioObjectID, UInt32, UInt32, UInt32,
                              const AudioServerPlugInIOCycleInfo*) {
  return noErr;
}

void InitInterface() {
  gInterface = AudioServerPlugInDriverInterface{
      nullptr,
      DriverQueryInterface,
      DriverAddRef,
      DriverRelease,
      DriverInitialize,
      DriverCreateDevice,
      DriverDestroyDevice,
      DriverAddDeviceClient,
      DriverRemoveDeviceClient,
      DriverPerformDeviceConfigurationChange,
      DriverAbortDeviceConfigurationChange,
      DriverHasProperty,
      DriverIsPropertySettable,
      DriverGetPropertyDataSize,
      DriverGetPropertyData,
      DriverSetPropertyData,
      DriverStartIO,
      DriverStopIO,
      DriverGetZeroTimeStamp,
      DriverWillDoIOOperation,
      DriverBeginIOOperation,
      DriverDoIOOperation,
      DriverEndIOOperation,
  };
  gInterfacePtr = &gInterface;
  gDriverRef = &gInterfacePtr;
}

struct Boot {
  Boot() { InitInterface(); }
} gBoot;

}  // namespace

extern "C" void* AirInput_Create(CFAllocatorRef, CFUUIDRef requestedTypeUUID) {
  if (!requestedTypeUUID) return nullptr;
  if (!CFEqual(requestedTypeUUID, kAudioServerPlugInTypeUUID)) return nullptr;
  return gDriverRef;
}
