cmake_minimum_required(VERSION 3.22.1)
project(airinput_opus)

set(OPUS_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../../native/opus")
if(NOT EXISTS "${OPUS_ROOT}/include/opus/opus.h")
  message(FATAL_ERROR "libopus sources missing at ${OPUS_ROOT}. Run scripts/fetch-opus.sh")
endif()

file(GLOB OPUS_SRC_SOURCES ${OPUS_ROOT}/src/*.c)
file(GLOB CELT_SOURCES ${OPUS_ROOT}/celt/*.c)
file(GLOB SILK_SOURCES
  ${OPUS_ROOT}/silk/*.c
  ${OPUS_ROOT}/silk/float/*.c
)

list(FILTER OPUS_SRC_SOURCES EXCLUDE REGEX ".*/opus_demo\\.c$")
list(FILTER OPUS_SRC_SOURCES EXCLUDE REGEX ".*/opus_compare\\.c$")
list(FILTER CELT_SOURCES EXCLUDE REGEX ".*opus_custom_demo\\.c$")
list(FILTER CELT_SOURCES EXCLUDE REGEX ".*dump_modes.*")
list(FILTER CELT_SOURCES EXCLUDE REGEX ".*/x86/.*")
list(FILTER CELT_SOURCES EXCLUDE REGEX ".*/arm/.*")
list(FILTER SILK_SOURCES EXCLUDE REGEX ".*/x86/.*")
list(FILTER SILK_SOURCES EXCLUDE REGEX ".*/arm/.*")
list(FILTER SILK_SOURCES EXCLUDE REGEX ".*/mips/.*")
list(FILTER SILK_SOURCES EXCLUDE REGEX ".*/fixed/.*")
list(FILTER SILK_SOURCES EXCLUDE REGEX ".*/tests/.*")
list(FILTER CELT_SOURCES EXCLUDE REGEX ".*/tests/.*")

add_library(opus_static STATIC ${OPUS_SRC_SOURCES} ${CELT_SOURCES} ${SILK_SOURCES})
target_include_directories(opus_static PUBLIC
  ${OPUS_ROOT}/include
  ${OPUS_ROOT}/celt
  ${OPUS_ROOT}/silk
  ${OPUS_ROOT}/silk/float
  ${OPUS_ROOT}/src
)
target_compile_definitions(opus_static PRIVATE
  OPUS_BUILD
  USE_ALLOCA
  HAVE_LRINT
  HAVE_LRINTF
  VAR_ARRAYS
  PACKAGE_VERSION="1.5.2"
)
target_compile_options(opus_static PRIVATE -O2 -fPIC -Wno-unused-parameter)

add_library(airinput_opus SHARED opus_encoder_jni.cpp)
target_include_directories(airinput_opus PRIVATE ${OPUS_ROOT}/include)
target_link_libraries(airinput_opus opus_static log)
