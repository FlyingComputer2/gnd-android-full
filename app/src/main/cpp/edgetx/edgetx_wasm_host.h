#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

struct EdgeTxWasmHost;

EdgeTxWasmHost* edgetx_wasm_create(const char* wasm_path, const char* sd_path,
                                     const char* settings_path, std::string* err);

void edgetx_wasm_destroy(EdgeTxWasmHost* host);

bool edgetx_wasm_start(EdgeTxWasmHost* host, int32_t utc_offset_sec, std::string* err);

void edgetx_wasm_stop(EdgeTxWasmHost* host);

void edgetx_wasm_pump(EdgeTxWasmHost* host);

void edgetx_wasm_set_analog(EdgeTxWasmHost* host, uint8_t index, int16_t value);

void edgetx_wasm_set_switch(EdgeTxWasmHost* host, uint8_t index, int8_t state);

void edgetx_wasm_set_key(EdgeTxWasmHost* host, uint8_t key, bool state);

void edgetx_wasm_touch_down(EdgeTxWasmHost* host, int16_t x, int16_t y);

void edgetx_wasm_touch_up(EdgeTxWasmHost* host);

void edgetx_wasm_lcd_flushed(EdgeTxWasmHost* host);

uint32_t edgetx_wasm_lcd_width(const EdgeTxWasmHost* host);

uint32_t edgetx_wasm_lcd_height(const EdgeTxWasmHost* host);

// Returns RGB565 framebuffer; empty if none.
bool edgetx_wasm_copy_lcd_rgb565(EdgeTxWasmHost* host, std::vector<uint8_t>* out);

uint8_t edgetx_wasm_copy_outputs(EdgeTxWasmHost* host, int16_t* buf, uint8_t max_count);

bool edgetx_wasm_boot_complete(const EdgeTxWasmHost* host);

using EdgetxAudioChunkFn = std::function<void(const uint8_t*, uint32_t)>;

void edgetx_wasm_set_audio_chunk_handler(EdgeTxWasmHost* host, EdgetxAudioChunkFn handler);
