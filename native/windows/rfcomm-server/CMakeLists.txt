cmake_minimum_required(VERSION 3.22)
project(airinput_rfcomm_server LANGUAGES CXX)

add_executable(airinput_rfcomm_server
  src/main.cpp
  src/rfcomm_server.cpp
  src/ipc_pipe.cpp
  ../../common/audio_protocol.cpp
)

target_include_directories(airinput_rfcomm_server PRIVATE
  src
  ../../common
)

if(WIN32)
  target_link_libraries(airinput_rfcomm_server PRIVATE ws2_32 bthprops)
  target_compile_definitions(airinput_rfcomm_server PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX)
endif()

enable_testing()
add_executable(protocol_tests
  tests/protocol_tests.cpp
  ../../common/audio_protocol.cpp
)
target_include_directories(protocol_tests PRIVATE ../../common)
add_test(NAME protocol_tests COMMAND protocol_tests)
