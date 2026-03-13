cmake_minimum_required(VERSION 3.22.1)
project(assistant_llm VERSION 1.0.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG        b4170
    GIT_SHALLOW    FALSE
    GIT_PROGRESS   TRUE
)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN          OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          ON  CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

add_library(assistant_llm SHARED
    LLMInference.cpp
    llm_jni.cpp
)

target_include_directories(assistant_llm PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
    ${llama_cpp_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(assistant_llm
    llama
    android
    log
)

find_package(OpenMP)
if(OpenMP_CXX_FOUND)
    target_link_libraries(assistant_llm OpenMP::OpenMP_CXX)
endif()
