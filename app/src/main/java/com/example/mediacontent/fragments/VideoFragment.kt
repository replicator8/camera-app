package com.example.mediacontent.fragments

import android.Manifest
import android.annotation.SuppressLint
import com.example.mediacontent.R
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.mediacontent.databinding.FragmentVideoBinding
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoFragment : Fragment() {
    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: androidx.camera.video.Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private var zoomRatio = 1f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            if (currentRecording != null) {
                val elapsedSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                binding.timeIndicator.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraAndMicPermissions()
        }
        setupClickListeners()
    }

    private fun hasCameraAndMicPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraAndMicPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            200
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200) {
            if (hasCameraAndMicPermissions()) {
                startCamera()
            } else {
                Toast.makeText(context, "Требуются разрешения на камеру и микрофон", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCamera() {
        val preview = androidx.camera.core.Preview.Builder().build()
        val recorder = Recorder.Builder()
            .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                preview.setSurfaceProvider(binding.preview.surfaceProvider)

                scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val newZoom = (zoomRatio * detector.scaleFactor)
                            .coerceIn(1f, camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                        camera.cameraControl.setZoomRatio(newZoom)
                        zoomRatio = newZoom
                        return true
                    }
                })

                val zoomObserver = androidx.lifecycle.Observer<ZoomState> { state ->
                    zoomRatio = state.zoomRatio
                }
                camera.cameraInfo.zoomState.observe(viewLifecycleOwner, zoomObserver)

                binding.preview.setOnTouchListener { _, event ->
                    scaleGestureDetector.onTouchEvent(event)
                    if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                        val point = binding.preview.meteringPointFactory.createPoint(event.x, event.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera.cameraControl.startFocusAndMetering(action)
                    }
                    true
                }

            } catch (e: Exception) {
                Log.e("VideoFragment", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun setupClickListeners() {
        binding.recordBtn.setOnClickListener {
            if (!hasCameraAndMicPermissions()) {
                requestCameraAndMicPermissions()
                return@setOnClickListener
            }

            @SuppressLint("MissingPermission")
            if (currentRecording == null) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        binding.switchBtn.setOnClickListener {
            if (currentRecording != null) {
                currentRecording?.stop()
                currentRecording = null
            }
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        val mediaDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: requireContext().filesDir

        val name = "VIDEO_${
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(System.currentTimeMillis())
        }.mp4"
        val file = File(mediaDir, name)

        val fileOutput = FileOutputOptions.Builder(file).build()

        currentRecording = videoCapture?.output?.prepareRecording(requireContext(), fileOutput)
            ?.withAudioEnabled()
            ?.start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        requireActivity().runOnUiThread {
                            updateUiForRecording(true)
                            recordingStartTime = System.currentTimeMillis()
                            handler.post(timeRunnable)
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        requireActivity().runOnUiThread {
                            if (event.hasError()) {
                                Log.e("VideoFragment", "Recording error: ${event.error}")
                                Toast.makeText(context, "Ошибка записи", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Сохранено: ${file.name}", Toast.LENGTH_SHORT).show()
                                Log.i("VideoFragment", "Video saved: ${file.absolutePath}")
                            }
                            updateUiForRecording(false)
                            handler.removeCallbacks(timeRunnable)
                            currentRecording = null
                        }
                    }
                }
            }
    }

    private fun stopRecording() {
        currentRecording?.stop()
    }

    private fun updateUiForRecording(isRecording: Boolean) {
        if (isRecording) {
            binding.recordBtn.setBackgroundResource(R.drawable.circle_gray_background)
            binding.timeIndicator.visibility = View.VISIBLE
        } else {
            binding.recordBtn.setBackgroundResource(R.drawable.circle_red_background)
            binding.timeIndicator.visibility = View.GONE
            binding.timeIndicator.text = "00:00"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handler.removeCallbacks(timeRunnable)
    }
}
