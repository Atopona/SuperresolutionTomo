cmake_minimum_required(VERSION 3.15)
project(SuperResolutionNativeDLSS)

# Skeleton DLSS provider module (no external deps yet)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT (ON_LINUX OR ON_WINDOWS) )
    message(FATAL_ERROR "${LIB_PLATFORM} 平台不支持DLSS(占位模块)")
endif()

if(CMAKE_SYSTEM_NAME MATCHES "Linux")
    add_definitions(-DON_LINUX64)
elseif(CMAKE_SYSTEM_NAME MATCHES "Windows")
    add_definitions(-DON_WIN64)
    add_definitions(-D_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR)
elseif(CMAKE_SYSTEM_NAME MATCHES "Android")
    add_definitions(-DON_ANDROID)
elseif(CMAKE_SYSTEM_NAME MATCHES "Darwin")
    add_definitions(-DON_MACOS)
else()
    message(FATAL_ERROR "不支持的平台: ${CMAKE_SYSTEM_NAME}")
endif()

link_directories(
    ${PROJECT_SOURCE_DIR}/libraries/${LIB_PLATFORM}
    ${PROJECT_SOURCE_DIR}/../SRNativeMain/libraries/${LIB_PLATFORM}
)
include_directories(
    ${PROJECT_SOURCE_DIR}/include
    ${PROJECT_SOURCE_DIR}/../SRNativeMain/include
    ${PROJECT_SOURCE_DIR}/third_party
    ${PROJECT_SOURCE_DIR}/src
)

file(GLOB ALL_SRC ${PROJECT_SOURCE_DIR}/src/*.cpp)
add_library(SR_DLSS_LIB SHARED ${ALL_SRC})

if(CMAKE_SYSTEM_NAME MATCHES "Linux")
    set_target_properties(SR_DLSS_LIB PROPERTIES OUTPUT_NAME "SuperResolutionDLSS+${LIB_PLATFORM}+${SR_BUILD_TYPE}")
elseif(CMAKE_SYSTEM_NAME MATCHES "Windows")
    set_target_properties(SR_DLSS_LIB PROPERTIES OUTPUT_NAME "libSuperResolutionDLSS+${LIB_PLATFORM}+${SR_BUILD_TYPE}")
endif()

# Only link against the core SR API for now
# (No external DLSS/Streamline deps yet)
target_link_libraries(SR_DLSS_LIB SR_MAIN_LIB)

