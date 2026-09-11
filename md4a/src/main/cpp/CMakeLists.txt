cmake_minimum_required(VERSION 3.22.1)
project(md4a C)

add_library(md4a SHARED
    md4a_jni.c
    md4a_events.c
    md4c/md4c.c
)
target_include_directories(md4a PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/md4c)
target_compile_options(md4a PRIVATE -O2)
