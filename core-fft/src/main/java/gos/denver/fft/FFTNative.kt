package gos.denver.fft

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FFTNative(private val size: Int) : AutoCloseable {
    private val planPtr: Long
    private val isClosed = AtomicBoolean(false)
    private val lock = ReentrantLock()

    init {
        require(size > 0) { "FFT size must be positive, got $size" }
        require(size and (size - 1) == 0) { "FFT size must be a power of 2, got $size" }

        try {
            System.loadLibrary("fftnative")
            planPtr = createPlan(size).also { ptr ->
                require(ptr != 0L) { "Failed to create FFT plan" }
            }
        } catch (e: Exception) {
            error("Failed to load FFT native library")
        }
    }

    @Synchronized
    private external fun createPlan(size: Int): Long

    @Synchronized
    private external fun destroyPlan(planPtr: Long)

    @Synchronized
    private external fun forwardTransform(
        planPtr: Long,
        inArray: FloatArray,
        magArray: FloatArray,
        phaseArray: FloatArray,
    )

    @Synchronized
    private external fun inverseTransform(
        planPtr: Long,
        magArray: FloatArray,
        phaseArray: FloatArray,
        outArray: FloatArray,
    )

    public fun forward(input: FloatArray): Pair<FloatArray, FloatArray> = lock.withLock {
        checkNotClosed()

        require(input.size == size) {
            "Input size must be $size, got ${input.size}"
        }

        val magnitude = FloatArray(size / 2 + 1)
        val phase = FloatArray(size / 2 + 1)

        try {
            forwardTransform(planPtr, input, magnitude, phase)
        } catch (e: Exception) {
            error("Forward FFT transform failed")
        }

        return Pair(magnitude, phase)
    }

    public fun inverse(magnitude: FloatArray, phase: FloatArray): FloatArray = lock.withLock {
        checkNotClosed()

        val expectedSize = size / 2 + 1

        require(magnitude.size == expectedSize) {
            "Magnitude array must have size $expectedSize, got ${magnitude.size}"
        }
        require(phase.size == expectedSize) {
            "Phase array must have size $expectedSize, got ${phase.size}"
        }

        val output = FloatArray(size)

        try {
            inverseTransform(planPtr, magnitude, phase, output)
        } catch (e: Exception) {
            error("Inverse FFT transform failed")
        }

        return output
    }

    @Throws(IllegalStateException::class)
    private fun checkNotClosed() {
        check(!isClosed.get()) { "FFTNative instance has been closed" }
    }

    public override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            destroyPlan(planPtr)
        }
    }
}
