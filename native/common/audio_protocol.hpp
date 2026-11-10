#pragma once
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace airinput {

constexpr uint16_t kMagic = 0x4149;
constexpr uint8_t kVersion = 1;
constexpr size_t kHeaderSize = 18;
constexpr size_t kMaxPayload = 4096;
constexpr size_t kMaxParserBuffer = 256 * 1024;

enum class PacketType : uint8_t {
  Start = 1,
  Audio = 2,
  Stop = 3,
  Ping = 4,
  Pong = 5,
  Challenge = 6,
  AuthResponse = 7,
  AuthOk = 8,
  AuthFailed = 9,
};

struct Frame {
  PacketType type{};
  uint32_t sequence{};
  uint64_t timestamp{};
  std::vector<uint8_t> payload;
};

std::vector<uint8_t> encodeFrame(PacketType type, uint32_t sequence, uint64_t timestamp,
                                 const std::vector<uint8_t>& payload = {});
std::string toHex(const std::vector<uint8_t>& bytes);

/** Streaming parser for RFCOMM byte streams (handles partial frames + magic resync). */
class FrameParser {
 public:
  explicit FrameParser(size_t max_buffer_bytes = kMaxParserBuffer);

  void reset();
  /** Push bytes; returns newly completed frames. Throws std::runtime_error on overflow. */
  std::vector<Frame> push(const uint8_t* data, size_t len);

 private:
  std::vector<uint8_t> buffer_;
  size_t max_buffer_bytes_;
};

}  // namespace airinput
