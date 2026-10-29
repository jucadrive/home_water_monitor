idf_component_register(SRCS "ads1115.c" "tds_math.c" "tds_sensor.c" "worker.c" "temperature.c" "temperature_runtime.c"
    INCLUDE_DIRS "include" REQUIRES esp_driver_i2c esp_timer freertos espressif__ds18b20 espressif__onewire_bus)
