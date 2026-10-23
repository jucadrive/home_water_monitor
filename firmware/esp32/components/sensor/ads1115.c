#include "ads1115.h"
#include "driver/i2c_master.h"

static i2c_master_bus_handle_t bus;
static i2c_master_dev_handle_t adc;
enum { ADC_ADDRESS = 0x48, TRANSFER_MS = 20, CONFIG = 0x4283 };

static bool reg_read(uint8_t reg, uint16_t *out)
{
    uint8_t bytes[2];
    if (!adc || i2c_master_transmit_receive(adc, &reg, 1, bytes, 2, TRANSFER_MS) != ESP_OK) return false;
    *out = ((uint16_t)bytes[0] << 8) | bytes[1];
    return true;
}
bool hwm_ads1115_init(void)
{
    /* External module pull-ups must be wired to 3.3V, not a 5V supply. */
    i2c_master_bus_config_t config = {.i2c_port = -1, .sda_io_num = 21, .scl_io_num = 22,
        .clk_source = I2C_CLK_SRC_DEFAULT, .glitch_ignore_cnt = 7};
    if (i2c_new_master_bus(&config, &bus) != ESP_OK) return false;
    i2c_device_config_t device = {.dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = ADC_ADDRESS, .scl_speed_hz = 100000};
    if (i2c_master_probe(bus, ADC_ADDRESS, TRANSFER_MS) != ESP_OK
        || i2c_master_bus_add_device(bus, &device, &adc) != ESP_OK) goto failed;
    /* MUX=A0-GND, PGA=+/-4.096V, continuous, 128SPS, comparator disabled. */
    uint8_t bytes[] = {1, (uint8_t)(CONFIG >> 8), (uint8_t)CONFIG};
    uint16_t actual;
    if (i2c_master_transmit(adc, bytes, sizeof(bytes), TRANSFER_MS) != ESP_OK
        || !reg_read(1, &actual) || (actual & 0x7fff) != CONFIG) goto failed;
    return true;
failed:
    if (adc) { i2c_master_bus_rm_device(adc); adc = NULL; }
    i2c_del_master_bus(bus); bus = NULL;
    return false;
}
bool hwm_ads1115_read(void *context, int16_t *raw)
{
    (void)context;
    uint16_t value, config;
    /* Detect reset/configuration loss rather than silently converting at the wrong gain. */
    if (!raw || !reg_read(1, &config) || (config & 0x7fff) != CONFIG || !reg_read(0, &value)) return false;
    *raw = value < 0x8000 ? (int16_t)value : (int16_t)((int32_t)value - 65536);
    return true;
}
