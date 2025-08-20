cmake_minimum_required(VERSION 3.15)
project(ngx_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Enable symbol export on Windows without .def
if (WIN32)
    set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()

# Find JNI
find_package(JNI REQUIRED)

# Optional NGX SDK
option(USE_NGX_SDK "Build with NVIDIA NGX SDK" OFF)
set(NGX_SDK_DIR "" CACHE PATH "Path to NVIDIA NGX SDK root")

# Sources
set(NGX_CORE_SRC src/ngx_core_stub.cpp)
if (USE_NGX_SDK AND EXISTS ${NGX_SDK_DIR})
    message(STATUS "NGX SDK enabled: ${NGX_SDK_DIR}")
    add_definitions(-DUSE_NGX_SDK)
    # In a real setup, include and link NGX + Vulkan SDK here
    # include_directories(${NGX_SDK_DIR}/include)
    # find_package(Vulkan REQUIRED)
    # target_link_libraries(ngx_jni PRIVATE Vulkan::Vulkan ...)
    # set(NGX_CORE_SRC src/ngx_core_ngx.cpp)
else()
    message(STATUS "NGX SDK disabled or not found, building stub")
endif()

add_library(ngx_jni SHARED
    src/ngx_jni.cpp
    ${NGX_CORE_SRC}
)

target_include_directories(ngx_jni PRIVATE ${JNI_INCLUDE_DIRS} src)

# On Windows, produce .dll; on Unix, .so
set_target_properties(ngx_jni PROPERTIES
    OUTPUT_NAME "ngx_jni"
)

message(STATUS "JNI include dirs: ${JNI_INCLUDE_DIRS}")

