package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.model.CultureVesselPreset
import com.example.model.NeubauerSquare
import com.example.model.NeubauerState
import com.example.model.NumberFormatter
import com.example.model.SavedCalculationRecord
import com.example.model.SeedingState
import com.example.ui.components.LabNumericField
import com.example.ui.components.TubeCompositionVisualBar
import com.example.ui.components.adaptiveMonospaceFontSize
import com.example.ui.theme.BioIndigoPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.DeadCellRed
import com.example.ui.theme.MediaCoral
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SerumAmber
import com.example.ui.theme.ViableGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeubauerCounterScreen(
    state: NeubauerState,
    onLiveCountChanged: (Int, String) -> Unit,
    onDeadCountChanged: (Int, String) -> Unit,
    onIncrementLive: (Int, Int) -> Unit,
    onIncrementDead: (Int, Int) -> Unit,
    onDilutionFactorChanged: (String) -> Unit,
    onSampleVolumeChanged: (String) -> Unit,
    onToggleSampleVolumeUnit: () -> Unit,
    onToggleResultCountUnit: () -> Unit,
    onResetCounts: () -> Unit,
    onSendToDilution: () -> Unit,
    onSendToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    Text(
                        text = "نتیجه شمارش لام نئوبار",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onResetCounts,
                        modifier = Modifier.testTag("btn_reset_neubauer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "صفر کردن",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صفر کردن")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 1: Full-width Viable Concentration Pill so long numbers are 100% readable
                NeubauerResultPill(
                    label = "کانت زنده (Viable Count)",
                    numberText = NumberFormatter.formatCountForUnitWithCommas(state.viableCellsPerMl, state.resultCountUnit),
                    unitText = "${state.resultCountUnit.shortLabel} ⇄",
                    sub = "= ${NumberFormatter.formatOppositeCountHelper(state.viableCellsPerMl, state.resultCountUnit)}",
                    color = BioIndigoPrimary,
                    onClick = onToggleResultCountUnit,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Viability % & Average per Square in spacious 2-column row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeubauerResultPill(
                        label = "درصد زنده‌مانی",
                        numberText = NumberFormatter.formatWithCommas(state.viabilityPercent, 1),
                        unitText = "%",
                        sub = "${state.totalLiveCellsCounted} زنده از ${state.totalAllCellsCounted} سلول",
                        color = ViableGreen,
                        modifier = Modifier.weight(1f)
                    )
                    NeubauerResultPill(
                        label = "میانگین هر خانه",
                        numberText = NumberFormatter.formatWithCommas(state.averageLivePerSquare, 2),
                        unitText = "سلول/خانه",
                        sub = "در ۴ خانه بزرگ لام",
                        color = SerumAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "کل سلول زنده در ${NumberFormatter.formatVolumeMlAndL(state.sampleVolumeMl, state.sampleVolumeUnit)}: ${NumberFormatter.formatScientificCount(state.totalViableCellsInSample)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSendToDilution()
                            Toast.makeText(
                                context,
                                "کانت نئوبار به بخش رقت و سرم منتقل شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_neubauer_to_dilution")
                    ) {
                        Text("انتقال به رقت و سرم")
                    }

                    OutlinedButton(
                        onClick = {
                            onSendToMixer()
                            Toast.makeText(
                                context,
                                "به لیست میکس سلولی اضافه شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_neubauer_to_mixer")
                    ) {
                        Text("افزودن به میکس")
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.trypanDilutionFactorText,
                        onValueChange = onDilutionFactorChanged,
                        label = "ضریب رقت تریپان بلو",
                        unitText = "برابر",
                        accentColor = BioIndigoPrimary,
                        helperText = "۱:۱ تریپان بلو = 2",
                        testTag = "input_trypan_factor",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.sampleVolumeText,
                        onValueChange = onSampleVolumeChanged,
                        label = "حجم سوسپانسیون فعلی",
                        unitText = state.sampleVolumeUnit.symbol,
                        onUnitClick = onToggleSampleVolumeUnit,
                        accentColor = CellCyan,
                        helperText = NumberFormatter.formatOppositeUnitHelper(state.sampleVolumeMl, state.sampleVolumeUnit),
                        testTag = "input_neubauer_sample_vol",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1" to "1x", "2" to "2x (۱:۱)", "4" to "4x", "5" to "5x", "10" to "10x").forEach { (factor, label) ->
                        FilterChip(
                            selected = state.trypanDilutionFactorText == factor,
                            onClick = { onDilutionFactorChanged(factor) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        state.squares.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { sq ->
                    NeubauerSquareCard(
                        square = sq,
                        onLiveChanged = { onLiveCountChanged(sq.index, it) },
                        onDeadChanged = { onDeadCountChanged(sq.index, it) },
                        onIncLive = { delta -> onIncrementLive(sq.index, delta) },
                        onIncDead = { delta -> onIncrementDead(sq.index, delta) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NeubauerResultPill(
    label: String,
    numberText: String,
    unitText: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val numFontSize = adaptiveMonospaceFontSize(numberText, baseSp = 15.5f, minSp = 11f)

    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .clickable { onClick() }
            } else Modifier
        ),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header row: Label on Right + Unit Badge on Left
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = unitText,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Dedicated full-width row for the Number
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
                        color = color,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NeubauerSquareCard(
    square: NeubauerSquare,
    onLiveChanged: (String) -> Unit,
    onDeadChanged: (String) -> Unit,
    onIncLive: (Int) -> Unit,
    onIncDead: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = null,
                    tint = BioIndigoPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = square.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LabNumericField(
                value = square.liveCountText,
                onValueChange = onLiveChanged,
                label = "سلول زنده",
                unitText = "عدد",
                accentColor = ViableGreen,
                testTag = "neubauer_live_${square.index}"
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ViableGreen.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onIncLive(1) },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_inc_live_${square.index}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "افزایش زنده", tint = ViableGreen)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onIncLive(-1) },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_dec_live_${square.index}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "کاهش زنده")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LabNumericField(
                value = square.deadCountText,
                onValueChange = onDeadChanged,
                label = "سلول مرده",
                unitText = "عدد",
                accentColor = DeadCellRed,
                testTag = "neubauer_dead_${square.index}"
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DeadCellRed.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onIncDead(1) },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_inc_dead_${square.index}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "افزایش مرده", tint = DeadCellRed)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onIncDead(-1) },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("btn_dec_dead_${square.index}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "کاهش مرده")
                    }
                }
            }
        }
    }
}

@Composable
fun SeedingAndHistoryScreen(
    seedingState: SeedingState,
    savedRecords: List<SavedCalculationRecord>,
    onSelectPreset: (CultureVesselPreset) -> Unit,
    onWellCountChanged: (String) -> Unit,
    onVolumePerWellChanged: (String) -> Unit,
    onToggleVolumePerWellUnit: () -> Unit,
    onTargetCellsPerWellChanged: (String) -> Unit,
    onToggleTargetCellsUnit: () -> Unit,
    onStockCountChanged: (String) -> Unit,
    onToggleStockCountUnit: () -> Unit,
    onSerumPercentChanged: (String) -> Unit,
    onOverfillPercentChanged: (String) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onOpenGrowthPredictor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = BioIndigoPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "محاسبه‌گر پلیت‌ریزی و فلاسک",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CultureVesselPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = seedingState.selectedPreset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(preset.title) },
                            modifier = Modifier.testTag("vessel_preset_${preset.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = seedingState.wellCountText,
                        onValueChange = onWellCountChanged,
                        label = "تعداد چاهک / فلاسک",
                        unitText = "عدد",
                        accentColor = BioIndigoPrimary,
                        testTag = "seeding_well_count",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = seedingState.volumePerWellText,
                        onValueChange = onVolumePerWellChanged,
                        label = "حجم هر چاهک",
                        unitText = seedingState.volumePerWellUnit.symbol,
                        onUnitClick = onToggleVolumePerWellUnit,
                        accentColor = MediaCoral,
                        helperText = NumberFormatter.formatOppositeUnitHelper(seedingState.volumePerWellMl, seedingState.volumePerWellUnit),
                        testTag = "seeding_vol_per_well",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = seedingState.targetCellsPerWellText,
                        onValueChange = onTargetCellsPerWellChanged,
                        label = "سلول در هر چاهک",
                        unitText = seedingState.targetCellsPerWellUnit.shortLabel,
                        onUnitClick = onToggleTargetCellsUnit,
                        accentColor = BioIndigoPrimary,
                        helperText = NumberFormatter.formatOppositeCountHelper(seedingState.targetCellsPerWellRaw, seedingState.targetCellsPerWellUnit),
                        testTag = "seeding_cells_per_well",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = seedingState.stockCountText,
                        onValueChange = onStockCountChanged,
                        label = "کانت استوک فعلی",
                        unitText = seedingState.stockCountUnit.shortLabel,
                        onUnitClick = onToggleStockCountUnit,
                        accentColor = CellCyan,
                        helperText = NumberFormatter.formatOppositeCountHelper(seedingState.stockCountRawPerMl, seedingState.stockCountUnit),
                        testTag = "seeding_stock_count",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = seedingState.serumPercentText,
                        onValueChange = onSerumPercentChanged,
                        label = "درصد سرم مسترمیکس",
                        unitText = "%",
                        accentColor = SerumAmber,
                        testTag = "seeding_serum_pct",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = seedingState.overfillPercentText,
                        onValueChange = onOverfillPercentChanged,
                        label = "درصد خطای پیپتینگ",
                        unitText = "%",
                        accentColor = ViableGreen,
                        helperText = "10% اضافه",
                        testTag = "seeding_overfill_pct",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenGrowthPredictor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_seeding_to_growth_predictor")
                ) {
                    Text("پیش‌بینی رشد سلول و زمان رسیدن به کانفلوئنسی برای این کشت")
                }
            }
        }

        TubeCompositionVisualBar(
            cellVolumeMl = seedingState.requiredCellStockVolumeMl,
            mediaVolumeMl = seedingState.requiredBasalMediaVolumeMl,
            serumVolumeMl = seedingState.requiredSerumVolumeMl,
            serumPercent = seedingState.serumPercent
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = BioIndigoPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "تاریخچه محاسبات ذخیره‌شده (${savedRecords.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (savedRecords.isEmpty()) {
                    Text(
                        text = "هنوز محاسبه‌ای ذخیره نشده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    savedRecords.forEachIndexed { idx, rec ->
                        if (idx > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rec.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = rec.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = rec.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteRecord(rec.id) },
                                modifier = Modifier.testTag("btn_delete_record_${rec.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف از تاریخچه",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
