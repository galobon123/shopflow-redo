package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** HU-07 · The name a person reads in the "¿Fuiste vos?" email. */
class DeviceNameTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 | Chrome en Windows",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0 | Edge en Windows",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1 | Safari en iPhone",
            "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 | Chrome en Android",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.7; rv:132.0) Gecko/20100101 Firefox/132.0 | Firefox en Mac",
            "curl/8.5.0 | un dispositivo desconocido",
            "null | un dispositivo desconocido",
    })
    void namesTheBrowserAndTheSystem(String userAgent, String name) {
        assertEquals(name, DeviceName.of(userAgent));
    }
}
