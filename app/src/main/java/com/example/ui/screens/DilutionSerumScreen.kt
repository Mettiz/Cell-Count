package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.CountUnitMultiplier
import com.example.model.DilutionState
import com.example.model.NumberFormatter
import com.example.model.SerumCalculationBasis
import com.example.model.StockUsageMode
import com.example.model.TargetAnchor
import com.example.model.VolumeUnit
import com.example.ui.components.LabNumericField
import com.example.ui.components.QuickUnitSwitchBar
import com.example.ui.components.TubeCompositionVisualBar
import com.example.ui.components.adaptiveMonospaceFontSize
import com.example.ui.theme.BioIndigoPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.CentrifugePurple
import com.example.ui.theme.MediaCoral
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SerumAmber
import com.example.ui.theme.ViableGreen

@Composable
fun DilutionSerumScreen(
    state: DilutionState,
    onCurrentVolumeChanged: (String) -> Unit,
    onCurrentCountChanged: (String) -> Unit,
    onTargetCountChanged: (String) -> Unit,
    onFinalVolumeChanged: (String) -> Unit,
    onSerumPercentChanged: (String) -> Unit,
    onInitialSerumPercentChanged: (String) -> Unit,
    onMediaVolumeChanged: (String) -> Unit,
    onSerumVolumeChanged: (String) -> Unit,
    onTotalAddedVolumeChanged: (String) -> Unit,
    onSetAllVolumeUnits: (VolumeUnit) -> Unit,
    onSetAllCountUnits: (CountUnitMultiplier) -> Unit,
    onToggleV1Unit: () -> Unit,
    onToggleV2Unit: () -> Unit,
    onToggleMediaUnit: () -> Unit,
    onToggleSerumUnit: () -> Unit,
    onToggleAddedUnit: () -> Unit,
    onToggleC1Unit: () -> Unit,
    onToggleC2Unit: () -> Unit,
    onSerumBasisSelected: (SerumCalculationBasis) -> Unit,
    onStockUsageModeSelected: (StockUsageMode) -> Unit,
    onTargetAnchorSelected: (TargetAnchor) -> Unit,
    onSaveCalculation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val activeGlobalVolUnit = if (
        state.v1Unit == state.v2Unit &&
        state.v2Unit == state.mediaUnit &&
        state.mediaUnit == state.serumUnit &&
        state.serumUnit == state.addedUnit
    ) state.v1Unit else null

    val activeGlobalCountUnit = if (state.c1Unit == state.c2Unit) state.c1Unit else null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. COMPACT GLOBAL UNIT CONVERSION BAR (mL ⇄ L | میلیون ⇄ عدد کامل)
        QuickUnitSwitchBar(
            activeVolumeUnit = activeGlobalVolUnit,
            onSelectVolumeUnit = onSetAllVolumeUnits,
            activeCountUnit = activeGlobalCountUnit,
            onSelectCountUnit = onSetAllCountUnits
        )

        // 2. CLEAN 2x2 CARD: STOCK (V1, C1) & TARGET (C2, V2)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "۱. سلول فعلی و کانت / حجم هدف",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "کل سلول: ${NumberFormatter.formatScientificCount(state.totalCellsInStock)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 1: Current Volume (V1) & Current Count (C1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.currentVolumeText,
                        onValueChange = onCurrentVolumeChanged,
                        label = "حجم فعلی (V₁)",
                        unitText = state.v1Unit.symbol,
                        onUnitClick = onToggleV1Unit,
                        accentColor = CellCyan,
                        helperText = NumberFormatter.formatOppositeUnitHelper(state.currentVolumeMl, state.v1Unit),
                        testTag = "input_current_volume",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.currentCountText,
                        onValueChange = onCurrentCountChanged,
                        label = "کانت فعلی (C₁)",
                        unitText = state.c1Unit.shortLabel,
                        onUnitClick = onToggleC1Unit,
                        accentColor = CellCyan,
                        helperText = NumberFormatter.formatOppositeCountHelper(state.currentCountRawPerMl, state.c1Unit),
                        testTag = "input_current_count",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Target Count (C2) & Final Volume (V2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.targetCountText,
                        onValueChange = onTargetCountChanged,
                        label = "کانت تارگت (C₂)",
                        unitText = state.c2Unit.shortLabel,
                        onUnitClick = onToggleC2Unit,
                        accentColor = BioIndigoPrimary,
                        helperText = NumberFormatter.formatOppositeCountHelper(state.targetCountRawPerMl, state.c2Unit),
                        testTag = "input_target_count",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.finalVolumeText,
                        onValueChange = onFinalVolumeChanged,
                        label = "حجم نهایی (V₂)",
                        unitText = state.v2Unit.symbol,
                        onUnitClick = onToggleV2Unit,
                        accentColor = BioIndigoPrimary,
                        helperText = NumberFormatter.formatOppositeUnitHelper(state.finalVolumeMl, state.v2Unit),
                        testTag = "input_final_volume",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. CLEAN CARD: SERUM % + MEDIA & SERUM VOLUMES
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = SerumAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "۲. محاسبه حجم مدیا و سرم (FBS)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAdvancedOptions = !showAdvancedOptions }
                            .testTag("btn_toggle_advanced")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تنظیمات",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (showAdvancedOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Serum % Input + Quick Chips on a single clean row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.serumPercentText,
                        onValueChange = onSerumPercentChanged,
                        label = "درصد سرم",
                        unitText = "%",
                        accentColor = SerumAmber,
                        testTag = "input_serum_percent",
                        modifier = Modifier.width(124.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "انتخاب سریع درصد سرم:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("0", "2", "3", "5", "10", "15", "20").forEach { pct ->
                                val isSelected = state.serumPercentText == pct
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSerumPercentChanged(pct) },
                                    label = {
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SerumAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = SerumAmber
                                    ),
                                    modifier = Modifier.testTag("serum_chip_$pct")
                                )
                            }
                        }
                    }
                }

                // Collapsible Advanced Settings
                AnimatedVisibility(visible = showAdvancedOptions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "مبنای محاسبه درصد سرم:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SerumCalculationBasis.entries.forEach { basis ->
                                FilterChip(
                                    selected = state.serumBasis == basis,
                                    onClick = { onSerumBasisSelected(basis) },
                                    label = {
                                        Text(
                                            text = basis.title,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("serum_basis_${basis.name}")
                                )
                            }
                        }

                        if (state.serumBasis == SerumCalculationBasis.FINAL_TOTAL_VOLUME) {
                            LabNumericField(
                                value = state.initialSerumPercentText,
                                onValueChange = onInitialSerumPercentChanged,
                                label = "درصد سرم فعلی داخل استوک (V₁)",
                                unitText = "%",
                                accentColor = SerumAmber,
                                testTag = "input_initial_serum_percent"
                            )
                        }

                        Text(
                            text = "حالت مصرف استوک سلول:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StockUsageMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.stockUsageMode == mode,
                                    onClick = { onStockUsageModeSelected(mode) },
                                    label = {
                                        Text(
                                            text = if (mode == StockUsageMode.USE_ALL_CURRENT_VOLUME) {
                                                "کل حجم فعلی (V₁)"
                                            } else {
                                                "برداشت بخشی از استوک"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("stock_mode_${mode.name}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (state.requiresCentrifugation) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CentrifugePurple.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CentrifugePurple.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CentrifugePurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "کانت تارگت بیشتر از کانت فعلی است (نیاز به سانتریفیوژ): پلت را در ${NumberFormatter.formatVolumeMlAndL(state.centrifugeResuspendMediaMl, state.mediaUnit)} مدیا + ${NumberFormatter.formatVolumeMlAndL(state.centrifugeResuspendSerumMl, state.serumUnit)} سرم حل کنید.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CentrifugePurple
                            )
                        }
                    }
                }

                // Editable Media Volume & Serum Volume side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.mediaVolumeText,
                        onValueChange = onMediaVolumeChanged,
                        label = "حجم مدیا پایه",
                        unitText = state.mediaUnit.symbol,
                        onUnitClick = onToggleMediaUnit,
                        accentColor = MediaCoral,
                        helperText = NumberFormatter.formatOppositeUnitHelper(state.mediaVolumeMl, state.mediaUnit),
                        testTag = "input_media_volume",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.serumVolumeText,
                        onValueChange = onSerumVolumeChanged,
                        label = "حجم سرم (${NumberFormatter.formatWithCommas(state.serumPercent, 1)}٪)",
                        unitText = state.serumUnit.symbol,
                        onUnitClick = onToggleSerumUnit,
                        accentColor = SerumAmber,
                        helperText = NumberFormatter.formatOppositeUnitHelper(state.serumVolumeMl, state.serumUnit),
                        testTag = "input_serum_volume",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LabNumericField(
                    value = state.totalAddedVolumeText,
                    onValueChange = onTotalAddedVolumeChanged,
                    label = "کل حجم اضافه‌شده (مدیا + سرم)",
                    unitText = state.addedUnit.symbol,
                    onUnitClick = onToggleAddedUnit,
                    accentColor = ViableGreen,
                    helperText = NumberFormatter.formatOppositeUnitHelper(state.totalAddedVolumeMl, state.addedUnit),
                    testTag = "input_total_added_volume"
                )
            }
        }

        // 4. CLEAN VISUAL BAR + CONCISE SUMMARY
        TubeCompositionVisualBar(
            cellVolumeMl = state.currentVolumeMl,
            mediaVolumeMl = if (state.requiresCentrifugation) state.centrifugeResuspendMediaMl else state.mediaVolumeMl,
            serumVolumeMl = if (state.requiresCentrifugation) state.centrifugeResuspendSerumMl else state.serumVolumeMl,
            serumPercent = state.serumPercent
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "خلاصه آماده پیپتینگ",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMetricTile(
                        title = "مدیا پایه",
                        numberText = NumberFormatter.addThousandsSeparator(state.mediaVolumeText),
                        unitText = state.mediaUnit.symbol,
                        secondaryNumberText = NumberFormatter.formatOppositeUnitNumber(state.mediaVolumeMl, state.mediaUnit),
                        secondaryUnitText = state.mediaUnit.toggle().symbol,
                        color = MediaCoral,
                        onClick = onToggleMediaUnit,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricTile(
                        title = "سرم (${NumberFormatter.formatWithCommas(state.serumPercent, 1)}٪)",
                        numberText = NumberFormatter.addThousandsSeparator(state.serumVolumeText),
                        unitText = state.serumUnit.symbol,
                        secondaryNumberText = NumberFormatter.formatOppositeUnitNumber(state.serumVolumeMl, state.serumUnit),
                        secondaryUnitText = state.serumUnit.toggle().symbol,
                        color = SerumAmber,
                        onClick = onToggleSerumUnit,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMetricTile(
                        title = "حجم نهایی (V₂)",
                        numberText = NumberFormatter.addThousandsSeparator(state.finalVolumeText),
                        unitText = state.v2Unit.symbol,
                        secondaryNumberText = NumberFormatter.formatOppositeUnitNumber(state.finalVolumeMl, state.v2Unit),
                        secondaryUnitText = state.v2Unit.toggle().symbol,
                        color = BioIndigoPrimary,
                        onClick = onToggleV2Unit,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricTile(
                        title = "کانت تارگت (C₂)",
                        numberText = NumberFormatter.addThousandsSeparator(state.targetCountText),
                        unitText = state.c2Unit.shortLabel,
                        secondaryNumberText = NumberFormatter.formatOppositeCountNumber(state.targetCountRawPerMl, state.c2Unit),
                        secondaryUnitText = state.c2Unit.toggle().shortLabel,
                        color = ViableGreen,
                        onClick = onToggleC2Unit,
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
                            onSaveCalculation()
                            Toast.makeText(
                                context,
                                "محاسبه در تاریخچه ذخیره شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_dilution")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "ذخیره",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره")
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val textToCopy = buildString {
                                appendLine("=== محاسبه رقت و سرم (CellCount Lab) ===")
                                appendLine("حجم فعلی (V1): ${NumberFormatter.formatVolumeMlAndL(state.currentVolumeMl, state.v1Unit)}")
                                appendLine("کانت فعلی (C1): ${NumberFormatter.formatCountBothUnits(state.currentCountRawPerMl, state.c1Unit)}")
                                appendLine("کانت تارگت (C2): ${NumberFormatter.formatCountBothUnits(state.targetCountRawPerMl, state.c2Unit)}")
                                appendLine("حجم نهایی (V2): ${NumberFormatter.formatVolumeMlAndL(state.finalVolumeMl, state.v2Unit)}")
                                appendLine("حجم مدیا پایه: ${NumberFormatter.formatVolumeMlAndL(state.mediaVolumeMl, state.mediaUnit)}")
                                appendLine("حجم سرم (${state.serumPercentText}%): ${NumberFormatter.formatVolumeMlAndL(state.serumVolumeMl, state.serumUnit)}")
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText("CellCount Calculation", textToCopy))
                            Toast.makeText(context, "نتیجه کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_copy_dilution")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "کپی",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کپی نتیجه")
                    }
                }
            }
        }
    }
}

/**
 * Summary tile where the Unit Pill sits in the top header row and the Number has 100% of the tile width
 * with automatic font scaling so long numbers never go under or collide with the unit.
 */
@Composable
private fun SummaryMetricTile(
    title: String,
    numberText: String,
    unitText: String,
    secondaryNumberText: String,
    secondaryUnitText: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numFontSize = adaptiveMonospaceFontSize(numberText, baseSp = 15.5f, minSp = 11f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header row: Title on Right + Unit Pill on Left
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
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "$unitText ⇄",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Dedicated full-width LTR row for the Primary Number
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
                // Secondary opposite-unit helper row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "= $secondaryNumberText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMonoFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = secondaryUnitText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
