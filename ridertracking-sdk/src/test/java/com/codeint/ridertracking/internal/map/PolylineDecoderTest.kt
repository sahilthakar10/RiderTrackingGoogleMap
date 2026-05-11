package com.codeint.ridertracking.internal.map

import org.junit.Assert.*
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decode - known encoded string - returns correct coordinates`() {
        // Simple known polyline: (38.5, -120.2) -> (40.7, -120.95) -> (43.252, -126.453)
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val result = PolylineDecoder.decode(encoded)
        assertEquals(3, result.size)
        assertEquals(38.5, result[0].latitude, 0.01)
        assertEquals(-120.2, result[0].longitude, 0.01)
    }

    @Test
    fun `decode - empty string - returns empty list`() {
        assertEquals(emptyList<LatLng>(), PolylineDecoder.decode(""))
    }

    @Test
    fun `decode - single point encoded - returns single LatLng`() {
        // Encode (0, 0) → "??"
        val result = PolylineDecoder.decode("??")
        assertEquals(1, result.size)
        assertEquals(0.0, result[0].latitude, 0.01)
        assertEquals(0.0, result[0].longitude, 0.01)
    }

    @Test
    fun `decode - negative coordinates - decodes correctly`() {
        // Known polyline with negative coordinates
        val encoded = "_p~iF~ps|U"
        val result = PolylineDecoder.decode(encoded)
        assertTrue(result.isNotEmpty())
        // First point should have negative longitude (western hemisphere)
        assertTrue("Longitude should be negative, was ${result[0].longitude}", result[0].longitude < 0)
    }

    @Test
    fun `decode - simulation route - has correct point count and range`() {
        val encoded = "}zymA_oayM_CtF{AzD]hAiBfIcAfCeCbGqAtEW|Ae@Kq@UIGOSo@kBaD}I{@eBoBmFiBGeEEsBG]I_@Ke@A"
        val result = PolylineDecoder.decode(encoded)
        assertTrue("Should have multiple points, had ${result.size}", result.size > 5)
        // All points should be in Bangalore region
        result.forEach { point ->
            assertTrue("Lat ${point.latitude} out of Bangalore range", point.latitude in 12.0..13.0)
            assertTrue("Lng ${point.longitude} out of Bangalore range", point.longitude in 77.0..78.0)
        }
    }
}
