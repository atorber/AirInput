cmake_minimum_required(VERSION 3.22)
project(airinput_common_tests LANGUAGES CXX)
add_executable(protocol_tests
  ../windows/rfcomm-server/tests/protocol_tests.cpp
  audio_protocol.cpp
)
target_include_directories(protocol_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
enable_testing()
add_test(NAME protocol_tests COMMAND protocol_tests)
