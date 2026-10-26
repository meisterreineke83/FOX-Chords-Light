package io.github.lightsongbook.core

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pitch detection for the tuner (YIN algorithm) and note naming.
 * Works on 16-bit mono samples as delivered by the microphone.
 */
object Tuner {

    const val MIN_FREQUENCY = 60f   // below low B on a 5-string bass… plenty for guitar/ukulele/voice
    const val MAX_FREQUENCY = 1200f
    private const val THRESHOLD = 0.12f
    private const val MIN_RMS = 250.0 // ignore near-silence

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    data class Reading(
        val frequency: Float,
        /** e.g. "E" */
        val note: String,
        val octave: Int,
        /** deviation from the nearest note, -50..+50 */
        val cents: Int,
    )

    /** Fundamental frequency in Hz, or null if there is no clear tone. */
    fun detect(samples: ShortArray, count: Int = samples.size, sampleRate: Int): Float? {
        val n = minOf(count, samples.size)
        if (n < 256) return null
        var sumSq = 0.0
        for (i in 0 until n) sumSq += samples[i].toDouble() * samples[i]
        if (sqrt(sumSq / n) < MIN_RMS) return null

        val window = n / 2
        val tauMin = (sampleRate / MAX_FREQUENCY).toInt().coerceAtLeast(2)
        val tauMax = minOf((sampleRate / MIN_FREQUENCY).toInt(), window - 1)
        if (tauMax <= tauMin) return null

        val x = FloatArray(n) { samples[it].toFloat() }
        val d = FloatArray(tauMax + 2)
        for (tau in 1..tauMax + 1) {
            var sum = 0f
            for (j in 0 until window) {
                val diff = x[j] - x[j + tau]
                sum += diff * diff
            }
            d[tau] = sum
        }
        // cumulative mean normalised difference
        val cmnd = FloatArray(tauMax + 2)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += d[tau]
            cmnd[tau] = if (running == 0f) 1f else d[tau] * tau / running
        }
        var tau = tauMin
        while (tau <= tauMax) {
            if (cmnd[tau] < THRESHOLD) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                break
            }
            tau++
        }
        if (tau > tauMax) return null

        // parabolic interpolation around the minimum for sub-sample accuracy
        val better = if (tau in 1 until tauMax + 1) {
            val s0 = cmnd[tau - 1]
            val s1 = cmnd[tau]
            val s2 = cmnd[tau + 1]
            val denom = 2 * (2 * s1 - s2 - s0)
            if (abs(denom) > 1e-9f) tau + (s2 - s0) / denom else tau.toFloat()
        } else {
            tau.toFloat()
        }
        val f = sampleRate / better
        return f.takeIf { it in MIN_FREQUENCY..MAX_FREQUENCY }
    }

    fun reading(frequency: Float, a4: Float = 440f): Reading {
        val midiExact = 69 + 12 * log2(frequency / a4)
        val midi = midiExact.roundToInt()
        val cents = ((midiExact - midi) * 100).roundToInt().coerceIn(-50, 50)
        val name = NAMES[Chords.mod12(midi)]
        return Reading(frequency, name, midi / 12 - 1, cents)
    }

    /** Smooths jittery readings: the median of the last few frequencies. */
    class Smoother(private val size: Int = 5) {
        private val recent = ArrayDeque<Float>()

        fun add(f: Float?): Float? {
            if (f == null) {
                if (recent.isNotEmpty()) recent.removeFirst()
                return recent.sorted().getOrNull(recent.size / 2)
            }
            // a jump of more than a semitone starts over (new string plucked)
            val last = recent.lastOrNull()
            if (last != null && abs(12 * log2(f / last)) > 1f) recent.clear()
            recent.addLast(f)
            while (recent.size > size) recent.removeFirst()
            return recent.sorted()[recent.size / 2]
        }
    }
}
