package com.example.voicechanger

import kotlin.math.*

// ============================================================
//  FFT (radix-2, in-place)
// ============================================================
object FFT {
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val ang = (if (inverse) 2.0 else -2.0) * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                start += len
            }
            len = len shl 1
        }
        if (inverse) {
            for (i in 0 until n) {
                re[i] = re[i] / n
                im[i] = im[i] / n
            }
        }
    }
}

// ============================================================
//  Biquad (RBJ cookbook): LPF, HPF, BPF, low/high shelf
// ============================================================
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    private fun setCoeffs(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun lowPass(fs: Double, f0: Double, q: Double) {
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val al = sin(w) / (2 * q)
        setCoeffs((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }

    fun highPass(fs: Double, f0: Double, q: Double) {
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val al = sin(w) / (2 * q)
        setCoeffs((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
    }

    fun bandPass(fs: Double, f0: Double, q: Double) {
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val al = sin(w) / (2 * q)
        setCoeffs(al, 0.0, -al, 1 + al, -2 * c, 1 - al)
    }

    fun lowShelf(fs: Double, f0: Double, dB: Double) {
        val a = 10.0.pow(dB / 40)
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * al
        setCoeffs(
            a * ((a + 1) - (a - 1) * c + sa),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - sa
        )
    }

    fun highShelf(fs: Double, f0: Double, dB: Double) {
        val a = 10.0.pow(dB / 40)
        val w = 2 * PI * f0 / fs
        val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * al
        setCoeffs(
            a * ((a + 1) + (a - 1) * c + sa),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - sa
        )
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}

// ============================================================
//  Pitch shifter: two-tap granular, keeps duration
//  ratio > 1 — голос выше, < 1 — ниже
// ============================================================
class PitchShifter {
    companion object {
        private const val SIZE = 4096
        private const val MASK = SIZE - 1
        private const val GRAIN = 2048.0
    }

    var ratio = 1.0
    private val buf = DoubleArray(SIZE)
    private var w = 0
    private var d1 = 0.0
    private var d2 = GRAIN / 2

    fun process(x: Double): Double {
        buf[w] = x
        val step = 1.0 - ratio
        d1 = wrap(d1 + step)
        d2 = wrap(d2 + step)
        val y = gain(d1) * read(d1) + gain(d2) * read(d2)
        w = (w + 1) and MASK
        return y
    }

    private fun wrap(d: Double): Double {
        var v = d
        while (v < 0) v += GRAIN
        while (v >= GRAIN) v -= GRAIN
        return v
    }

    private fun gain(d: Double): Double = 0.5 - 0.5 * cos(2 * PI * d / GRAIN)

    private fun read(d: Double): Double {
        var pos = w - d
        if (pos < 0) pos += SIZE
        val base = floor(pos)
        val i0 = base.toInt() and MASK
        val i1 = (i0 + 1) and MASK
        val f = pos - base
        return buf[i0] * (1 - f) + buf[i1] * f
    }
}

// ============================================================
//  Formant shifter: STFT + cepstral envelope warp.
//  Огибающая спектра (форманты) сдвигается, основной тон остаётся.
//  ratio > 1 — тембр "тоньше", < 1 — "толще/глубже"
// ============================================================
class FormantShifter {
    companion object {
        private const val N = 1024
        private const val MASK = N - 1
        private const val HOP = N / 4
        private const val LIFTER = 60
        private const val OLA_GAIN = 1.0 / 1.5   // сумма Hann² при перекрытии 75 %
    }

    var ratio = 1.0

    private val window = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / N) }
    private val inRing = DoubleArray(N)
    private val outRing = DoubleArray(N)
    private val re = DoubleArray(N)
    private val im = DoubleArray(N)
    private val logMag = DoubleArray(N)
    private val unitRe = DoubleArray(N)
    private val unitIm = DoubleArray(N)
    private val cep = DoubleArray(N)
    private val env = DoubleArray(N)
    private val newEnv = DoubleArray(N)
    private var cur = 0
    private var hopCount = 0

    fun process(x: Double): Double {
        inRing[cur] = x
        val next = (cur + 1) and MASK
        if (++hopCount == HOP) {
            hopCount = 0
            processFrame(next)
        }
        val y = outRing[next] * OLA_GAIN
        outRing[next] = 0.0
        cur = next
        return y
    }

    private fun processFrame(start: Int) {
        // 1. Окно + FFT
        for (i in 0 until N) {
            re[i] = inRing[(start + i) and MASK] * window[i]
            im[i] = 0.0
        }
        FFT.transform(re, im, false)

        // 2. Лог-амплитуда и единичный фазовый вектор
        for (k in 0 until N) {
            val m = hypot(re[k], im[k]).coerceAtLeast(1e-9)
            logMag[k] = ln(m)
            unitRe[k] = re[k] / m
            unitIm[k] = im[k] / m
        }

        // 3. Кепстр -> лифтер (только низкие квефренси = огибающая) -> обратно в спектр
        logMag.copyInto(re)
        im.fill(0.0)
        FFT.transform(re, im, false)
        val inv = 1.0 / N
        for (q in 0 until N) {
            cep[q] = if (q <= LIFTER || q >= N - LIFTER) re[q] * inv else 0.0
        }
        cep.copyInto(re)
        im.fill(0.0)
        FFT.transform(re, im, false)
        re.copyInto(env)

        // 4. Растягиваем огибающую: E'(k) = E(k / ratio)
        val half = N / 2
        for (k in 0..half) {
            val src = k / ratio
            newEnv[k] = if (src >= half) {
                env[half]
            } else {
                val i0 = floor(src).toInt()
                val f = src - i0
                env[i0] * (1 - f) + env[i0 + 1] * f
            }
        }
        for (k in 1 until half) newEnv[N - k] = newEnv[k]

        // 5. Новый спектр: тонкая структура (гармоники) + новая огибающая
        for (k in 0 until N) {
            val mag = exp(logMag[k] - env[k] + newEnv[k])
            re[k] = mag * unitRe[k]
            im[k] = mag * unitIm[k]
        }
        FFT.transform(re, im, true)

        // 6. Overlap-add с синтезирующим окном
        for (i in 0 until N) {
            outRing[(start + i) and MASK] += re[i] * window[i]
        }
    }
}

// ============================================================
//  Noise reduction: HPF (в EffectChain) + адаптивный шумовой гейт
// ============================================================
class NoiseGate {
    var amount = 0.5
    private var env = 0.0
    private var noiseFloor = 0.002
    private var gain = 1.0

    fun process(x: Double): Double {
        env += (abs(x) - env) * 0.001
        val threshold = noiseFloor * (2 + 6 * amount)
        val open = env > threshold
        // Пол шума подстраивается под тишину; во время речи почти не двигается
        noiseFloor += (env - noiseFloor) * (if (open) 0.000002 else 0.0001)
        if (noiseFloor < 0.0001) noiseFloor = 0.0001
        val target = if (open) 1.0 else 0.0
        gain += (target - gain) * (if (target > gain) 0.05 else 0.0008)
        return x * gain
    }
}

// ============================================================
//  Echo
// ============================================================
class Echo(sampleRate: Int) {
    private val size = sampleRate
    private val buf = DoubleArray(size)
    private val delay = (sampleRate * 0.3).toInt()
    private var w = 0
    var mix = 0.3

    fun process(x: Double): Double {
        val r = (w - delay + size) % size
        val d = buf[r]
        buf[w] = x + d * 0.4
        w = (w + 1) % size
        return x + d * mix
    }
}

// ============================================================
//  Цепочка эффектов
// ============================================================
class EffectChain(private val fs: Double) {
    private val rumble = Biquad()
    private val lowPass = Biquad()
    private val bandPass = Biquad()
    private val bass = Biquad()
    private val treble = Biquad()
    private val pitch = PitchShifter()
    private val formant = FormantShifter()
    private val gate = NoiseGate()
    private val echo = Echo(fs.toInt())

    private var noiseOn = false
    private var pitchOn = false
    private var formantOn = false
    private var distortionOn = false
    private var distortionK = 1.0
    private var distortionNorm = 1.0
    private var robotOn = false
    private var robotInc = 0.0
    private var robotPhase = 0.0
    private var lowOn = false
    private var bandOn = false
    private var bassOn = false
    private var trebleOn = false
    private var echoOn = false
    private var volume = 1.0

    /** Вызывается раз в блок (256 сэмплов) — переносит настройки из UI в DSP. */
    fun update(s: VoiceSettings) {
        noiseOn = s.noiseOn
        if (noiseOn) {
            rumble.highPass(fs, 80.0, 0.707)
            gate.amount = s.noiseAmount.toDouble()
        }

        pitchOn = s.pitchOn
        if (pitchOn) pitch.ratio = 2.0.pow(s.pitchSemitones.toDouble() / 12.0)

        formantOn = s.formantOn
        if (formantOn) formant.ratio = s.formantRatio.toDouble()

        distortionOn = s.distortionOn
        if (distortionOn) {
            distortionK = 1.0 + 4.0 * s.distortionAmount
            distortionNorm = tanh(distortionK)
        }

        robotOn = s.robotOn
        robotInc = 2 * PI * s.robotHz.toDouble() / fs

        lowOn = s.lowPassOn
        if (lowOn) lowPass.lowPass(fs, s.lowPassHz.toDouble(), 0.707)

        bandOn = s.bandPassOn
        if (bandOn) bandPass.bandPass(fs, s.bandPassHz.toDouble(), 1.2)

        bassOn = s.bassOn
        if (bassOn) bass.lowShelf(fs, 120.0, s.bassDb.toDouble())

        trebleOn = s.trebleOn
        if (trebleOn) treble.highShelf(fs, 3500.0, s.trebleDb.toDouble())

        echoOn = s.echoOn
        echo.mix = s.echoMix.toDouble()

        volume = s.volume.toDouble()
    }

    fun process(input: Double): Double {
        var x = input
        if (noiseOn) {
            x = rumble.process(x)
            x = gate.process(x)
        }
        if (pitchOn) x = pitch.process(x)
        if (formantOn) x = formant.process(x)
        if (distortionOn) x = tanh(distortionK * x) / distortionNorm
        if (robotOn) {
            x *= sin(robotPhase)
            robotPhase += robotInc
            if (robotPhase > 2 * PI) robotPhase -= 2 * PI
        }
        if (lowOn) x = lowPass.process(x)
        if (bandOn) x = bandPass.process(x)
        if (bassOn) x = bass.process(x)
        if (trebleOn) x = treble.process(x)
        if (echoOn) x = echo.process(x)
        return x * volume
    }
}
