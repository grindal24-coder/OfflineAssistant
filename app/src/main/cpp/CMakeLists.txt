cmake_minimum_required(VERSION 3.22.1)
project(offline_assistant_llama)

# --------------------------------------------------------------------------
# llama.cpp подключается как git submodule в ai/llama.cpp (см. корневой README,
# раздел "Подключение llama.cpp"). Здесь просто указываем его как поддиректорию.
# --------------------------------------------------------------------------
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../../../../../ai/llama.cpp)

if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "llama.cpp не найден в ${LLAMA_CPP_DIR}. "
        "Выполни: git submodule add https://github.com/ggerganov/llama.cpp ai/llama.cpp "
        "(см. README.md, раздел 'Подключение llama.cpp')")
endif()

# Отключаем ненужные для мобильной сборки таргеты llama.cpp (сервер, тесты и т.п.)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama_cpp_build)

add_library(offline_assistant_llama SHARED
    native-lib.cpp
)

target_include_directories(offline_assistant_llama PRIVATE
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

find_library(log-lib log)

target_link_libraries(offline_assistant_llama
    llama
    ggml
    ${log-lib}
    android
)
