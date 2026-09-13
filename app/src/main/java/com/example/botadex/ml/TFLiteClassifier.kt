package com.example.botadex.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteClassifier(context: Context) {

    private val interpreter: Interpreter
    private val inputSize: Int
    private val numClasses: Int

    init {
        val model = loadModelFile(context)
        interpreter = Interpreter(model)

        // Dynamically get input size and output classes from the model
        val inputShape = interpreter.getInputTensor(0).shape() // e.g., [1, 224, 224, 3]
        inputSize = inputShape[1]

        val outputShape = interpreter.getOutputTensor(0).shape() // e.g., [1, 3]
        numClasses = outputShape[1]
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        return try {
            val fileDescriptor = context.assets.openFd("botadex_model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        } catch (e: Exception) {
            throw RuntimeException("Model file not found. Make sure botadex_model.tflite is in assets/", e)
        }
    }

    /**
     * Classifies the given bitmap and returns the index of the predicted class
     * and the confidence score (probability).
     */
    fun classify(bitmap: Bitmap): Pair<Int, Float> {
        val input = convertBitmapToByteBuffer(bitmap)
        input.rewind()

        val output = Array(1) { FloatArray(numClasses) }

        try {
            interpreter.run(input, output)
        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(-1, 0f)
        }

        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val confidence = if (maxIndex != -1) output[0][maxIndex] else 0f

        return Pair(maxIndex, confidence)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            // Normalize to [0, 1] if required by your model
            buffer.putFloat(((pixel shr 16 and 0xFF) / 255f))
            buffer.putFloat(((pixel shr 8 and 0xFF) / 255f))
            buffer.putFloat(((pixel and 0xFF) / 255f))
        }

        return buffer
    }
}
