package com.example.model

import kotlin.math.abs
import kotlin.math.roundToLong

enum class AppTab {
    DILUTION_SERUM,
    CELL_MIXER,
    IMAGE_COUNTER,
    NEUBAUER_COUNTER,
    SEEDING_AND_HISTORY
}

enum class VolumeUnit(
    val symbol: String,
    val persianName: String,
    val toMlFactor: Double
) {
    ML("mL", "میلی‌لیتر (mL)", 1.0),
    L("L", "لیتر (L)", 1000.0);

    fun toggle(): VolumeUnit = if (this == ML) L else ML

    fun toMl(valueInThisUnit: Double): Double = valueInThisUnit * toMlFactor

    fun fromMl(valueInMl: Double): Double = valueInMl / toMlFactor
}

enum class CountUnitMultiplier(
    val label: String,
    val shortLabel: String,
    val factor: Double
) {
    MILLION("میلیون (×10⁶/mL)", "میلیون", 1_000_000.0),
    RAW("عدد کامل (سلول/mL)", "عدد کامل", 1.0);

    fun toggle(): CountUnitMultiplier = if (this == MILLION) RAW else MILLION

    fun toRaw(valueInThisUnit: Double): Double = valueInThisUnit * factor

    fun fromRaw(rawCells: Double): Double = rawCells / factor
}

enum class SerumCalculationBasis(
    val title: String,
    val description: String
) {
    ADDED_DILUENT(
        title = "درصد از حجم اضافه‌شده (مدیا + سرم)",
        description = "سرم معادل X% از حجم مایع اضافه‌شده (V₂ - V₁)"
    ),
    FINAL_TOTAL_VOLUME(
        title = "درصد از کل حجم نهایی (V₂)",
        description = "غلظت نهایی سرم در کل V₂ برابر X%"
    )
}

enum class StockUsageMode(
    val title: String,
    val subtitle: String
) {
    USE_ALL_CURRENT_VOLUME(
        title = "استفاده از کل حجم سلول فعلی (V₁)",
        subtitle = "حجم نهایی یا کانت تارگت اتوماتیک محاسبه می‌شود"
    ),
    WITHDRAW_FROM_STOCK(
        title = "برداشت بخشی از استوک برای حجم نهایی دلخواه",
        subtitle = "حجم برداشت سلول (V₁) بر اساس حجم نهایی حساب می‌شود"
    )
}

enum class ActiveEditedField {
    CURRENT_VOLUME,
    CURRENT_COUNT,
    TARGET_COUNT,
    FINAL_VOLUME,
    SERUM_PERCENT,
    INITIAL_SERUM_PERCENT,
    MEDIA_VOLUME,
    SERUM_VOLUME,
    TOTAL_ADDED_VOLUME
}

enum class TargetAnchor {
    HOLD_TARGET_COUNT,
    HOLD_FINAL_VOLUME
}

data class DilutionState(
    val currentVolumeText: String = "2",
    val currentCountText: String = "2.5",
    val targetCountText: String = "0.5",
    val finalVolumeText: String = "10",
    val serumPercentText: String = "3",
    val initialSerumPercentText: String = "0",
    val mediaVolumeText: String = "7.76",
    val serumVolumeText: String = "0.24",
    val totalAddedVolumeText: String = "8",
    val v1Unit: VolumeUnit = VolumeUnit.ML,
    val v2Unit: VolumeUnit = VolumeUnit.ML,
    val mediaUnit: VolumeUnit = VolumeUnit.ML,
    val serumUnit: VolumeUnit = VolumeUnit.ML,
    val addedUnit: VolumeUnit = VolumeUnit.ML,
    val c1Unit: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val c2Unit: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val serumBasis: SerumCalculationBasis = SerumCalculationBasis.ADDED_DILUENT,
    val stockUsageMode: StockUsageMode = StockUsageMode.USE_ALL_CURRENT_VOLUME,
    val targetAnchor: TargetAnchor = TargetAnchor.HOLD_TARGET_COUNT,
    val lastEditedField: ActiveEditedField = ActiveEditedField.TARGET_COUNT
) {
    // Canonical volumes in mL
    val currentVolumeMl: Double get() = v1Unit.toMl(NumberFormatter.parseInput(currentVolumeText))
    val finalVolumeMl: Double get() = v2Unit.toMl(NumberFormatter.parseInput(finalVolumeText))
    val mediaVolumeMl: Double get() = mediaUnit.toMl(NumberFormatter.parseInput(mediaVolumeText))
    val serumVolumeMl: Double get() = serumUnit.toMl(NumberFormatter.parseInput(serumVolumeText))
    val totalAddedVolumeMl: Double get() = addedUnit.toMl(NumberFormatter.parseInput(totalAddedVolumeText))

    // Canonical counts in raw cells/mL and in display unit
    val currentCountVal: Double get() = NumberFormatter.parseInput(currentCountText)
    val targetCountVal: Double get() = NumberFormatter.parseInput(targetCountText)
    val currentCountRawPerMl: Double get() = c1Unit.toRaw(currentCountVal)
    val targetCountRawPerMl: Double get() = c2Unit.toRaw(targetCountVal)

    val serumPercent: Double get() = NumberFormatter.parseInput(serumPercentText).coerceIn(0.0, 100.0)
    val initialSerumPercent: Double get() = NumberFormatter.parseInput(initialSerumPercentText).coerceIn(0.0, 100.0)

    val totalCellsInStock: Double
        get() = currentVolumeMl * currentCountRawPerMl

    val dilutionFactor: Double
        get() = if (targetCountRawPerMl > 0.0) currentCountRawPerMl / targetCountRawPerMl else 0.0

    val requiresCentrifugation: Boolean
        get() = currentCountRawPerMl > 0.0 && targetCountRawPerMl > currentCountRawPerMl

    val centrifugeResuspendMediaMl: Double
        get() = finalVolumeMl * (1.0 - serumPercent / 100.0)

    val centrifugeResuspendSerumMl: Double
        get() = finalVolumeMl * (serumPercent / 100.0)
}

data class CellSampleItem(
    val id: Int,
    val label: String,
    val volumeText: String,
    val countText: String,
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val countUnit: CountUnitMultiplier = CountUnitMultiplier.MILLION
) {
    val volumeMl: Double get() = volumeUnit.toMl(NumberFormatter.parseInput(volumeText))
    val countVal: Double get() = NumberFormatter.parseInput(countText)
    val countRawPerMl: Double get() = countUnit.toRaw(countVal)
    val totalRawCells: Double get() = volumeMl * countRawPerMl
}

data class MixerState(
    val samples: List<CellSampleItem> = listOf(
        CellSampleItem(1, "نمونه سلولی ۱", "3", "2.4", VolumeUnit.ML, CountUnitMultiplier.MILLION),
        CellSampleItem(2, "نمونه سلولی ۲", "2", "1.2", VolumeUnit.ML, CountUnitMultiplier.MILLION),
        CellSampleItem(3, "نمونه سلولی ۳", "1.5", "0.8", VolumeUnit.ML, CountUnitMultiplier.MILLION)
    ),
    val extraMediaVolumeText: String = "0",
    val extraMediaUnit: VolumeUnit = VolumeUnit.ML,
    val outputVolumeUnit: VolumeUnit = VolumeUnit.ML,
    val unitMultiplier: CountUnitMultiplier = CountUnitMultiplier.MILLION
) {
    val extraMediaMl: Double get() = extraMediaUnit.toMl(NumberFormatter.parseInput(extraMediaVolumeText))

    val totalCellVolumeMl: Double
        get() = samples.sumOf { it.volumeMl }

    val totalFinalMixVolumeMl: Double
        get() = totalCellVolumeMl + extraMediaMl

    val totalAbsoluteCells: Double
        get() = samples.sumOf { it.totalRawCells }

    val totalCellsInSelectedUnit: Double
        get() = unitMultiplier.fromRaw(totalAbsoluteCells)

    val mixedCountRawPerMl: Double
        get() = if (totalFinalMixVolumeMl > 0.0) totalAbsoluteCells / totalFinalMixVolumeMl else 0.0

    val mixedCountVal: Double
        get() = unitMultiplier.fromRaw(mixedCountRawPerMl)
}

enum class CellMarkerTool(val label: String) {
    ADD_LIVE("سلول زنده (+)"),
    ADD_DEAD("سلول مرده (+)"),
    ERASE("حذف نشانه")
}

data class CellPointMarker(
    val id: Long,
    val normalizedX: Float,
    val normalizedY: Float,
    val isLive: Boolean,
    val isOnGridLine: Boolean = false,
    val isOverlapping: Boolean = false
)

data class DetectedNeubauerGrid(
    val verticalLines: List<Float> = listOf(0.11f, 0.308f, 0.498f, 0.678f, 0.858f),
    val horizontalLines: List<Float> = listOf(0.065f, 0.285f, 0.502f, 0.704f, 0.902f),
    val detectedSmallSquares: Int = 16,
    val detectedLargeSquares: Int = 1
) {
    val totalLinesCount: Int get() = verticalLines.size + horizontalLines.size
}

sealed interface AiCellAnalysisUiState {
    data object Idle : AiCellAnalysisUiState
    data object Loading : AiCellAnalysisUiState
    data class Success(
        val summary: String,
        val liveCount: Int,
        val deadCount: Int,
        val onGridLineCount: Int = 0,
        val overlappingCount: Int = 0,
        val gridLinesDetected: Int = 0,
        val viableConcentrationPerMl: Double = 0.0,
        val totalConcentrationPerMl: Double = 0.0,
        val viabilityPercent: Double = 0.0
    ) : AiCellAnalysisUiState
    data class Error(val message: String) : AiCellAnalysisUiState
}

data class ImageCounterState(
    val markers: List<CellPointMarker> = emptyList(),
    val detectedGrid: DetectedNeubauerGrid = DetectedNeubauerGrid(),
    val activeTool: CellMarkerTool = CellMarkerTool.ADD_LIVE,
    val showGridOverlay: Boolean = true,
    val showFullScreenEditor: Boolean = false,
    val dilutionFactorText: String = "2",
    val squaresCountText: String = "1",
    val chamberFactorText: String = "10000",
    val sampleVolumeText: String = "5",
    val sampleVolumeUnit: VolumeUnit = VolumeUnit.ML,
    val resultCountUnit: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val detectionSensitivity: Float = 0.78f,
    val markerScale: Float = 0.45f,
    val imageSourceLabel: String = "عکسی انتخاب نشده — از گالری یا دوربین عکس لام را وارد کنید",
    val aiAnalysisState: AiCellAnalysisUiState = AiCellAnalysisUiState.Idle
) {
    val liveCount: Int get() = markers.count { it.isLive }
    val deadCount: Int get() = markers.count { !it.isLive }
    val totalCount: Int get() = liveCount + deadCount
    val onGridLineCount: Int get() = markers.count { it.isOnGridLine }
    val overlappingCount: Int get() = markers.count { it.isOverlapping }

    val viabilityPercent: Double
        get() = if (totalCount > 0) (liveCount.toDouble() / totalCount.toDouble()) * 100.0 else 0.0

    val dilutionFactor: Double
        get() = NumberFormatter.parseInput(dilutionFactorText).coerceAtLeast(1.0)

    val squaresCount: Double
        get() = NumberFormatter.parseInput(squaresCountText).coerceAtLeast(0.01)

    val chamberFactor: Double
        get() = NumberFormatter.parseInput(chamberFactorText).coerceAtLeast(1.0)

    val sampleVolumeMl: Double
        get() = sampleVolumeUnit.toMl(NumberFormatter.parseInput(sampleVolumeText).coerceAtLeast(0.0))

    val averageLivePerSquare: Double
        get() = liveCount.toDouble() / squaresCount

    val averageDeadPerSquare: Double
        get() = deadCount.toDouble() / squaresCount

    val averageTotalPerSquare: Double
        get() = totalCount.toDouble() / squaresCount

    val viableCellsPerMl: Double
        get() = averageLivePerSquare * dilutionFactor * chamberFactor

    val deadCellsPerMl: Double
        get() = averageDeadPerSquare * dilutionFactor * chamberFactor

    val totalCellsPerMl: Double
        get() = averageTotalPerSquare * dilutionFactor * chamberFactor

    val totalViableCellsInSample: Double
        get() = viableCellsPerMl * sampleVolumeMl

    val totalAllCellsInSample: Double
        get() = totalCellsPerMl * sampleVolumeMl

    companion object {
        fun defaultSampleMarkers(): List<CellPointMarker> = listOf(
            CellPointMarker(1, 0.16f, 0.18f, true),
            CellPointMarker(2, 0.24f, 0.22f, true),
            CellPointMarker(3, 0.34f, 0.15f, true),
            CellPointMarker(4, 0.46f, 0.19f, true),
            CellPointMarker(5, 0.58f, 0.21f, true),
            CellPointMarker(6, 0.71f, 0.16f, true),
            CellPointMarker(7, 0.81f, 0.17f, false),
            CellPointMarker(8, 0.19f, 0.33f, true),
            CellPointMarker(9, 0.29f, 0.38f, true),
            CellPointMarker(10, 0.43f, 0.34f, true),
            CellPointMarker(11, 0.54f, 0.37f, false),
            CellPointMarker(12, 0.66f, 0.41f, true),
            CellPointMarker(13, 0.77f, 0.35f, true),
            CellPointMarker(14, 0.84f, 0.39f, true),
            CellPointMarker(15, 0.18f, 0.52f, true),
            CellPointMarker(16, 0.31f, 0.55f, true),
            CellPointMarker(17, 0.39f, 0.59f, false),
            CellPointMarker(18, 0.51f, 0.53f, true),
            CellPointMarker(19, 0.61f, 0.64f, true),
            CellPointMarker(20, 0.73f, 0.58f, true),
            CellPointMarker(21, 0.82f, 0.62f, true),
            CellPointMarker(22, 0.22f, 0.74f, true),
            CellPointMarker(23, 0.35f, 0.78f, true),
            CellPointMarker(24, 0.48f, 0.73f, true),
            CellPointMarker(25, 0.59f, 0.80f, false),
            CellPointMarker(26, 0.69f, 0.76f, true),
            CellPointMarker(27, 0.79f, 0.83f, true)
        )
    }
}

data class NeubauerSquare(
    val index: Int,
    val name: String,
    val liveCountText: String,
    val deadCountText: String
) {
    val liveCount: Int get() = NumberFormatter.parseInput(liveCountText).toInt().coerceAtLeast(0)
    val deadCount: Int get() = NumberFormatter.parseInput(deadCountText).toInt().coerceAtLeast(0)
    val totalCount: Int get() = liveCount + deadCount
}

data class NeubauerState(
    val squares: List<NeubauerSquare> = listOf(
        NeubauerSquare(1, "خانه ۱ (بالا راست)", "42", "3"),
        NeubauerSquare(2, "خانه ۲ (بالا چپ)", "38", "2"),
        NeubauerSquare(3, "خانه ۳ (پایین راست)", "45", "4"),
        NeubauerSquare(4, "خانه ۴ (پایین چپ)", "39", "1")
    ),
    val trypanDilutionFactorText: String = "2",
    val sampleVolumeText: String = "5",
    val sampleVolumeUnit: VolumeUnit = VolumeUnit.ML,
    val resultCountUnit: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val chamberFactor: Double = 10_000.0
) {
    val activeSquaresCount: Int get() = squares.size.coerceAtLeast(1)
    val totalLiveCellsCounted: Int get() = squares.sumOf { it.liveCount }
    val totalDeadCellsCounted: Int get() = squares.sumOf { it.deadCount }
    val totalAllCellsCounted: Int get() = totalLiveCellsCounted + totalDeadCellsCounted

    val averageLivePerSquare: Double
        get() = totalLiveCellsCounted.toDouble() / activeSquaresCount

    val averageTotalPerSquare: Double
        get() = totalAllCellsCounted.toDouble() / activeSquaresCount

    val viabilityPercent: Double
        get() = if (totalAllCellsCounted > 0) {
            (totalLiveCellsCounted.toDouble() / totalAllCellsCounted.toDouble()) * 100.0
        } else 0.0

    val dilutionFactor: Double
        get() = NumberFormatter.parseInput(trypanDilutionFactorText).coerceAtLeast(1.0)

    val sampleVolumeMl: Double
        get() = sampleVolumeUnit.toMl(NumberFormatter.parseInput(sampleVolumeText).coerceAtLeast(0.0))

    val viableCellsPerMl: Double
        get() = averageLivePerSquare * dilutionFactor * chamberFactor

    val viableCountInMillions: Double
        get() = viableCellsPerMl / 1_000_000.0

    val viableCountInSelectedUnit: Double
        get() = resultCountUnit.fromRaw(viableCellsPerMl)

    val totalCellsPerMl: Double
        get() = averageTotalPerSquare * dilutionFactor * chamberFactor

    val totalViableCellsInSample: Double
        get() = viableCellsPerMl * sampleVolumeMl
}

enum class CultureVesselPreset(
    val title: String,
    val category: String,
    val defaultMediaPerWellMl: Double,
    val defaultTargetCellsPerWellMillion: Double,
    val defaultWellCount: Int
) {
    PLATE_6("پلیت ۶ خانه", "Plate", 2.0, 0.3, 6),
    PLATE_12("پلیت ۱۲ خانه", "Plate", 1.0, 0.12, 12),
    PLATE_24("پلیت ۲۴ خانه", "Plate", 0.5, 0.06, 24),
    PLATE_96("پلیت ۹۶ خانه", "Plate", 0.1, 0.01, 96),
    FLASK_T25("فلاسک T25", "Flask", 5.0, 0.7, 1),
    FLASK_T75("فلاسک T75", "Flask", 12.0, 2.1, 1),
    FLASK_T175("فلاسک T175", "Flask", 25.0, 4.9, 1)
}

data class SeedingState(
    val selectedPreset: CultureVesselPreset = CultureVesselPreset.PLATE_6,
    val wellCountText: String = "6",
    val volumePerWellText: String = "2",
    val volumePerWellUnit: VolumeUnit = VolumeUnit.ML,
    val targetCellsPerWellText: String = "0.3",
    val targetCellsPerWellUnit: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val stockCountText: String = "2.5",
    val stockCountUnit: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val serumPercentText: String = "10",
    val overfillPercentText: String = "10"
) {
    val wellCount: Double get() = NumberFormatter.parseInput(wellCountText).coerceAtLeast(1.0)
    val volumePerWellMl: Double get() = volumePerWellUnit.toMl(NumberFormatter.parseInput(volumePerWellText).coerceAtLeast(0.0001))
    val targetCellsPerWellRaw: Double get() = targetCellsPerWellUnit.toRaw(NumberFormatter.parseInput(targetCellsPerWellText).coerceAtLeast(0.0001))
    val stockCountRawPerMl: Double get() = stockCountUnit.toRaw(NumberFormatter.parseInput(stockCountText).coerceAtLeast(0.0001))
    val serumPercent: Double get() = NumberFormatter.parseInput(serumPercentText).coerceIn(0.0, 100.0)
    val overfillPercent: Double get() = NumberFormatter.parseInput(overfillPercentText).coerceIn(0.0, 100.0)

    val overfillMultiplier: Double get() = 1.0 + (overfillPercent / 100.0)
    val effectiveWells: Double get() = wellCount * overfillMultiplier

    val totalMasterMixVolumeMl: Double get() = effectiveWells * volumePerWellMl
    val totalCellsNeededRaw: Double get() = effectiveWells * targetCellsPerWellRaw

    val targetConcentrationRawPerMl: Double
        get() = if (volumePerWellMl > 0) targetCellsPerWellRaw / volumePerWellMl else 0.0

    val requiredCellStockVolumeMl: Double
        get() = if (stockCountRawPerMl > 0) totalCellsNeededRaw / stockCountRawPerMl else 0.0

    val remainingDiluentVolumeMl: Double
        get() = (totalMasterMixVolumeMl - requiredCellStockVolumeMl).coerceAtLeast(0.0)

    val requiredSerumVolumeMl: Double
        get() = remainingDiluentVolumeMl * (serumPercent / 100.0)

    val requiredBasalMediaVolumeMl: Double
        get() = (remainingDiluentVolumeMl - requiredSerumVolumeMl).coerceAtLeast(0.0)
}

data class SavedCalculationRecord(
    val id: Long,
    val title: String,
    val summary: String,
    val details: String,
    val timestampLabel: String
)

object NumberFormatter {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun normalizeInputString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var hasDot = false
        for (ch in raw) {
            val pIdx = persianDigits.indexOf(ch)
            if (pIdx >= 0) {
                sb.append(('0'.code + pIdx).toChar())
                continue
            }
            val aIdx = arabicDigits.indexOf(ch)
            if (aIdx >= 0) {
                sb.append(('0'.code + aIdx).toChar())
                continue
            }
            when (ch) {
                ',', '٬', ' ', '_' -> continue
                '٫', '/', '.' -> {
                    if (!hasDot) {
                        sb.append('.')
                        hasDot = true
                    }
                }
                else -> if (ch.isDigit()) sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseInput(raw: String): Double {
        val normalized = normalizeInputString(raw.trim())
        if (normalized.isEmpty() || normalized == ".") return 0.0
        return normalized.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    fun formatClean(value: Double, maxDecimals: Int = 4): String {
        if (!value.isFinite() || value <= 0.0) return "0"
        val factor = when (maxDecimals) {
            0 -> 1.0
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            4 -> 10000.0
            5 -> 100000.0
            6 -> 1000000.0
            else -> 10000.0
        }
        val rounded = (value * factor).roundToLong() / factor
        val asLong = rounded.toLong()
        return if (abs(rounded - asLong) < 1e-8) {
            asLong.toString()
        } else {
            rounded.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    fun addThousandsSeparator(rawStr: String): String {
        if (rawStr.isEmpty()) return ""
        val clean = normalizeInputString(rawStr)
        if (clean.isEmpty()) return ""
        val dotIndex = clean.indexOf('.')
        val intPart = if (dotIndex >= 0) clean.substring(0, dotIndex) else clean
        val decPart = if (dotIndex >= 0) clean.substring(dotIndex) else ""

        if (intPart.length <= 3) return intPart + decPart

        val sb = StringBuilder(intPart.length + intPart.length / 3 + decPart.length)
        for (i in intPart.indices) {
            if (i > 0 && (intPart.length - i) % 3 == 0) {
                sb.append(',')
            }
            sb.append(intPart[i])
        }
        sb.append(decPart)
        return sb.toString()
    }

    fun formatWithCommas(value: Double, maxDecimals: Int = 4): String {
        return addThousandsSeparator(formatClean(value, maxDecimals))
    }

    fun formatCountForUnit(rawCells: Double, unit: CountUnitMultiplier): String {
        val converted = unit.fromRaw(rawCells)
        return if (unit == CountUnitMultiplier.RAW) {
            formatClean(converted, 0)
        } else {
            formatClean(converted, 4)
        }
    }

    fun formatCountForUnitWithCommas(rawCells: Double, unit: CountUnitMultiplier): String {
        return addThousandsSeparator(formatCountForUnit(rawCells, unit))
    }

    fun formatVolumeMlAndL(volumeMl: Double, primaryUnit: VolumeUnit = VolumeUnit.ML): String {
        if (!volumeMl.isFinite() || volumeMl <= 0.0) return "0 mL (0 L)"
        val mlStr = formatWithCommas(volumeMl, 3)
        val lVal = volumeMl / 1000.0
        val lStr = formatWithCommas(lVal, 5)
        return if (primaryUnit == VolumeUnit.ML) {
            "$mlStr mL ($lStr L)"
        } else {
            "$lStr L ($mlStr mL)"
        }
    }

    fun formatOppositeUnitNumber(volumeMl: Double, currentUnit: VolumeUnit): String {
        if (!volumeMl.isFinite() || volumeMl <= 0.0) return "0"
        return if (currentUnit == VolumeUnit.ML) {
            formatWithCommas(volumeMl / 1000.0, 5)
        } else {
            formatWithCommas(volumeMl, 3)
        }
    }

    fun formatOppositeUnitHelper(volumeMl: Double, currentUnit: VolumeUnit): String {
        val oppUnit = currentUnit.toggle()
        val numStr = formatOppositeUnitNumber(volumeMl, currentUnit)
        return "$numStr ${oppUnit.symbol}"
    }

    fun formatOppositeCountNumber(rawCells: Double, currentUnit: CountUnitMultiplier): String {
        if (!rawCells.isFinite() || rawCells <= 0.0) return "0"
        return if (currentUnit == CountUnitMultiplier.MILLION) {
            formatWithCommas(rawCells, 0)
        } else {
            formatWithCommas(rawCells / 1_000_000.0, 4)
        }
    }

    fun formatOppositeCountHelper(rawCells: Double, currentUnit: CountUnitMultiplier): String {
        val oppUnit = currentUnit.toggle()
        val numStr = formatOppositeCountNumber(rawCells, currentUnit)
        return "$numStr ${oppUnit.shortLabel}"
    }

    fun formatCountBothUnits(rawCells: Double, primaryUnit: CountUnitMultiplier = CountUnitMultiplier.MILLION): String {
        if (!rawCells.isFinite() || rawCells <= 0.0) return "0 میلیون (0)"
        val millionStr = "${formatWithCommas(rawCells / 1_000_000.0, 3)} میلیون"
        val rawStr = "${formatWithCommas(rawCells, 0)} عدد کامل"
        return if (primaryUnit == CountUnitMultiplier.MILLION) {
            "$millionStr ($rawStr)"
        } else {
            "$rawStr ($millionStr)"
        }
    }

    fun formatScientificCount(totalCells: Double): String {
        if (!totalCells.isFinite() || totalCells <= 0.0) return "0"
        return if (totalCells >= 100_000.0) {
            "${formatWithCommas(totalCells / 1_000_000.0, 3)} میلیون (${formatWithCommas(totalCells, 0)})"
        } else {
            formatWithCommas(totalCells, 0)
        }
    }
}
