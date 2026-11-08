package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CultureVesselPreset
import com.example.model.NeubauerSquare
import com.example.model.NeubauerState
import com.example.model.NumberFormatter
import com.example.model.SavedCalculationRecord
import com.example.model.SeedingState
import com.example.ui.components.LabHeroBanner
import com.example.ui.components.LabNumericField
import com.example.ui.components.TubeCompositionVisualBar
import com.example.ui.theme.BioTealPrimary
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LabHeroBanner(
            title = "شمارشگر لام نئوبار و درصد زنده‌مانی (Viability)",
            subtitle = "تعداد سلول‌های زنده (شفاف) و مرده (آبی با تریپان بلو) را در ۴ خانه لام نئوبار وارد یا با دکمه‌های شمارنده ثبت کنید.",
            badgeText = "فرمول نئوبار: میانگین هر خانه × ضریب رقت × 10⁴"
        )

        // NEUBAUER LIVE SUMMARY CARD
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نتیجه شمارش هموسایتومتر (نئوبار)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onResetCounts,
                        modifier = Modifier.testTag("btn_reset_neubauer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "صفر کردن شمارنده",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صفر کردن")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeubauerResultPill(
                        label = "کانت سلول زنده / mL",
                        value = "${NumberFormatter.formatClean(state.viableCountInMillions, 3)} ×10⁶",
                        sub = "${NumberFormatter.formatClean(state.viableCellsPerMl, 0)} cells/mL",
                        color = BioTealPrimary,
                        modifier = Modifier.weight(1.1f)
                    )
                    NeubauerResultPill(
                        label = "درصد زنده‌مانی (Viability)",
                        value = "${NumberFormatter.formatClean(state.viabilityPercent, 1)}٪",
                        sub = "${state.totalLiveCellsCounted} زنده از ${state.totalAllCellsCounted}",
                        color = ViableGreen,
                        modifier = Modifier.weight(1f)
                    )
                    NeubauerResultPill(
                        label = "میانگین هر خانه",
                        value = NumberFormatter.formatClean(state.averageLivePerSquare, 2),
                        sub = "زنده در هر مربع بزرگ",
                        color = SerumAmber,
                        modifier = Modifier.weight(0.95f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "کل سلول‌های زنده در ${state.sampleVolumeMlText} میلی‌لیتر سوسپانسیون: ${NumberFormatter.formatScientificCount(state.totalViableCellsInSample)} سلول",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSendToDilution()
                            Toast.makeText(
                                context,
                                "کانت نئوبار به بخش رقت و محاسبه سرم منتقل شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_neubauer_to_dilution")
                    ) {
                        Text("ارسال به محاسبه‌گر مدیا و سرم")
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
                        Text("افزودن به میکس سلولی")
                    }
                }
            }
        }

        // TRYPAN BLUE DILUTION FACTOR & SAMPLE VOLUME
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = state.trypanDilutionFactorText,
                        onValueChange = onDilutionFactorChanged,
                        label = "ضریب رقت تریپان بلو",
                        unitText = " برابر (X)",
                        accentColor = BioTealPrimary,
                        helperText = "مثلاً ۱ به ۱ با تریپان بلو = ضریب 2",
                        testTag = "input_trypan_factor",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.sampleVolumeMlText,
                        onValueChange = onSampleVolumeChanged,
                        label = "حجم فالکون سلول فعلی",
                        unitText = "mL",
                        accentColor = CellCyan,
                        helperText = "حجم کل سوسپانسیون شما",
                        testTag = "input_neubauer_sample_vol",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1" to "بدون رقت (1x)", "2" to "۱:۱ تریپان بلو (2x)", "4" to "۱:۳ (4x)", "5" to "۱:۴ (5x)", "10" to "۱:۹ (10x)").forEach { (factor, label) ->
                        FilterChip(
                            selected = state.trypanDilutionFactorText == factor,
                            onClick = { onDilutionFactorChanged(factor) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // 4 NEUBAUER CHAMBER SQUARES
        state.squares.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NeubauerResultPill(
    label: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = RobotoMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = BioTealPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = square.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Cells Counter Row
            LabNumericField(
                value = square.liveCountText,
                onValueChange = onLiveChanged,
                label = "سلول زنده",
                unitText = "عدد",
                accentColor = ViableGreen,
                testTag = "neubauer_live_${square.index}"
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                            .height(36.dp)
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
                            .height(36.dp)
                            .testTag("btn_dec_live_${square.index}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "کاهش زنده")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dead Cells Counter Row
            LabNumericField(
                value = square.deadCountText,
                onValueChange = onDeadChanged,
                label = "سلول مرده (آبی)",
                unitText = "عدد",
                accentColor = DeadCellRed,
                testTag = "neubauer_dead_${square.index}"
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                            .height(36.dp)
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
                            .height(36.dp)
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
    onTargetCellsPerWellChanged: (String) -> Unit,
    onStockCountChanged: (String) -> Unit,
    onSerumPercentChanged: (String) -> Unit,
    onOverfillPercentChanged: (String) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LabHeroBanner(
            title = "محاسبه‌گر پلیت‌ریزی، فلاسک و تاریخچه آزمایشگاه",
            subtitle = "محاسبه دقیق مسترمیکس کشت سلول برای پلیت‌های ۶، ۱۲، ۲۴، ۹۶ خانه و فلاسک‌های T25/T75/T175 به همراه درصد خطای پیپتینگ.",
            badgeText = "پلیت‌ریزی + آرشیو محاسبات"
        )

        // VESSEL PRESET SELECTOR
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = BioTealPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "انتخاب ظرف کشت استاندارد (پلیت / فلاسک)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = seedingState.wellCountText,
                        onValueChange = onWellCountChanged,
                        label = "تعداد چاهک (Well) / فلاسک",
                        unitText = "عدد",
                        accentColor = BioTealPrimary,
                        testTag = "seeding_well_count",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = seedingState.volumePerWellMlText,
                        onValueChange = onVolumePerWellChanged,
                        label = "حجم هر چاهک / فلاسک",
                        unitText = "mL",
                        accentColor = MediaCoral,
                        helperText = "${NumberFormatter.formatClean(seedingState.volumePerWellMl * 1000.0, 0)} µL در هر چاهک",
                        testTag = "seeding_vol_per_well",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = seedingState.targetCellsPerWellMillionText,
                        onValueChange = onTargetCellsPerWellChanged,
                        label = "سلول مورد نیاز در هر چاهک",
                        unitText = "×10⁶",
                        accentColor = BioTealPrimary,
                        helperText = "غلظت: ${NumberFormatter.formatClean(seedingState.targetConcentrationMillionPerMl, 3)} ×10⁶/mL",
                        testTag = "seeding_cells_per_well",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = seedingState.stockCountMillionText,
                        onValueChange = onStockCountChanged,
                        label = "کانت استوک سلول فعلی",
                        unitText = "×10⁶/mL",
                        accentColor = CellCyan,
                        testTag = "seeding_stock_count",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        label = "درصد خطای پیپتینگ (اضافه)",
                        unitText = "%",
                        accentColor = ViableGreen,
                        helperText = "معمولاً 10% برای جبران خطای سمپلر",
                        testTag = "seeding_overfill_pct",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // MASTER MIX VISUALIZER & RECIPE
        TubeCompositionVisualBar(
            cellVolumeMl = seedingState.requiredCellStockVolumeMl,
            mediaVolumeMl = seedingState.requiredBasalMediaVolumeMl,
            serumVolumeMl = seedingState.requiredSerumVolumeMl,
            serumPercent = seedingState.serumPercent
        )

        // SAVED CALCULATIONS ARCHIVE
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = BioTealPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تاریخچه محاسبات ذخیره‌شده (${savedRecords.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (savedRecords.isEmpty()) {
                    Text(
                        text = "هنوز محاسبه‌ای ذخیره نشده است. از دکمه «ذخیره در تاریخچه» در تب رقت یا میکس استفاده کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    savedRecords.forEachIndexed { idx, rec ->
                        if (idx > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}
