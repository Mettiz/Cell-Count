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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.model.CountUnitMultiplier
import com.example.model.DilutionState
import com.example.model.NumberFormatter
import com.example.model.SerumCalculationBasis
import com.example.model.StockUsageMode
import com.example.model.TargetAnchor
import com.example.ui.components.LabHeroBanner
import com.example.ui.components.LabNumericField
import com.example.ui.components.TubeCompositionVisualBar
import com.example.ui.theme.BioTealPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.CentrifugePurple
import com.example.ui.theme.MediaCoral
import com.example.ui.theme.SerumAmber
import com.example.ui.theme.ViableGreen

@OptIn(ExperimentalLayoutApi::class)
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
    onUnitMultiplierSelected: (CountUnitMultiplier) -> Unit,
    onSerumBasisSelected: (SerumCalculationBasis) -> Unit,
    onStockUsageModeSelected: (StockUsageMode) -> Unit,
    onTargetAnchorSelected: (TargetAnchor) -> Unit,
    onSaveCalculation: () -> Unit,
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
            title = "محاسبه‌گر هوشمند کانت، مدیا و سرم",
            subtitle = "هر پارامتری را تغییر دهید (کانت تارگت، حجم نهایی، درصد سرم یا حجم مدیا)؛ بقیه مقادیر به صورت خودکار محاسبه می‌شوند.",
            badgeText = "فرمول پایه: C₁ × V₁ = C₂ × V₂ + تنظیم دقیق درصد سرم"
        )

        // Stock Usage Mode Selector + Count Unit Multiplier
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "واحد شمارش کانت سلولی",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountUnitMultiplier.entries.forEach { mult ->
                        val selected = state.unitMultiplier == mult
                        FilterChip(
                            selected = selected,
                            onClick = { onUnitMultiplierSelected(mult) },
                            label = {
                                Text(
                                    text = mult.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.testTag("unit_chip_${mult.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "نحوه استفاده از استوک سلول فعلی",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StockUsageMode.entries.forEach { mode ->
                        val isSelected = state.stockUsageMode == mode
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onStockUsageModeSelected(mode) }
                                .testTag("stock_mode_${mode.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 1: CURRENT CELL STOCK (V1 & C1)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "مشخصات سلول فعلی",
                            tint = CellCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "۱. مشخصات سوسپانسیون سلولی فعلی (استوک)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = state.currentVolumeText,
                        onValueChange = onCurrentVolumeChanged,
                        label = "حجم سلول فعلی (V₁)",
                        unitText = "mL",
                        accentColor = CellCyan,
                        helperText = "معادل ${NumberFormatter.formatClean(state.currentVolumeMl * 1000.0, 1)} µL",
                        badgeLabel = if (state.stockUsageMode == StockUsageMode.WITHDRAW_FROM_STOCK) "محاسبه خودکار" else "قابل تغییر",
                        testTag = "input_current_volume",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.currentCountText,
                        onValueChange = onCurrentCountChanged,
                        label = "کانت فعلی (C₁)",
                        unitText = state.unitMultiplier.shortLabel,
                        accentColor = CellCyan,
                        helperText = "کل سلول: ${NumberFormatter.formatScientificCount(state.totalCellsInStock)}",
                        badgeLabel = "قابل تغییر",
                        testTag = "input_current_count",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // SECTION 2: TARGET COUNT (C2) <-> FINAL VOLUME (V2) BIDIRECTIONAL SOLVER
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "ارتباط دوطرفه کانت تارگت و حجم نهایی",
                            tint = BioTealPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "۲. کانت تارگت (C₂) و حجم نهایی کل (V₂)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "کانت تارگت را بدهید تا حجم نهایی حساب شود، یا حجم نهایی را بدهید تا کانت تارگت را بگوید!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val isTargetCountAnchored = state.targetAnchor == TargetAnchor.HOLD_TARGET_COUNT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = state.targetCountText,
                        onValueChange = onTargetCountChanged,
                        label = "کانت تارگت (C₂)",
                        unitText = state.unitMultiplier.shortLabel,
                        accentColor = BioTealPrimary,
                        helperText = if (state.dilutionFactor > 0) {
                            "ضریب رقت: ${NumberFormatter.formatClean(state.dilutionFactor, 2)} برابر"
                        } else null,
                        badgeLabel = if (isTargetCountAnchored) "ثابت (مرجع)" else "محاسبه از V₂",
                        onBadgeClick = { onTargetAnchorSelected(TargetAnchor.HOLD_TARGET_COUNT) },
                        leadingIcon = if (isTargetCountAnchored) Icons.Default.Lock else Icons.Default.LockOpen,
                        testTag = "input_target_count",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.finalVolumeText,
                        onValueChange = onFinalVolumeChanged,
                        label = "حجم نهایی کل (V₂)",
                        unitText = "mL",
                        accentColor = BioTealPrimary,
                        helperText = "معادل ${NumberFormatter.formatClean(state.finalVolumeMl * 1000.0, 1)} µL",
                        badgeLabel = if (!isTargetCountAnchored) "ثابت (مرجع)" else "محاسبه از C₂",
                        onBadgeClick = { onTargetAnchorSelected(TargetAnchor.HOLD_FINAL_VOLUME) },
                        leadingIcon = if (!isTargetCountAnchored) Icons.Default.Lock else Icons.Default.LockOpen,
                        testTag = "input_final_volume",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Target Volume presets for lab convenience
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "انتخاب سریع حجم نهایی (V₂) برای مشاهده کانت تارگت متناظر:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("3", "5", "8", "10", "12", "15", "20", "25").forEach { presetVol ->
                        FilterChip(
                            selected = state.finalVolumeText == presetVol,
                            onClick = { onFinalVolumeChanged(presetVol) },
                            label = { Text("$presetVol mL") },
                            modifier = Modifier.testTag("preset_final_vol_$presetVol")
                        )
                    }
                }
            }
        }

        // SECTION 3: SERUM PERCENTAGE & MEDIA / SERUM VOLUMES (ALL EDITABLE)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "تنظیم درصد سرم و حجم مدیا",
                        tint = SerumAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "۳. تنظیم درصد سرم (FBS) و حجم مدیا + سرم",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "درصد سرم (مثلاً ۳٪ یا ۱۰٪) یا حتی حجم مدیا و سرم را مستقیماً تغییر دهید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Serum % Chips
                Text(
                    text = "انتخاب سریع درصد سرم (FBS %):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("0", "1", "2", "3", "5", "10", "15", "20").forEach { pct ->
                        val isSelected = state.serumPercentText == pct
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSerumPercentChanged(pct) },
                            label = {
                                Text(
                                    text = "$pct%",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SerumAmber.copy(alpha = 0.22f),
                                selectedLabelColor = SerumAmber
                            ),
                            modifier = Modifier.testTag("serum_chip_$pct")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Direct Serum % Input + Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = state.serumPercentText,
                        onValueChange = onSerumPercentChanged,
                        label = "درصد سرم (FBS)",
                        unitText = "%",
                        accentColor = SerumAmber,
                        testTag = "input_serum_percent",
                        modifier = Modifier.weight(0.95f)
                    )

                    Column(modifier = Modifier.weight(1.25f)) {
                        Text(
                            text = "اسلایدر تنظیم دقیق سرم: ${NumberFormatter.formatClean(state.serumPercent, 1)}٪",
                            style = MaterialTheme.typography.labelSmall,
                            color = SerumAmber
                        )
                        Slider(
                            value = state.serumPercent.toFloat().coerceIn(0f, 30f),
                            onValueChange = { newVal ->
                                val rounded = (newVal * 2).toInt() / 2.0
                                onSerumPercentChanged(NumberFormatter.formatClean(rounded, 1))
                            },
                            valueRange = 0f..30f,
                            colors = SliderDefaults.colors(
                                thumbColor = SerumAmber,
                                activeTrackColor = SerumAmber
                            ),
                            modifier = Modifier.testTag("slider_serum_percent")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Serum Calculation Basis Toggle (Added Volume vs Total Final Volume)
                Text(
                    text = "مبنای محاسبه درصد سرم:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SerumCalculationBasis.entries.forEach { basis ->
                        val selected = state.serumBasis == basis
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                SerumAmber.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) SerumAmber else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSerumBasisSelected(basis) }
                                .testTag("serum_basis_${basis.name}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = basis.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) SerumAmber else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = basis.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = state.serumBasis == SerumCalculationBasis.FINAL_TOTAL_VOLUME) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        LabNumericField(
                            value = state.initialSerumPercentText,
                            onValueChange = onInitialSerumPercentChanged,
                            label = "درصد سرم فعلی موجود در استوک سلول (V₁)",
                            unitText = "%",
                            accentColor = SerumAmber,
                            helperText = "اگر سلول فعلی در محیط بدون سرم یا PBS است، عدد 0 بماند.",
                            testTag = "input_initial_serum_percent"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                // If Target Count > Current Count, show Centrifugation & Resuspension Card
                if (state.requiresCentrifugation) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = CentrifugePurple.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, CentrifugePurple.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "نیاز به سانتریفیوژ",
                                    tint = CentrifugePurple
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "کانت تارگت بیشتر از کانت فعلی است (نیاز به تغلیظ / سانتریفیوژ)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CentrifugePurple,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "چون کانت تارگت (${state.targetCountText}) بیشتر از کانت فعلی (${state.currentCountText}) است، حجم نهایی (${NumberFormatter.formatClean(state.finalVolumeMl, 3)} mL) کمتر از حجم فعلی است. سوسپانسیون را سانتریفیوژ کرده، مایع رویی را دور بریزید و پلت سلولی را در مقادیر زیر حل (Resuspend) کنید:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• حجم مدیا پایه: ${NumberFormatter.formatVolumeWithMicroliter(state.centrifugeResuspendMediaMl)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MediaCoral,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• حجم سرم (${NumberFormatter.formatClean(state.serumPercent, 1)}٪): ${NumberFormatter.formatVolumeWithMicroliter(state.centrifugeResuspendSerumMl)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = SerumAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Editable Media Volume, Serum Volume, and Total Added Volume fields!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabNumericField(
                        value = state.mediaVolumeText,
                        onValueChange = onMediaVolumeChanged,
                        label = "حجم مدیا پایه (Media)",
                        unitText = "mL",
                        accentColor = MediaCoral,
                        helperText = "معادل ${NumberFormatter.formatClean(state.mediaVolumeMl * 1000.0, 1)} µL",
                        badgeLabel = "قابل ویرایش",
                        testTag = "input_media_volume",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.serumVolumeText,
                        onValueChange = onSerumVolumeChanged,
                        label = "حجم سرم (${NumberFormatter.formatClean(state.serumPercent, 1)}٪)",
                        unitText = "mL",
                        accentColor = SerumAmber,
                        helperText = "معادل ${NumberFormatter.formatClean(state.serumVolumeMl * 1000.0, 1)} µL",
                        badgeLabel = "قابل ویرایش",
                        testTag = "input_serum_volume",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LabNumericField(
                    value = state.totalAddedVolumeText,
                    onValueChange = onTotalAddedVolumeChanged,
                    label = "مجموع حجم اضافه‌شده به سلول (مدیا + سرم = V₂ - V₁)",
                    unitText = "mL",
                    accentColor = ViableGreen,
                    helperText = "معادل ${NumberFormatter.formatClean(state.totalAddedVolumeMl * 1000.0, 1)} µL | با تغییر این عدد، کانت تارگت و حجم نهایی اتوماتیک آپدیت می‌شوند.",
                    badgeLabel = "قابل ویرایش",
                    testTag = "input_total_added_volume"
                )
            }
        }

        // VISUAL TUBE COMPOSITION BAR
        TubeCompositionVisualBar(
            cellVolumeMl = state.currentVolumeMl,
            mediaVolumeMl = if (state.requiresCentrifugation) state.centrifugeResuspendMediaMl else state.mediaVolumeMl,
            serumVolumeMl = if (state.requiresCentrifugation) state.centrifugeResuspendSerumMl else state.serumVolumeMl,
            serumPercent = state.serumPercent
        )

        // STEP-BY-STEP LAB PIPETTING SUMMARY CARD + BOTH SERUM BASIS COMPARISON
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "دستورالعمل نهایی پیپتینگ (آماده اجرا زیر هود)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(10.dp))

                PipettingStepRow(
                    stepNumber = "۱",
                    title = "سوسپانسیون سلول فعلی (V₁)",
                    valueText = NumberFormatter.formatVolumeWithMicroliter(state.currentVolumeMl),
                    subText = "با کانت ${state.currentCountText} (${state.unitMultiplier.shortLabel})",
                    color = CellCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                PipettingStepRow(
                    stepNumber = "۲",
                    title = "افزودن محیط کشت پایه (مدیا بدون سرم)",
                    valueText = NumberFormatter.formatVolumeWithMicroliter(state.mediaVolumeMl),
                    subText = "${NumberFormatter.formatClean(100.0 - state.serumPercent, 1)}٪ از محیط تازه",
                    color = MediaCoral
                )
                Spacer(modifier = Modifier.height(8.dp))
                PipettingStepRow(
                    stepNumber = "۳",
                    title = "افزودن سرم (FBS - ${NumberFormatter.formatClean(state.serumPercent, 1)}٪)",
                    valueText = NumberFormatter.formatVolumeWithMicroliter(state.serumVolumeMl),
                    subText = "پیشنهاد سمپلر: ${NumberFormatter.formatClean(state.serumVolumeMl * 1000.0, 1)} میکرولیتر (µL)",
                    color = SerumAmber
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))
                PipettingStepRow(
                    stepNumber = "✓",
                    title = "نتیجه نهایی در فالکون / فلاسک (V₂ و C₂)",
                    valueText = "حجم نهایی: ${NumberFormatter.formatVolumeWithMicroliter(state.finalVolumeMl)}",
                    subText = "کانت تارگت نهایی: ${state.targetCountText} (${state.unitMultiplier.shortLabel}) | کل سلول: ${NumberFormatter.formatScientificCount(state.totalCellsInStock)}",
                    color = ViableGreen
                )

                // Quick comparison between "3% of Added Volume" and "3% of Total Final Volume"
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "مقایسه سریع هر دو روش محاسبه سرم ${NumberFormatter.formatClean(state.serumPercent, 1)}٪:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• بر اساس حجم اضافه‌شده (${NumberFormatter.formatClean(state.totalAddedVolumeMl)} mL): مدیا = ${NumberFormatter.formatVolumeWithMicroliter(state.addedBasisMediaMl)} | سرم = ${NumberFormatter.formatVolumeWithMicroliter(state.addedBasisSerumMl)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• بر اساس کل حجم نهایی (${NumberFormatter.formatClean(state.finalVolumeMl)} mL): مدیا = ${NumberFormatter.formatVolumeWithMicroliter(state.totalBasisMediaMl)} | سرم = ${NumberFormatter.formatVolumeWithMicroliter(state.totalBasisSerumMl)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveCalculation()
                            Toast.makeText(
                                context,
                                "محاسبه در تاریخچه آزمایشگاه ذخیره شد",
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
                            contentDescription = "ذخیره در تاریخچه",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره در تاریخچه")
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val textToCopy = buildString {
                                appendLine("=== گزارش محاسبه کانت سلولی (CellCount Lab) ===")
                                appendLine("حجم فعلی (V1): ${state.currentVolumeText} mL")
                                appendLine("کانت فعلی (C1): ${state.currentCountText} (${state.unitMultiplier.shortLabel})")
                                appendLine("کانت تارگت (C2): ${state.targetCountText} (${state.unitMultiplier.shortLabel})")
                                appendLine("حجم نهایی (V2): ${state.finalVolumeText} mL")
                                appendLine("حجم مدیا پایه: ${NumberFormatter.formatVolumeWithMicroliter(state.mediaVolumeMl)}")
                                appendLine("حجم سرم (${state.serumPercentText}%): ${NumberFormatter.formatVolumeWithMicroliter(state.serumVolumeMl)}")
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText("CellCount Calculation", textToCopy))
                            Toast.makeText(context, "فرمول و مقادیر کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_copy_dilution")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "کپی مقادیر",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کپی پروتکل")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PipettingStepRow(
    stepNumber: String,
    title: String,
    valueText: String,
    subText: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1.1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = color.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
