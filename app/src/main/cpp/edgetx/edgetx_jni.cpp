#include "edgetx_wasm_host.h"

#include <jni.h>
#include <vector>

namespace {
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
    edgetx_wasm_destroy(ptr(handle));
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
