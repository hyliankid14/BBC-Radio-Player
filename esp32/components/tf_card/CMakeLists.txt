idf_component_register(
    SRCS "tf_card.c"
    INCLUDE_DIRS "."
    REQUIRES
        driver
        fatfs
        vfs
        log
)
