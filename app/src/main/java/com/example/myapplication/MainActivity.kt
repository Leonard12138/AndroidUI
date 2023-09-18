package com.example.myapplication

import android.Manifest
import android.animation.ValueAnimator
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraBtn: Button
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private lateinit var blurOverlayImageView: ImageView
    private lateinit var previewAnimator: ValueAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        cameraPreview = findViewById(R.id.cameraPreview);
        cameraBtn = findViewById(R.id.cameraButton);
        blurOverlayImageView = findViewById(R.id.blurOverlay);

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

    override fun onResume() {
        super.onResume()

        if (allPermissionsGranted()) {
            startCamera()
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
        initializePreviewAnimator()
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

    private fun blurRenderScript(smallBitmap: Bitmap, radius: Int): Bitmap? {
        val defaultBitmapScale = 0.1f
        val width = (smallBitmap.width * defaultBitmapScale).roundToInt()
        val height = (smallBitmap.height * defaultBitmapScale).roundToInt()
        val inputBitmap = Bitmap.createScaledBitmap(smallBitmap, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)
        val renderScript = RenderScript.create(this)
        val theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        val tmpIn = Allocation.createFromBitmap(renderScript, inputBitmap)
        val tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap)
        theIntrinsic.setRadius(radius.toFloat())
        theIntrinsic.setInput(tmpIn)
        theIntrinsic.forEach(tmpOut)
        tmpOut.copyTo(outputBitmap)
        return outputBitmap
    }

    private fun initializePreviewAnimator() {
        // Initializing the preview animator with values to blur
        previewAnimator = ValueAnimator.ofInt(6, 12, 18, 24, 25)
        // Set the animation to repeat infinitely
        previewAnimator.repeatCount = ValueAnimator.INFINITE
        previewAnimator.repeatMode = ValueAnimator.RESTART

        // Setting animation duration for each frame (adjust as needed)
        previewAnimator.duration = 1000 // Set the duration for each frame (in milliseconds)

        // Adding listener for every value update of the animation
        previewAnimator.addUpdateListener {
            // Get the current blur level from the animator
            val blurLevel = 2
            // Capture the camera preview frame

            if (cameraPreview.bitmap != null) {
                // Blurring the captured frame using the blurRenderScript function
                val blurredBitmap = cameraPreview.bitmap?.let { blurRenderScript(it, 25) }
                // Set the blurred bitmap as the ImageView's image
                blurOverlayImageView.setImageBitmap(blurredBitmap)
            } else {
                Log.e(TAG, "Camera preview bitmap is null")
            }
        }

        // Start the previewAnimator when you start the camera
        previewAnimator.start()
    }





    companion object {
        const val CAMERA_PERM_CODE = 101
        const val CAMERA_REQUEST_CODE = 102
    }
}
