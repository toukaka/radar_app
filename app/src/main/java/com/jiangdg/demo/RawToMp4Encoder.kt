package com.jiangdg.demo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.FileInputStream
import android.util.Log



class RawToMp4Encoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int = 30,
) {
    private val TAG = "RawToMp4Encoder"
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    val timestamp = sdf.format(Date())
    private val rawFilePath = "/storage/emulated/0/Android/data/com.jiangdg.ausbc/files/raw_video.rgb"
    private val outputPath = "/storage/emulated/0/DCIM/easycam360/easycam_video_$timestamp.mp4"

    fun encode() {
        val rawFile = File(rawFilePath)

        // Basic checks before processing
        if (!rawFile.exists()) {
            Log.e(TAG, "Raw video file does not exist: $rawFilePath")
            return
        }
        val yuvBufferSize = width * height * 3 / 2
        val rgbFrameSize = width * height * 3
        val fileSize = rawFile.length()
        if (fileSize == 0L || fileSize % rgbFrameSize.toLong() != 0L) {
            Log.e(TAG, "Raw video file size is inconsistent. Size: $fileSize bytes, Frame size: $rgbFrameSize bytes")
            return
        }
        val inputStream = FileInputStream(File(rawFilePath))

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val outputFile = File(outputPath)
        if (outputFile.exists()) outputFile.delete()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        var frameCount = 0
        val rgbBuffer = ByteArray(rgbFrameSize)

        while (inputStream.read(rgbBuffer) == rgbFrameSize) {
            val yuv = rgbToNv12(rgbBuffer, width, height)

            val inputBufferIndex = codec.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(yuv)
                val pts = computePresentationTime(frameCount, fps)
                codec.queueInputBuffer(inputBufferIndex, 0, yuv.size, pts, 0)
                frameCount++
            }

            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                val encodedData = codec.getOutputBuffer(outputBufferIndex) ?: continue

                if (!muxerStarted) {
                    val outputFormat = codec.outputFormat
                    trackIndex = muxer.addTrack(outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                if (bufferInfo.size > 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        // Send end-of-stream
        val inputIndex = codec.dequeueInputBuffer(10000)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, computePresentationTime(frameCount, fps), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }

        // Drain final buffers
        var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
        while (outIndex >= 0) {
            val encodedData = codec.getOutputBuffer(outIndex)
            if (bufferInfo.size > 0 && encodedData != null && muxerStarted) {
                encodedData.position(bufferInfo.offset)
                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
            }
            codec.releaseOutputBuffer(outIndex, false)
            outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
        }

        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
        inputStream.close()

        Log.i(TAG, "Video encoded to $outputPath")
        val file_ = File("$rawFilePath")
        if (file_.exists()) {
            val deleted = file_.delete()
            if (deleted) {
                Log.d("FileDelete", "File deleted successfully")
            } else {
                Log.e("FileDelete", "Failed to delete file")
            }
        }
    }

    private fun computePresentationTime(frameIndex: Int, fps: Int): Long {
        return 132L + frameIndex * 6_000_000L / fps
    }

    // Converts RGB24 to YUV420 semi-planar (I420)
    private fun rgbToNv12(rgb: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val yuv = ByteArray(frameSize * 3 / 2)
    
        var yIndex = 0
        var uvIndex = frameSize
    
        for (j in 0 until height) {
            for (i in 0 until width) {
                val index = (j * width + i) * 3
                val r = rgb[index].toInt() and 0xff
                val g = rgb[index + 1].toInt() and 0xff
                val b = rgb[index + 2].toInt() and 0xff
    
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
    
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
    
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    
        return yuv
    }
}
