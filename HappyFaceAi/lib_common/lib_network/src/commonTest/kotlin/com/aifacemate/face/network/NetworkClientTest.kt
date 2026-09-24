package com.aifacemate.face.network

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkClientTest {
    @Test fun responseDefaultsAreStable() {
        val response = NetworkResponse(204)
        assertEquals(204, response.statusCode)
        assertEquals("", response.text)
    }
}
