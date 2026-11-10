#include "edgetx_wasm_host.h"

#include <jni.h>
#include <vector>

namespace {

JavaVM* g_jvm = nullptr;
jmethodID g_audio_deliver_mid = nullptr;
jobject g_audio_sink_global = nullptr;

void clear_audio_jni() {
    if (!g_jvm || !g_audio_sink_global) {
        return;
    }
    JNIEnv* env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || !env) {
        return;
    }
    env->DeleteGlobalRef(g_audio_sink_global);
    g_audio_sink_global = nullptr;
    g_audio_deliver_mid = nullptr;
}

void deliver_audio_jni(const uint8_t* data, uint32_t len) {
    if (!g_jvm || !g_audio_sink_global || !g_audio_deliver_mid || !data || len == 0) {
        return;
    }
    JNIEnv* env = nullptr;
    bool detach = false;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) {
            return;
        }
        detach = true;
    }
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(len));
    if (!arr) {
        if (detach) {
            g_jvm->DetachCurrentThread();
        }
        return;
    }
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte*>(data));
    env->CallVoidMethod(g_audio_sink_global, g_audio_deliver_mid, arr);
    env->DeleteLocalRef(arr);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (detach) {
        g_jvm->DetachCurrentThread();
    }
}

EdgeTxWasmHost* ptr(jlong handle) {
    return reinterpret_cast<EdgeTxWasmHost*>(handle);
}

jbyteArray to_jbytes(JNIEnv* env, const std::vector<uint8_t>& data) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(data.size()));
    if (!out) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(data.size()),
                            reinterpret_cast<const jbyte*>(data.data()));
    return out;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeCreate(
    JNIEnv* env, jobject, jstring wasm_path, jstring sd_path, jstring settings_path) {
    const char* wasm = env->GetStringUTFChars(wasm_path, nullptr);
    const char* sd = env->GetStringUTFChars(sd_path, nullptr);
    const char* settings = env->GetStringUTFChars(settings_path, nullptr);
    std::string err;
    EdgeTxWasmHost* host = edgetx_wasm_create(wasm, sd, settings, &err);
    env->ReleaseStringUTFChars(wasm_path, wasm);
    env->ReleaseStringUTFChars(sd_path, sd);
    env->ReleaseStringUTFChars(settings_path, settings);
    if (!host) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), err.c_str());
        return 0;
    }
    return reinterpret_cast<jlong>(host);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    edgetx_wasm_set_audio_chunk_handler(ptr(handle), EdgetxAudioChunkFn{});
    edgetx_wasm_destroy(ptr(handle));
    clear_audio_jni();
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeBindAudioSink(
    JNIEnv* env, jobject, jlong handle, jobject sink) {
    clear_audio_jni();
    if (!sink) {
        edgetx_wasm_set_audio_chunk_handler(ptr(handle), EdgetxAudioChunkFn{});
        return;
    }
    jclass cls = env->GetObjectClass(sink);
    g_audio_deliver_mid = env->GetMethodID(cls, "deliverNativeChunk", "([B)V");
    if (!g_audio_deliver_mid) {
        env->ExceptionClear();
        edgetx_wasm_set_audio_chunk_handler(ptr(handle), EdgetxAudioChunkFn{});
        return;
    }
    g_audio_sink_global = env->NewGlobalRef(sink);
    EdgeTxWasmHost* host = ptr(handle);
    edgetx_wasm_set_audio_chunk_handler(
        host, [](const uint8_t* data, uint32_t len) { deliver_audio_jni(data, len); });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeStart(
    JNIEnv* env, jobject, jlong handle, jint utcOffsetSec) {
    std::string err;
    bool ok = edgetx_wasm_start(ptr(handle), utcOffsetSec, &err);
    if (!ok) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), err.c_str());
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeStop(JNIEnv*, jobject, jlong handle) {
    edgetx_wasm_stop(ptr(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativePump(JNIEnv*, jobject, jlong handle) {
    edgetx_wasm_pump(ptr(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeBootComplete(JNIEnv*, jobject, jlong handle) {
    return edgetx_wasm_boot_complete(ptr(handle)) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeSetAnalog(
    JNIEnv*, jobject, jlong handle, jint index, jint value) {
    edgetx_wasm_set_analog(ptr(handle), static_cast<uint8_t>(index), static_cast<int16_t>(value));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeSetSwitch(
    JNIEnv*, jobject, jlong handle, jint index, jint state) {
    edgetx_wasm_set_switch(ptr(handle), static_cast<uint8_t>(index), static_cast<int8_t>(state));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeSetKey(
    JNIEnv*, jobject, jlong handle, jint key, jboolean down) {
    edgetx_wasm_set_key(ptr(handle), static_cast<uint8_t>(key), down == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeTouchDown(
    JNIEnv*, jobject, jlong handle, jint x, jint y) {
    edgetx_wasm_touch_down(ptr(handle), static_cast<int16_t>(x), static_cast<int16_t>(y));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeTouchUp(JNIEnv*, jobject, jlong handle) {
    edgetx_wasm_touch_up(ptr(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeLcdFlushed(JNIEnv*, jobject, jlong handle) {
    edgetx_wasm_lcd_flushed(ptr(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeLcdWidth(JNIEnv*, jobject, jlong handle) {
    return static_cast<jint>(edgetx_wasm_lcd_width(ptr(handle)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeLcdHeight(JNIEnv*, jobject, jlong handle) {
    return static_cast<jint>(edgetx_wasm_lcd_height(ptr(handle)));
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeCopyLcdRgb565(JNIEnv* env, jobject, jlong handle) {
    std::vector<uint8_t> buf;
    if (!edgetx_wasm_copy_lcd_rgb565(ptr(handle), &buf)) {
        return env->NewByteArray(0);
    }
    return to_jbytes(env, buf);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_flyingcomputer_gndfull_edgetx_WasmEdgeTxAdapter_nativeCopyChannelOutputs(JNIEnv* env, jobject, jlong handle) {
    int16_t temp[32];
    const uint8_t n = edgetx_wasm_copy_outputs(ptr(handle), temp, 32);
    jintArray out = env->NewIntArray(n);
    if (!out) {
        return nullptr;
    }
    jint mapped[32];
    for (uint8_t i = 0; i < n; ++i) {
        mapped[i] = temp[i];
    }
    env->SetIntArrayRegion(out, 0, n, mapped);
    return out;
}
