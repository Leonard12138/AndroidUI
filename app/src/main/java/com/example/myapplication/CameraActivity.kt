package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.kyleduo.switchbutton.SwitchButton

@ExperimentalGetImage
class CameraActivity : ComponentActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var zoomSeekBar: SeekBar
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var isCameraOn = false
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var cameraInfo: CameraInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        cameraPreview = findViewById(R.id.cameraPreview)
        zoomSeekBar = findViewById(R.id.seeker)
        val cameraSwitchButton = findViewById<SwitchButton>(R.id.CameraSwitchButton)
        val cameraButton: Button = findViewById(R.id.cameraButton)

        cameraSwitchButton.isChecked = false
        cameraButton.isEnabled = false // Initially disable the video record button

        cameraSwitchButton.setOnCheckedChangeListener { _, isChecked ->
            isCameraOn = if (isChecked) {
                if (allPermissionsGranted()) {
                    startCamera(cameraSelector)
                } else {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.CAMERA), MainActivity.CAMERA_PERM_CODE
                    )
                }
                cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                cameraProviderFuture?.addListener({
                    val cameraProvider = cameraProviderFuture?.get()
                    bindPreview(cameraProvider)
                }, ContextCompat.getMainExecutor(this))
                cameraButton.isEnabled = true // Enable the video record button
                true
            } else {
                stopCamera()
                cameraButton.isEnabled = false // Disable the video record button
                false
            }
        }

        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                camera?.cameraControl?.setLinearZoom(progress / 100.toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val halfButton = findViewById<RadioButton>(R.id.halfButton)
        val oneButton = findViewById<RadioButton>(R.id.oneButton)
        val twoButton = findViewById<RadioButton>(R.id.twoButton)
        val frontButton = findViewById<RadioButton>(R.id.frontButton)

        var isPressed = false

        cameraButton.setOnClickListener {
            if (isPressed) {
                cameraButton.setBackgroundResource(R.drawable.custom_button_background)
                cameraButton.setText(R.string.video_record_button_text)
            } else {
                cameraButton.setBackgroundResource(R.drawable.video_record_button_pressed)
                cameraButton.setText(R.string.stop_record_button_text)
            }
            isPressed = !isPressed
        }

        frontButton.setOnClickListener {
            if (cameraSwitchButton.isChecked) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                startCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                bindPreview(cameraProvider)
            }
        }

        halfButton.setOnClickListener {
            camera?.cameraControl?.setZoomRatio(cameraInfo?.zoomState?.value?.minZoomRatio ?: 1.0f)
        }

        oneButton.setOnClickListener {
            camera?.cameraControl?.setLinearZoom(0.toFloat())
        }

        twoButton.setOnClickListener {
            camera?.cameraControl?.setLinearZoom(0.5.toFloat())
        }
    }

    private fun stopCamera() {
        val cameraProvider = cameraProviderFuture?.get()
        cameraProvider?.unbindAll()
        camera = null

        // Show the "Display Disabled" text
        val displayDisabledText = findViewById<TextView>(R.id.displayDisabledText)
        displayDisabledText.visibility = View.VISIBLE
    }

    private fun startCamera(cameraSelector: CameraSelector) {
        val cameraProvider = cameraProviderFuture?.get()

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(cameraPreview.surfaceProvider) }

        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview
            )

            // Hide the "Display Disabled" text
            val displayDisabledText = findViewById<TextView>(R.id.displayDisabledText)
            displayDisabledText.visibility = View.GONE
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider?) {
        // Configure the preview use case
        val preview = Preview.Builder().build()

        // Update camera selector based on current selection
        val updatedCameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        } else {
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        }

        // Connect the preview to the PreviewView
        preview.setSurfaceProvider(cameraPreview.surfaceProvider)

        // Unbind any previous use cases before rebinding
        try {
            cameraProvider?.unbindAll()

            // Bind the camera use cases to the cameraProvider
            camera = cameraProvider?.bindToLifecycle(this, updatedCameraSelector, preview)
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    private fun allPermissionsGranted() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}
