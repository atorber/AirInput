#include "audio_protocol.hpp"

#include <stdexcept>

namespace airinput {
namespace {

void putU16BE(std::vector<uint8_t>& out, uint16_t v) {
  out.push_back(static_cast<uint8_t>((v >> 8) & 0xff));
  out.push_back(static_cast<uint8_t>(v & 0xff));
}

void putU32BE(std::vector<uint8_t>& out, uint32_t v) {
  out.push_back(static_cast<uint8_t>((v >> 24) & 0xff));
  out.push_back(static_cast<uint8_t>((v >> 16) & 0xff));
  out.push_back(static_cast<uint8_t>((v >> 8) & 0xff));
  out.push_back(static_cast<uint8_t>(v & 0xff));
}

void putU64BE(std::vector<uint8_t>& out, uint64_t v) {
  for (int i = 7; i >= 0; --i) {
    out.push_back(static_cast<uint8_t>((v >> (8 * i)) & 0xff));
  }
}

uint16_t getU16BE(const uint8_t* p) {
  return static_cast<uint16_t>((p[0] << 8) | p[1]);
}

uint32_t getU32BE(const uint8_t* p) {
  return (static_cast<uint32_t>(p[0]) << 24) | (static_cast<uint32_t>(p[1]) << 16) |
         (static_cast<uint32_t>(p[2]) << 8) | static_cast<uint32_t>(p[3]);
}

uint64_t getU64BE(const uint8_t* p) {
  uint64_t v = 0;
  for (int i = 0; i < 8; ++i) {
    v = (v << 8) | p[i];
  }
  return v;
}

bool tryParseHeader(const uint8_t* p, Frame* out, uint16_t* payload_len) {
  if (getU16BE(p) != kMagic) return false;
  if (p[2] != kVersion) return false;
  const uint8_t type = p[3];
  if (type < static_cast<uint8_t>(PacketType::Start) ||
      type > static_cast<uint8_t>(PacketType::AuthFailed)) {
    return false;
  }
  const uint16_t plen = getU16BE(p + 16);
  if (plen > kMaxPayload) return false;
  out->type = static_cast<PacketType>(type);
  out->sequence = getU32BE(p + 4);
  out->timestamp = getU64BE(p + 8);
  *payload_len = plen;
  return true;
}

}  // namespace

std::vector<uint8_t> encodeFrame(PacketType type, uint32_t sequence, uint64_t timestamp,
                                 const std::vector<uint8_t>& payload) {
  if (payload.size() > kMaxPayload) {
    throw std::runtime_error("payload too large");
  }
  std::vector<uint8_t> out;
  out.reserve(kHeaderSize + payload.size());
  putU16BE(out, kMagic);
  out.push_back(kVersion);
  out.push_back(static_cast<uint8_t>(type));
  putU32BE(out, sequence);
  putU64BE(out, timestamp);
  putU16BE(out, static_cast<uint16_t>(payload.size()));
  out.insert(out.end(), payload.begin(), payload.end());
  return out;
}

std::string toHex(const std::vector<uint8_t>& bytes) {
  static const char* kHex = "0123456789abcdef";
  std::string s;
  s.resize(bytes.size() * 2);
  for (size_t i = 0; i < bytes.size(); ++i) {
    s[i * 2] = kHex[(bytes[i] >> 4) & 0xf];
    s[i * 2 + 1] = kHex[bytes[i] & 0xf];
  }
  return s;
}

FrameParser::FrameParser(size_t max_buffer_bytes) : max_buffer_bytes_(max_buffer_bytes) {}

void FrameParser::reset() { buffer_.clear(); }

std::vector<Frame> FrameParser::push(const uint8_t* data, size_t len) {
  if (!data || len == 0) return {};
  buffer_.insert(buffer_.end(), data, data + len);
  if (buffer_.size() > max_buffer_bytes_) {
    buffer_.clear();
    throw std::runtime_error("parser buffer overflow");
  }

  std::vector<Frame> frames;
  size_t offset = 0;
  while (buffer_.size() - offset >= kHeaderSize) {
    Frame header{};
    uint16_t payload_len = 0;
    if (!tryParseHeader(buffer_.data() + offset, &header, &payload_len)) {
      offset += 1;
      continue;
    }
    const size_t total = kHeaderSize + payload_len;
    if (buffer_.size() - offset < total) break;
    header.payload.assign(buffer_.begin() + static_cast<std::ptrdiff_t>(offset + kHeaderSize),
                          buffer_.begin() + static_cast<std::ptrdiff_t>(offset + total));
    frames.push_back(std::move(header));
    offset += total;
  }
  if (offset > 0) {
    buffer_.erase(buffer_.begin(), buffer_.begin() + static_cast<std::ptrdiff_t>(offset));
  }
  return frames;
}

}  // namespace airinput
