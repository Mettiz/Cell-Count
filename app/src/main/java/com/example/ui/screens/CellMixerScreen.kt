package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.CellSampleItem
import com.example.model.CountUnitMultiplier
import com.example.model.MixerState
import com.example.model.NumberFormatter
import com.example.model.VolumeUnit
import com.example.ui.components.LabNumericField
import com.example.ui.components.QuickUnitSwitchBar
import com.example.ui.components.adaptiveMonospaceFontSize
import com.example.ui.theme.BioIndigoPrimary
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
    onToggleSampleVolumeUnit: (Int) -> Unit,
    onToggleSampleCountUnit: (Int) -> Unit,
    onSetAllMixerVolumeUnits: (VolumeUnit) -> Unit,
    onSetAllMixerCountUnits: (CountUnitMultiplier) -> Unit,
    onSampleCountChanged: (Int, String) -> Unit,
    onSampleLabelChanged: (Int, String) -> Unit,
    onExtraMediaChanged: (String) -> Unit,
    onToggleExtraMediaUnit: () -> Unit,
    onTransferMixToDilution: () -> Unit,
    onSaveMixToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val sampleColors = listOf(
        CellCyan,
        BioIndigoPrimary,
        SerumAmber,
        MediaCoral,
        ViableGreen
    )

    val activeGlobalVolUnit = if (
        state.samples.all { it.volumeUnit == state.outputVolumeUnit } &&
        state.extraMediaUnit == state.outputVolumeUnit
    ) state.outputVolumeUnit else null

    val activeGlobalCountUnit = if (
        state.samples.all { it.countUnit == state.unitMultiplier }
    ) state.unitMultiplier else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. GLOBAL UNIT CONVERSION BAR FOR MIXER (mL ⇄ L | میلیون ⇄ عدد کامل)
        QuickUnitSwitchBar(
            activeVolumeUnit = activeGlobalVolUnit,
            onSelectVolumeUnit = onSetAllMixerVolumeUnits,
            activeCountUnit = activeGlobalCountUnit,
            onSelectCountUnit = onSetAllMixerCountUnits
        )

        // 2. LIVE MIX RESULT SUMMARY CARD
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MergeType,
                            contentDescription = "نتیجه میکس سلولی",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "نتیجه میکس (${state.samples.size} نمونه)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetAllMixerCountUnits(state.unitMultiplier.toggle()) }
                    ) {
                        Text(
                            text = "${state.unitMultiplier.shortLabel} ⇄",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 1: Full-width Final Mixed Concentration so long numbers are 100% readable
                MixMetricBox(
                    title = "کانت نهایی میکس",
                    numberText = NumberFormatter.formatCountForUnitWithCommas(state.mixedCountRawPerMl, state.unitMultiplier),
                    unitText = state.unitMultiplier.shortLabel,
                    subText = NumberFormatter.formatOppositeCountHelper(state.mixedCountRawPerMl, state.unitMultiplier),
                    accentColor = BioIndigoPrimary,
                    onClick = { onSetAllMixerCountUnits(state.unitMultiplier.toggle()) },
                    testTag = "mix_result_count",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Total Mix Volume & Total Cells in spacious 2-column grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MixMetricBox(
                        title = "حجم کل میکس",
                        numberText = NumberFormatter.formatWithCommas(state.outputVolumeUnit.fromMl(state.totalFinalMixVolumeMl), 4),
                        unitText = state.outputVolumeUnit.symbol,
                        subText = NumberFormatter.formatOppositeUnitHelper(state.totalFinalMixVolumeMl, state.outputVolumeUnit),
                        accentColor = CellCyan,
                        onClick = { onSetAllMixerVolumeUnits(state.outputVolumeUnit.toggle()) },
                        testTag = "mix_result_volume",
                        modifier = Modifier.weight(1f)
                    )
                    MixMetricBox(
                        title = "کل سلول‌ها",
                        numberText = NumberFormatter.formatCountForUnitWithCommas(state.totalAbsoluteCells, state.unitMultiplier),
                        unitText = state.unitMultiplier.shortLabel,
                        subText = NumberFormatter.formatOppositeCountHelper(state.totalAbsoluteCells, state.unitMultiplier),
                        accentColor = SerumAmber,
                        onClick = { onSetAllMixerCountUnits(state.unitMultiplier.toggle()) },
                        testTag = "mix_result_total_cells",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onTransferMixToDilution()
                            Toast.makeText(
                                context,
                                "حجم و کانت میکس به بخش رقت و سرم منتقل شد",
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
                            text = "محاسبه مدیا و سرم برای میکس",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onSaveMixToHistory()
                            Toast.makeText(context, "نتیجه میکس ذخیره شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("btn_save_mix")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "ذخیره",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ذخیره")
                    }
                }
            }
        }

        // 3. DYNAMIC LIST OF CELL SAMPLES
        state.samples.forEachIndexed { idx, sample ->
            val accent = sampleColors[idx % sampleColors.size]
            val cellSharePercent = if (state.totalAbsoluteCells > 0.0) {
                (sample.totalRawCells / state.totalAbsoluteCells) * 100.0
            } else 0.0
            val volumeSharePercent = if (state.totalFinalMixVolumeMl > 0.0) {
                (sample.volumeMl / state.totalFinalMixVolumeMl) * 100.0
            } else 0.0

            SampleMixerCard(
                index = idx + 1,
                sample = sample,
                accentColor = accent,
                cellSharePercent = cellSharePercent,
                volumeSharePercent = volumeSharePercent,
                canDelete = state.samples.size > 2,
                onVolumeChanged = { onSampleVolumeChanged(sample.id, it) },
                onToggleVolumeUnit = { onToggleSampleVolumeUnit(sample.id) },
                onCountChanged = { onSampleCountChanged(sample.id, it) },
                onToggleCountUnit = { onToggleSampleCountUnit(sample.id) },
                onLabelChanged = { onSampleLabelChanged(sample.id, it) },
                onDelete = { onRemoveSample(sample.id) }
            )
        }

        OutlinedButton(
            onClick = onAddSample,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_add_mixer_sample")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "افزودن نمونه جدید"
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "افزودن نمونه سلولی دیگر به میکس (+)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                LabNumericField(
                    value = state.extraMediaVolumeText,
                    onValueChange = onExtraMediaChanged,
                    label = "حجم مدیا / بافر خالی اضافه‌شده حین میکس (اختیاری)",
                    unitText = state.extraMediaUnit.symbol,
                    onUnitClick = onToggleExtraMediaUnit,
                    accentColor = MediaCoral,
                    helperText = NumberFormatter.formatOppositeUnitHelper(state.extraMediaMl, state.extraMediaUnit),
                    testTag = "input_mixer_extra_media"
                )
            }
        }
    }
}

@Composable
private fun MixMetricBox(
    title: String,
    numberText: String,
    unitText: String,
    subText: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val numFontSize = adaptiveMonospaceFontSize(numberText, baseSp = 15.5f, minSp = 11f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header row: Title on Right + Unit Badge on Left
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$unitText ⇄",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Full-width row dedicated to the Number so it never goes under the unit
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = numberText,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = RobotoMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = numFontSize
                        ),
                        color = accentColor,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "= $subText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleMixerCard(
    index: Int,
    sample: CellSampleItem,
    accentColor: Color,
    cellSharePercent: Double,
    volumeSharePercent: Double,
    canDelete: Boolean,
    onVolumeChanged: (String) -> Unit,
    onToggleVolumeUnit: () -> Unit,
    onCountChanged: (String) -> Unit,
    onToggleCountUnit: () -> Unit,
    onLabelChanged: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                    Spacer(modifier = Modifier.width(6.dp))
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LabNumericField(
                    value = sample.volumeText,
                    onValueChange = onVolumeChanged,
                    label = "حجم نمونه $index",
                    unitText = sample.volumeUnit.symbol,
                    onUnitClick = onToggleVolumeUnit,
                    accentColor = accentColor,
                    helperText = NumberFormatter.formatOppositeUnitHelper(sample.volumeMl, sample.volumeUnit),
                    testTag = "mixer_sample_volume_$index",
                    modifier = Modifier.weight(1f)
                )

                LabNumericField(
                    value = sample.countText,
                    onValueChange = onCountChanged,
                    label = "کانت نمونه $index",
                    unitText = sample.countUnit.shortLabel,
                    onUnitClick = onToggleCountUnit,
                    accentColor = accentColor,
                    helperText = NumberFormatter.formatOppositeCountHelper(sample.countRawPerMl, sample.countUnit),
                    testTag = "mixer_sample_count_$index",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سهم سلولی: ${NumberFormatter.formatWithCommas(cellSharePercent, 1)}٪",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "سهم حجمی: ${NumberFormatter.formatWithCommas(volumeSharePercent, 1)}٪",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (cellSharePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}
