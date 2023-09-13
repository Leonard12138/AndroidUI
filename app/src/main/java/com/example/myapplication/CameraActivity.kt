package com.example.myapplication
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.kyleduo.switchbutton.SwitchButton

class CameraActivity : ComponentActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var zoomSeekBar: SeekBar
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var isCameraOn = false
    private var camera: Camera? = null // Add Camera reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        cameraPreview = findViewById(R.id.cameraPreview)
        zoomSeekBar = findViewById(R.id.seeker)

        if (allPermissionsGranted()) {
            startCamera()
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

        val cameraSwitchButton = findViewById<SwitchButton>(R.id.CameraSwitchButton)

        cameraSwitchButton.setOnCheckedChangeListener { _, isChecked ->
            isCameraOn = if (isChecked) {
                startCamera()
                true
            } else {
                stopCamera()
                false
            }
        }

        // Set up the SeekBar listener to update camera zoom
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setCameraZoom(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setCameraZoom(zoomLevel: Int) {
        camera?.cameraControl?.setZoomRatio(zoomLevel / 100.0f) // Convert progress to zoom ratio
    }

    private fun stopCamera() {
        val cameraProvider = cameraProviderFuture?.get()
        cameraProvider?.unbindAll()
        camera = null
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider?) {
        // Configure the preview use case
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Connect the preview to the PreviewView
        preview.setSurfaceProvider(cameraPreview.surfaceProvider)

        // Unbind any previous use cases before rebinding
        try {
            cameraProvider?.unbindAll()

            // Bind the camera use cases to the cameraProvider
            camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview)
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    private fun allPermissionsGranted() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProvider = cameraProviderFuture?.get()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(cameraPreview.surfaceProvider) }

        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview
            )
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }
}
