package com.example.myapplication

import android.content.Context
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class AudioEncoder(
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 2,
    private val bitRate: Int = 128_000,
) {
    companion object {
        private const val TAG = "AudioEncoder"
        private const val MIME_TYPE = "audio/mp4a-latm"
        private const val TIMEOUT_US = 10_000L
    }

    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var encodingThread: Thread? = null
    private var running = AtomicBoolean(false)

    private var muxerController: MuxerController? = null
    private var audioTrackAdded = false

    fun prepare(context: Context, mediaProjection: MediaProjection?): Boolean {
        return try {
            // Configure AAC encoder
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            codec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // Prepare AudioRecord: prefer playback capture on API 29+
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(sampleRate,
                if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(minBuf * 2, 32768)

            audioRecord = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                    val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .build()
                    AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                } else {
                    // Fallback to mic requires RECORD_AUDIO permission
                    val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        Log.w(TAG, "RECORD_AUDIO permission not granted; disabling audio")
                        null
                    } else {
                        AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                        )
                    }
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "AudioRecord creation failed due to SecurityException", se)
                null
            }

            if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord not initialized; proceeding without audio")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepare failed", e)
            release()
            false
        }
    }

    fun start(muxer: MuxerController) {
        val codec = this.codec ?: throw IllegalStateException("Call prepare() first")
        val record = this.audioRecord ?: throw IllegalStateException("Call prepare() first")
        muxerController = muxer
        running.set(true)

        encodingThread = Thread({
            try {
                record.startRecording()
                val inputBuffer = ByteArray(4096)
                val bufferInfo = MediaCodec.BufferInfo()
                var presentationTimeUs = 0L
                val frameDurationUs = (1024_000_000L / sampleRate) // AAC frame of 1024 samples per channel

                while (running.get()) {
                    val read = record.read(inputBuffer, 0, inputBuffer.size)
                    if (read <= 0) continue

                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: continue
                        inBuf.clear()
                        inBuf.put(inputBuffer, 0, read)
                        codec.queueInputBuffer(inIndex, 0, read, presentationTimeUs, 0)
                        presentationTimeUs += frameDurationUs
                    }

                    // Drain output
                    while (true) {
                        val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                        when {
                            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                if (!audioTrackAdded) {
                                    val format = codec.outputFormat
                                    muxerController?.addAudioTrack(format)
                                    audioTrackAdded = true
                                }
                            }
                            outIndex >= 0 -> {
                                val outBuf = codec.getOutputBuffer(outIndex)
                                if (outBuf != null && bufferInfo.size > 0) {
                                    muxerController?.writeSampleData(MuxerController.TrackType.AUDIO, outBuf, bufferInfo)
                                }
                                codec.releaseOutputBuffer(outIndex, false)
                            }
                        }
                    }
                }

                // queue EOS
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                // Final drain
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!audioTrackAdded) {
                            muxerController?.addAudioTrack(codec.outputFormat)
                            audioTrackAdded = true
                        }
                        continue
                    }
                    if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            muxerController?.writeSampleData(MuxerController.TrackType.AUDIO, outBuf, bufferInfo)
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (eos) break
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "audio encoding failed", t)
            } finally {
                try { record.stop() } catch (_: Exception) {}
                try { record.release() } catch (_: Exception) {}
                try { codec.stop() } catch (_: Exception) {}
                try { codec.release() } catch (_: Exception) {}
            }
        }, "AudioEncoderThread")
        encodingThread?.start()
    }

    fun stop() {
        running.set(false)
        try { encodingThread?.join(1500) } catch (_: Exception) {}
        encodingThread = null
    }

    fun release() {
        try { audioRecord?.release() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        audioRecord = null
        codec = null
    }
}
