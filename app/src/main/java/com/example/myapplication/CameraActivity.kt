package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.kyleduo.switchbutton.SwitchButton
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraActivity : ComponentActivity() {
    private lateinit var cameraPreview: PreviewView
    private lateinit var zoomSeekBar: SeekBar
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var isCameraOn = false
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var cameraInfo: CameraInfo? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var recording: Recording
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var videoRecordButton: Button
    private var isPressed = false

    @OptIn(ExperimentalGetImage::class) override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        cameraPreview = findViewById(R.id.cameraPreview)
        zoomSeekBar = findViewById(R.id.seeker)
        val cameraSwitchButton = findViewById<SwitchButton>(R.id.CameraSwitchButton)
        videoRecordButton = findViewById(R.id.videoRecordButton)

        cameraSwitchButton.isChecked = false
        videoRecordButton.isEnabled = false // Initially disable the video record button

        cameraExecutor = Executors.newSingleThreadExecutor()

        val SDK_INT = Build.VERSION.SDK_INT
        if (SDK_INT > 8) {
            val policy = ThreadPolicy.Builder()
                .permitAll().build()
            StrictMode.setThreadPolicy(policy)
            //your codes here
        }

        cameraSwitchButton.setOnCheckedChangeListener { _, isChecked ->
            isCameraOn = if (isChecked) {
                if (allPermissionsGranted()) {
                    startCamera(cameraSelector)
                } else {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), MainActivity.CAMERA_PERM_CODE
                    )
                }
                cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                cameraProviderFuture?.addListener({
                    val cameraProvider = cameraProviderFuture?.get()
                    bindPreview(cameraProvider)
                }, ContextCompat.getMainExecutor(this))
                videoRecordButton.isEnabled = true // Enable the video record button
                true
            } else {
                stopCamera()
                videoRecordButton.isEnabled = false // Disable the video record button
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

        videoRecordButton.setOnClickListener {
            if (isPressed) {
                videoRecordButton.setBackgroundResource(R.drawable.custom_button_background)
                videoRecordButton.setText(R.string.video_record_button_text)
                stopRecording()
            } else {
                videoRecordButton.setBackgroundResource(R.drawable.video_record_button_pressed)
                videoRecordButton.setText(R.string.stop_record_button_text)
                startRecording()
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

        startSendingHelloWorld();
    }

    private fun startCamera(cameraSelector: CameraSelector) {
        val cameraProvider = cameraProviderFuture?.get()

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(cameraPreview.surfaceProvider) }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, videoCapture
            )

            val displayDisabledText = findViewById<TextView>(R.id.displayDisabledText)
            displayDisabledText.visibility = View.GONE
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider?) {
        val preview = Preview.Builder().build()

        val updatedCameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        } else {
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        }

        preview.setSurfaceProvider(cameraPreview.surfaceProvider)

        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(this, updatedCameraSelector, preview, videoCapture)
        } catch (exc: Exception) {
            // Handle exceptions here
        }
    }

    private fun startRecording() {
        val videoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions if not granted
            // TODO: Handle permission request logic here
            return
        }

        // Start video recording
        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Video recording started")
                        showToast("Video recording started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Log.d(TAG, "Video recording finalized successfully")
                            showToast("Video saveed in ${System.currentTimeMillis()}.mp4")
                            // Video saved successfully
                        } else {
                            Log.e(TAG, "Error occurred during video recording finalization: ${recordEvent.error}")
                            showToast("Error occurred during video recording finalization: ${recordEvent.error}")
                            // Error occurred
                        }
                    }
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "VideoRecordingDebug"
    }

    private fun stopRecording() {
        recording.stop()
    }

    private fun stopCamera() {
        val cameraProvider = cameraProviderFuture?.get()
        cameraProvider?.unbindAll()
        camera = null

        val displayDisabledText = findViewById<TextView>(R.id.displayDisabledText)
        displayDisabledText.visibility = View.VISIBLE
    }

    private fun allPermissionsGranted() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private val handler = Handler(Looper.getMainLooper())
    private val runnable = object : Runnable {
        override fun run() {
            sendCameraDataToServer()
            handler.postDelayed(this, 10000) // Run this runnable again after 10 seconds
        }
    }

    private fun sendCameraDataToServer() {
        val serverIp = "192.168.100.10"
        val serverPort = 12345

        try {
            Log.d("TCPClient", "Attempting to connect to server: $serverIp:$serverPort")
            val socket = Socket(serverIp, serverPort)
            val outputStream = socket.getOutputStream()
            val writer = BufferedWriter(OutputStreamWriter(outputStream))

            // Collect camera data
            val zoomLevel = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
            val isRecording = this::recording.isInitialized
            val lensFacing = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "front" else "back"
            val cameraSelectorString = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) "front" else "back"
            val alignmentSwitchButtonStatus = findViewById<SwitchButton>(R.id.AlignmentSwitchButton).isChecked
            val cameraSwitchButtonStatus = findViewById<SwitchButton>(R.id.CameraSwitchButton).isChecked

            val cameraData = CameraData(
                zoomLevel = zoomLevel,
                isRecording = isRecording,
                lensFacing = lensFacing,
                cameraSelector = cameraSelectorString,
                alignmentSwitchButtonStatus = alignmentSwitchButtonStatus,
                cameraSwitchButtonStatus = cameraSwitchButtonStatus
            )

            // Convert camera data to JSON
            val gson = com.google.gson.Gson()
            val message = gson.toJson(cameraData)

            // Send camera data
            writer.write(message)
            writer.newLine()
            writer.flush()

            Log.d("TCPClient", "Camera data sent to server")
            socket.close()
        } catch (e: Exception) {
            Log.e("TCPClient", "Error connecting to server", e)
        }
    }


    // Call this method to start sending "Hello, World!" messages every 10 seconds
    private fun startSendingHelloWorld() {
        handler.post(runnable)
    }

    // Call this method to stop sending "Hello, World!" messages
    private fun stopSendingHelloWorld() {
        handler.removeCallbacks(runnable)
    }
}
