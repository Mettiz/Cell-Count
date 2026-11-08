package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CellSampleItem
import com.example.model.CountUnitMultiplier
import com.example.model.MixerState
import com.example.model.NumberFormatter
import com.example.ui.components.LabHeroBanner
import com.example.ui.components.LabNumericField
import com.example.ui.theme.BioTealPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.MediaCoral
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SerumAmber
import com.example.ui.theme.ViableGreen

@Composable
fun CellMixerScreen(
    state: MixerState,
    onAddSample: () -> Unit,
    onRemoveSample: (Int) -> Unit,
    onSampleVolumeChanged: (Int, String) -> Unit,
    onSampleCountChanged: (Int, String) -> Unit,
    onSampleLabelChanged: (Int, String) -> Unit,
    onExtraMediaChanged: (String) -> Unit,
    onUnitMultiplierSelected: (CountUnitMultiplier) -> Unit,
    onTransferMixToDilution: () -> Unit,
    onSaveMixToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val sampleColors = listOf(
        CellCyan,
        BioTealPrimary,
        SerumAmber,
        MediaCoral,
        ViableGreen
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LabHeroBanner(
            title = "میکس چند سوسپانسیون سلولی با حجم و کانت مختلف",
            subtitle = "هر تعداد فالکون یا فلاسک با کانت و حجم متفاوت را اضافه کنید تا کانت نهایی میکس، حجم کل و تعداد کل سلول‌ها به صورت آنی محاسبه شود.",
            badgeText = "فرمول میکس: C_mix = Σ(Cᵢ × Vᵢ) / ΣVᵢ"
        )

        // LIVE MIX RESULT SUMMARY CARD
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MergeType,
                            contentDescription = "نتیجه میکس سلولی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نتیجه نهایی میکس (${state.samples.size} نمونه)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = state.unitMultiplier.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main 3 Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MixMetricBox(
                        title = "کانت نهایی میکس",
                        mainValue = NumberFormatter.formatClean(state.mixedCountVal, 4),
                        subValue = state.unitMultiplier.shortLabel,
                        accentColor = BioTealPrimary,
                        testTag = "mix_result_count",
                        modifier = Modifier.weight(1.2f)
                    )
                    MixMetricBox(
                        title = "حجم کل میکس",
                        mainValue = "${NumberFormatter.formatClean(state.totalFinalMixVolumeMl, 3)} mL",
                        subValue = "${NumberFormatter.formatClean(state.totalFinalMixVolumeMl * 1000.0, 1)} µL",
                        accentColor = CellCyan,
                        testTag = "mix_result_volume",
                        modifier = Modifier.weight(1f)
                    )
                    MixMetricBox(
                        title = "کل سلول‌های میکس",
                        mainValue = NumberFormatter.formatClean(state.totalCellsScaled, 3),
                        subValue = NumberFormatter.formatScientificCount(state.totalAbsoluteCells),
                        accentColor = SerumAmber,
                        testTag = "mix_result_total_cells",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Send Mix to Dilution & Serum Calculator or Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onTransferMixToDilution()
                            Toast.makeText(
                                context,
                                "حجم و کانت میکس به بخش رقت و محاسبه سرم منتقل شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("btn_transfer_mix_to_dilution")
                    ) {
                        Text(
                            text = "محاسبه مدیا و سرم برای این میکس",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "انتقال به محاسبه سرم",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onSaveMixToHistory()
                            Toast.makeText(context, "نتیجه میکس ذخیره شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(0.85f)
                            .testTag("btn_save_mix")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "ذخیره میکس",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ذخیره")
                    }
                }
            }
        }

        // Unit Multiplier Selector for Mixer
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "واحد کانت نمونه‌های ورودی",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountUnitMultiplier.entries.forEach { mult ->
                        FilterChip(
                            selected = state.unitMultiplier == mult,
                            onClick = { onUnitMultiplierSelected(mult) },
                            label = { Text(mult.label) }
                        )
                    }
                }
            }
        }

        // DYNAMIC LIST OF CELL SAMPLES
        state.samples.forEachIndexed { idx, sample ->
            val accent = sampleColors[idx % sampleColors.size]
            val cellSharePercent = if (state.totalCellsScaled > 0.0) {
                (sample.totalCellsScaled / state.totalCellsScaled) * 100.0
            } else 0.0
            val volumeSharePercent = if (state.totalFinalMixVolumeMl > 0.0) {
                (sample.volumeMl / state.totalFinalMixVolumeMl) * 100.0
            } else 0.0

            SampleMixerCard(
                index = idx + 1,
                sample = sample,
                unitLabel = state.unitMultiplier.shortLabel,
                accentColor = accent,
                cellSharePercent = cellSharePercent,
                volumeSharePercent = volumeSharePercent,
                canDelete = state.samples.size > 2,
                onVolumeChanged = { onSampleVolumeChanged(sample.id, it) },
                onCountChanged = { onSampleCountChanged(sample.id, it) },
                onLabelChanged = { onSampleLabelChanged(sample.id, it) },
                onDelete = { onRemoveSample(sample.id) }
            )
        }

        // ADD NEW CELL SAMPLE BUTTON
        OutlinedButton(
            onClick = onAddSample,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_add_mixer_sample")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "افزودن نمونه جدید"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "افزودن نمونه سلولی دیگر به میکس (+)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // OPTIONAL EXTRA BLANK MEDIA IN MIX
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabNumericField(
                    value = state.extraMediaVolumeText,
                    onValueChange = onExtraMediaChanged,
                    label = "حجم محیط کشت / بافر بدون سلول اضافه‌شده حین میکس (اختیاری)",
                    unitText = "mL",
                    accentColor = MediaCoral,
                    helperText = "اگر هنگام شستشوی فالکون‌ها مدیای خالی هم به میکس اضافه کرده‌اید، حجم آن را وارد کنید تا در حجم نهایی میکس لحاظ شود.",
                    testTag = "input_mixer_extra_media"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MixMetricBox(
    title: String,
    mainValue: String,
    subValue: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mainValue,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = RobotoMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SampleMixerCard(
    index: Int,
    sample: CellSampleItem,
    unitLabel: String,
    accentColor: Color,
    cellSharePercent: Double,
    volumeSharePercent: Double,
    canDelete: Boolean,
    onVolumeChanged: (String) -> Unit,
    onCountChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = sample.label,
                        onValueChange = onLabelChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mixer_sample_label_$index")
                    )
                }

                if (canDelete) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("btn_delete_sample_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف نمونه",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LabNumericField(
                    value = sample.volumeText,
                    onValueChange = onVolumeChanged,
                    label = "حجم نمونه $index (V_$index)",
                    unitText = "mL",
                    accentColor = accentColor,
                    helperText = "معادل ${NumberFormatter.formatClean(sample.volumeMl * 1000.0, 1)} µL",
                    testTag = "mixer_sample_volume_$index",
                    modifier = Modifier.weight(1f)
                )

                LabNumericField(
                    value = sample.countText,
                    onValueChange = onCountChanged,
                    label = "کانت نمونه $index (C_$index)",
                    unitText = unitLabel,
                    accentColor = accentColor,
                    helperText = "سلول این نمونه: ${NumberFormatter.formatClean(sample.totalCellsScaled, 3)}",
                    testTag = "mixer_sample_count_$index",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سهم سلولی در میکس: ${NumberFormatter.formatClean(cellSharePercent, 1)}٪",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "سهم حجمی: ${NumberFormatter.formatClean(volumeSharePercent, 1)}٪",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (cellSharePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}
