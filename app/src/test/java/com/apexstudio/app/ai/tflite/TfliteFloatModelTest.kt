package com.apexstudio.app.ai.tflite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TfliteFloatModelTest {
    @Test fun tensorElementCountUsesExactShapeProduct() {
        assertEquals(24, TfliteFloatModel.elementCount(intArrayOf(2, 3, 4)))
        assertEquals(TfliteFloatModel.MAX_TENSOR_ELEMENTS,
            TfliteFloatModel.elementCount(intArrayOf(4_096, 4_096)))
        assertEquals(12, TfliteFloatModel.tensorBufferElementCount(intArrayOf(2, 3), intArrayOf(2, 3)))
    }

    @Test fun overflowAndUnboundedTensorsAreRejectedBeforeBufferAllocation() {
        assertRejected { TfliteFloatModel.elementCount(intArrayOf(Int.MAX_VALUE, 2)) }
        assertRejected { TfliteFloatModel.elementCount(intArrayOf(5_000, 5_000)) }
        assertRejected { TfliteFloatModel.elementCount(intArrayOf(1, 0, 2)) }
        assertRejected { TfliteFloatModel.elementCount(intArrayOf()) }
    }

    @Test fun combinedInputAndOutputBuffersCannotExceedResidentMemoryBudget() {
        assertEquals(TfliteFloatModel.MAX_TOTAL_BUFFER_ELEMENTS,
            TfliteFloatModel.tensorBufferElementCount(intArrayOf(4_096, 2_048), intArrayOf(4_096, 2_048)))
        assertRejected {
            TfliteFloatModel.tensorBufferElementCount(intArrayOf(4_096, 4_096), intArrayOf(1, 1))
        }
        assertRejected {
            TfliteFloatModel.tensorBufferElementCount(intArrayOf(1, 1), intArrayOf(4_096, 4_096))
        }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected unsafe tensor shape to be rejected")
        } catch (e: RuntimeException) {
            assertTrue(e is IllegalArgumentException || e is ArithmeticException)
        }
    }
}
