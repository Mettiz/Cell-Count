package com.example.model

import kotlin.math.abs
import kotlin.math.roundToLong

enum class AppTab {
    DILUTION_SERUM,
    CELL_MIXER,
    NEUBAUER_COUNTER,
    SEEDING_AND_HISTORY
}

enum class CountUnitMultiplier(
    val label: String,
    val shortLabel: String,
    val factor: Double
) {
    MILLION("×10⁶ سلول/mL (میلیون)", "×10⁶/mL", 1_000_000.0),
    HUNDRED_THOUSAND("×10⁵ سلول/mL (صدهزار)", "×10⁵/mL", 100_000.0),
    TEN_THOUSAND("×10⁴ سلول/mL (ده‌هزار)", "×10⁴/mL", 10_000.0),
    RAW("عدد کامل (سلول/mL)", "cells/mL", 1.0)
}

/**
 * Defines how the Serum % (e.g., 3% FBS) is calculated:
 * - ADDED_DILUENT: Serum is X% of the newly added volume (V2 - V1).
 *   Example: V_add = 4 mL with 3% serum -> 0.12 mL (120 µL) serum + 3.88 mL media.
 * - FINAL_TOTAL_VOLUME: Serum is X% of the entire final volume (V2), taking into account
 *   any initial serum percentage in V1 (default 0% if cells are in basal media/PBS).
 */
enum class SerumCalculationBasis(
    val title: String,
    val description: String
) {
    ADDED_DILUENT(
        title = "درصد از حجم اضافه‌شده (مدیا + سرم جدید)",
        description = "حجم سرم معادل X% از حجم مایع اضافه‌شده (V₂ - V₁) محاسبه می‌شود"
    ),
    FINAL_TOTAL_VOLUME(
        title = "درصد از کل حجم نهایی (V₂)",
        description = "حجم سرم به گونه‌ای محاسبه می‌شود که غلظت نهایی سرم در کل V₂ برابر X% شود"
    )
}

/**
 * Defines whether the user is diluting their entire current cell suspension (V1 fixed)
 * or calculating how much stock volume (V1) to withdraw to make a target V2 at target C2.
 */
enum class StockUsageMode(
    val title: String,
    val subtitle: String
) {
    USE_ALL_CURRENT_VOLUME(
        title = "استفاده از کل حجم سلول فعلی (V₁)",
        subtitle = "با تغییر کانت تارگت، حجم نهایی (و بالعکس) اتوماتیک حساب می‌شود"
    ),
    WITHDRAW_FROM_STOCK(
        title = "برداشت بخشی از استوک برای حجم و کانت دلخواه",
        subtitle = "حجم نهایی و کانت تارگت ثابت مانده و حجم برداشت سلول (V₁) حساب می‌شود"
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
    HOLD_TARGET_COUNT, // Keep C2 fixed, auto-solve V2 when V1 or C1 changes
    HOLD_FINAL_VOLUME  // Keep V2 fixed, auto-solve C2 when V1 or C1 changes
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
    val unitMultiplier: CountUnitMultiplier = CountUnitMultiplier.MILLION,
    val serumBasis: SerumCalculationBasis = SerumCalculationBasis.ADDED_DILUENT,
    val stockUsageMode: StockUsageMode = StockUsageMode.USE_ALL_CURRENT_VOLUME,
    val targetAnchor: TargetAnchor = TargetAnchor.HOLD_TARGET_COUNT,
    val lastEditedField: ActiveEditedField = ActiveEditedField.TARGET_COUNT
) {
    val currentVolumeMl: Double get() = NumberFormatter.parseInput(currentVolumeText)
    val currentCountVal: Double get() = NumberFormatter.parseInput(currentCountText)
    val targetCountVal: Double get() = NumberFormatter.parseInput(targetCountText)
    val finalVolumeMl: Double get() = NumberFormatter.parseInput(finalVolumeText)
    val serumPercent: Double get() = NumberFormatter.parseInput(serumPercentText).coerceIn(0.0, 100.0)
    val initialSerumPercent: Double get() = NumberFormatter.parseInput(initialSerumPercentText).coerceIn(0.0, 100.0)
    val mediaVolumeMl: Double get() = NumberFormatter.parseInput(mediaVolumeText)
    val serumVolumeMl: Double get() = NumberFormatter.parseInput(serumVolumeText)
    val totalAddedVolumeMl: Double get() = NumberFormatter.parseInput(totalAddedVolumeText)

    val totalCellsInStock: Double
        get() = currentVolumeMl * currentCountVal * unitMultiplier.factor

    val dilutionFactor: Double
        get() = if (targetCountVal > 0.0) currentCountVal / targetCountVal else 0.0

    val requiresCentrifugation: Boolean
        get() = currentCountVal > 0.0 && targetCountVal > currentCountVal

    // Compute alternate serum basis values for instant comparison in the UI
    val addedBasisSerumMl: Double
        get() {
            val added = (finalVolumeMl - currentVolumeMl).coerceAtLeast(0.0)
            return added * (serumPercent / 100.0)
        }

    val addedBasisMediaMl: Double
        get() {
            val added = (finalVolumeMl - currentVolumeMl).coerceAtLeast(0.0)
            return (added - addedBasisSerumMl).coerceAtLeast(0.0)
        }

    val totalBasisSerumMl: Double
        get() {
            val totalNeeded = finalVolumeMl * (serumPercent / 100.0)
            val alreadyInStock = currentVolumeMl * (initialSerumPercent / 100.0)
            val added = (finalVolumeMl - currentVolumeMl).coerceAtLeast(0.0)
            return (totalNeeded - alreadyInStock).coerceIn(0.0, added)
        }

    val totalBasisMediaMl: Double
        get() {
            val added = (finalVolumeMl - currentVolumeMl).coerceAtLeast(0.0)
            return (added - totalBasisSerumMl).coerceAtLeast(0.0)
        }

    // For centrifugation case (when Target Count > Current Count, so V2 < V1)
    val centrifugeResuspendMediaMl: Double
        get() = finalVolumeMl * (1.0 - serumPercent / 100.0)

    val centrifugeResuspendSerumMl: Double
        get() = finalVolumeMl * (serumPercent / 100.0)
}

data class CellSampleItem(
    val id: Int,
    val label: String,
    val volumeText: String,
    val countText: String
) {
    val volumeMl: Double get() = NumberFormatter.parseInput(volumeText)
    val countVal: Double get() = NumberFormatter.parseInput(countText)
    val totalCellsScaled: Double get() = volumeMl * countVal
}

data class MixerState(
    val samples: List<CellSampleItem> = listOf(
        CellSampleItem(1, "نمونه سلولی ۱ (فلاسک A)", "3", "2.4"),
        CellSampleItem(2, "نمونه سلولی ۲ (فلاسک B)", "2", "1.2"),
        CellSampleItem(3, "نمونه سلولی ۳ (فلاسک C)", "1.5", "0.8")
    ),
    val extraMediaVolumeText: String = "0",
    val unitMultiplier: CountUnitMultiplier = CountUnitMultiplier.MILLION
) {
    val extraMediaMl: Double get() = NumberFormatter.parseInput(extraMediaVolumeText)

    val totalCellVolumeMl: Double
        get() = samples.sumOf { it.volumeMl }

    val totalFinalMixVolumeMl: Double
        get() = totalCellVolumeMl + extraMediaMl

    val totalCellsScaled: Double
        get() = samples.sumOf { it.totalCellsScaled }

    val totalAbsoluteCells: Double
        get() = totalCellsScaled * unitMultiplier.factor

    val mixedCountVal: Double
        get() = if (totalFinalMixVolumeMl > 0.0) totalCellsScaled / totalFinalMixVolumeMl else 0.0
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
    val sampleVolumeMlText: String = "5",
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
        get() = NumberFormatter.parseInput(sampleVolumeMlText).coerceAtLeast(0.0)

    // Cells per mL = Avg per square * Dilution Factor * 10^4
    val viableCellsPerMl: Double
        get() = averageLivePerSquare * dilutionFactor * chamberFactor

    val viableCountInMillions: Double
        get() = viableCellsPerMl / 1_000_000.0

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
    PLATE_6("پلیت ۶ خانه (6-Well)", "Plate", 2.0, 0.3, 6),
    PLATE_12("پلیت ۱۲ خانه (12-Well)", "Plate", 1.0, 0.12, 12),
    PLATE_24("پلیت ۲۴ خانه (24-Well)", "Plate", 0.5, 0.06, 24),
    PLATE_96("پلیت ۹۶ خانه (96-Well)", "Plate", 0.1, 0.01, 96),
    FLASK_T25("فلاسک T25", "Flask", 5.0, 0.7, 1),
    FLASK_T75("فلاسک T75", "Flask", 12.0, 2.1, 1),
    FLASK_T175("فلاسک T175", "Flask", 25.0, 4.9, 1)
}

data class SeedingState(
    val selectedPreset: CultureVesselPreset = CultureVesselPreset.PLATE_6,
    val wellCountText: String = "6",
    val volumePerWellMlText: String = "2",
    val targetCellsPerWellMillionText: String = "0.3",
    val stockCountMillionText: String = "2.5",
    val serumPercentText: String = "10",
    val overfillPercentText: String = "10" // 10% extra for pipetting loss
) {
    val wellCount: Double get() = NumberFormatter.parseInput(wellCountText).coerceAtLeast(1.0)
    val volumePerWellMl: Double get() = NumberFormatter.parseInput(volumePerWellMlText).coerceAtLeast(0.01)
    val targetCellsPerWellMillion: Double get() = NumberFormatter.parseInput(targetCellsPerWellMillionText).coerceAtLeast(0.0001)
    val stockCountMillion: Double get() = NumberFormatter.parseInput(stockCountMillionText).coerceAtLeast(0.0001)
    val serumPercent: Double get() = NumberFormatter.parseInput(serumPercentText).coerceIn(0.0, 100.0)
    val overfillPercent: Double get() = NumberFormatter.parseInput(overfillPercentText).coerceIn(0.0, 100.0)

    val overfillMultiplier: Double get() = 1.0 + (overfillPercent / 100.0)
    val effectiveWells: Double get() = wellCount * overfillMultiplier

    val totalMasterMixVolumeMl: Double get() = effectiveWells * volumePerWellMl
    val totalCellsNeededMillion: Double get() = effectiveWells * targetCellsPerWellMillion
    val targetConcentrationMillionPerMl: Double
        get() = if (volumePerWellMl > 0) targetCellsPerWellMillion / volumePerWellMl else 0.0

    val requiredCellStockVolumeMl: Double
        get() = if (stockCountMillion > 0) totalCellsNeededMillion / stockCountMillion else 0.0

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
                '٫', ',', '/' -> sb.append('.')
                else -> if (ch.isDigit() || ch == '.') sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseInput(raw: String): Double {
        val normalized = normalizeInputString(raw.trim())
        if (normalized.isEmpty() || normalized == ".") return 0.0
        return normalized.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    fun formatClean(value: Double, maxDecimals: Int = 3): String {
        if (!value.isFinite() || value <= 0.0) return "0"
        val factor = when (maxDecimals) {
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            4 -> 10000.0
            else -> 1000.0
        }
        val rounded = (value * factor).roundToLong() / factor
        val asLong = rounded.toLong()
        return if (abs(rounded - asLong) < 1e-7) {
            asLong.toString()
        } else {
            rounded.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    fun formatVolumeWithMicroliter(volumeMl: Double): String {
        if (!volumeMl.isFinite() || volumeMl <= 0.0) return "0 mL (0 µL)"
        val mlStr = formatClean(volumeMl, 3)
        val ulVal = volumeMl * 1000.0
        val ulStr = formatClean(ulVal, 1)
        return "$mlStr mL ($ulStr µL)"
    }

    fun formatScientificCount(totalCells: Double): String {
        if (!totalCells.isFinite() || totalCells <= 0.0) return "0"
        return when {
            totalCells >= 1_000_000.0 -> "${formatClean(totalCells / 1_000_000.0, 3)} × 10⁶"
            totalCells >= 10_000.0 -> "${formatClean(totalCells / 10_000.0, 2)} × 10⁴"
            else -> formatClean(totalCells, 0)
        }
    }
}
