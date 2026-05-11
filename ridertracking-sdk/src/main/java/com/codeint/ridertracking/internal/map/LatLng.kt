package com.codeint.ridertracking.internal.map

/**
 * Simple data class to represent latitude and longitude coordinates.
 * This is our own LatLng, separate from Google Maps LatLng.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)
