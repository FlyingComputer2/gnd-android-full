include(FetchContent)

set(WAMR_BUILD_INTERP 1)
set(WAMR_BUILD_FAST_INTERP 1)
set(WAMR_BUILD_AOT 0)
set(WAMR_BUILD_JIT 0)
set(WAMR_BUILD_LIBC_BUILTIN 1)
set(WAMR_BUILD_LIBC_WASI 1)
set(WAMR_BUILD_LIB_WASI_THREADS 1)
set(WAMR_BUILD_SHARED_MEMORY 1)
set(WAMR_BUILD_BULK_MEMORY 1)
set(WAMR_BUILD_REF_TYPES 1)
set(WAMR_BUILD_SIMD 0)
set(WAMR_BUILD_EXCE_HANDLING 1)
set(WAMR_DISABLE_HW_BOUND_CHECK 1)

if(ANDROID)
    set(WAMR_BUILD_PLATFORM "android")
endif()

FetchContent_Declare(
    wamr
    GIT_REPOSITORY https://github.com/bytecodealliance/wasm-micro-runtime
    GIT_TAG WAMR-2.4.4
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(wamr)
if(NOT wamr_POPULATED)
    FetchContent_Populate(wamr)
    add_subdirectory("${wamr_SOURCE_DIR}" "${wamr_BINARY_DIR}" EXCLUDE_FROM_ALL)
endif()
