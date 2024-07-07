package com.example.myapplication

data class CameraData(
    val zoomLevel: Float,
    val isRecording: Boolean,
    val lensFacing: String,
    val cameraSelector: String,
    val alignmentSwitchButtonStatus: Boolean,
    val cameraSwitchButtonStatus: Boolean
)
