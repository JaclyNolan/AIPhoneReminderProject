package com.example.myapplication

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.util.concurrent.LinkedBlockingQueue

/**
 * VideoEncoder encodes Bitmap frames (ARGB_8888) into H.264 MP4.
 * Frames are queued via queueFrame() and consumed on a background thread.
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int = 30
) {
    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = "video/avc" // H.264
        private const val BIT_RATE = 8 * 1024 * 1024 // 8 Mbps
        private const val I_FRAME_INTERVAL = 1 // I-frame every 1 second
        private const val TIMEOUT_US = 10000L
    }

    private var mediaCodec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    private val frameQueue = LinkedBlockingQueue<FrameData>(60)
    private var encodingThread: Thread? = null
    @Volatile private var isEncoding = false

    private var startTimeNanos = 0L
    private var frameCount = 0L
    private var eosQueued = false

    private var muxerController: MuxerController? = null
    private var videoTrackAdded = false

    private data class FrameData(val bitmap: Bitmap, val timestampNanos: Long)

    fun start(outputFile: File, muxer: MuxerController) {
        try {
            muxerController = muxer
            startTimeNanos = System.nanoTime()
            frameCount = 0

            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            isEncoding = true

            encodingThread = Thread({
                try {
                    encodeLoop()
                } catch (t: Throwable) {
                    Log.e(TAG, "Encoding thread crashed", t)
                } finally {
                    try {
                        if (!eosQueued) queueEos()
                        // Drain until EOS output flag observed
                        drainEncoder(true)
                    } catch (_: Exception) {}
                    releaseInternal()
                }
            }, "VideoEncoderThread").also { it.start() }

            Log.d(TAG, "VideoEncoder started at ${frameRate}fps")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video encoder", e)
            releaseInternal()
        }
    }

    /** Queue a frame (copy is taken internally). */
    fun queueFrame(bitmap: Bitmap) {
        if (!isEncoding) return
        try {
            // Copy to avoid external mutations and ensure ARGB_8888
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val ts = System.nanoTime()
            if (!frameQueue.offer(FrameData(copy, ts))) {
                // Queue full: drop and recycle
                Log.w(TAG, "Frame queue full, dropping frame")
                copy.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue frame", e)
        }
    }

    fun stop() {
        isEncoding = false
        // Join encoding thread
        try {
            encodingThread?.join(1500)
        } catch (_: Exception) {}
        encodingThread = null

        // Clear any remaining frames
        while (true) {
            val f = frameQueue.poll() ?: break
            try { f.bitmap.recycle() } catch (_: Exception) {}
        }
        Log.d(TAG, "VideoEncoder stopped after encoding $frameCount frames")
    }

    private fun encodeLoop() {
        while (isEncoding || frameQueue.isNotEmpty()) {
            val frame = frameQueue.poll()
            if (frame != null) {
                try {
                    encodeFrame(frame)
                } finally {
                    try { frame.bitmap.recycle() } catch (_: Exception) {}
                }
            }
            // Drain output regularly
            drainEncoder(false)
        }
    }

    private fun queueEos() {
        val codec = mediaCodec ?: return
        if (eosQueued) return
        try {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                codec.queueInputBuffer(inIndex, 0, 0, (System.nanoTime() - startTimeNanos) / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                eosQueued = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue EOS", e)
        }
    }

    private fun encodeFrame(frame: FrameData) {
        val codec = mediaCodec ?: return
        try {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val inBuf = codec.getInputBuffer(inIndex) ?: return
                // Convert to YUV420 planar
                val yuv = convertBitmapToYUV420(frame.bitmap)
                inBuf.clear()
                inBuf.put(yuv)
                val ptsUs = (frame.timestampNanos - startTimeNanos) / 1000
                codec.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
                frameCount++
            }
        } catch (e: Exception) {
            Log.e(TAG, "encodeFrame failed", e)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        val mux = muxerController
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break else continue
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!videoTrackAdded) {
                        mux?.addVideoTrack(codec.outputFormat)
                        videoTrackAdded = true
                    }
                }
                outIndex >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        mux?.writeSampleData(MuxerController.TrackType.VIDEO, outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    /** Convert ARGB_8888 bitmap to YUV420 planar (I420). */
    private fun convertBitmapToYUV420(bitmap: Bitmap): ByteArray {
        val w = width
        val h = height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)

        val ySize = w * h
        val uvSize = ySize / 4
        val yuv = ByteArray(ySize + 2 * uvSize)
        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uvSize

        var index = 0
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[index++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                // ITU-R BT.601 conversion
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    private fun releaseInternal() {
        try {
            mediaCodec?.stop()
        } catch (_: Exception) {}
        try {
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null
    }
}
