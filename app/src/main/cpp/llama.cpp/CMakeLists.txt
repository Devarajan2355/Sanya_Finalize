cmake_minimum_required(VERSION 3.22)
project(llama_submodule)

# Minimal placeholder for the real llama.cpp submodule so Android CMake can add_subdirectory
add_library(llama STATIC
    ll.cpp
)

target_include_directories(llama PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/include
)

# Keep compile options conservative for cross-compilation
target_compile_features(llama PUBLIC cxx_std_17)
