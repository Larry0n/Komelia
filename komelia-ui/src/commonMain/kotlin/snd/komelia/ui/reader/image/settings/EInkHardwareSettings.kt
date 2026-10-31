package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_hardware
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_hardware_description
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_hardware_unavailable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_mode_diagnostic
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_mode_system
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_mode_manual
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_delay
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_delay_ms
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_eink_count
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.EInkRefreshMode
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.common.components.AppSliderDefaults
import kotlin.math.roundToLong
import kotlin.math.roundToInt

data class EInkHardwareSettingsState(
    val supported: Boolean,
    val enabled: Boolean,
    val mode: EInkRefreshMode,
    val delayMillis: Long,
    val refreshCount: Int,
    val onEnabledChange: (Boolean) -> Unit,
    val onModeChange: (EInkRefreshMode) -> Unit,
    val onDelayChange: (Long) -> Unit,
    val onCountChange: (Int) -> Unit,
)

val LocalEInkHardwareSettings = staticCompositionLocalOf<EInkHardwareSettingsState?> { null }

@Composable
fun EInkHardwareSettings() {
    val settings = LocalEInkHardwareSettings.current ?: return
    if (!settings.supported && !settings.enabled) return
    Column {
        SwitchWithLabel(
            checked = settings.enabled,
            onCheckedChange = settings.onEnabledChange,
            enabled = settings.supported || settings.enabled,
            label = { Text(stringResource(Res.string.reader_eink_hardware)) },
            supportingText = {
                Text(stringResource(
                    if (settings.supported) Res.string.reader_eink_hardware_description
                    else Res.string.reader_eink_hardware_unavailable
                ))
            },
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
        if (settings.enabled && settings.supported) {
            Column(Modifier.padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(100.dp)) {
                        Text(stringResource(Res.string.reader_eink_delay), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(Res.string.reader_eink_delay_ms, settings.delayMillis),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Slider(
                        value = settings.delayMillis.toFloat(),
                        onValueChange = { settings.onDelayChange((it / 100).roundToLong() * 100) },
                        steps = 13,
                        valueRange = 100f..1500f,
                        colors = AppSliderDefaults.colors(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(100.dp)) {
                        Text(stringResource(Res.string.reader_eink_count), style = MaterialTheme.typography.labelLarge)
                        Text(settings.refreshCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = settings.refreshCount.toFloat(),
                        onValueChange = { settings.onCountChange(it.roundToInt()) },
                        steps = 3,
                        valueRange = 1f..5f,
                        colors = AppSliderDefaults.colors(),
                    )
                }
                Text(stringResource(Res.string.reader_eink_mode_diagnostic))
                FlowRow {
                    InputChip(
                        selected = settings.mode == EInkRefreshMode.MANUAL,
                        onClick = { settings.onModeChange(EInkRefreshMode.MANUAL) },
                        label = { Text(stringResource(Res.string.reader_eink_mode_manual)) },
                    )
                    InputChip(
                        selected = settings.mode == EInkRefreshMode.SYSTEM,
                        onClick = { settings.onModeChange(EInkRefreshMode.SYSTEM) },
                        label = { Text(stringResource(Res.string.reader_eink_mode_system)) },
                    )
                    InputChip(
                        selected = settings.mode == EInkRefreshMode.GU16,
                        onClick = { settings.onModeChange(EInkRefreshMode.GU16) },
                        label = { Text("GU16 (132)") },
                    )
                }
            }
        }
    }
}
