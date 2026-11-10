#include "airinput_pipeline.h"

#include "audio_protocol.hpp"
#include "shared_ring.hpp"

#include <CommonCrypto/CommonHMAC.h>
#include <opus.h>

#include <chrono>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <random>
#include <string>
#include <vector>

namespace {

constexpr int kSampleRate = 48000;
constexpr int kFrameSamples = 960;
constexpr size_t kChallengeLen = 16;
constexpr char kDefaultPairingKey[] = "AirInput-Default-Pairing-Key-v1!!";
constexpr char kCaptureWavPath[] = "/tmp/airinput-capture.wav";

uint64_t nowMs() {
  using namespace std::chrono;
  return static_cast<uint64_t>(
      duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count());
}

void hmacSha256(const uint8_t* key, size_t key_len, const uint8_t* data, size_t data_len,
                uint8_t out[32]) {
  CCHmac(kCCHmacAlgSHA256, key, key_len, data, data_len, out);
}

bool constTimeEq(const uint8_t* a, const uint8_t* b, size_t n) {
  uint8_t diff = 0;
  for (size_t i = 0; i < n; ++i) diff = static_cast<uint8_t>(diff | (a[i] ^ b[i]));
  return diff == 0;
}

void writeU32LE(uint8_t* p, uint32_t v) {
  p[0] = static_cast<uint8_t>(v);
  p[1] = static_cast<uint8_t>(v >> 8);
  p[2] = static_cast<uint8_t>(v >> 16);
  p[3] = static_cast<uint8_t>(v >> 24);
}

void writeU16LE(uint8_t* p, uint16_t v) {
  p[0] = static_cast<uint8_t>(v);
  p[1] = static_cast<uint8_t>(v >> 8);
}

}  // namespace

struct AirInputPipeline {
  std::mutex mu;
  airinput::FrameParser parser;
  airinput::vmic::SharedRingView ring{};
  OpusDecoder* decoder = nullptr;
  std::vector<uint8_t> pairing_key;
  std::vector<uint8_t> pending_challenge;
  bool authenticated = false;
  bool ring_ready = false;
  uint64_t packets = 0;
  uint64_t audio_frames = 0;
  uint64_t decode_errors = 0;
  uint64_t auth_ok = 0;
  uint32_t out_seq = 1;
  std::vector<int16_t> pcm_buf;
  FILE* wav = nullptr;
  uint32_t wav_data_bytes = 0;
};

static void ensureRing(AirInputPipeline* p) {
  if (p->ring_ready) return;
  if (airinput::vmic::SharedRingCreate(&p->ring, airinput::vmic::kDefaultRingCapacityBytes, false) ||
      airinput::vmic::SharedRingCreate(&p->ring, airinput::vmic::kDefaultRingCapacityBytes, true)) {
    p->ring_ready = true;
  }
}

static void ensureDecoder(AirInputPipeline* p) {
  if (p->decoder) return;
  int err = OPUS_OK;
  p->decoder = opus_decoder_create(kSampleRate, 1, &err);
  if (err != OPUS_OK || !p->decoder) {
    p->decoder = nullptr;
  }
}

static void wavWriteHeader(FILE* f, uint32_t data_bytes) {
  uint8_t hdr[44]{};
  std::memcpy(hdr + 0, "RIFF", 4);
  writeU32LE(hdr + 4, 36 + data_bytes);
  std::memcpy(hdr + 8, "WAVE", 4);
  std::memcpy(hdr + 12, "fmt ", 4);
  writeU32LE(hdr + 16, 16);
  writeU16LE(hdr + 20, 1);  // PCM
  writeU16LE(hdr + 22, 1);  // mono
  writeU32LE(hdr + 24, static_cast<uint32_t>(kSampleRate));
  writeU32LE(hdr + 28, static_cast<uint32_t>(kSampleRate * 2));
  writeU16LE(hdr + 32, 2);
  writeU16LE(hdr + 34, 16);
  std::memcpy(hdr + 36, "data", 4);
  writeU32LE(hdr + 40, data_bytes);
  std::fseek(f, 0, SEEK_SET);
  std::fwrite(hdr, 1, 44, f);
  std::fflush(f);
}

static void wavOpen(AirInputPipeline* p) {
  if (p->wav) return;
  p->wav = std::fopen(kCaptureWavPath, "wb+");
  if (!p->wav) return;
  p->wav_data_bytes = 0;
  wavWriteHeader(p->wav, 0);
  std::fseek(p->wav, 44, SEEK_SET);
}

static void wavClose(AirInputPipeline* p) {
  if (!p->wav) return;
  wavWriteHeader(p->wav, p->wav_data_bytes);
  std::fclose(p->wav);
  p->wav = nullptr;
}

static void wavAppend(AirInputPipeline* p, const int16_t* pcm, int frames) {
  if (!p->wav || !pcm || frames <= 0) return;
  const size_t bytes = static_cast<size_t>(frames) * sizeof(int16_t);
  if (std::fwrite(pcm, 1, bytes, p->wav) == bytes) {
    p->wav_data_bytes += static_cast<uint32_t>(bytes);
    // Keep header roughly fresh for players that open mid-capture.
    if ((p->audio_frames % 50) == 0) {
      const long pos = std::ftell(p->wav);
      wavWriteHeader(p->wav, p->wav_data_bytes);
      if (pos >= 44) std::fseek(p->wav, pos, SEEK_SET);
      else std::fseek(p->wav, 44 + p->wav_data_bytes, SEEK_SET);
    }
  }
}

static void sendFrame(AirInputPipeline* p, airinput::PacketType type,
                      const std::vector<uint8_t>& payload, AirInputSendFn send_fn, void* send_ctx) {
  if (!send_fn) return;
  auto bytes = airinput::encodeFrame(type, p->out_seq++, nowMs(), payload);
  send_fn(bytes.data(), bytes.size(), send_ctx);
}

static void handleFrame(AirInputPipeline* p, const airinput::Frame& frame, AirInputSendFn send_fn,
                        void* send_ctx) {
  p->packets += 1;
  switch (frame.type) {
    case airinput::PacketType::Ping:
      sendFrame(p, airinput::PacketType::Pong, {}, send_fn, send_ctx);
      break;
    case airinput::PacketType::AuthResponse: {
      if (p->pending_challenge.empty() || frame.payload.size() != 32) {
        sendFrame(p, airinput::PacketType::AuthFailed, {}, send_fn, send_ctx);
        p->authenticated = false;
        break;
      }
      uint8_t expect[32];
      hmacSha256(p->pairing_key.data(), p->pairing_key.size(), p->pending_challenge.data(),
                 p->pending_challenge.size(), expect);
      if (constTimeEq(expect, frame.payload.data(), 32)) {
        p->authenticated = true;
        p->auth_ok += 1;
        p->pending_challenge.clear();
        ensureRing(p);
        ensureDecoder(p);
        sendFrame(p, airinput::PacketType::AuthOk, {}, send_fn, send_ctx);
      } else {
        p->authenticated = false;
        sendFrame(p, airinput::PacketType::AuthFailed, {}, send_fn, send_ctx);
      }
      break;
    }
    case airinput::PacketType::Start:
      if (!p->authenticated) break;
      ensureRing(p);
      ensureDecoder(p);
      wavClose(p);
      wavOpen(p);
      break;
    case airinput::PacketType::Stop:
      wavClose(p);
      break;
    case airinput::PacketType::Audio: {
      if (!p->authenticated) {
        p->decode_errors += 1;
        break;
      }
      ensureRing(p);
      ensureDecoder(p);
      if (!p->decoder) {
        p->decode_errors += 1;
        break;
      }
      // Ring may fail if plugin never created the file; still decode to WAV.
      if (!p->ring_ready) {
        ensureRing(p);
      }
      p->pcm_buf.resize(static_cast<size_t>(kFrameSamples) * 2);
      const int got = opus_decode(p->decoder, frame.payload.data(),
                                  static_cast<opus_int32>(frame.payload.size()), p->pcm_buf.data(),
                                  kFrameSamples * 2, 0);
      if (got < 0) {
        p->decode_errors += 1;
        break;
      }
      if (p->ring_ready) {
        airinput::vmic::SharedRingWriteS16(&p->ring, p->pcm_buf.data(), static_cast<size_t>(got));
      }
      if (!p->wav) wavOpen(p);
      wavAppend(p, p->pcm_buf.data(), got);
      p->audio_frames += 1;
      break;
    }
    default:
      break;
  }
}

extern "C" AirInputPipeline* airinput_pipeline_create(const uint8_t* pairing_key,
                                                      size_t pairing_key_len) {
  auto* p = new AirInputPipeline();
  if (pairing_key && pairing_key_len > 0) {
    p->pairing_key.assign(pairing_key, pairing_key + pairing_key_len);
  } else {
    p->pairing_key.assign(reinterpret_cast<const uint8_t*>(kDefaultPairingKey),
                          reinterpret_cast<const uint8_t*>(kDefaultPairingKey) +
                              std::strlen(kDefaultPairingKey));
  }
  return p;
}

extern "C" void airinput_pipeline_destroy(AirInputPipeline* p) {
  if (!p) return;
  std::lock_guard<std::mutex> lock(p->mu);
  wavClose(p);
  if (p->decoder) {
    opus_decoder_destroy(p->decoder);
    p->decoder = nullptr;
  }
  if (p->ring_ready) {
    p->ring.owner = false;
    airinput::vmic::SharedRingClose(&p->ring);
    p->ring_ready = false;
  }
  delete p;
}

extern "C" void airinput_pipeline_on_connected(AirInputPipeline* p, AirInputSendFn send_fn,
                                               void* send_ctx) {
  if (!p) return;
  std::lock_guard<std::mutex> lock(p->mu);
  p->parser.reset();
  p->authenticated = false;
  p->pending_challenge.resize(kChallengeLen);
  std::random_device rd;
  for (size_t i = 0; i < kChallengeLen; ++i) {
    p->pending_challenge[i] = static_cast<uint8_t>(rd());
  }
  if (p->decoder) {
    opus_decoder_ctl(p->decoder, OPUS_RESET_STATE);
  }
  sendFrame(p, airinput::PacketType::Challenge, p->pending_challenge, send_fn, send_ctx);
}

extern "C" void airinput_pipeline_on_disconnected(AirInputPipeline* p) {
  if (!p) return;
  std::lock_guard<std::mutex> lock(p->mu);
  wavClose(p);
  p->parser.reset();
  p->authenticated = false;
  p->pending_challenge.clear();
}

extern "C" void airinput_pipeline_push(AirInputPipeline* p, const uint8_t* data, size_t len,
                                       AirInputSendFn send_fn, void* send_ctx) {
  if (!p || !data || len == 0) return;
  std::lock_guard<std::mutex> lock(p->mu);
  try {
    auto frames = p->parser.push(data, len);
    for (const auto& frame : frames) {
      handleFrame(p, frame, send_fn, send_ctx);
    }
  } catch (...) {
    p->decode_errors += 1;
    p->parser.reset();
  }
}

extern "C" void airinput_pipeline_get_stats(const AirInputPipeline* p, AirInputPipelineStats* out) {
  if (!out) return;
  if (!p) {
    std::memset(out, 0, sizeof(*out));
    return;
  }
  std::lock_guard<std::mutex> lock(const_cast<AirInputPipeline*>(p)->mu);
  out->packets = p->packets;
  out->audio_frames = p->audio_frames;
  out->decode_errors = p->decode_errors;
  out->auth_ok = p->auth_ok;
  out->authenticated = p->authenticated ? 1 : 0;
  out->ring_ready = p->ring_ready ? 1 : 0;
}
