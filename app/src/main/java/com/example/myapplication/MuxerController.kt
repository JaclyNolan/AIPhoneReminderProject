package com.example.myapplication

import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

class MuxerController(
    outputFile: File,
    private val expectAudio: Boolean
) {
    enum class TrackType { VIDEO, AUDIO }

    private val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    @Volatile private var started = false
    @Volatile private var released = false

    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1

    private val lock = Object()

    fun addVideoTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (released) throw IllegalStateException("Muxer already released")
            if (videoTrackIndex != -1) return videoTrackIndex
            videoTrackIndex = muxer.addTrack(format)
            maybeStartLocked()
            return videoTrackIndex
        }
    }

    fun addAudioTrack(format: MediaFormat): Int {
        synchronized(lock) {
            if (released) throw IllegalStateException("Muxer already released")
            if (audioTrackIndex != -1) return audioTrackIndex
            audioTrackIndex = muxer.addTrack(format)
            maybeStartLocked()
            return audioTrackIndex
        }
    }

    private fun maybeStartLocked() {
        if (!started && videoTrackIndex != -1 && (!expectAudio || audioTrackIndex != -1)) {
            muxer.start()
            started = true
            lock.notifyAll()
            Log.d("MuxerController", "Muxer started (expectAudio=$expectAudio)")
        }
    }

    fun writeSampleData(type: TrackType, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            while (!started && !released) {
                try { lock.wait(50) } catch (_: InterruptedException) {}
            }
            if (released || !started) return
            val track = when (type) {
                TrackType.VIDEO -> videoTrackIndex
                TrackType.AUDIO -> audioTrackIndex
            }
            if (track >= 0 && info.size > 0) {
                muxer.writeSampleData(track, buffer, info)
            }
        }
    }

    fun stopAndRelease() {
        synchronized(lock) {
            if (released) return
            try {
                if (started) muxer.stop()
            } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            released = true
            started = false
            lock.notifyAll()
        }
    }
}

