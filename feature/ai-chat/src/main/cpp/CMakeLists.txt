cmake_minimum_required(VERSION 3.22.1)
project(assistant_llm VERSION 1.0.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── FETCH llama.cpp — pinned to commit b3447 ────────────────────────────────
include(FetchContent)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        b3447
    GIT_SHALLOW    FALSE   # Must be FALSE for commit-based pin (not a tag)
    GIT_PROGRESS   TRUE
)

# Disable everything we don't need to keep build fast + clean
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# Vulkan GPU acceleration — runtime-detected, silently falls back to CPU if unavailable
option(GGML_VULKAN "Enable Vulkan backend" ON)

# OpenMP for multi-core CPU parallelism
option(GGML_OPENMP "Enable OpenMP" ON)

FetchContent_MakeAvailable(llama_cpp)

# ─── JNI BRIDGE LIBRARY ──────────────────────────────────────────────────────
add_library(
    assistant_llm
    SHARED
    LLMInference.cpp
    llm_jni.cpp
)

target_include_directories(assistant_llm PRIVATE
    ${llama_cpp_SOURCE_DIR}
    ${llama_cpp_SOURCE_DIR}/common
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(assistant_llm
    llama
    ggml
    android
    log
)

# Link OpenMP if available
find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    target_link_libraries(assistant_llm OpenMP::OpenMP_CXX)
endif()
