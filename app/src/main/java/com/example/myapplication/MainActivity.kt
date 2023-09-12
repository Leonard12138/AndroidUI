package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager

import android.view.TextureView

import android.widget.Toast

import androidx.activity.compose.setContent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraBtn: Button
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        cameraPreview = findViewById(R.id.cameraPreview)
        cameraBtn = findViewById(R.id.cameraButton)

        // Request camera permission and initialize CameraX

            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERM_CODE
                )
            }


        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture?.addListener({
            val cameraProvider = cameraProviderFuture?.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        cameraBtn.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)

        }
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
            val camera = cameraProvider?.bindToLifecycle(this, cameraSelector, preview)
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
            val camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview
            )
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERM_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera Permission is Required to Use the camera.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val CAMERA_PERM_CODE = 101
        const val CAMERA_REQUEST_CODE = 102
    }
}