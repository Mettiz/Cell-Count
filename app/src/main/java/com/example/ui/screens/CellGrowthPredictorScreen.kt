package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.CellGrowthState
import com.example.model.CellLinePreset
import com.example.model.CultureVesselPreset
import com.example.model.NumberFormatter
import com.example.ui.components.LabNumericField
import com.example.ui.components.adaptiveMonospaceFontSize
import com.example.ui.theme.BioIndigoPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.DeadCellRed
import com.example.ui.theme.MediaCoral
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SerumAmber
import com.example.ui.theme.ViableGreen
import kotlin.math.max

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CellGrowthPredictorScreen(
    state: CellGrowthState,
    onSelectVessel: (CultureVesselPreset) -> Unit,
    onSelectCellLine: (CellLinePreset) -> Unit,
    onInitialCellsChanged: (String) -> Unit,
    onToggleInitialCellsUnit: () -> Unit,
    onCarryingCapacityChanged: (String) -> Unit,
    onToggleCarryingCapacityUnit: () -> Unit,
    onDoublingTimeChanged: (String) -> Unit,
    onLagPhaseChanged: (String) -> Unit,
    onIncubationHoursChanged: (String) -> Unit,
    onTargetConfluencyChanged: (String) -> Unit,
    onToggleResultCountUnit: () -> Unit,
    onEmpiricalN1Changed: (String) -> Unit,
    onEmpiricalN2Changed: (String) -> Unit,
    onEmpiricalElapsedHoursChanged: (String) -> Unit,
    onToggleEmpiricalCountUnit: () -> Unit,
    onApplyEmpiricalDoublingTime: () -> Unit,
    onSavePredictionToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val confluency = state.predictedConfluencyPercent
    val confluencyColor = when {
        confluency >= 90.0 -> DeadCellRed
        confluency >= 70.0 -> SerumAmber
        else -> ViableGreen
    }
    val confluencyStatusLabel = when {
        confluency >= 92.0 -> "اشباع ظرف (نیاز فوری به پاساژ / ساب‌کالچر)"
        confluency >= 75.0 -> "محدوده ایده‌آل پاساژ (۷۵٪ تا ۹۰٪ کانفلوئنسی)"
        confluency >= 35.0 -> "فاز رشد لگاریتمی فعال (Log Phase)"
        else -> "تراکم اولیه / ابتدای رشد"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // =====================================================================
        // 1. HERO LIVE GROWTH PREDICTION CARD
        // =====================================================================
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "پیش‌بینی رشد سلول و زمان پاساژ",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${state.selectedCellLine.shortName} • ${state.selectedVessel.title} (${state.selectedVessel.specSubtitle})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onSavePredictionToHistory()
                            Toast.makeText(
                                context,
                                "پیش‌بینی رشد سلول در تاریخچه ذخیره شد",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("btn_save_growth_history")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdded,
                            contentDescription = "ذخیره در تاریخچه",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ذخیره", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Primary Full-Width Pill: Logistic Predicted Cell Count at t hours
                GrowthMetricPill(
                    label = "تعداد سلول پیش‌بینی‌شده پس از ${NumberFormatter.formatWithCommas(state.incubationHours, 1)} ساعت (مدل لجستیک)",
                    numberText = NumberFormatter.formatCountForUnitWithCommas(
                        state.predictedLogisticCellsRaw,
                        state.resultCountUnit
                    ),
                    unitText = "${state.resultCountUnit.shortLabel} ⇄",
                    subText = "معادل: ${NumberFormatter.formatOppositeCountHelper(state.predictedLogisticCellsRaw, state.resultCountUnit)} | مدل نمایی خالص: ${NumberFormatter.formatCountForUnitWithCommas(state.predictedExponentialCellsRaw, state.resultCountUnit)} ${state.resultCountUnit.shortLabel}",
                    accentColor = ViableGreen,
                    onClick = onToggleResultCountUnit,
                    testTag = "pill_predicted_logistic_cells",
                    modifier = Modifier.fillMaxWidth()
                )

                // Confluency Progress Bar & Status Card
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, confluencyColor.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "درصد کانفلوئنسی (پوشش سطح ظرف)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "${NumberFormatter.formatWithCommas(confluency, 1)} %",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = RobotoMonoFontFamily,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = confluencyColor
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { (confluency / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = confluencyColor,
                            trackColor = confluencyColor.copy(alpha = 0.16f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = confluencyStatusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = confluencyColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "شروع: ${NumberFormatter.formatWithCommas(state.initialConfluencyPercent, 1)}٪",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2-Column Row: Time to Target Confluency & Population Doublings (PDL)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val hoursToTarget = state.hoursToTargetConfluencyLogistic
                    val daysToTarget = hoursToTarget / 24.0
                    GrowthMetricPill(
                        label = "زمان رسیدن به ${NumberFormatter.formatWithCommas(state.targetConfluencyPercent, 0)}٪ کانفلوئنسی",
                        numberText = if (state.initialConfluencyPercent >= state.targetConfluencyPercent) {
                            "0"
                        } else {
                            NumberFormatter.formatWithCommas(hoursToTarget, 1)
                        },
                        unitText = "ساعت",
                        subText = if (state.initialConfluencyPercent >= state.targetConfluencyPercent) {
                            "هم‌اکنون بالای هدف است"
                        } else {
                            "معادل ${NumberFormatter.formatWithCommas(daysToTarget, 2)} روز (${NumberFormatter.formatCountForUnitWithCommas(state.targetPassageCellsRaw, state.resultCountUnit)} ${state.resultCountUnit.shortLabel})"
                        },
                        accentColor = SerumAmber,
                        testTag = "pill_hours_to_target",
                        modifier = Modifier.weight(1f)
                    )

                    GrowthMetricPill(
                        label = "تعداد تقسیم (PDL)",
                        numberText = NumberFormatter.formatWithCommas(state.populationDoublingLevel, 2),
                        unitText = "نسل",
                        subText = "μ = ${NumberFormatter.formatWithCommas(state.specificGrowthRatePerHour, 4)} h⁻¹",
                        accentColor = BioIndigoPrimary,
                        testTag = "pill_pdl_count",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // =====================================================================
        // 2. INTERACTIVE GROWTH CURVE CHART (LOGISTIC VS EXPONENTIAL)
        // =====================================================================
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = ViableGreen,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "منحنی رشد سلول و اشباع ظرف کشت",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "K = ${NumberFormatter.formatCountForUnitWithCommas(state.carryingCapacityRaw, state.resultCountUnit)} ${state.resultCountUnit.shortLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CellGrowthCurveCanvas(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(10.dp)
                )

                // Legend Row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChartLegendItem(color = ViableGreen, text = "رشد لجستیک واقعی (با مهار تماسی)")
                    ChartLegendItem(color = CellCyan, text = "رشد نمایی تئوری")
                    ChartLegendItem(color = SerumAmber, text = "مرز پاساژ (${NumberFormatter.formatWithCommas(state.targetConfluencyPercent, 0)}٪)")
                }
            }
        }

        // =====================================================================
        // 3. CELL LINE & CULTURE VESSEL PRESETS + INPUT PARAMETERS
        // =====================================================================
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Biotech,
                        contentDescription = null,
                        tint = BioIndigoPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "انتخاب رده سلولی و ظرف کشت",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "رده سلولی استاندارد (تنظیم خودکار زمان دو برابر شدن Td):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CellLinePreset.entries.forEach { line ->
                        FilterChip(
                            selected = state.selectedCellLine == line,
                            onClick = { onSelectCellLine(line) },
                            label = {
                                Text(
                                    if (line == CellLinePreset.CUSTOM) {
                                        line.shortName
                                    } else {
                                        "${line.shortName} (${NumberFormatter.formatClean(line.defaultDoublingTimeHours, 1)}h)"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("growth_cell_line_${line.name}")
                        )
                    }
                }

                Text(
                    text = "مشخصات رده انتخابی: ${state.selectedCellLine.title} • Td = ${NumberFormatter.formatClean(state.selectedCellLine.defaultDoublingTimeHours, 1)} ساعت • فاز چسبیدن = ${NumberFormatter.formatClean(state.selectedCellLine.defaultLagPhaseHours, 1)} ساعت",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "ظرف کشت / بیوراکتور (تنظیم خودکار ظرفیت اشباع K و سلول اولیه):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CultureVesselPreset.entries.forEach { vessel ->
                        FilterChip(
                            selected = state.selectedVessel == vessel,
                            onClick = { onSelectVessel(vessel) },
                            label = {
                                Text(vessel.chipLabel)
                            },
                            modifier = Modifier.testTag("growth_vessel_${vessel.name}")
                        )
                    }
                }

                Text(
                    text = if (state.selectedVessel.isBioreactor) {
                        "مشخصات بیوراکتور: ${state.selectedVessel.title} • حجم کاری: ${NumberFormatter.formatWithCommas(state.selectedVessel.workingVolumeLiters, 0)} لیتر • تلقیح اولیه (0.5×10⁶/mL): ${NumberFormatter.formatWithCommas(state.selectedVessel.defaultTargetCellsPerWellMillion, 0)} میلیون • حداکثر تراکم (3×10⁶/mL): ${NumberFormatter.formatWithCommas(state.selectedVessel.maxConfluentCellsMillion, 0)} میلیون"
                    } else {
                        "مشخصات ظرف: ${state.selectedVessel.title} • سطح کشت: ${NumberFormatter.formatWithCommas(state.selectedVessel.surfaceAreaCm2, 1)} cm² • حجم مدیا: ${NumberFormatter.formatClean(state.selectedVessel.defaultMediaPerWellMl, 2)} mL • ظرفیت ۱۰۰٪: ${NumberFormatter.formatWithCommas(state.selectedVessel.maxConfluentCellsMillion, 2)} میلیون"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Row 1: Initial Cells (N0) & Carrying Capacity (K)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.initialCellsText,
                        onValueChange = onInitialCellsChanged,
                        label = "تعداد سلول کشت اولیه (N₀)",
                        unitText = state.initialCellsUnit.shortLabel,
                        onUnitClick = onToggleInitialCellsUnit,
                        accentColor = BioIndigoPrimary,
                        helperText = NumberFormatter.formatOppositeCountHelper(
                            state.initialCellsRaw,
                            state.initialCellsUnit
                        ),
                        testTag = "input_growth_initial_cells",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.carryingCapacityText,
                        onValueChange = onCarryingCapacityChanged,
                        label = "ظرفیت ۱۰۰٪ ظرف کشت (K)",
                        unitText = state.carryingCapacityUnit.shortLabel,
                        onUnitClick = onToggleCarryingCapacityUnit,
                        accentColor = MediaCoral,
                        helperText = NumberFormatter.formatOppositeCountHelper(
                            state.carryingCapacityRaw,
                            state.carryingCapacityUnit
                        ),
                        testTag = "input_growth_capacity_k",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Doubling Time (Td) & Lag Phase (T_lag)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.doublingTimeHoursText,
                        onValueChange = onDoublingTimeChanged,
                        label = "زمان دو برابر شدن (Td)",
                        unitText = "ساعت",
                        accentColor = ViableGreen,
                        helperText = "مثلاً HeLa = 24 ساعت",
                        testTag = "input_growth_doubling_time",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.lagPhaseHoursText,
                        onValueChange = onLagPhaseChanged,
                        label = "فاز تأخیر چسبیدن (Lag)",
                        unitText = "ساعت",
                        accentColor = CellCyan,
                        helperText = "معمولاً ۲ تا ۶ ساعت پس از کشت",
                        testTag = "input_growth_lag_hours",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Incubation Time (t) & Target Confluency (%)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.incubationHoursText,
                        onValueChange = onIncubationHoursChanged,
                        label = "مدت زمان انکوباسیون (t)",
                        unitText = "ساعت",
                        accentColor = BioIndigoPrimary,
                        helperText = "معادل ${NumberFormatter.formatWithCommas(state.incubationHours / 24.0, 2)} روز",
                        testTag = "input_growth_incubation_hours",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.targetConfluencyPercentText,
                        onValueChange = onTargetConfluencyChanged,
                        label = "کانفلوئنسی هدف پاساژ",
                        unitText = "%",
                        accentColor = SerumAmber,
                        helperText = "استاندارد پاساژ: 80%",
                        testTag = "input_growth_target_confluency",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Incubation Hour Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    listOf(
                        "12" to "۱۲ ساعت",
                        "24" to "۲۴ ساعت (۱ روز)",
                        "36" to "۳۶ ساعت",
                        "48" to "۴۸ ساعت (۲ روز)",
                        "72" to "۷۲ ساعت (۳ روز)",
                        "96" to "۹۶ ساعت (۴ روز)",
                        "120" to "۱۲۰ ساعت (۵ روز)"
                    ).forEach { (hourVal, label) ->
                        FilterChip(
                            selected = state.incubationHoursText == hourVal,
                            onClick = { onIncubationHoursChanged(hourVal) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. TIME-TABLE SCHEDULE CARD (0h TO 120h)
        // =====================================================================
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = BioIndigoPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "جدول زمان‌بندی رشد سلول (۰ تا ۱۲۰ ساعت)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Table Header
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "زمان",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = "سلول (${state.resultCountUnit.shortLabel})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "کانفلوئنسی",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = "وضعیت",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }

                state.growthSchedulePoints.forEachIndexed { idx, pt ->
                    if (idx > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                    val rowColor = when {
                        pt.confluencyPercent >= 90.0 -> DeadCellRed
                        pt.confluencyPercent >= 70.0 -> SerumAmber
                        else -> ViableGreen
                    }
                    val statusShort = when {
                        pt.confluencyPercent >= 90.0 -> "اشباع (پاساژ فوری)"
                        pt.confluencyPercent >= 70.0 -> "آماده پاساژ"
                        pt.hour <= state.lagPhaseHours -> "فاز چسبیدن (Lag)"
                        else -> "در حال رشد"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIncubationHoursChanged(NumberFormatter.formatClean(pt.hour, 0)) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${NumberFormatter.formatClean(pt.hour, 0)}h (${NumberFormatter.formatClean(pt.hour / 24.0, 1)}d)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.9f)
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = NumberFormatter.formatCountForUnitWithCommas(
                                    pt.logisticCellsRaw,
                                    state.resultCountUnit
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = RobotoMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1.3f)
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${NumberFormatter.formatWithCommas(pt.confluencyPercent, 1)}%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = RobotoMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = rowColor,
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = rowColor.copy(alpha = 0.14f),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                text = statusShort,
                                style = MaterialTheme.typography.labelSmall,
                                color = rowColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 5. EMPIRICAL DOUBLING TIME CALCULATOR (FROM 2 LAB COUNTS)
        // =====================================================================
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = CellCyan,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "محاسبه زمان دو برابر شدن واقعی رده سلولی شما (Td)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "با وارد کردن کانت اولیه (N₁)، کانت نهایی (N₂) و زمان سپری‌شده، سرعت رشد دقیق سلول خودتان را حساب کنید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.empiricalN1Text,
                        onValueChange = onEmpiricalN1Changed,
                        label = "شمارش اول (N₁)",
                        unitText = state.empiricalCountUnit.shortLabel,
                        onUnitClick = onToggleEmpiricalCountUnit,
                        accentColor = BioIndigoPrimary,
                        testTag = "input_empirical_n1",
                        modifier = Modifier.weight(1f)
                    )

                    LabNumericField(
                        value = state.empiricalN2Text,
                        onValueChange = onEmpiricalN2Changed,
                        label = "شمارش دوم (N₂)",
                        unitText = state.empiricalCountUnit.shortLabel,
                        onUnitClick = onToggleEmpiricalCountUnit,
                        accentColor = ViableGreen,
                        testTag = "input_empirical_n2",
                        modifier = Modifier.weight(1f)
                    )
                }

                LabNumericField(
                    value = state.empiricalElapsedHoursText,
                    onValueChange = onEmpiricalElapsedHoursChanged,
                    label = "فاصله زمانی بین دو شمارش (Δt)",
                    unitText = "ساعت",
                    accentColor = CellCyan,
                    helperText = "فرمول: Td = Δt × ln(2) / ln(N₂ / N₁)",
                    testTag = "input_empirical_dt",
                    modifier = Modifier.fillMaxWidth()
                )

                val empTd = state.calculatedEmpiricalDoublingTimeHours
                if (empTd > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ViableGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ViableGreen.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "زمان دو برابر شدن محاسبه‌شده: ${NumberFormatter.formatWithCommas(empTd, 2)} ساعت",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ViableGreen
                                )
                                Text(
                                    text = "تعداد تقسیم انجام‌شده: ${NumberFormatter.formatWithCommas(state.calculatedEmpiricalDoublings, 2)} نسل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onApplyEmpiricalDoublingTime()
                                    Toast.makeText(
                                        context,
                                        "زمان دو برابر شدن (${NumberFormatter.formatWithCommas(empTd, 2)} ساعت) در پیش‌بینی اعمال شد",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.testTag("btn_apply_empirical_td")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اعمال در پیش‌بینی")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowthMetricPill(
    label: String,
    numberText: String,
    unitText: String,
    subText: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null
) {
    val numFontSize = adaptiveMonospaceFontSize(numberText, baseSp = 16.5f, minSp = 11.5f)

    Surface(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onClick() }
                } else Modifier
            ),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = unitText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

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
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = RobotoMonoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = numFontSize
                        ),
                        color = accentColor,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = subText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CellGrowthCurveCanvas(
    state: CellGrowthState,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val maxHorizonHours = max(120.0, state.incubationHours * 1.25).coerceAtMost(360.0)
    val maxYCells = max(
        state.carryingCapacityRaw * 1.12,
        state.predictedExponentialCellsRaw * 1.12
    ).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Horizontal grid lines (25%, 50%, 75%, 100% of Carrying Capacity K)
        listOf(0.25, 0.50, 0.75, 1.0).forEach { frac ->
            val yCell = state.carryingCapacityRaw * frac
            val yNorm = (1.0 - (yCell / maxYCells)).toFloat().coerceIn(0f, 1f)
            val yPx = yNorm * h
            drawLine(
                color = gridColor,
                start = Offset(0f, yPx),
                end = Offset(w, yPx),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Target Confluency Threshold Line (e.g. 80% K)
        val targetYNorm = (1.0 - (state.targetPassageCellsRaw / maxYCells)).toFloat().coerceIn(0f, 1f)
        val targetYPx = targetYNorm * h
        drawLine(
            color = SerumAmber,
            start = Offset(0f, targetYPx),
            end = Offset(w, targetYPx),
            strokeWidth = 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )

        val steps = 80
        val logisticPath = Path()
        val expPath = Path()

        for (i in 0..steps) {
            val t = (i.toDouble() / steps.toDouble()) * maxHorizonHours
            val xPx = ((t / maxHorizonHours) * w).toFloat()

            val logCells = state.computeLogisticCellsAt(t)
            val logYNorm = (1.0 - (logCells / maxYCells)).toFloat().coerceIn(0.02f, 0.98f)
            val logYPx = logYNorm * h

            val expCells = state.computeExponentialCellsAt(t).coerceAtMost(maxYCells)
            val expYNorm = (1.0 - (expCells / maxYCells)).toFloat().coerceIn(0.02f, 0.98f)
            val expYPx = expYNorm * h

            if (i == 0) {
                logisticPath.moveTo(xPx, logYPx)
                expPath.moveTo(xPx, expYPx)
            } else {
                logisticPath.lineTo(xPx, logYPx)
                expPath.lineTo(xPx, expYPx)
            }
        }

        // Draw Exponential curve (dashed cyan)
        drawPath(
            path = expPath,
            color = CellCyan.copy(alpha = 0.75f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        )

        // Draw Logistic curve (solid green)
        drawPath(
            path = logisticPath,
            color = ViableGreen,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Current Incubation Time marker
        val currentX = ((state.incubationHours / maxHorizonHours).coerceIn(0.0, 1.0) * w).toFloat()
        val currentYNorm = (1.0 - (state.predictedLogisticCellsRaw / maxYCells)).toFloat().coerceIn(0.02f, 0.98f)
        val currentY = currentYNorm * h

        drawLine(
            color = BioIndigoPrimary.copy(alpha = 0.65f),
            start = Offset(currentX, 0f),
            end = Offset(currentX, h),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )

        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = Offset(currentX, currentY)
        )
        drawCircle(
            color = ViableGreen,
            radius = 4.5.dp.toPx(),
            center = Offset(currentX, currentY)
        )
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
