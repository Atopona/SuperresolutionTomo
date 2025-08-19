# DLSS原生库CMake配置

# 查找DLSS SDK
find_path(DLSS_INCLUDE_DIR
    NAMES nvsdk_ngx.h
    PATHS
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/dlss/include
        ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/dlss/include
        $ENV{DLSS_SDK_PATH}/include
        "C:/Program Files/NVIDIA Corporation/NVIDIA NGX SDK/include"
    DOC "DLSS SDK include directory"
)

find_library(DLSS_LIBRARY
    NAMES nvsdk_ngx_s nvsdk_ngx
    PATHS
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/dlss/lib
        ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/dlss/lib
        $ENV{DLSS_SDK_PATH}/lib
        "C:/Program Files/NVIDIA Corporation/NVIDIA NGX SDK/lib"
    DOC "DLSS SDK library"
)

# 检查DLSS SDK是否可用
if(DLSS_INCLUDE_DIR AND DLSS_LIBRARY)
    set(DLSS_FOUND TRUE)
    message(STATUS "找到DLSS SDK: ${DLSS_INCLUDE_DIR}")
    message(STATUS "DLSS库: ${DLSS_LIBRARY}")
else()
    set(DLSS_FOUND FALSE)
    message(WARNING "未找到DLSS SDK，将使用模拟模式编译")
endif()

# 创建DLSS库
add_library(superresolution_dlss SHARED
    dlss_wrapper.cpp
    dlss_wrapper.h
)

# 设置包含目录
target_include_directories(superresolution_dlss PRIVATE
    ${JNI_INCLUDE_DIRS}
)

if(DLSS_FOUND)
    target_include_directories(superresolution_dlss PRIVATE ${DLSS_INCLUDE_DIR})
    target_link_libraries(superresolution_dlss ${DLSS_LIBRARY})
    target_compile_definitions(superresolution_dlss PRIVATE DLSS_AVAILABLE)
    
    # Windows特定设置
    if(WIN32)
        # 复制DLSS运行时DLL
        find_file(DLSS_DLL
            NAMES nvsdk_ngx.dll
            PATHS
                ${CMAKE_CURRENT_SOURCE_DIR}/third_party/dlss/bin
                ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/dlss/bin
                $ENV{DLSS_SDK_PATH}/bin
                "C:/Program Files/NVIDIA Corporation/NVIDIA NGX SDK/bin"
        )
        
        if(DLSS_DLL)
            message(STATUS "找到DLSS DLL: ${DLSS_DLL}")
            # 在构建后复制DLL到输出目录
            add_custom_command(TARGET superresolution_dlss POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                ${DLSS_DLL}
                $<TARGET_FILE_DIR:superresolution_dlss>
            )
        endif()
    endif()
else()
    message(STATUS "使用DLSS模拟模式编译")
endif()

# 设置输出目录
set_target_properties(superresolution_dlss PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
    ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib
)

# Windows特定设置
if(WIN32)
    target_compile_definitions(superresolution_dlss PRIVATE WIN32_LEAN_AND_MEAN)
    target_link_libraries(superresolution_dlss opengl32)
endif()

# Linux特定设置
if(UNIX AND NOT APPLE)
    target_link_libraries(superresolution_dlss GL)
endif()

# 编译选项
target_compile_features(superresolution_dlss PRIVATE cxx_std_17)

if(MSVC)
    target_compile_options(superresolution_dlss PRIVATE /W4)
else()
    target_compile_options(superresolution_dlss PRIVATE -Wall -Wextra -Wpedantic)
endif()

# 安装规则
install(TARGETS superresolution_dlss
    RUNTIME DESTINATION bin
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)

if(DLSS_FOUND AND WIN32 AND DLSS_DLL)
    install(FILES ${DLSS_DLL} DESTINATION bin)
endif()