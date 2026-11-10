#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct AirInputPipeline AirInputPipeline;

typedef struct AirInputPipelineStats {
  uint64_t packets;
  uint64_t audio_frames;
  uint64_t decode_errors;
  uint64_t auth_ok;
  int authenticated; /* 0/1 */
  int ring_ready;    /* 0/1 */
} AirInputPipelineStats;

typedef void (*AirInputSendFn)(const uint8_t* data, size_t len, void* ctx);

/** Create pipeline. pairing_key may be NULL to use built-in default. */
AirInputPipeline* airinput_pipeline_create(const uint8_t* pairing_key, size_t pairing_key_len);

void airinput_pipeline_destroy(AirInputPipeline* p);

/** Reset parser/decoder state for a new RFCOMM session and emit Challenge via send_fn. */
void airinput_pipeline_on_connected(AirInputPipeline* p, AirInputSendFn send_fn, void* send_ctx);

void airinput_pipeline_on_disconnected(AirInputPipeline* p);

/** Feed RFCOMM bytes. May call send_fn for AuthOk/AuthFailed/Pong. */
void airinput_pipeline_push(AirInputPipeline* p, const uint8_t* data, size_t len,
                            AirInputSendFn send_fn, void* send_ctx);

void airinput_pipeline_get_stats(const AirInputPipeline* p, AirInputPipelineStats* out);

#ifdef __cplusplus
}
#endif
