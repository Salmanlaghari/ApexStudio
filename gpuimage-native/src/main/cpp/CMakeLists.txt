cmake_minimum_required(VERSION 3.22.1)
project(gpuimage_yuv_decoder C)

add_library(yuv-decoder SHARED yuv-decoder.c)

find_library(log-lib log)
target_link_libraries(yuv-decoder PRIVATE ${log-lib} GLESv2 jnigraphics)
target_link_options(yuv-decoder PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
