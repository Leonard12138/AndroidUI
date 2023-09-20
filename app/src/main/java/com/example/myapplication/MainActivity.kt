@file:Suppress("DEPRECATION")

package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@ExperimentalGetImage
class MainActivity : ComponentActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraBtn: Button
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private lateinit var blurOverlayImageView: ImageView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        cameraPreview = findViewById(R.id.cameraPreview)
        cameraBtn = findViewById(R.id.cameraButton)
        blurOverlayImageView = findViewById(R.id.blurOverlay)

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


        try {
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)
            cameraProvider?.unbindAll()
            val camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview
            )

            handler.post {
                // Add the ImageAnalysis use case here
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->

                    // Get the rotation degrees from the ImageProxy
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                    // Convert the imageProxy to a Bitmap
                    val inputBitmap =
                        imageProxy.image?.toBitmap(rotationDegrees) ?: return@setAnalyzer
                    // Apply blur to the Bitmap
                    val blurredBitmap = blurRenderScript(inputBitmap)
                    // Set the blurred Bitmap to the ImageView
                    runOnUiThread {
                        blurOverlayImageView.setImageBitmap(blurredBitmap)
                    }

                    imageProxy.close()
                }

                cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } // Delay for 1 second (adjust as needed)

        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    private fun Image.toBitmap(rotationDegrees: Int): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()

        // Rotate the image based on rotationDegrees
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)

        // Create a ByteArray from the rotated image
        val imageBytes = out.toByteArray()

        // Create a BitmapFactory to decode the ByteArray into a Bitmap
        val options = BitmapFactory.Options()
        options.inSampleSize = 1 // Adjust this if needed
        options.inPreferredConfig = Bitmap.Config.ARGB_8888 // Adjust this if needed
        options.inMutable = true // Adjust this if needed
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        // Rotate the bitmap to match the rotationDegrees
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    @Deprecated("Deprecated in Java")
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

    private fun blurRenderScript(smallBitmap: Bitmap): Bitmap? {
        val defaultBitmapScale = 0.1f
        val width = (smallBitmap.width * defaultBitmapScale).roundToInt()
        val height = (smallBitmap.height * defaultBitmapScale).roundToInt()
        val inputBitmap = Bitmap.createScaledBitmap(smallBitmap, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)
        val renderScript = RenderScript.create(this)
        val theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        val tmpIn = Allocation.createFromBitmap(renderScript, inputBitmap)
        val tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap)
        theIntrinsic.setRadius(20.toFloat())
        theIntrinsic.setInput(tmpIn)
        theIntrinsic.forEach(tmpOut)
        tmpOut.copyTo(outputBitmap)
        return outputBitmap
    }

//    private fun initializePreviewAnimator() {
//        // Initializing the preview animator with values to blur
//        previewAnimator = ValueAnimator.ofInt(6, 12, 18, 24, 25)
//        // Set the animation to repeat infinitely
//        previewAnimator.repeatCount = ValueAnimator.INFINITE
//        previewAnimator.repeatMode = ValueAnimator.RESTART
//
//        // Setting animation duration for each frame (adjust as needed)
//        previewAnimator.duration = 1000 // Set the duration for each frame (in milliseconds)
//
//        // Adding listener for every value update of the animation
//        previewAnimator.addUpdateListener {
//            // Get the current blur level from the animator
//            val blurLevel = 2
//            // Capture the camera preview frame
//
//            if (cameraPreview.bitmap != null) {
//                // Blurring the captured frame using the blurRenderScript function
//                val blurredBitmap = cameraPreview.bitmap?.let { blurRenderScript(it, 25) }
//                // Set the blurred bitmap as the ImageView's image
//                blurOverlayImageView.setImageBitmap(blurredBitmap)
//            } else {
//                Log.e(TAG, "Camera preview bitmap is null")
//            }
//        }
//
//        // Start the previewAnimator when you start the camera
//        previewAnimator.start()
//    }





    companion object {
        const val CAMERA_PERM_CODE = 101
//        const val CAMERA_REQUEST_CODE = 102
    }
}
