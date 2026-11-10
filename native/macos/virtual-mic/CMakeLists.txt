cmake_minimum_required(VERSION 3.22)
project(AirInputMic LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET default)

add_library(airinput_shared_ring STATIC
  src/shared_ring.cpp
)
target_include_directories(airinput_shared_ring PUBLIC src)

# CFPlugIn is loaded as a Mach-O bundle (.so-like) inside AirInputMic.driver
add_library(AirInputMic MODULE
  src/driver.cpp
  src/device_state.cpp
  src/plugin_main.cpp
)
target_compile_options(AirInputMic PRIVATE -fvisibility=default)
target_link_options(AirInputMic PRIVATE "-Wl,-exported_symbol,_AirInput_Create")
target_link_libraries(AirInputMic PRIVATE airinput_shared_ring
  "-framework CoreAudio"
  "-framework CoreFoundation"
)
target_include_directories(AirInputMic PRIVATE src)
set_target_properties(AirInputMic PROPERTIES
  PREFIX ""
  SUFFIX ""
  OUTPUT_NAME "AirInputMic"
)

set(DRIVER_DIR "${CMAKE_BINARY_DIR}/AirInputMic.driver")
add_custom_command(TARGET AirInputMic POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E rm -rf "${DRIVER_DIR}"
  COMMAND ${CMAKE_COMMAND} -E make_directory "${DRIVER_DIR}/Contents/MacOS"
  COMMAND ${CMAKE_COMMAND} -E copy "$<TARGET_FILE:AirInputMic>" "${DRIVER_DIR}/Contents/MacOS/AirInputMic"
  COMMAND ${CMAKE_COMMAND} -E copy "${CMAKE_CURRENT_SOURCE_DIR}/Info.plist" "${DRIVER_DIR}/Contents/Info.plist"
  COMMENT "Packaging ${DRIVER_DIR}"
)

add_executable(airinput_pcm_inject tools/pcm_inject.cpp)
target_link_libraries(airinput_pcm_inject PRIVATE airinput_shared_ring)
target_include_directories(airinput_pcm_inject PRIVATE src)

enable_testing()
add_executable(shared_ring_tests tests/shared_ring_tests.cpp)
target_link_libraries(shared_ring_tests PRIVATE airinput_shared_ring)
target_include_directories(shared_ring_tests PRIVATE src)
add_test(NAME shared_ring_tests COMMAND shared_ring_tests)
