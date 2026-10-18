package com.apexstudio.app.ai.tflite

import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal CPU-only TensorFlow Lite adapter for known single-input/single-output float32 models.
 * The caller must supply a model file and exact flat input values; no default model is bundled or fetched.
 * This intentionally rejects dynamic/non-float tensors rather than guessing preprocessing or output meaning.
 */
class TfliteFloatModel private constructor(
    private val interpreter: Interpreter,
    val inputShape: IntArray,
    val outputShape: IntArray
) : Closeable {
    fun run(input: FloatArray): FloatArray {
        check(input.size == elementCount(inputShape)) { "Expected ${elementCount(inputShape)} input values for ${inputShape.contentToString()}, got ${input.size}." }
        val inBuffer = ByteBuffer.allocateDirect(input.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        input.forEach(inBuffer::putFloat)
        inBuffer.rewind()
        val nOut = elementCount(outputShape)
        val outBuffer = ByteBuffer.allocateDirect(nOut * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        interpreter.run(inBuffer, outBuffer)
        outBuffer.rewind()
        return FloatArray(nOut) { outBuffer.float }
    }

    override fun close() = interpreter.close()

    companion object {
        fun open(modelFile: File, threads: Int = 2): TfliteFloatModel {
            require(threads in 1..8)
            if (!modelFile.isFile || modelFile.length() == 0L) throw IllegalStateException("TFLite model is not installed: ${modelFile.name}")
            val interpreter = try {
                Interpreter(modelFile, Interpreter.Options().setNumThreads(threads))
            } catch (e: Exception) {
                throw IllegalArgumentException("Could not load TFLite model ${modelFile.name}: ${e.message}", e)
            }
            try {
                require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1) {
                    "This adapter requires one input and one output tensor."
                }
                val inputTensor = interpreter.getInputTensor(0)
                val outputTensor = interpreter.getOutputTensor(0)
                require(inputTensor.dataType().name == "FLOAT32" && outputTensor.dataType().name == "FLOAT32") {
                    "This adapter supports float32 tensors only; model reports ${inputTensor.dataType()} → ${outputTensor.dataType()}."
                }
                val inputShape = inputTensor.shape()
                val outputShape = outputTensor.shape()
                require(inputShape.isNotEmpty() && outputShape.isNotEmpty() && inputShape.all { it > 0 } && outputShape.all { it > 0 }) {
                    "Dynamic or empty model tensor shapes are unsupported."
                }
                return TfliteFloatModel(interpreter, inputShape, outputShape)
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
        }

        private fun elementCount(shape: IntArray): Int = shape.fold(1) { n, d -> Math.multiplyExact(n, d) }
    }
}
