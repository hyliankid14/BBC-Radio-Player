cmake_minimum_required(VERSION 3.16)

set(EXTRA_COMPONENT_DIRS
    "${CMAKE_CURRENT_SOURCE_DIR}/components/bsp"
    "${CMAKE_CURRENT_SOURCE_DIR}/components/lvgl_port"
    "${CMAKE_CURRENT_SOURCE_DIR}/components/tf_card"
)

set(BBC_HAS_ADF 0)
if(DEFINED ENV{ADF_PATH} AND EXISTS "$ENV{ADF_PATH}/components/audio_pipeline")
    message(STATUS "BBC Radio Player: ESP-ADF detected at $ENV{ADF_PATH}")
    list(APPEND EXTRA_COMPONENT_DIRS
        "$ENV{ADF_PATH}/components/adf_utils"
        "$ENV{ADF_PATH}/components/audio_pipeline"
        "$ENV{ADF_PATH}/components/audio_stream"
        "$ENV{ADF_PATH}/components/audio_sal"
        "$ENV{ADF_PATH}/components/audio_hal"
        "$ENV{ADF_PATH}/components/esp-adf-libs"
    )
    set(BBC_HAS_ADF 1)
else()
    message(STATUS "BBC Radio Player: ESP-ADF not found, using tone fallback")
endif()

add_compile_definitions(BBC_HAS_ADF=${BBC_HAS_ADF})

include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(bbc_radio_player)
