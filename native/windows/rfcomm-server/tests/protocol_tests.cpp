#include "audio_protocol.hpp"

#include <cassert>
#include <iostream>
#include <vector>

int main() {
  using airinput::FrameParser;
  using airinput::PacketType;
  using airinput::encodeFrame;
  using airinput::toHex;

  const auto ping = encodeFrame(PacketType::Ping, 0, 0);
  assert(toHex(ping) == "414901040000000000000000000000000000");

  const auto audio = encodeFrame(PacketType::Audio, 42, 1710000000020ULL, {0xde, 0xad, 0xbe, 0xef});
  assert(toHex(audio) == "414901020000002a0000018e23f14c140004deadbeef");

  const auto challenge = encodeFrame(PacketType::Challenge, 1, 100, {1, 2, 3, 4});
  assert(challenge[3] == static_cast<uint8_t>(PacketType::Challenge));

  FrameParser parser;
  std::vector<uint8_t> stream;
  stream.insert(stream.end(), ping.begin(), ping.end());
  // Split audio across two pushes.
  const auto frames1 = parser.push(audio.data(), 10);
  assert(frames1.empty());
  const auto frames2 = parser.push(audio.data() + 10, audio.size() - 10);
  assert(frames2.size() == 1);
  assert(frames2[0].type == PacketType::Audio);
  assert(frames2[0].payload.size() == 4);

  // Resync past junk then challenge.
  std::vector<uint8_t> junk = {0x00, 0xff};
  junk.insert(junk.end(), challenge.begin(), challenge.end());
  const auto frames3 = parser.push(junk.data(), junk.size());
  assert(frames3.size() == 1);
  assert(frames3[0].type == PacketType::Challenge);

  std::cout << "protocol_tests OK\n";
  return 0;
}
