package com.geocamera.app.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Decodes an input video, composites a fixed overlay bitmap onto every frame via OpenGL,
 * re-encodes as H.264/MP4, and writes the result to [outputFile]. The source audio track (if
 * any) is copied through unmodified (no re-encode). This follows Android's well-known
 * "extract-decode-edit-encode-mux" pattern (as used in Google's Grafika sample) with a static
 * image overlay in place of a live filter.
 *
 * Must be called from a background thread — this blocks until processing completes.
 */
object VideoOverlayProcessor {

    private const val TIMEOUT_US = 10_000L
    private const val MIME_VIDEO = "video/avc"
    private const val DEFAULT_BITRATE = 8_000_000

    private class MuxerState(val muxer: MediaMuxer, val audioFormat: MediaFormat?) {
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var started = false
    }

    fun process(context: Context, inputUri: Uri, outputFile: File, overlayBitmapFactory: (width: Int, height: Int) -> Bitmap) {
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(context, inputUri, null)
        val videoTrackIndex = selectTrack(videoExtractor, "video/")
        require(videoTrackIndex >= 0) { "No video track found in the selected file" }
        videoExtractor.selectTrack(videoTrackIndex)
        val inputFormat = videoExtractor.getTrackFormat(videoTrackIndex)

        val width = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val overlayBitmap = overlayBitmapFactory(width, height)
        val frameRate = if (inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE))
            inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE) else 30
        val rotation = if (inputFormat.containsKey(MediaFormat.KEY_ROTATION))
            inputFormat.getInteger(MediaFormat.KEY_ROTATION) else 0

        // Opened up front so its format is known before muxer.start() — MediaMuxer requires
        // every track to be added before starting, and no tracks can be added afterward.
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(context, inputUri, null)
        val audioTrackIndex = selectTrack(audioExtractor, "audio/")
        val hasAudio = audioTrackIndex >= 0
        if (hasAudio) audioExtractor.selectTrack(audioTrackIndex)
        val audioFormat = if (hasAudio) audioExtractor.getTrackFormat(audioTrackIndex) else null

        val outputFormat = MediaFormat.createVideoFormat(MIME_VIDEO, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, DEFAULT_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MIME_VIDEO)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderInputSurface = EncoderInputSurface(encoder.createInputSurface())
        encoder.start()

        encoderInputSurface.makeCurrent()
        val renderer = FrameRenderer()
        renderer.setup(overlayBitmap)

        val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputFormat, renderer.decoderSurface, null, 0)
        decoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if (rotation != 0) muxer.setOrientationHint(rotation)
        val muxerState = MuxerState(muxer, audioFormat)

        try {
            runVideoPipeline(videoExtractor, decoder, encoder, encoderInputSurface, renderer, muxerState, width, height)
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
            runCatching { encoder.stop() }
            encoder.release()
            renderer.release()
            encoderInputSurface.release()
            videoExtractor.release()
        }

        if (hasAudio && muxerState.started) {
            copyAudioSamples(audioExtractor, muxer, muxerState.audioTrackIndex)
        }
        audioExtractor.release()

        muxer.stop()
        muxer.release()
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun runVideoPipeline(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        encoderInputSurface: EncoderInputSurface,
        renderer: FrameRenderer,
        muxerState: MuxerState,
        width: Int,
        height: Int
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var encoderDone = false

        while (!encoderDone) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            if (!decoderDone) {
                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val doRender = bufferInfo.size != 0
                    decoder.releaseOutputBuffer(outIndex, doRender)
                    if (doRender) {
                        renderer.awaitNewImage()
                        encoderInputSurface.makeCurrent()
                        renderer.drawFrame(width, height)
                        encoderInputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                        encoderInputSurface.swapBuffers()
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderDone = true
                        encoder.signalEndOfInputStream()
                    }
                }
            }

            val encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxerState.videoTrackIndex = muxerState.muxer.addTrack(encoder.outputFormat)
                    if (muxerState.audioFormat != null) {
                        muxerState.audioTrackIndex = muxerState.muxer.addTrack(muxerState.audioFormat)
                    }
                    muxerState.muxer.start()
                    muxerState.started = true
                }
                encOutIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(encOutIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerState.started) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxerState.muxer.writeSampleData(muxerState.videoTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        encoderDone = true
                    }
                }
            }
        }
    }

    /**
     * Appends the source's audio samples into [muxer] using direct stream copy (no re-encode).
     * Written after all video samples; MediaMuxer does not require cross-track chronological
     * interleaving for a correct, playable output file.
     */
    private fun copyAudioSamples(extractor: MediaExtractor, muxer: MediaMuxer, audioTrack: Int) {
        if (audioTrack < 0) return
        val buffer = ByteBuffer.allocate(1_000_000)
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(audioTrack, buffer, bufferInfo)
            extractor.advance()
        }
    }
}
