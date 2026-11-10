#include <jni.h>
#include <opus.h>
#include <stdlib.h>
#include <string.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_airinput_audio_OpusEncoder_nativeCreate(
    JNIEnv *,
    jobject,
    jint sampleRate,
    jint channels,
    jint bitrate
) {
    int err = OPUS_OK;
    OpusEncoder *enc = opus_encoder_create(sampleRate, channels, OPUS_APPLICATION_VOIP, &err);
    if (err != OPUS_OK || enc == nullptr) {
        return 0;
    }
    opus_encoder_ctl(enc, OPUS_SET_BITRATE(bitrate));
    opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(5));
    opus_encoder_ctl(enc, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
    return reinterpret_cast<jlong>(enc);
}

JNIEXPORT jbyteArray JNICALL
Java_app_airinput_audio_OpusEncoder_nativeEncode(
    JNIEnv *env,
    jobject,
    jlong handle,
    jshortArray pcm
) {
    auto *enc = reinterpret_cast<OpusEncoder *>(handle);
    if (enc == nullptr || pcm == nullptr) {
        return nullptr;
    }
    const jsize len = env->GetArrayLength(pcm);
    if (len <= 0) {
        return nullptr;
    }
    jshort *samples = env->GetShortArrayElements(pcm, nullptr);
    if (samples == nullptr) {
        return nullptr;
    }

    unsigned char packet[4000];
    const int bytes = opus_encode(enc, samples, len, packet, static_cast<opus_int32>(sizeof(packet)));
    env->ReleaseShortArrayElements(pcm, samples, JNI_ABORT);
    if (bytes < 0) {
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(bytes);
    if (out == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, bytes, reinterpret_cast<jbyte *>(packet));
    return out;
}

JNIEXPORT void JNICALL
Java_app_airinput_audio_OpusEncoder_nativeDestroy(
    JNIEnv *,
    jobject,
    jlong handle
) {
    auto *enc = reinterpret_cast<OpusEncoder *>(handle);
    if (enc != nullptr) {
        opus_encoder_destroy(enc);
    }
}

}  // extern "C"
