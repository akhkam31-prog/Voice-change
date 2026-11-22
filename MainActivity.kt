package com.example.voicechanger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val settings = VoiceSettings()
    private val engine = VoiceEngine(settings)
    private var isRunning by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startEngine()
        } else {
            Toast.makeText(this, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceScreen(
                        settings = settings,
                        running = isRunning,
                        onToggle = { toggle() }
                    )
                }
            }
        }
    }

    // Микрофон в фоне Android 14 всё равно отберёт — останавливаемся сами
    override fun onStop() {
        super.onStop()
        if (isRunning) stopEngine()
    }

    private fun toggle() {
        when {
            isRunning -> stopEngine()
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> startEngine()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startEngine() {
        isRunning = engine.start()
    }

    private fun stopEngine() {
        engine.stop()
        isRunning = false
    }
}

@Composable
fun VoiceScreen(settings: VoiceSettings, running: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Voice Changer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Используй наушники. Без них микрофон поймает динамик и получится эхо-петля.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Остановить" else "Запустить")
        }

        Text("Пресеты", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Preset.entries.forEach { p ->
                OutlinedButton(onClick = { settings.applyPreset(p) }) {
                    Text(p.title)
                }
            }
        }
        OutlinedButton(onClick = { settings.clearAll() }) {
            Text("Выключить всё")
        }

        EffectCard(
            title = "Noise reduction — шумодав",
            hint = "Фильтр 80 Гц и шумовой гейт. Убирает фон.",
            enabled = settings.noiseOn,
            onEnabledChange = { settings.noiseOn = it }
        ) {
            SliderRow("Сила", settings.noiseAmount, 0f..1f) { settings.noiseAmount = it }
        }

        EffectCard(
            title = "Pitch shifting — высота голоса",
            hint = "Полутоны: плюс выше, минус ниже.",
            enabled = settings.pitchOn,
            onEnabledChange = { settings.pitchOn = it }
        ) {
            SliderRow("Полутоны", settings.pitchSemitones, -12f..12f) { settings.pitchSemitones = it }
        }

        EffectCard(
            title = "Formant shifting — тембр",
            hint = "Двигает форманты, высота тона остаётся.",
            enabled = settings.formantOn,
            onEnabledChange = { settings.formantOn = it }
        ) {
            SliderRow("Коэффициент", settings.formantRatio, 0.7f..1.4f) { settings.formantRatio = it }
        }

        EffectCard(
            title = "Mild distortion — мультяшность",
            hint = "Лёгкое искажение, голос становится мультяшным.",
            enabled = settings.distortionOn,
            onEnabledChange = { settings.distortionOn = it }
        ) {
            SliderRow("Сила", settings.distortionAmount, 0f..1f) { settings.distortionAmount = it }
        }

        EffectCard(
            title = "Robot — ring modulator",
            hint = "Металлический роботизированный голос.",
            enabled = settings.robotOn,
            onEnabledChange = { settings.robotOn = it }
        ) {
            SliderRow("Частота, Гц", settings.robotHz, 30f..300f) { settings.robotHz = it }
        }

        EffectCard(
            title = "Low-pass — как через капюшон",
            hint = "Приглушает высокие частоты.",
            enabled = settings.lowPassOn,
            onEnabledChange = { settings.lowPassOn = it }
        ) {
            SliderRow("Срез, Гц", settings.lowPassHz, 200f..8000f) { settings.lowPassHz = it }
        }

        EffectCard(
            title = "Band-pass — из-под ткани",
            hint = "Оставляет только середину диапазона.",
            enabled = settings.bandPassOn,
            onEnabledChange = { settings.bandPassOn = it }
        ) {
            SliderRow("Центр, Гц", settings.bandPassHz, 300f..3000f) { settings.bandPassHz = it }
        }

        EffectCard(
            title = "Bass boost — более глухое звучание",
            hint = "Поднимает низкие частоты.",
            enabled = settings.bassOn,
            onEnabledChange = { settings.bassOn = it }
        ) {
            SliderRow("Усиление, дБ", settings.bassDb, 0f..12f) { settings.bassDb = it }
        }

        EffectCard(
            title = "Treble reduction — без писклявости",
            hint = "Срезает верхние частоты.",
            enabled = settings.trebleOn,
            onEnabledChange = { settings.trebleOn = it }
        ) {
            SliderRow("Срез, дБ", settings.trebleDb, -18f..0f) { settings.trebleDb = it }
        }

        EffectCard(
            title = "Эхо",
            hint = "Задержка 300 мс с повторами.",
            enabled = settings.echoOn,
            onEnabledChange = { settings.echoOn = it }
        ) {
            SliderRow("Уровень", settings.echoMix, 0f..1f) { settings.echoMix = it }
        }

        Spacer(Modifier.height(8.dp))
        Text("Громкость: ${"%.2f".format(settings.volume)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = settings.volume,
            onValueChange = { settings.volume = it },
            valueRange = 0f..2f
        )
    }
}

@Composable
private fun EffectCard(
    title: String,
    hint: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            if (enabled) content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}
