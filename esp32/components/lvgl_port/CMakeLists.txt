idf_component_register(
    SRCS "lvgl_port.c"
    INCLUDE_DIRS "."
    REQUIRES
        bsp
        esp_lcd
        esp_timer
        log
)
