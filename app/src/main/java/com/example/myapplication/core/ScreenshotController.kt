package com.example.myapplication.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import com.example.myapplication.NotificationHelper
import com.example.myapplication.PrefsHelper
import com.example.myapplication.agents.AnalyzerAgent
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
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
import kotlin.math.roundToInt

/**
 * ScreenshotController manages the media projection, persistent virtual display and screenshots.
 * It is NOT a Service; a long-lived Service (MainForegroundService) should own an instance of this controller.
 */
class ScreenshotController(private val context: Context, private val notifier: NotificationHelper) {
    companion object {
        private const val TAG = "ScreenshotController"
    }

    private var mediaProjection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentVirtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var currentImageReader: ImageReader? = null
    // Keep a reference to the MediaProjection callback so we can unregister it later
    private var projectionCallback: MediaProjection.Callback? = null
    // Track whether we've created the persistent virtual display
    private var persistentDisplayCreated = false
    // Store persistent display dimensions
    private var persistentWidth: Int = 0
    private var persistentHeight: Int = 0
    private var persistentDensity: Int = 0

    fun startProjection(resultCode: Int, resultData: Intent) {
        Log.d(TAG, "Starting media projection")

        if (mediaProjection != null) {
            Log.w(TAG, "startProjection called but mediaProjection already exists — ignoring duplicate start")
            return
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        // Register a MediaProjection.Callback before starting capture to manage resources
        try {
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    // Ensure cleanup happens on main thread
                    mainHandler.post {
                        try {
                            release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during release in projection callback", e)
                        }
                    }
                }
            }
            projectionCallback?.let { cb ->
                mediaProjection?.registerCallback(cb, mainHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register MediaProjection callback", e)
        }

        // Create the persistent virtual display and ImageReader so we don't recreate it on every capture
        try {
            setupPersistentVirtualDisplay()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup persistent virtual display", e)
        }
    }

    fun isReady(): Boolean = mediaProjection != null

    // Allow external callers to check if the persistent virtual display has been created
    fun isPersistentDisplayReady(): Boolean = persistentDisplayCreated

    fun takeScreenshot(notify: Boolean) {
        if (mediaProjection == null) {
            Log.e(TAG, "Cannot take screenshot: mediaProjection is null")
            return
        }

        if (!persistentDisplayCreated || currentImageReader == null) {
            Log.e(TAG, "Persistent virtual display not available. Cannot take screenshot.")
            return
        }

        // Log that we're about to take a screenshot
        Log.i(TAG, "📸 Taking screenshot...")

        // Acquire the latest image from the existing ImageReader on the main thread
        mainHandler.postDelayed({
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
                    val bitmap = Bitmap.createBitmap(
                        bitmapWidth,
                        bitmapHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    saveBitmap(bitmap)
                    bitmap.recycle()
                    if (notify) notifier.showScreenshotNotification()
                    Log.i(TAG, "✅ Screenshot captured successfully (${persistentWidth}x${persistentHeight})")
                } else {
                    Log.e(TAG, "Failed to acquire image from persistent ImageReader")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error taking screenshot (persistent display)", e)
            } finally {
                image?.close()
            }
        }, 300)
    }

    // Create a persistent ImageReader and VirtualDisplay on the main thread
    private fun setupPersistentVirtualDisplay() {
        if (persistentDisplayCreated) {
            Log.d(TAG, "Persistent virtual display already created")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        Log.d(TAG, "Creating persistent virtual display: ${width}x${height} @${density}dpi")

        mainHandler.post {
            try {
                if (mediaProjection == null) {
                    Log.e(TAG, "Cannot create virtual display: mediaProjection is null")
                    return@post
                }

                // create ImageReader
                val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also {
                    currentImageReader = it
                }

                if (projectionCallback == null) {
                    Log.w(TAG, "No MediaProjection.Callback registered when setting up persistent display; registering one now")
                    projectionCallback = object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d(TAG, "MediaProjection callback (persistent): onStop called")
                            mainHandler.post { release() }
                        }
                    }
                    projectionCallback?.let { cb ->
                        mediaProjection?.registerCallback(cb, mainHandler)
                    }
                }

                try {
                    mediaProjection?.createVirtualDisplay(
                        "PersistentScreenCapture",
                        width,
                        height,
                        density,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.surface,
                        null,
                        null
                    ).also { currentVirtualDisplay = it }
                    persistentDisplayCreated = currentVirtualDisplay != null
                    if (persistentDisplayCreated) {
                        persistentWidth = width
                        persistentHeight = height
                        persistentDensity = density
                    }
                    Log.d(TAG, "Persistent virtual display created: $persistentDisplayCreated")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create persistent virtual display", e)
                    currentImageReader?.close()
                    currentImageReader = null
                    persistentDisplayCreated = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while setting up persistent virtual display", e)
            }
        }
    }

    fun release() {
        currentVirtualDisplay?.release()
        currentImageReader?.close()
        // Unregister callback to avoid leaks
        try {
            projectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
                Log.d(TAG, "MediaProjection callback unregistered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering MediaProjection callback", e)
        }
        mediaProjection?.stop()
        mediaProjection = null
        projectionCallback = null
        persistentDisplayCreated = false
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

            // First compress into memory so we can both write and optionally send the bytes directly
            val baos = ByteArrayOutputStream()
            val compressedOk = bitmapToSave.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val imageBytes = baos.toByteArray()

            // Check user preference: whether to save screenshots to device storage
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
                // Preference disables saving to device; still keep bytes for analysis
                Log.d(TAG, "Skipping saving screenshot to device (user preference)")
            }

            // Enqueue bytes directly for analysis if enabled
            try {
                if (prefs.isOpenAIAnalysisEnabled()) {
                    // Use AnalyzerAgent for structured batch processing with 3-frame windows
                    AnalyzerAgent.enqueueImageBytes(context, imageBytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue image bytes for analysis", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap", e)
            e.printStackTrace()
        } finally {
            // Recycle scaled bitmap if we created one
            try {
                scaledBitmap?.recycle()
            } catch (_: Exception) {
            }
        }

        return savedUriString
    }
}