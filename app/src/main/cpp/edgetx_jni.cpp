#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstring>
#include <mutex>
#include <vector>

namespace {
constexpr int kLcdW = 480;
constexpr int kLcdH = 272;
constexpr int kChannels = 16;

struct Instance {
    std::mutex lock;
    bool running = false;
    int sticks[4] = {};
    int switches[8] = {};
    int outputs[kChannels] = {};
};

Instance* get(jlong handle) {
    return reinterpret_cast<Instance*>(handle);
}

void paint_frame(std::vector<uint16_t>& rgb565, const Instance& inst) {
    rgb565.assign(static_cast<size_t>(kLcdW * kLcdH), 0x0000);
    const uint16_t border = 0x4A69;
    const uint16_t fill = 0x2D16;
    for (int y = 0; y < kLcdH; ++y) {
        for (int x = 0; x < kLcdW; ++x) {
            const bool edge = x < 2 || y < 2 || x >= kLcdW - 2 || y >= kLcdH - 2;
            rgb565[static_cast<size_t>(y * kLcdW + x)] = edge ? border : fill;
        }
    }
    const int cx = kLcdW / 2 + (inst.sticks[0] * kLcdW) / 2048;
    const int cy = kLcdH / 2 - (inst.sticks[1] * kLcdH) / 2048;
    const int r = 10;
    for (int y = std::max(0, cy - r); y < std::min(kLcdH, cy + r); ++y) {
        for (int x = std::max(0, cx - r); x < std::min(kLcdW, cx + r); ++x) {
            const int dx = x - cx;
            const int dy = y - cy;
            if (dx * dx + dy * dy <= r * r) {
                rgb565[static_cast<size_t>(y * kLcdW + x)] = 0xF800;
            }
        }
    }
}

void recompute_outputs(Instance& inst) {
    for (int i = 0; i < 4; ++i) {
        inst.outputs[i] = 512 + (inst.sticks[i] * 512) / 1024;
    }
    for (int i = 0; i < 8; ++i) {
        inst.outputs[4 + i] = inst.switches[i] <= 0 ? 988 : 2012;
    }
    for (int i = 12; i < kChannels; ++i) {
        inst.outputs[i] = 1500;
    }
}

void throw_illegal(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(type, message);
    env->DeleteLocalRef(type);
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new Instance());
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete get(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeStart(JNIEnv* env, jobject, jlong handle) {
    Instance* inst = get(handle);
    if (inst == nullptr) {
        throw_illegal(env, "edgetx handle missing");
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> guard(inst->lock);
    inst->running = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeStop(JNIEnv*, jobject, jlong handle) {
    Instance* inst = get(handle);
    if (inst == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> guard(inst->lock);
    inst->running = false;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeSubmitInput(
    JNIEnv* env, jobject, jlong handle, jintArray sticks, jintArray switches) {
    Instance* inst = get(handle);
    if (inst == nullptr) {
        throw_illegal(env, "edgetx handle missing");
        return;
    }
    std::lock_guard<std::mutex> guard(inst->lock);
    jsize stick_len = env->GetArrayLength(sticks);
    jsize switch_len = env->GetArrayLength(switches);
    if (stick_len != 4 || switch_len != 8) {
        throw_illegal(env, "unexpected input vector size");
        return;
    }
    env->GetIntArrayRegion(sticks, 0, 4, inst->sticks);
    env->GetIntArrayRegion(switches, 0, 8, inst->switches);
    recompute_outputs(*inst);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeCopyLcdRgb565(
    JNIEnv* env, jobject, jlong handle) {
    Instance* inst = get(handle);
    if (inst == nullptr) {
        throw_illegal(env, "edgetx handle missing");
        return nullptr;
    }
    std::vector<uint16_t> frame;
    {
        std::lock_guard<std::mutex> guard(inst->lock);
        paint_frame(frame, *inst);
    }
    const jsize bytes = static_cast<jsize>(frame.size() * sizeof(uint16_t));
    jbyteArray out = env->NewByteArray(bytes);
    if (out == nullptr) {
        return nullptr;
    }
    env->SetByteArrayRegion(out, 0, bytes, reinterpret_cast<const jbyte*>(frame.data()));
    return out;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_flyingcomputer_gndfull_NativeEdgeTxAdapter_nativeCopyOutputs(
    JNIEnv* env, jobject, jlong handle) {
    Instance* inst = get(handle);
    if (inst == nullptr) {
        throw_illegal(env, "edgetx handle missing");
        return nullptr;
    }
    jintArray out = env->NewIntArray(kChannels);
    if (out == nullptr) {
        return nullptr;
    }
  {
    std::lock_guard<std::mutex> guard(inst->lock);
    env->SetIntArrayRegion(out, 0, kChannels, inst->outputs);
  }
    return out;
}
