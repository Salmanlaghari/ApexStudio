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
    inputShape: IntArray,
    outputShape: IntArray
) : Closeable {
    // Do not let callers mutate the interpreter's checked shapes through the public IntArray properties.
    private val checkedInputShape = inputShape.copyOf()
    private val checkedOutputShape = outputShape.copyOf()
    val inputShape: IntArray get() = checkedInputShape.copyOf()
    val outputShape: IntArray get() = checkedOutputShape.copyOf()
    private var closed = false

    @Synchronized
    fun run(input: FloatArray): FloatArray {
        check(!closed) { "TFLite model is closed" }
        val expectedInput = elementCount(checkedInputShape)
        require(input.size == expectedInput) {
            "Expected $expectedInput input values for ${checkedInputShape.contentToString()}, got ${input.size}."
        }
        val inBytes = Math.multiplyExact(input.size, Float.SIZE_BYTES)
        val inBuffer = ByteBuffer.allocateDirect(inBytes).order(ByteOrder.nativeOrder())
        input.forEach(inBuffer::putFloat)
        inBuffer.rewind()
        val nOut = elementCount(checkedOutputShape)
        val outBuffer = ByteBuffer.allocateDirect(Math.multiplyExact(nOut, Float.SIZE_BYTES)).order(ByteOrder.nativeOrder())
        interpreter.run(inBuffer, outBuffer)
        outBuffer.rewind()
        return FloatArray(nOut) { outBuffer.float }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            interpreter.close()
        }
    }

    companion object {
        /** Bound each float tensor to 16M values (64 MiB), before any direct-buffer allocation. */
        internal const val MAX_TENSOR_ELEMENTS: Int = 16_777_216

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
                elementCount(inputShape)
                elementCount(outputShape)
                return TfliteFloatModel(interpreter, inputShape, outputShape)
            } catch (e: Exception) {
                interpreter.close()
                throw e
            }
        }

        internal fun elementCount(shape: IntArray): Int {
            require(shape.isNotEmpty() && shape.all { it > 0 }) { "Tensor dimensions must be positive" }
            var count = 1
            for (dimension in shape) {
                count = Math.multiplyExact(count, dimension)
                require(count <= MAX_TENSOR_ELEMENTS) {
                    "Tensor exceeds the safe $MAX_TENSOR_ELEMENTS-element limit"
                }
            }
            return count
        }
    }
}
