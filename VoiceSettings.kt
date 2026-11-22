package com.example.voicechanger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Preset(val title: String) {
    HOOD("Капюшон"),
    FABRIC("Из-под ткани"),
    CARTOON("Мультяшный"),
    ROBOT("Робот"),
    DEEP("Низкий голос")
}

class VoiceSettings {
    var noiseOn by mutableStateOf(false)
    var noiseAmount by mutableFloatStateOf(0.5f)

    var pitchOn by mutableStateOf(false)
    var pitchSemitones by mutableFloatStateOf(0f)

    var formantOn by mutableStateOf(false)
    var formantRatio by mutableFloatStateOf(1f)

    var distortionOn by mutableStateOf(false)
    var distortionAmount by mutableFloatStateOf(0.3f)

    var robotOn by mutableStateOf(false)
    var robotHz by mutableFloatStateOf(80f)

    var lowPassOn by mutableStateOf(false)
    var lowPassHz by mutableFloatStateOf(800f)

    var bandPassOn by mutableStateOf(false)
    var bandPassHz by mutableFloatStateOf(1200f)

    var bassOn by mutableStateOf(false)
    var bassDb by mutableFloatStateOf(6f)

    var trebleOn by mutableStateOf(false)
    var trebleDb by mutableFloatStateOf(-8f)

    var echoOn by mutableStateOf(false)
    var echoMix by mutableFloatStateOf(0.3f)

    var volume by mutableFloatStateOf(1f)

    fun clearAll() {
        noiseOn = false
        pitchOn = false
        formantOn = false
        distortionOn = false
        robotOn = false
        lowPassOn = false
        bandPassOn = false
        bassOn = false
        trebleOn = false
        echoOn = false
    }

    fun applyPreset(p: Preset) {
        clearAll()
        when (p) {
            Preset.HOOD -> {
                lowPassOn = true; lowPassHz = 700f
                trebleOn = true; trebleDb = -8f
                bassOn = true; bassDb = 4f
            }
            Preset.FABRIC -> {
                bandPassOn = true; bandPassHz = 1000f
                distortionOn = true; distortionAmount = 0.15f
            }
            Preset.CARTOON -> {
                pitchOn = true; pitchSemitones = 5f
                formantOn = true; formantRatio = 1.15f
                distortionOn = true; distortionAmount = 0.2f
            }
            Preset.ROBOT -> {
                robotOn = true; robotHz = 80f
            }
            Preset.DEEP -> {
                pitchOn = true; pitchSemitones = -4f
                formantOn = true; formantRatio = 0.85f
                bassOn = true; bassDb = 6f
            }
        }
    }
}
