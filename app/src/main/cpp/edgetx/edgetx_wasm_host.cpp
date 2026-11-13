#include "edgetx_wasm_host.h"

#include <wasm_export.h>

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <thread>
#include <vector>

struct EdgeTxWasmHost {
    mutable std::mutex mutex;
    std::string last_error;
    std::vector<uint8_t> wasm_bytes;
    std::vector<uint8_t> lcd_host;
    std::atomic<bool> running{false};
    std::atomic<bool> lcd_notify{false};
    std::atomic<int16_t> analogs[32]{};
    wasm_module_t module = nullptr;
    wasm_module_inst_t module_inst = nullptr;
    wasm_exec_env_t exec_env = nullptr;
    std::thread worker;
    uint32_t wasm_lcd_buf = 0;
    uint32_t wasm_scratch = 0;
    uint32_t lcd_w = 480;
    uint32_t lcd_h = 272;
    uint32_t lcd_depth = 16;
    uint32_t lcd_bytes = 0;
    wasm_function_inst_t fn_init = nullptr;
    wasm_function_inst_t fn_start = nullptr;
    wasm_function_inst_t fn_stop = nullptr;
    wasm_function_inst_t fn_is_running = nullptr;
    wasm_function_inst_t fn_set_key = nullptr;
    wasm_function_inst_t fn_set_trim = nullptr;
    wasm_function_inst_t fn_set_switch = nullptr;
    wasm_function_inst_t fn_lcd_copy = nullptr;
    wasm_function_inst_t fn_lcd_flushed = nullptr;
    wasm_function_inst_t fn_touch_down = nullptr;
    wasm_function_inst_t fn_touch_up = nullptr;
    wasm_function_inst_t fn_fatfs_paths = nullptr;
    wasm_function_inst_t fn_lcd_w = nullptr;
    wasm_function_inst_t fn_lcd_h = nullptr;
    wasm_function_inst_t fn_lcd_d = nullptr;
    wasm_function_inst_t fn_copy_outputs = nullptr;
    wasm_function_inst_t fn_malloc = nullptr;
    wasm_function_inst_t fn_free = nullptr;
    wasm_function_inst_t fn_create_defaults = nullptr;
    wasm_function_inst_t fn_boot_complete = nullptr;
    wasm_function_inst_t fn_lcd_changed = nullptr;
    EdgetxAudioChunkFn audio_chunk;
    // Kept alive until module_inst is destroyed (wasm_runtime_set_wasi_args stores pointers only).
    std::vector<std::string> wasi_dir_storage;
    std::vector<const char*> wasi_dir_list;
    std::vector<const char*> wasi_map_list;
    std::string fatfs_sd_guest = "/";
    std::string fatfs_settings_guest = "/";
    wasm_function_inst_t fn_send_telemetry = nullptr;
};

static EdgeTxWasmHost* g_notify_host = nullptr;

static uint32_t host_simu_get_analog(wasm_exec_env_t, uint32_t idx) {
    if (idx >= 32) {
        return 2048;
    }
    int16_t raw = g_notify_host ? g_notify_host->analogs[idx].load() : 0;
    uint32_t adc = static_cast<uint32_t>((raw + 1024) * 2);
    if (adc > 4096) {
        adc = 4096;
    }
    return adc;
}

static void host_simu_lcd_notify(wasm_exec_env_t) {
    if (g_notify_host) {
        g_notify_host->lcd_notify.store(true);
    }
}

static void host_simu_queue_audio(wasm_exec_env_t exec_env, uint8_t* buf, uint32_t len) {
    (void)exec_env;
    if (!g_notify_host || !buf || len == 0) {
        return;
    }
    EdgetxAudioChunkFn cb;
    {
        std::lock_guard<std::mutex> lock(g_notify_host->mutex);
        cb = g_notify_host->audio_chunk;
    }
    if (!cb) {
        return;
    }
    // WAMR passes a native pointer into WASM linear memory (same as EdgeTX Companion).
    cb(buf, len);
}
static void host_simu_trace(wasm_exec_env_t, const char* text) {
    if (text && text[0] != '\0') {
        __android_log_print(ANDROID_LOG_DEBUG, "EdgeTxWasm", "%s", text);
    }
}
static void host_simu_aux_serial_start(wasm_exec_env_t, uint32_t, uint32_t, uint32_t) {}
static void host_simu_aux_serial_stop(wasm_exec_env_t, uint32_t) {}
static void host_simu_aux_serial_baud(wasm_exec_env_t, uint32_t, uint32_t) {}
static void host_simu_aux_serial_send(wasm_exec_env_t, uint32_t, uint8_t*, uint32_t) {}

static NativeSymbol k_natives[] = {
    {"simuGetAnalog", reinterpret_cast<void*>(host_simu_get_analog), "(i)i", nullptr},
    {"simuQueueAudio", reinterpret_cast<void*>(host_simu_queue_audio), "(*~)", nullptr},
    {"simuTrace", reinterpret_cast<void*>(host_simu_trace), "(*)", nullptr},
    {"simuLcdNotify", reinterpret_cast<void*>(host_simu_lcd_notify), "()", nullptr},
    {"simuAuxSerialStart", reinterpret_cast<void*>(host_simu_aux_serial_start), "(iii)", nullptr},
    {"simuAuxSerialStop", reinterpret_cast<void*>(host_simu_aux_serial_stop), "(i)", nullptr},
    {"simuAuxSerialSetBaudrate", reinterpret_cast<void*>(host_simu_aux_serial_baud), "(ii)", nullptr},
    {"simuAuxSerialSendBuffer", reinterpret_cast<void*>(host_simu_aux_serial_send), "(i*~)", nullptr},
};

static bool ensure_wamr() {
    static bool inited = false;
    if (!inited) {
        RuntimeInitArgs args = {};
        args.mem_alloc_type = Alloc_With_System_Allocator;
        args.max_thread_num = 16;
        if (!wasm_runtime_full_init(&args)) {
            return false;
        }
        wasm_runtime_set_log_level(WASM_LOG_LEVEL_WARNING);
        wasm_runtime_register_natives("env", k_natives, sizeof(k_natives) / sizeof(k_natives[0]));
        inited = true;
    }
    return true;
}

static bool lookup(EdgeTxWasmHost* host) {
    auto look = [&](const char* name) { return wasm_runtime_lookup_function(host->module_inst, name); };
    host->fn_init = look("simuInit");
    host->fn_start = look("simuStart");
    host->fn_stop = look("simuStop");
    host->fn_is_running = look("simuIsRunning");
    host->fn_set_key = look("simuSetKey");
    host->fn_set_trim = look("simuSetTrim");
    host->fn_set_switch = look("simuSetSwitch");
    host->fn_lcd_copy = look("simuLcdCopy");
    host->fn_lcd_flushed = look("simuLcdFlushed");
    host->fn_touch_down = look("simuTouchDown");
    host->fn_touch_up = look("simuTouchUp");
    host->fn_fatfs_paths = look("simuFatfsSetPaths");
    host->fn_lcd_w = look("simuLcdGetWidth");
    host->fn_lcd_h = look("simuLcdGetHeight");
    host->fn_lcd_d = look("simuLcdGetDepth");
    host->fn_copy_outputs = look("simuCopyChannelOutputs");
    host->fn_malloc = look("malloc");
    host->fn_free = look("free");
    host->fn_create_defaults = look("simuCreateDefaults");
    host->fn_boot_complete = look("simuIsBootComplete");
    host->fn_lcd_changed = look("simuLcdChanged");
    host->fn_send_telemetry = look("simuSendTelemetry");
    return host->fn_init && host->fn_start && host->fn_stop && host->fn_lcd_copy && host->fn_malloc && host->fn_free;
}

static bool wasm_call1(EdgeTxWasmHost* host, wasm_function_inst_t fn, uint32_t* ret) {
    uint32_t argv[1] = {0};
    if (!wasm_runtime_call_wasm(host->exec_env, fn, 0, argv)) {
        const char* ex = wasm_runtime_get_exception(host->module_inst);
        if (ex) {
            host->last_error = ex;
        }
        return false;
    }
    if (ret) {
        *ret = argv[0];
    }
    return true;
}

static uint32_t wasm_alloc(EdgeTxWasmHost* host, uint32_t size) {
    uint32_t argv[1] = {size};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_malloc, 1, argv)) {
        return 0;
    }
    return argv[0];
}

static void wasm_free(EdgeTxWasmHost* host, uint32_t ptr) {
    if (!ptr) {
        return;
    }
    uint32_t argv[1] = {ptr};
    wasm_runtime_call_wasm(host->exec_env, host->fn_free, 1, argv);
}

static bool wasm_copy_cstr(EdgeTxWasmHost* host, const char* text, uint32_t* out_ptr) {
    if (!text) {
        return false;
    }
    const uint32_t len = static_cast<uint32_t>(std::strlen(text) + 1);
    const uint32_t ptr = wasm_alloc(host, len);
    if (!ptr) {
        return false;
    }
    void* native = wasm_runtime_addr_app_to_native(host->module_inst, ptr);
    if (!native) {
        wasm_free(host, ptr);
        return false;
    }
    std::memcpy(native, text, len);
    *out_ptr = ptr;
    return true;
}

static bool configure_wasi_preopens(EdgeTxWasmHost* host, const char* sd_path,
                                    const char* settings_path) {
    if (!host || !host->module || !sd_path || sd_path[0] == '\0') {
        return false;
    }
    host->wasi_dir_storage.clear();
    host->wasi_dir_list.clear();
    host->wasi_map_list.clear();
    host->fatfs_sd_guest = "/";
    host->fatfs_settings_guest = "/";

    const std::string sd_host = sd_path;
    host->wasi_dir_storage.push_back(sd_host);
    host->wasi_dir_storage.push_back("/::" + sd_host);
    host->wasi_dir_list.push_back(host->wasi_dir_storage[0].c_str());
    host->wasi_map_list.push_back(host->wasi_dir_storage[1].c_str());

    const bool separate_settings =
        settings_path && settings_path[0] != '\0' && std::string(settings_path) != sd_host;
    if (separate_settings) {
        const std::string settings_host = settings_path;
        host->fatfs_settings_guest = "/settings";
        host->wasi_dir_storage.push_back(settings_host);
        host->wasi_dir_storage.push_back("/settings::" + settings_host);
        host->wasi_dir_list.push_back(host->wasi_dir_storage[2].c_str());
        host->wasi_map_list.push_back(host->wasi_dir_storage[3].c_str());
    }

    wasm_runtime_set_wasi_args(
            host->module, host->wasi_dir_list.data(), static_cast<uint32_t>(host->wasi_dir_list.size()),
            host->wasi_map_list.data(), static_cast<uint32_t>(host->wasi_map_list.size()), nullptr, 0,
            nullptr, 0);
    __android_log_print(ANDROID_LOG_INFO, "EdgeTxWasm",
                        "WASI preopen sd guest=%s settings guest=%s host=%s",
                        host->fatfs_sd_guest.c_str(), host->fatfs_settings_guest.c_str(),
                        sd_host.c_str());
    return true;
}

static void call_fatfs_paths(EdgeTxWasmHost* host) {
    if (!host->fn_fatfs_paths || !host->exec_env) {
        return;
    }
    uint32_t sd_ptr = 0;
    uint32_t set_ptr = 0;
    if (!wasm_copy_cstr(host, host->fatfs_sd_guest.c_str(), &sd_ptr) ||
        !wasm_copy_cstr(host, host->fatfs_settings_guest.c_str(), &set_ptr)) {
        __android_log_print(ANDROID_LOG_WARN, "EdgeTxWasm", "simuFatfsSetPaths alloc failed");
        return;
    }
    uint32_t argv[2] = {sd_ptr, set_ptr};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_fatfs_paths, 2, argv)) {
        const char* ex = wasm_runtime_get_exception(host->module_inst);
        __android_log_print(ANDROID_LOG_WARN, "EdgeTxWasm", "simuFatfsSetPaths failed: %s",
                            ex ? ex : "unknown");
    }
    wasm_free(host, sd_ptr);
    wasm_free(host, set_ptr);
}

static bool lcd_changed_locked(EdgeTxWasmHost* host) {
    if (!host->fn_lcd_changed || !host->exec_env) {
        return false;
    }
    uint32_t argv[1] = {0};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_lcd_changed, 0, argv)) {
        return false;
    }
    return argv[0] != 0;
}

static void refresh_lcd_locked(EdgeTxWasmHost* host) {
    if (!host->fn_lcd_copy || !host->wasm_lcd_buf || host->lcd_host.empty()) {
        return;
    }
    uint32_t argv[2] = {host->wasm_lcd_buf, host->lcd_bytes};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_lcd_copy, 2, argv)) {
        return;
    }
    void* native = wasm_runtime_addr_app_to_native(host->module_inst, host->wasm_lcd_buf);
    if (!native) {
        return;
    }
    const uint32_t n = argv[0] < host->lcd_bytes ? argv[0] : host->lcd_bytes;
    std::memcpy(host->lcd_host.data(), native, n);
}

EdgeTxWasmHost* edgetx_wasm_create(const char* wasm_path, const char* sd_path,
                                     const char* settings_path, std::string* err) {
    if (!ensure_wamr()) {
        if (err) {
            *err = "WAMR init failed";
        }
        return nullptr;
    }
    auto* host = new EdgeTxWasmHost();
    g_notify_host = host;
    FILE* f = std::fopen(wasm_path, "rb");
    if (!f) {
        if (err) {
            *err = "cannot open wasm";
        }
        delete host;
        return nullptr;
    }
    std::fseek(f, 0, SEEK_END);
    long sz = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);
    host->wasm_bytes.resize(static_cast<size_t>(sz));
    if (std::fread(host->wasm_bytes.data(), 1, host->wasm_bytes.size(), f) != host->wasm_bytes.size()) {
        std::fclose(f);
        delete host;
        if (err) {
            *err = "wasm read failed";
        }
        return nullptr;
    }
    std::fclose(f);

    char error_buf[128];
    host->module = wasm_runtime_load(host->wasm_bytes.data(), host->wasm_bytes.size(), error_buf,
                                      sizeof(error_buf));
    if (!host->module) {
        if (err) {
            *err = error_buf;
        }
        delete host;
        return nullptr;
    }
    if (!configure_wasi_preopens(host, sd_path, settings_path)) {
        wasm_runtime_unload(host->module);
        delete host;
        if (err) {
            *err = "WASI sd path missing";
        }
        return nullptr;
    }
    host->module_inst =
        wasm_runtime_instantiate(host->module, 256 * 1024, 32 * 1024 * 1024, error_buf, sizeof(error_buf));
    if (!host->module_inst) {
        if (err) {
            *err = error_buf;
        }
        wasm_runtime_unload(host->module);
        delete host;
        return nullptr;
    }
    host->exec_env = wasm_runtime_create_exec_env(host->module_inst, 256 * 1024);
    if (!lookup(host)) {
        if (err) {
            *err = "missing wasm exports";
        }
        wasm_runtime_destroy_exec_env(host->exec_env);
        wasm_runtime_deinstantiate(host->module_inst);
        wasm_runtime_unload(host->module);
        delete host;
        return nullptr;
    }
    wasm_runtime_call_wasm(host->exec_env, host->fn_init, 0, nullptr);
    uint32_t argv[1] = {0};
    if (host->fn_lcd_w) {
        wasm_runtime_call_wasm(host->exec_env, host->fn_lcd_w, 0, argv);
        host->lcd_w = argv[0];
    }
    argv[0] = 0;
    if (host->fn_lcd_h) {
        wasm_runtime_call_wasm(host->exec_env, host->fn_lcd_h, 0, argv);
        host->lcd_h = argv[0];
    }
    argv[0] = 0;
    if (host->fn_lcd_d) {
        wasm_runtime_call_wasm(host->exec_env, host->fn_lcd_d, 0, argv);
        host->lcd_depth = argv[0];
    }
    host->lcd_bytes = host->lcd_w * host->lcd_h * 2;
    host->lcd_host.assign(host->lcd_bytes, 0);
    host->wasm_lcd_buf = wasm_alloc(host, host->lcd_bytes);
    host->wasm_scratch = wasm_alloc(host, 64 * sizeof(int16_t));
    return host;
}

void edgetx_wasm_destroy(EdgeTxWasmHost* host) {
    if (!host) {
        return;
    }
    edgetx_wasm_stop(host);
    if (host->exec_env && host->fn_free) {
        wasm_free(host, host->wasm_lcd_buf);
        wasm_free(host, host->wasm_scratch);
    }
    if (host->exec_env) {
        wasm_runtime_destroy_exec_env(host->exec_env);
    }
    if (host->module_inst) {
        wasm_runtime_deinstantiate(host->module_inst);
    }
    if (host->module) {
        wasm_runtime_unload(host->module);
    }
    if (g_notify_host == host) {
        g_notify_host = nullptr;
    }
    delete host;
}

bool edgetx_wasm_start(EdgeTxWasmHost* host, int32_t utc_offset_sec, std::string* err) {
    if (!host || host->running) {
        return false;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    call_fatfs_paths(host);
    if (host->fn_create_defaults) {
        wasm_runtime_call_wasm(host->exec_env, host->fn_create_defaults, 0, nullptr);
    }
    uint32_t argv[2] = {1, static_cast<uint32_t>(utc_offset_sec)};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_start, 2, argv)) {
        const char* ex = wasm_runtime_get_exception(host->module_inst);
        if (ex) {
            host->last_error = ex;
        }
        if (err) {
            *err = host->last_error.empty() ? "simuStart failed" : host->last_error;
        }
        return false;
    }
    refresh_lcd_locked(host);
    host->running = true;
    host->worker = std::thread([host]() {
        while (host->running.load()) {
            if (host->lcd_notify.exchange(false)) {
                std::lock_guard<std::mutex> guard(host->mutex);
                refresh_lcd_locked(host);
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
        }
    });
    return true;
}

void edgetx_wasm_stop(EdgeTxWasmHost* host) {
    if (!host) {
        return;
    }
    host->running = false;
    if (host->worker.joinable()) {
        host->worker.join();
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    if (host->fn_stop && host->exec_env) {
        wasm_runtime_call_wasm(host->exec_env, host->fn_stop, 0, nullptr);
    }
}

void edgetx_wasm_pump(EdgeTxWasmHost* host) {
    if (!host || !host->running) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    const bool notify = host->lcd_notify.exchange(false);
    const bool changed = notify || lcd_changed_locked(host);
    if (changed) {
        refresh_lcd_locked(host);
    }
}

void edgetx_wasm_set_analog(EdgeTxWasmHost* host, uint8_t index, int16_t value) {
    if (host && index < 32) {
        host->analogs[index].store(value);
    }
}

void edgetx_wasm_set_switch(EdgeTxWasmHost* host, uint8_t index, int8_t state) {
    if (!host || !host->fn_set_switch || !host->exec_env) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    uint32_t argv[2] = {index, static_cast<uint32_t>(static_cast<int32_t>(state))};
    wasm_runtime_call_wasm(host->exec_env, host->fn_set_switch, 2, argv);
}

void edgetx_wasm_set_key(EdgeTxWasmHost* host, uint8_t key, bool state) {
    if (!host || !host->fn_set_key || !host->exec_env) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    uint32_t argv[2] = {key, state ? 1u : 0u};
    wasm_runtime_call_wasm(host->exec_env, host->fn_set_key, 2, argv);
}

void edgetx_wasm_set_trim(EdgeTxWasmHost* host, uint8_t trim, bool state) {
    if (!host || !host->fn_set_trim || !host->exec_env) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    uint32_t argv[2] = {trim, state ? 1u : 0u};
    wasm_runtime_call_wasm(host->exec_env, host->fn_set_trim, 2, argv);
}

void edgetx_wasm_touch_down(EdgeTxWasmHost* host, int16_t x, int16_t y) {
    if (!host || !host->fn_touch_down) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    uint32_t argv[2] = {static_cast<uint32_t>(x), static_cast<uint32_t>(y)};
    wasm_runtime_call_wasm(host->exec_env, host->fn_touch_down, 2, argv);
}

void edgetx_wasm_touch_up(EdgeTxWasmHost* host) {
    if (!host || !host->fn_touch_up) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    wasm_runtime_call_wasm(host->exec_env, host->fn_touch_up, 0, nullptr);
}

void edgetx_wasm_lcd_flushed(EdgeTxWasmHost* host) {
    if (!host || !host->fn_lcd_flushed) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    wasm_runtime_call_wasm(host->exec_env, host->fn_lcd_flushed, 0, nullptr);
}

uint32_t edgetx_wasm_lcd_width(const EdgeTxWasmHost* host) {
    return host ? host->lcd_w : 0;
}

uint32_t edgetx_wasm_lcd_height(const EdgeTxWasmHost* host) {
    return host ? host->lcd_h : 0;
}

bool edgetx_wasm_copy_lcd_rgb565(EdgeTxWasmHost* host, std::vector<uint8_t>* out) {
    if (!host || !out) {
        return false;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    *out = host->lcd_host;
    return !out->empty();
}

bool edgetx_wasm_boot_complete(const EdgeTxWasmHost* host) {
    if (!host || !host->running || !host->fn_boot_complete || !host->exec_env) {
        return false;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    uint32_t argv[1] = {0};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_boot_complete, 0, argv)) {
        return false;
    }
    return argv[0] != 0;
}

void edgetx_wasm_set_audio_chunk_handler(EdgeTxWasmHost* host, EdgetxAudioChunkFn handler) {
    if (!host) {
        return;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    host->audio_chunk = std::move(handler);
}

bool edgetx_wasm_send_telemetry(EdgeTxWasmHost* host, uint8_t module, uint8_t protocol,
                                const uint8_t* data, uint32_t len) {
    if (!host || !host->running || !host->fn_send_telemetry || !host->exec_env || !data || len == 0) {
        return false;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    const uint32_t ptr = wasm_alloc(host, len);
    if (!ptr) {
        return false;
    }
    void* native = wasm_runtime_addr_app_to_native(host->module_inst, ptr);
    if (!native) {
        wasm_free(host, ptr);
        return false;
    }
    std::memcpy(native, data, len);
    uint32_t argv[4] = {module, protocol, ptr, len};
    const bool ok = wasm_runtime_call_wasm(host->exec_env, host->fn_send_telemetry, 4, argv);
    wasm_free(host, ptr);
    if (!ok) {
        const char* ex = wasm_runtime_get_exception(host->module_inst);
        if (ex) {
            host->last_error = ex;
        }
        wasm_runtime_clear_exception(host->module_inst);
    }
    return ok;
}

uint8_t edgetx_wasm_copy_outputs(EdgeTxWasmHost* host, int16_t* buf, uint8_t max_count) {
    if (!host || !host->fn_copy_outputs || !host->wasm_scratch || !buf || max_count == 0) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(host->mutex);
    uint32_t argv[2] = {host->wasm_scratch, max_count};
    if (!wasm_runtime_call_wasm(host->exec_env, host->fn_copy_outputs, 2, argv)) {
        return 0;
    }
    void* native = wasm_runtime_addr_app_to_native(host->module_inst, host->wasm_scratch);
    if (!native) {
        return 0;
    }
    const uint8_t count = static_cast<uint8_t>(argv[0]);
    const uint8_t n = count < max_count ? count : max_count;
    std::memcpy(buf, native, n * sizeof(int16_t));
    return n;
}
