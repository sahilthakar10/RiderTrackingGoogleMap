package com.codeint.ridertracking.internal.map

object GoogleMapConstants {

    // Camera and Animation Constants
    const val DEFAULT_CAMERA_ZOOM = 17f
    const val RIDER_FOCUSED_CAMERA_ZOOM = 19f
    const val CAMERA_ANIMATION_DURATION_MS = 300
    const val CAMERA_ANIMATION_DURATION_FALLBACK_MS = 150
    const val CAMERA_BOUNDS_PADDING_PIXELS = 100
    const val CAMERA_BOUNDS_MARGIN = 0.003
    const val CAMERA_BOUNDS_MARGIN_EXTENDED = 0.002

    // Tight focus camera constants
    const val TIGHT_FOCUS_BOUNDS_MARGIN = 0.001
    const val RIDER_FOCUS_BOUNDS_MARGIN = 0.0008
    const val MIN_TIGHT_ZOOM_LEVEL = 17f
    const val MAX_TIGHT_ZOOM_LEVEL = 19f
    const val TIGHT_CAMERA_PADDING_PIXELS = 200
    const val SUPER_TIGHT_FOCUS_MARGIN = 0.0005

    // Location Update and Validation Constants
    const val MIN_LOCATION_UPDATE_INTERVAL_MS = 1000L
    const val MAX_DEVIATION_DISTANCE_METERS = 100.0
    const val MAX_DEVIATION_THRESHOLD_MULTIPLIER = 1.5
    const val MAX_DISTANCE_FROM_ROUTE_METERS = 10000.0

    // Movement and Animation Constants
    const val MIN_ANIMATION_DISTANCE_METERS = 2.0
    const val MAX_ANIMATION_DISTANCE_METERS = 1500.0
    const val LARGE_BACKWARD_MOVEMENT_THRESHOLD_METERS = 50.0
    const val SMALL_MOVEMENT_DIRECT_UPDATE_THRESHOLD_METERS = 5.0
    const val MAX_ALLOWED_BACKWARD_MOVEMENT_PERCENTAGE = -0.05f

    // Animation Steps and Timing
    const val ANIMATION_STEPS_SHORT_DISTANCE = 25
    const val ANIMATION_STEPS_MEDIUM_DISTANCE = 50
    const val ANIMATION_STEPS_LONG_DISTANCE = 150
    const val ANIMATION_STEP_DELAY_MS = 80L
    const val ANIMATION_DISTANCE_SHORT_THRESHOLD = 10.0
    const val ANIMATION_DISTANCE_MEDIUM_THRESHOLD = 50.0

    // Route and Trail Management
    const val RIDER_TRAIL_MAX_SIZE = 20
    const val RIDER_TRAIL_RECALCULATION_SIZE = 5
    const val ROUTE_SEARCH_RANGE_POINTS = 10
    const val MIN_HEADING_CHANGE_DEGREES = 20.0

    // Route Pruning Management
    const val MIN_PRUNING_DISTANCE_METERS = 15.0
    const val MIN_PROGRESS_CHANGE_THRESHOLD = 0.02f

    // Polyline Styling
    const val ROUTE_POLYLINE_WIDTH = 10f
    const val VISITED_ROUTE_POLYLINE_WIDTH = 6f
    const val DESTINATION_LINE_POLYLINE_WIDTH = 3f
    const val DASH_LENGTH = 15f
    const val DASH_GAP = 8f
    const val VISITED_ROUTE_DASH_LENGTH = 10f
    const val VISITED_ROUTE_DASH_GAP = 5f
    const val DESTINATION_LINE_DASH_LENGTH = 20f
    const val DESTINATION_LINE_DASH_GAP = 15f

    // Marker Scaling
    const val SOURCE_DESTINATION_MARKER_SCALE = 1f
    const val MARKER_ANCHOR_CENTER = 0.5f

    // Coordinate Validation
    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0
    const val NULL_ISLAND_LATITUDE = 0.0
    const val NULL_ISLAND_LONGITUDE = 0.0

    // Progress and Distance Display
    const val DISTANCE_DISPLAY_THRESHOLD_METERS = 1000
    const val PROGRESS_PERCENTAGE_MULTIPLIER = 100

    // Heading and Bearing
    const val BEARING_WRAPAROUND_THRESHOLD = 180
    const val FULL_CIRCLE_DEGREES = 360

    // Canvas and Drawing
    const val CANVAS_DENSITY = 1f
    const val CANVAS_ALPHA = 1f

    // Multi-stop timing constants
    const val STORE_PICKUP_DELAY_MINUTES = 1
    const val STORE_PICKUP_DELAY_MS = STORE_PICKUP_DELAY_MINUTES * 60 * 1000L
    const val MULTI_STOP_JOURNEY_DURATION_MINUTES = 2
    const val INTER_STORE_TRAVEL_TIME_MULTIPLIER = 0.3f

    // Multi-stop marker constants
    const val STORE_MARKER_SCALE = 1.0f
    const val COMPLETED_STORE_MARKER_ALPHA = 0.0f
    const val PENDING_STORE_MARKER_ALPHA = 1.0f
    const val ACTIVE_STORE_MARKER_SCALE = 2.8f

    // Multi-stop camera constants
    const val MULTI_STOP_CAMERA_PADDING = 50
    const val MULTI_STOP_BOUNDS_MARGIN = 0.001
    const val SEGMENT_TRANSITION_ANIMATION_MS = 500

    // Multi-stop progress constants
    const val SEGMENT_PROGRESS_THRESHOLD = 0.95f
    const val STORE_ARRIVAL_THRESHOLD_METERS = 50.0
    const val NEXT_STOP_DETECTION_RADIUS_METERS = 100.0

    // Multi-stop route management
    const val MAX_ROUTE_SEGMENTS = 4
    const val SEGMENT_CACHE_SIZE = 2
    const val MULTI_STOP_DEVIATION_THRESHOLD_METERS = 200.0

    const val DESTINATION_ARRIVAL_CIRCLE_RADIUS_METERS = 150.0

    val MAP_UI_JSON = """
                    [
                      {
                        "elementType": "geometry",
                        "stylers": [
                          {
                            "color": "#e2e7ea"
                          }
                        ]
                      },
                      {
                        "elementType": "labels.icon",
                        "stylers": [
                          {
                            "visibility": "off"
                          }
                        ]
                      },
                      {
                        "elementType": "labels.text.fill",
                        "stylers": [
                          {
                            "color": "#9e9e9e"
                          }
                        ]
                      },
                      {
                        "elementType": "labels.text.stroke",
                        "stylers": [
                          {
                            "color": "#e2e7ea"
                          }
                        ]
                      },
                      {
                        "featureType": "poi",
                        "elementType": "labels.icon",
                        "stylers": [
                          {
                            "visibility": "off"
                          }
                        ]
                      },
                      {
                        "featureType": "poi",
                        "elementType": "labels.text.fill",
                        "stylers": [
                          {
                            "color": "#bdbdbd"
                          }
                        ]
                      },
                      {
                        "featureType": "poi",
                        "elementType": "labels.text.stroke",
                        "stylers": [
                          {
                            "color": "#e2e7ea"
                          }
                        ]
                      },
                      {
                        "featureType": "poi.park",
                        "elementType": "geometry",
                        "stylers": [
                          {
                            "color": "#a7e0b7"
                          }
                        ]
                      },
                      {
                        "featureType": "landscape.man_made",
                        "elementType": "geometry",
                        "stylers": [
                          {
                            "color": "#e0e6e9"
                          }
                        ]
                      },
                      {
                        "featureType": "road",
                        "elementType": "geometry",
                        "stylers": [
                          {
                            "color": "#ffffff"
                          }
                        ]
                      },
                      {
                        "featureType": "road",
                        "elementType": "labels.text.fill",
                        "stylers": [
                          {
                            "color": "#9e9e9e"
                          }
                        ]
                      },
                      {
                        "featureType": "road",
                        "elementType": "labels.text.stroke",
                        "stylers": [
                          {
                            "color": "#ffffff"
                          }
                        ]
                      },
                      {
                        "featureType": "water",
                        "elementType": "geometry",
                        "stylers": [
                          {
                            "color": "#64b5f6"
                          }
                        ]
                      }
                    ]
    """.trimIndent()
}
