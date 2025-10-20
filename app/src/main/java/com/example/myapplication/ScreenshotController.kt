package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * ScreenshotController manages a single MediaProjection instance with a single VirtualDisplay.
 * Frames are sourced from ImageReader and distributed (IPC-style) to both screenshot save and video encoder.
 */
class ScreenshotController(private val context: Context, private val notifier: NotificationHelper) {
    companion object {
        private const val TAG = "ScreenshotController"
        private const val VIDEO_FRAME_RATE = 30 // Use 30fps for CPU balance
    }

    // Single MediaProjection instance shared between features
    private var mediaProjection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Single VirtualDisplay backed by ImageReader
    private var screenshotVirtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var currentImageReader: ImageReader? = null

    // Video recording via MediaCodec-based encoder fed from ImageReader frames
    private var videoEncoder: VideoEncoder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null

    // Audio recording via AudioRecord + AAC encoder
    private var audioEncoder: AudioEncoder? = null
    private var muxerController: MuxerController? = null

    // Background thread for ImageReader callbacks during recording
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var projectionCallback: MediaProjection.Callback? = null
    private var persistentDisplayCreated = false
    private var persistentWidth: Int = 0
    private var persistentHeight: Int = 0
    private var persistentDensity: Int = 0

    // Frame listeners for additional IPC-like communication
    private val frameListeners = CopyOnWriteArrayList<(Bitmap) -> Unit>()

    fun startProjection(resultCode: Int, resultData: Intent) {
        Log.d(TAG, "Starting media projection with shared frame distribution")

        if (mediaProjection != null) {
            Log.w(TAG, "MediaProjection already exists")
            return
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        try {
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post {
                        try {
                            release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during release", e)
                        }
                    }
                }
            }
            projectionCallback?.let { cb ->
                mediaProjection?.registerCallback(cb, mainHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register callback", e)
        }

        // Setup virtual display with ImageReader for frame capture
        setupVirtualDisplayAndRecording()
    }

    fun isReady(): Boolean = mediaProjection != null
    fun isPersistentDisplayReady(): Boolean = persistentDisplayCreated

    fun takeScreenshot(notify: Boolean) {
        if (!persistentDisplayCreated || currentImageReader == null) {
            Log.e(TAG, "Virtual display not ready")
            return
        }

        mainHandler.post {
            val image = currentImageReader?.acquireLatestImage()
            try {
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * persistentWidth
                    val bitmapWidth = persistentWidth + rowPadding / pixelStride
                    val bitmapHeight = persistentHeight
                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)

                    val croppedBitmap = if (bitmapWidth != persistentWidth) {
                        Bitmap.createBitmap(bitmap, 0, 0, persistentWidth, persistentHeight)
                    } else bitmap

                    val bitmapCopy = croppedBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    frameListeners.forEach { it.invoke(bitmapCopy) }

                    saveBitmap(croppedBitmap)

                    if (croppedBitmap != bitmap) croppedBitmap.recycle()
                    bitmap.recycle()

                    if (notify) notifier.showScreenshotNotification()
                    Log.d(TAG, "Screenshot captured and distributed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screenshot", e)
            } finally {
                image?.close()
            }
        }
    }

    private fun setupVirtualDisplayAndRecording() {
        mainHandler.post {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val density = metrics.densityDpi

                val prefs = PrefsHelper(context)
                val shouldRecord = prefs.isAutoVideoRecordingEnabled()

                // Always create a single ImageReader-backed VirtualDisplay (maxImages=3 to allow concurrent access)
                val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).also {
                    currentImageReader = it
                }

                screenshotVirtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCaptureShared",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null,
                    mainHandler
                )

                persistentDisplayCreated = screenshotVirtualDisplay != null
                if (persistentDisplayCreated) {
                    persistentWidth = width
                    persistentHeight = height
                    persistentDensity = density
                }

                Log.d(TAG, "MediaProjection setup complete: ${width}x${height} (single VD)")

                if (shouldRecord) {
                    startVideoRecording()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up projection", e)
                cleanup()
            }
        }
    }

    private fun startVideoRecording() {
        if (isRecording || currentImageReader == null || !persistentDisplayCreated) return
        try {
            // Prepare output file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Recording_$timestamp.mp4"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            currentRecordingFile = File(storageDir, fileName)

            // Prepare audio (playback capture if possible, else MIC). We decide muxer expectation based on success.
            val audio = AudioEncoder()
            val audioPrepared = audio.prepare(context, mediaProjection)

            // Create shared muxer controller (coordinate video+audio)
            val mux = MuxerController(currentRecordingFile!!, expectAudio = audioPrepared)
            muxerController = mux

            // Start video encoder using shared muxer
            videoEncoder = VideoEncoder(persistentWidth, persistentHeight, VIDEO_FRAME_RATE)
            videoEncoder?.start(currentRecordingFile!!, mux)

            // Start audio encoder if prepared
            if (audioPrepared) {
                audioEncoder = audio
                audioEncoder?.start(mux)
            } else {
                Log.w(TAG, "Audio capture not available; recording video-only")
            }

            // Start capture thread & listener for video frames
            captureThread = HandlerThread("ImageReaderCapture").also { it.start() }
            captureHandler = Handler(captureThread!!.looper)

            currentImageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image == null) return@setOnImageAvailableListener
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * persistentWidth
                    val bitmapWidth = persistentWidth + rowPadding / pixelStride
                    val bitmapHeight = persistentHeight
                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cropped = if (bitmapWidth != persistentWidth) {
                        Bitmap.createBitmap(bitmap, 0, 0, persistentWidth, persistentHeight)
                    } else bitmap

                    // Feed to encoder (makes its own internal copy)
                    videoEncoder?.queueFrame(cropped)

                    // Also distribute to in-app listeners by sharing a copy
                    val copyForListeners = cropped.copy(Bitmap.Config.ARGB_8888, false)
                    frameListeners.forEach { it.invoke(copyForListeners) }

                    if (cropped != bitmap) cropped.recycle()
                    bitmap.recycle()
                } catch (t: Throwable) {
                    Log.e(TAG, "Error processing frame for recording", t)
                } finally {
                    try { image.close() } catch (_: Exception) {}
                }
            }, captureHandler)

            isRecording = true
            Log.i(TAG, "Recording started (Video+${if (audioPrepared) "Audio" else "NoAudio"}), file: ${currentRecordingFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start video recording", e)
            stopRecording()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        // Stop frame listener first to stop producing frames
        try { currentImageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}

        // Stop encoders
        try { audioEncoder?.stop() } catch (e: Exception) { Log.e(TAG, "Error stopping audio encoder", e) }
        try { videoEncoder?.stop() } catch (e: Exception) { Log.e(TAG, "Error stopping video encoder", e) }

        // Shutdown capture thread
        try { captureThread?.quitSafely(); captureThread?.join(500) } catch (_: Exception) {}
        captureThread = null
        captureHandler = null

        // Release encoders and muxer
        try { audioEncoder?.release() } catch (_: Exception) {}
        audioEncoder = null
        videoEncoder = null

        try { muxerController?.stopAndRelease() } catch (_: Exception) {}
        muxerController = null

        Log.d(TAG, "Recording stopped: ${currentRecordingFile?.absolutePath}")
    }

    fun release() {
        stopRecording()
        cleanup()

        mediaProjection?.stop()
        mediaProjection = null
        projectionCallback = null
        persistentDisplayCreated = false
    }

    private fun cleanup() {
        screenshotVirtualDisplay?.release()
        screenshotVirtualDisplay = null

        currentImageReader?.close()
        currentImageReader = null

        try {
            projectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering callback", e)
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        val prefs = PrefsHelper(context)
        val scale = prefs.getImageScale().coerceIn(0.1f, 1.0f)
        val quality = prefs.getImageQuality().coerceIn(0, 100)

        val filenameBase = "Screenshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val filename = "$filenameBase.jpg"

        var scaledBitmap: Bitmap? = null
        var savedUriString: String? = null
        try {
            val bitmapToSave = if (scale != 1.0f) {
                val newW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                val newH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                scaledBitmap
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            val compressedOk = bitmapToSave.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val imageBytes = baos.toByteArray()

            val saveToDevice = prefs.getSaveScreenshots()

            if (saveToDevice) {
                val fos: OutputStream?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                    }
                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    savedUriString = imageUri?.toString()
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Screenshots"
                    val file = File(imagesDir)
                    if (!file.exists()) file.mkdirs()
                    val image = File(file, filename)
                    savedUriString = image.absolutePath
                    fos = FileOutputStream(image)
                }

                var wroteOk = false
                if (compressedOk) {
                    fos?.use {
                        it.write(imageBytes)
                        wroteOk = true
                    } ?: run {
                        Log.w(TAG, "No OutputStream available to write image; savedUriString=$savedUriString")
                        wroteOk = false
                    }
                } else {
                    Log.e(TAG, "Bitmap compression to bytes failed")
                }

                if (wroteOk) {
                    Log.d(TAG, "Saved image to storage: $savedUriString")
                }
            } else {
                Log.d(TAG, "Skipping saving screenshot to device (user preference)")
            }

            try {
                if (prefs.isOpenAIAnalysisEnabled()) {
                    OpenAIAnalyzer.enqueueImageBytes(context, imageBytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue image bytes for OpenAI analysis", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap", e)
            e.printStackTrace()
        } finally {
            try { scaledBitmap?.recycle() } catch (_: Exception) {}
        }

        return savedUriString
    }
}