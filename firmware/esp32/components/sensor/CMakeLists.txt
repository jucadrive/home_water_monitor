idf_component_register(SRCS "ads1115.c" "tds_math.c" "tds_sensor.c" "worker.c"
    INCLUDE_DIRS "include" REQUIRES esp_driver_i2c esp_timer freertos)
