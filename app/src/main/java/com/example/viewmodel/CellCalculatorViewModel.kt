package com.example.viewmodel

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ActiveEditedField
import com.example.model.AiCellAnalysisUiState
import com.example.model.AiModelOption
import com.example.model.AppTab
import com.example.model.CellMarkerTool
import com.example.model.CellPointMarker
import com.example.model.CellSampleItem
import com.example.model.CountUnitMultiplier
import com.example.model.CultureVesselPreset
import com.example.model.DetectedNeubauerGrid
import com.example.model.DilutionState
import com.example.model.ImageCounterState
import com.example.model.MixerState
import com.example.model.NeubauerState
import com.example.model.NumberFormatter
import com.example.model.SavedCalculationRecord
import com.example.model.SeedingState
import com.example.model.SerumCalculationBasis
import com.example.model.StockUsageMode
import com.example.model.TargetAnchor
import com.example.model.VolumeUnit
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CellCalculatorVM"

class CellCalculatorViewModel : ViewModel() {

    private data class DetectedCellCandidate(
        val normalizedX: Float,
        val normalizedY: Float,
        val blobScore: Float,
        val viabilityScore: Float,
        val defaultIsLive: Boolean,
        val isOnGridLine: Boolean = false,
        val isOverlapping: Boolean = false
    )

    private data class FullSlideAnalysisResult(
        val candidates: List<DetectedCellCandidate>,
        val detectedGrid: DetectedNeubauerGrid
    )

    private val aiCellItemSchema = Schema.obj(
        properties = mapOf(
            "x" to Schema.integer(description = "Normalized X coordinate from 0 to 1000 on the original image"),
            "y" to Schema.integer(description = "Normalized Y coordinate from 0 to 1000 on the original image"),
            "isLive" to Schema.boolean(description = "true if live (viable, bright refractile center), false if dead (dark/Trypan-blue stained)"),
            "onGridLine" to Schema.boolean(description = "true if this cell is sitting on or touching a Neubauer grid line"),
            "isOverlapping" to Schema.boolean(description = "true if this cell is part of an overlapping/touching doublet or cluster")
        )
    )

    private val aiCellDetectionSchema = Schema.obj(
        properties = mapOf(
            "liveCount" to Schema.integer(description = "Total number of live (viable, bright/refractile, unstained) cells directly detected in the image"),
            "deadCount" to Schema.integer(description = "Total number of dead (non-viable, Trypan-blue stained or dark/lysed) cells directly detected in the image"),
            "onGridLineCount" to Schema.integer(description = "Number of cells located on or touching the Neubauer grid lines"),
            "overlappingCellCount" to Schema.integer(description = "Number of individual cells resolved inside overlapping or touching cell clusters/doublets"),
            "detectedSquaresCount" to Schema.integer(description = "Number of large Neubauer counting squares visible in the image (typically 1)"),
            "brightCenterLive" to Schema.boolean(description = "true if live cells have bright refractile centers/halos, false if inverted contrast"),
            "verticalGridLines" to Schema.array(
                items = Schema.integer(),
                description = "Normalized X coordinates (0..1000) of vertical Neubauer grid lines detected in the image"
            ),
            "horizontalGridLines" to Schema.array(
                items = Schema.integer(),
                description = "Normalized Y coordinates (0..1000) of horizontal Neubauer grid lines detected in the image"
            ),
            "detectedCells" to Schema.array(
                items = aiCellItemSchema,
                description = "List of cells directly localized from the original microscope image"
            ),
            "analysisSummary" to Schema.string(description = "Comprehensive Persian explanation of the full image analysis: live cells, dead cells, Neubauer grid lines, cells on grid lines, overlapping cells, and final cell count")
        ),
        optionalProperties = listOf("verticalGridLines", "horizontalGridLines", "detectedCells", "detectedSquaresCount")
    )

    private val aiCellDetectionConfig by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = aiCellDetectionSchema
            temperature = 0.1f
            maxOutputTokens = 4096
        }
    }

    private val geminiArgonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = aiCellDetectionConfig
        )
    }

    private val gemini38FlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = aiCellDetectionConfig
        )
    }

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedTab = MutableStateFlow(AppTab.DILUTION_SERUM)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _dilutionState = MutableStateFlow(DilutionState())
    val dilutionState: StateFlow<DilutionState> = _dilutionState.asStateFlow()

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val _imageCounterState = MutableStateFlow(ImageCounterState())
    val imageCounterState: StateFlow<ImageCounterState> = _imageCounterState.asStateFlow()

    private val _neubauerState = MutableStateFlow(NeubauerState())
    val neubauerState: StateFlow<NeubauerState> = _neubauerState.asStateFlow()

    private val _seedingState = MutableStateFlow(SeedingState())
    val seedingState: StateFlow<SeedingState> = _seedingState.asStateFlow()

    private val _savedRecords = MutableStateFlow(
        listOf(
            SavedCalculationRecord(
                id = 1L,
                title = "رقت سلول با ۳٪ سرم (نمونه پیش‌فرض)",
                summary = "V₁: 2 mL (کانت 2.5 میلیون) ⟵ V₂: 10 mL (کانت تارگت 0.5 میلیون)",
                details = "مدیا پایه: 7.76 mL (0.00776 L) | سرم (۳٪): 0.24 mL (0.00024 L)",
                timestampLabel = "آماده در آزمایشگاه"
            )
        )
    )
    val savedRecords: StateFlow<List<SavedCalculationRecord>> = _savedRecords.asStateFlow()

    private var nextSampleId = 4
    private var nextRecordId = 2L
    private var nextMarkerId = 100L

    fun toggleDarkMode() {
        _isDarkMode.update { !it }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    // =========================================================================
    // TAB 1: OMNI-DIRECTIONAL DILUTION, MEDIA & SERUM CALCULATOR
    // =========================================================================

    fun setAllDilutionVolumeUnits(unit: VolumeUnit) {
        _dilutionState.update { state ->
            val v1Ml = state.currentVolumeMl
            val v2Ml = state.finalVolumeMl
            val mediaMl = state.mediaVolumeMl
            val serumMl = state.serumVolumeMl
            val addedMl = state.totalAddedVolumeMl
            state.copy(
                v1Unit = unit,
                v2Unit = unit,
                mediaUnit = unit,
                serumUnit = unit,
                addedUnit = unit,
                currentVolumeText = NumberFormatter.formatClean(unit.fromMl(v1Ml), 5),
                finalVolumeText = NumberFormatter.formatClean(unit.fromMl(v2Ml), 5),
                mediaVolumeText = NumberFormatter.formatClean(unit.fromMl(mediaMl), 5),
                serumVolumeText = NumberFormatter.formatClean(unit.fromMl(serumMl), 5),
                totalAddedVolumeText = NumberFormatter.formatClean(unit.fromMl(addedMl), 5)
            )
        }
    }

    fun setAllDilutionCountUnits(unit: CountUnitMultiplier) {
        _dilutionState.update { state ->
            val c1Raw = state.currentCountRawPerMl
            val c2Raw = state.targetCountRawPerMl
            state.copy(
                c1Unit = unit,
                c2Unit = unit,
                currentCountText = NumberFormatter.formatCountForUnit(c1Raw, unit),
                targetCountText = NumberFormatter.formatCountForUnit(c2Raw, unit)
            )
        }
    }

    fun toggleV1Unit() {
        _dilutionState.update { state ->
            val currentMl = state.currentVolumeMl
            val nextUnit = state.v1Unit.toggle()
            state.copy(
                v1Unit = nextUnit,
                currentVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleV2Unit() {
        _dilutionState.update { state ->
            val currentMl = state.finalVolumeMl
            val nextUnit = state.v2Unit.toggle()
            state.copy(
                v2Unit = nextUnit,
                finalVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleMediaUnit() {
        _dilutionState.update { state ->
            val currentMl = state.mediaVolumeMl
            val nextUnit = state.mediaUnit.toggle()
            state.copy(
                mediaUnit = nextUnit,
                mediaVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleSerumUnit() {
        _dilutionState.update { state ->
            val currentMl = state.serumVolumeMl
            val nextUnit = state.serumUnit.toggle()
            state.copy(
                serumUnit = nextUnit,
                serumVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleAddedUnit() {
        _dilutionState.update { state ->
            val currentMl = state.totalAddedVolumeMl
            val nextUnit = state.addedUnit.toggle()
            state.copy(
                addedUnit = nextUnit,
                totalAddedVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleC1Unit() {
        _dilutionState.update { state ->
            val rawCells = state.currentCountRawPerMl
            val nextUnit = state.c1Unit.toggle()
            state.copy(
                c1Unit = nextUnit,
                currentCountText = NumberFormatter.formatCountForUnit(rawCells, nextUnit)
            )
        }
    }

    fun toggleC2Unit() {
        _dilutionState.update { state ->
            val rawCells = state.targetCountRawPerMl
            val nextUnit = state.c2Unit.toggle()
            state.copy(
                c2Unit = nextUnit,
                targetCountText = NumberFormatter.formatCountForUnit(rawCells, nextUnit)
            )
        }
    }

    fun onCurrentVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val v1Ml = state.v1Unit.toMl(NumberFormatter.parseInput(clean))
            val c1Raw = state.currentCountRawPerMl
            val updated = state.copy(
                currentVolumeText = clean,
                lastEditedField = ActiveEditedField.CURRENT_VOLUME
            )
            recalculateFromStockChange(updated, v1Ml, c1Raw)
        }
    }

    fun onCurrentCountChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val c1Raw = state.c1Unit.toRaw(NumberFormatter.parseInput(clean))
            val v1Ml = state.currentVolumeMl
            val updated = state.copy(
                currentCountText = clean,
                lastEditedField = ActiveEditedField.CURRENT_COUNT
            )
            recalculateFromStockChange(updated, v1Ml, c1Raw)
        }
    }

    fun onTargetCountChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val c2Raw = state.c2Unit.toRaw(NumberFormatter.parseInput(clean))
            val c1Raw = state.currentCountRawPerMl
            val baseState = state.copy(
                targetCountText = clean,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT,
                lastEditedField = ActiveEditedField.TARGET_COUNT
            )
            if (c2Raw <= 0.0 || c1Raw <= 0.0) {
                baseState
            } else if (state.stockUsageMode == StockUsageMode.USE_ALL_CURRENT_VOLUME) {
                val v1Ml = state.currentVolumeMl
                val v2Ml = (c1Raw * v1Ml) / c2Raw
                updateVolumesFromV1AndV2(baseState, v1Ml, v2Ml)
            } else {
                val v2Ml = state.finalVolumeMl
                val v1Ml = (c2Raw * v2Ml) / c1Raw
                updateVolumesFromV1AndV2(
                    baseState.copy(
                        currentVolumeText = NumberFormatter.formatClean(state.v1Unit.fromMl(v1Ml), 5)
                    ),
                    v1Ml,
                    v2Ml
                )
            }
        }
    }

    fun onFinalVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val v2Ml = state.v2Unit.toMl(NumberFormatter.parseInput(clean))
            val c1Raw = state.currentCountRawPerMl
            val baseState = state.copy(
                finalVolumeText = clean,
                targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                lastEditedField = ActiveEditedField.FINAL_VOLUME
            )
            if (v2Ml <= 0.0 || c1Raw <= 0.0) {
                baseState
            } else if (state.stockUsageMode == StockUsageMode.USE_ALL_CURRENT_VOLUME) {
                val v1Ml = state.currentVolumeMl
                val c2Raw = (c1Raw * v1Ml) / v2Ml
                val withTarget = baseState.copy(
                    targetCountText = NumberFormatter.formatCountForUnit(c2Raw, state.c2Unit)
                )
                updateMediaAndSerumFromV1AndV2(withTarget, v1Ml, v2Ml)
            } else {
                val c2Raw = state.targetCountRawPerMl
                val v1Ml = if (c1Raw > 0.0) (c2Raw * v2Ml) / c1Raw else 0.0
                val withV1 = baseState.copy(
                    currentVolumeText = NumberFormatter.formatClean(state.v1Unit.fromMl(v1Ml), 5)
                )
                updateMediaAndSerumFromV1AndV2(withV1, v1Ml, v2Ml)
            }
        }
    }

    fun onSerumPercentChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val sPercent = NumberFormatter.parseInput(clean).coerceIn(0.0, 100.0)
            val updated = state.copy(
                serumPercentText = clean,
                lastEditedField = ActiveEditedField.SERUM_PERCENT
            )
            updateMediaAndSerumFromV1AndV2(
                updated,
                updated.currentVolumeMl,
                updated.finalVolumeMl,
                overrideSerumPercent = sPercent
            )
        }
    }

    fun onInitialSerumPercentChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val updated = state.copy(
                initialSerumPercentText = clean,
                lastEditedField = ActiveEditedField.INITIAL_SERUM_PERCENT
            )
            updateMediaAndSerumFromV1AndV2(
                updated,
                updated.currentVolumeMl,
                updated.finalVolumeMl
            )
        }
    }

    fun onMediaVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val vMediaMl = state.mediaUnit.toMl(NumberFormatter.parseInput(clean))
            val v1Ml = state.currentVolumeMl
            val c1Raw = state.currentCountRawPerMl
            val sFraction = (state.serumPercent / 100.0).coerceIn(0.0, 0.9999)

            val vSerumMl: Double
            val vAddMl: Double
            val v2Ml: Double

            if (state.serumBasis == SerumCalculationBasis.ADDED_DILUENT) {
                vAddMl = if (1.0 - sFraction > 0.0) vMediaMl / (1.0 - sFraction) else vMediaMl
                vSerumMl = (vAddMl - vMediaMl).coerceAtLeast(0.0)
                v2Ml = v1Ml + vAddMl
            } else {
                val initFrac = (state.initialSerumPercent / 100.0).coerceIn(0.0, 1.0)
                val numerator = (v1Ml + vMediaMl) * sFraction - (v1Ml * initFrac)
                vSerumMl = if (1.0 - sFraction > 0.0) (numerator / (1.0 - sFraction)).coerceAtLeast(0.0) else 0.0
                vAddMl = vMediaMl + vSerumMl
                v2Ml = v1Ml + vAddMl
            }

            val c2Raw = if (v2Ml > 0.0) (c1Raw * v1Ml) / v2Ml else 0.0

            state.copy(
                mediaVolumeText = clean,
                serumVolumeText = NumberFormatter.formatClean(state.serumUnit.fromMl(vSerumMl), 5),
                totalAddedVolumeText = NumberFormatter.formatClean(state.addedUnit.fromMl(vAddMl), 5),
                finalVolumeText = NumberFormatter.formatClean(state.v2Unit.fromMl(v2Ml), 5),
                targetCountText = NumberFormatter.formatCountForUnit(c2Raw, state.c2Unit),
                targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                lastEditedField = ActiveEditedField.MEDIA_VOLUME
            )
        }
    }

    fun onSerumVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val vSerumMl = state.serumUnit.toMl(NumberFormatter.parseInput(clean))
            val v1Ml = state.currentVolumeMl
            val c1Raw = state.currentCountRawPerMl
            val sFraction = (state.serumPercent / 100.0).coerceIn(0.0, 1.0)

            if (sFraction > 0.0) {
                val vAddMl: Double
                val vMediaMl: Double
                if (state.serumBasis == SerumCalculationBasis.ADDED_DILUENT) {
                    vAddMl = vSerumMl / sFraction
                    vMediaMl = (vAddMl - vSerumMl).coerceAtLeast(0.0)
                } else {
                    val initFrac = (state.initialSerumPercent / 100.0).coerceIn(0.0, 1.0)
                    val v2Target = (vSerumMl + v1Ml * initFrac) / sFraction
                    vAddMl = (v2Target - v1Ml).coerceAtLeast(vSerumMl)
                    vMediaMl = (vAddMl - vSerumMl).coerceAtLeast(0.0)
                }
                val v2Ml = v1Ml + vAddMl
                val c2Raw = if (v2Ml > 0.0) (c1Raw * v1Ml) / v2Ml else 0.0
                state.copy(
                    serumVolumeText = clean,
                    mediaVolumeText = NumberFormatter.formatClean(state.mediaUnit.fromMl(vMediaMl), 5),
                    totalAddedVolumeText = NumberFormatter.formatClean(state.addedUnit.fromMl(vAddMl), 5),
                    finalVolumeText = NumberFormatter.formatClean(state.v2Unit.fromMl(v2Ml), 5),
                    targetCountText = NumberFormatter.formatCountForUnit(c2Raw, state.c2Unit),
                    targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                    lastEditedField = ActiveEditedField.SERUM_VOLUME
                )
            } else {
                val vMediaMl = state.mediaVolumeMl
                val vAddMl = vMediaMl + vSerumMl
                val v2Ml = v1Ml + vAddMl
                val c2Raw = if (v2Ml > 0.0) (c1Raw * v1Ml) / v2Ml else 0.0
                val newSerumPct = if (vAddMl > 0.0) (vSerumMl / vAddMl) * 100.0 else 0.0
                state.copy(
                    serumVolumeText = clean,
                    serumPercentText = NumberFormatter.formatClean(newSerumPct, 2),
                    totalAddedVolumeText = NumberFormatter.formatClean(state.addedUnit.fromMl(vAddMl), 5),
                    finalVolumeText = NumberFormatter.formatClean(state.v2Unit.fromMl(v2Ml), 5),
                    targetCountText = NumberFormatter.formatCountForUnit(c2Raw, state.c2Unit),
                    lastEditedField = ActiveEditedField.SERUM_VOLUME
                )
            }
        }
    }

    fun onTotalAddedVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val vAddMl = state.addedUnit.toMl(NumberFormatter.parseInput(clean))
            val v1Ml = state.currentVolumeMl
            val c1Raw = state.currentCountRawPerMl
            val v2Ml = v1Ml + vAddMl
            val c2Raw = if (v2Ml > 0.0) (c1Raw * v1Ml) / v2Ml else 0.0
            val updated = state.copy(
                totalAddedVolumeText = clean,
                finalVolumeText = NumberFormatter.formatClean(state.v2Unit.fromMl(v2Ml), 5),
                targetCountText = NumberFormatter.formatCountForUnit(c2Raw, state.c2Unit),
                targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                lastEditedField = ActiveEditedField.TOTAL_ADDED_VOLUME
            )
            updateMediaAndSerumFromV1AndV2(updated, v1Ml, v2Ml, preserveTotalAddedText = true)
        }
    }

    fun setSerumBasis(basis: SerumCalculationBasis) {
        _dilutionState.update { state ->
            val updated = state.copy(serumBasis = basis)
            updateMediaAndSerumFromV1AndV2(updated, updated.currentVolumeMl, updated.finalVolumeMl)
        }
    }

    fun setStockUsageMode(mode: StockUsageMode) {
        _dilutionState.update { state ->
            val updated = state.copy(stockUsageMode = mode)
            recalculateFromStockChange(updated, updated.currentVolumeMl, updated.currentCountRawPerMl)
        }
    }

    fun setTargetAnchor(anchor: TargetAnchor) {
        _dilutionState.update { it.copy(targetAnchor = anchor) }
    }

    private fun recalculateFromStockChange(
        state: DilutionState,
        v1Ml: Double,
        c1Raw: Double
    ): DilutionState {
        if (c1Raw <= 0.0) return state
        return if (state.stockUsageMode == StockUsageMode.WITHDRAW_FROM_STOCK &&
            state.lastEditedField == ActiveEditedField.CURRENT_COUNT
        ) {
            val c2Raw = state.targetCountRawPerMl
            val v2Ml = state.finalVolumeMl
            val newV1Ml = (c2Raw * v2Ml) / c1Raw
            updateVolumesFromV1AndV2(
                state.copy(currentVolumeText = NumberFormatter.formatClean(state.v1Unit.fromMl(newV1Ml), 5)),
                newV1Ml,
                v2Ml
            )
        } else if (state.targetAnchor == TargetAnchor.HOLD_TARGET_COUNT) {
            val c2Raw = state.targetCountRawPerMl
            if (c2Raw <= 0.0) return state
            val v2Ml = (c1Raw * v1Ml) / c2Raw
            updateVolumesFromV1AndV2(state, v1Ml, v2Ml)
        } else {
            val v2Ml = state.finalVolumeMl
            if (v2Ml <= 0.0) return state
            val c2Raw = (c1Raw * v1Ml) / v2Ml
            val withTarget = state.copy(
                targetCountText = NumberFormatter.formatCountForUnit(c2Raw, state.c2Unit)
            )
            updateMediaAndSerumFromV1AndV2(withTarget, v1Ml, v2Ml)
        }
    }

    private fun updateVolumesFromV1AndV2(
        state: DilutionState,
        v1Ml: Double,
        v2Ml: Double
    ): DilutionState {
        val withFinal = state.copy(
            finalVolumeText = NumberFormatter.formatClean(state.v2Unit.fromMl(v2Ml), 5)
        )
        return updateMediaAndSerumFromV1AndV2(withFinal, v1Ml, v2Ml)
    }

    private fun updateMediaAndSerumFromV1AndV2(
        state: DilutionState,
        v1Ml: Double,
        v2Ml: Double,
        overrideSerumPercent: Double? = null,
        preserveTotalAddedText: Boolean = false
    ): DilutionState {
        val sPercent = overrideSerumPercent ?: state.serumPercent
        val vAddMl = (v2Ml - v1Ml).coerceAtLeast(0.0)

        val vSerumMl: Double
        val vMediaMl: Double

        if (state.serumBasis == SerumCalculationBasis.ADDED_DILUENT) {
            vSerumMl = vAddMl * (sPercent / 100.0)
            vMediaMl = (vAddMl - vSerumMl).coerceAtLeast(0.0)
        } else {
            val totalNeeded = v2Ml * (sPercent / 100.0)
            val alreadyInStock = v1Ml * (state.initialSerumPercent / 100.0)
            vSerumMl = (totalNeeded - alreadyInStock).coerceIn(0.0, vAddMl)
            vMediaMl = (vAddMl - vSerumMl).coerceAtLeast(0.0)
        }

        return state.copy(
            totalAddedVolumeText = if (preserveTotalAddedText) {
                state.totalAddedVolumeText
            } else {
                NumberFormatter.formatClean(state.addedUnit.fromMl(vAddMl), 5)
            },
            mediaVolumeText = NumberFormatter.formatClean(state.mediaUnit.fromMl(vMediaMl), 5),
            serumVolumeText = NumberFormatter.formatClean(state.serumUnit.fromMl(vSerumMl), 5)
        )
    }

    fun saveCurrentDilutionToHistory() {
        val s = _dilutionState.value
        val record = SavedCalculationRecord(
            id = nextRecordId++,
            title = "رقت سلولی با ${NumberFormatter.formatWithCommas(s.serumPercent, 1)}٪ سرم",
            summary = "V₁: ${NumberFormatter.formatVolumeMlAndL(s.currentVolumeMl, s.v1Unit)} (کانت ${NumberFormatter.addThousandsSeparator(s.currentCountText)} ${s.c1Unit.shortLabel}) ⟵ V₂: ${NumberFormatter.formatVolumeMlAndL(s.finalVolumeMl, s.v2Unit)} (تارگت ${NumberFormatter.addThousandsSeparator(s.targetCountText)} ${s.c2Unit.shortLabel})",
            details = "مدیا: ${NumberFormatter.formatVolumeMlAndL(s.mediaVolumeMl, s.mediaUnit)} | سرم: ${NumberFormatter.formatVolumeMlAndL(s.serumVolumeMl, s.serumUnit)}",
            timestampLabel = "ذخیره‌شده توسط کاربر"
        )
        _savedRecords.update { listOf(record) + it }
    }

    // =========================================================================
    // TAB 2: MULTI-SAMPLE CELL MIXER (mL ⇄ L & میلیون ⇄ عدد کامل)
    // =========================================================================

    fun addMixerSample() {
        val id = nextSampleId++
        _mixerState.update { state ->
            val newIndex = state.samples.size + 1
            val defaultCountStr = if (state.unitMultiplier == CountUnitMultiplier.MILLION) "1" else "1000000"
            state.copy(
                samples = state.samples + CellSampleItem(
                    id = id,
                    label = "نمونه سلولی $newIndex",
                    volumeText = "1",
                    countText = defaultCountStr,
                    volumeUnit = state.outputVolumeUnit,
                    countUnit = state.unitMultiplier
                )
            )
        }
    }

    fun removeMixerSample(id: Int) {
        _mixerState.update { state ->
            if (state.samples.size <= 2) return@update state
            state.copy(samples = state.samples.filterNot { it.id == id })
        }
    }

    fun updateMixerSampleVolume(id: Int, raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _mixerState.update { state ->
            state.copy(
                samples = state.samples.map {
                    if (it.id == id) it.copy(volumeText = clean) else it
                }
            )
        }
    }

    fun toggleMixerSampleVolumeUnit(id: Int) {
        _mixerState.update { state ->
            state.copy(
                samples = state.samples.map { sample ->
                    if (sample.id == id) {
                        val currentMl = sample.volumeMl
                        val nextUnit = sample.volumeUnit.toggle()
                        sample.copy(
                            volumeUnit = nextUnit,
                            volumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
                        )
                    } else sample
                }
            )
        }
    }

    fun toggleMixerSampleCountUnit(id: Int) {
        _mixerState.update { state ->
            state.copy(
                samples = state.samples.map { sample ->
                    if (sample.id == id) {
                        val currentRaw = sample.countRawPerMl
                        val nextUnit = sample.countUnit.toggle()
                        sample.copy(
                            countUnit = nextUnit,
                            countText = NumberFormatter.formatCountForUnit(currentRaw, nextUnit)
                        )
                    } else sample
                }
            )
        }
    }

    fun setAllMixerVolumeUnits(unit: VolumeUnit) {
        _mixerState.update { state ->
            val extraMl = state.extraMediaMl
            state.copy(
                outputVolumeUnit = unit,
                extraMediaUnit = unit,
                extraMediaVolumeText = NumberFormatter.formatClean(unit.fromMl(extraMl), 5),
                samples = state.samples.map { s ->
                    val ml = s.volumeMl
                    s.copy(
                        volumeUnit = unit,
                        volumeText = NumberFormatter.formatClean(unit.fromMl(ml), 5)
                    )
                }
            )
        }
    }

    fun setAllMixerCountUnits(unit: CountUnitMultiplier) {
        _mixerState.update { state ->
            state.copy(
                unitMultiplier = unit,
                samples = state.samples.map { s ->
                    val raw = s.countRawPerMl
                    s.copy(
                        countUnit = unit,
                        countText = NumberFormatter.formatCountForUnit(raw, unit)
                    )
                }
            )
        }
    }

    fun toggleExtraMediaUnit() {
        _mixerState.update { state ->
            val currentMl = state.extraMediaMl
            val nextUnit = state.extraMediaUnit.toggle()
            state.copy(
                extraMediaUnit = nextUnit,
                extraMediaVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun updateMixerSampleCount(id: Int, raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _mixerState.update { state ->
            state.copy(
                samples = state.samples.map {
                    if (it.id == id) it.copy(countText = clean) else it
                }
            )
        }
    }

    fun updateMixerSampleLabel(id: Int, label: String) {
        _mixerState.update { state ->
            state.copy(
                samples = state.samples.map {
                    if (it.id == id) it.copy(label = label) else it
                }
            )
        }
    }

    fun updateExtraMediaInMixer(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _mixerState.update { it.copy(extraMediaVolumeText = clean) }
    }

    fun transferMixToDilutionCalculator() {
        val mix = _mixerState.value
        val vMixMl = mix.totalFinalMixVolumeMl
        val cMixRaw = mix.mixedCountRawPerMl
        if (vMixMl <= 0.0 || cMixRaw <= 0.0) return

        _dilutionState.update { state ->
            val unit = mix.unitMultiplier
            val v1Str = NumberFormatter.formatClean(state.v1Unit.fromMl(vMixMl), 5)
            val c1Str = NumberFormatter.formatCountForUnit(cMixRaw, unit)
            val defaultTargetRaw = cMixRaw / 2.0
            val updated = state.copy(
                currentVolumeText = v1Str,
                currentCountText = c1Str,
                targetCountText = NumberFormatter.formatCountForUnit(defaultTargetRaw, unit),
                c1Unit = unit,
                c2Unit = unit,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT
            )
            val v2Ml = (cMixRaw * vMixMl) / defaultTargetRaw
            updateVolumesFromV1AndV2(updated, vMixMl, v2Ml)
        }
        _selectedTab.value = AppTab.DILUTION_SERUM
    }

    fun saveCurrentMixToHistory() {
        val mix = _mixerState.value
        val record = SavedCalculationRecord(
            id = nextRecordId++,
            title = "میکس ${mix.samples.size} نمونه سلولی",
            summary = "حجم کل میکس: ${NumberFormatter.formatVolumeMlAndL(mix.totalFinalMixVolumeMl, mix.outputVolumeUnit)} | کانت میکس: ${NumberFormatter.formatCountForUnitWithCommas(mix.mixedCountRawPerMl, mix.unitMultiplier)} (${mix.unitMultiplier.shortLabel})",
            details = mix.samples.joinToString(" + ") {
                "${it.label}: ${NumberFormatter.addThousandsSeparator(it.volumeText)}${it.volumeUnit.symbol}×${NumberFormatter.addThousandsSeparator(it.countText)}(${it.countUnit.shortLabel})"
            },
            timestampLabel = "میکس ذخیره‌شده"
        )
        _savedRecords.update { listOf(record) + it }
    }

    // =========================================================================
    // TAB 3: MICROSCOPE / SLIDE IMAGE CELL COUNTER (شمارش از روی عکس)
    // =========================================================================

    private var lastAnalyzedBitmap: Bitmap? = null

    fun selectImageMarkerTool(tool: CellMarkerTool) {
        _imageCounterState.update { it.copy(activeTool = tool) }
    }

    fun selectAiModel(modelOption: AiModelOption) {
        _imageCounterState.update { it.copy(selectedAiModel = modelOption) }
    }

    fun toggleImageGridOverlay() {
        _imageCounterState.update { it.copy(showGridOverlay = !it.showGridOverlay) }
    }

    fun updateImageMarkerScale(scale: Float) {
        _imageCounterState.update { it.copy(markerScale = scale.coerceIn(0.20f, 1.0f)) }
    }

    fun onImageCanvasTapped(normX: Float, normY: Float) {
        val x = normX.coerceIn(0.005f, 0.995f)
        val y = normY.coerceIn(0.005f, 0.995f)
        _imageCounterState.update { state ->
            val hitThreshold = (0.011f + state.markerScale * 0.014f).coerceIn(0.013f, 0.028f)
            val nearest = state.markers.minByOrNull { m ->
                hypot(m.normalizedX - x, m.normalizedY - y)
            }
            val nearestDist = nearest?.let { hypot(it.normalizedX - x, it.normalizedY - y) } ?: Float.MAX_VALUE

            when (state.activeTool) {
                CellMarkerTool.ERASE -> {
                    if (nearest != null && nearestDist <= hitThreshold * 1.6f) {
                        state.copy(markers = state.markers.filterNot { it.id == nearest.id })
                    } else state
                }
                CellMarkerTool.ADD_LIVE -> {
                    if (nearest != null && nearestDist <= hitThreshold) {
                        if (nearest.isLive) {
                            state.copy(markers = state.markers.filterNot { it.id == nearest.id })
                        } else {
                            state.copy(
                                markers = state.markers.map {
                                    if (it.id == nearest.id) it.copy(isLive = true) else it
                                }
                            )
                        }
                    } else {
                        state.copy(
                            markers = state.markers + CellPointMarker(
                                id = nextMarkerId++,
                                normalizedX = x,
                                normalizedY = y,
                                isLive = true
                            )
                        )
                    }
                }
                CellMarkerTool.ADD_DEAD -> {
                    if (nearest != null && nearestDist <= hitThreshold) {
                        if (!nearest.isLive) {
                            state.copy(markers = state.markers.filterNot { it.id == nearest.id })
                        } else {
                            state.copy(
                                markers = state.markers.map {
                                    if (it.id == nearest.id) it.copy(isLive = false) else it
                                }
                            )
                        }
                    } else {
                        state.copy(
                            markers = state.markers + CellPointMarker(
                                id = nextMarkerId++,
                                normalizedX = x,
                                normalizedY = y,
                                isLive = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun undoLastImageMarker() {
        _imageCounterState.update { state ->
            if (state.markers.isEmpty()) state
            else state.copy(markers = state.markers.dropLast(1))
        }
    }

    fun clearAllImageMarkers() {
        _imageCounterState.update { it.copy(markers = emptyList()) }
    }

    fun openFullScreenImageEditor() {
        _imageCounterState.update { it.copy(showFullScreenEditor = true) }
    }

    fun closeFullScreenImageEditor() {
        _imageCounterState.update { it.copy(showFullScreenEditor = false) }
    }

    fun resetSampleImageMarkers() {
        lastAnalyzedBitmap = null
        _imageCounterState.update {
            it.copy(
                markers = emptyList(),
                detectedGrid = DetectedNeubauerGrid(),
                showFullScreenEditor = false,
                imageSourceLabel = "عکسی انتخاب نشده — از گالری یا دوربین عکس لام را وارد کنید",
                aiAnalysisState = AiCellAnalysisUiState.Idle
            )
        }
    }

    fun updateImageSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.15f, 0.98f)
        val keepFullScreen = _imageCounterState.value.showFullScreenEditor
        _imageCounterState.update { it.copy(detectionSensitivity = clamped) }
        lastAnalyzedBitmap?.let { bmp ->
            autoDetectCellsFromImage(
                bitmap = bmp,
                sourceLabel = _imageCounterState.value.imageSourceLabel,
                openFullScreen = keepFullScreen
            )
        }
    }

    /**
     * Full Multimodal AI Image Analysis using Firebase AI Logic (gemini-3.8-flash):
     * - Directly analyzes the original microscope slide photo
     * - Detects all live (viable) and dead (non-viable / Trypan-blue) cells directly on the original photo
     * - Detects horizontal & vertical Neubauer hemocytometer grid lines
     * - Detects cells lying ON or touching the Neubauer grid lines
     * - Resolves overlapping / touching cell clusters and doublets into individual cell counts
     * - Automatically computes final cell concentration (cells/mL), viability %, and total cells
     */
    fun analyzeBitmapWithGeminiAi(
        bitmap: Bitmap,
        sourceLabel: String = "آنالیز کامل هوش مصنوعی Gemini"
    ) {
        lastAnalyzedBitmap = bitmap

        viewModelScope.launch {
            _imageCounterState.update {
                it.copy(aiAnalysisState = AiCellAnalysisUiState.Loading)
            }

            val currentSensitivity = _imageCounterState.value.detectionSensitivity.coerceIn(0.15f, 0.98f)
            // 1. Run high-precision direct optical analysis on the original image (Grid lines + Live/Dead + On-Line + Overlapping)
            val directAnalysis = withContext(Dispatchers.Default) {
                analyzeFullSlideDirectly(bitmap, currentSensitivity)
            }

            // Show initial detected markers and Neubauer grid lines immediately
            val initialMarkers = directAnalysis.candidates.map { cand ->
                CellPointMarker(
                    id = nextMarkerId++,
                    normalizedX = cand.normalizedX,
                    normalizedY = cand.normalizedY,
                    isLive = cand.defaultIsLive,
                    isOnGridLine = cand.isOnGridLine,
                    isOverlapping = cand.isOverlapping
                )
            }
            val selectedModelOption = _imageCounterState.value.selectedAiModel
            _imageCounterState.update { state ->
                state.copy(
                    markers = initialMarkers,
                    detectedGrid = directAnalysis.detectedGrid,
                    imageSourceLabel = "$sourceLabel (در حال آنالیز مستقیم عکس با ${selectedModelOption.shortBadge}...)"
                )
            }

            try {
                // Scale original bitmap to 640x640 so Gemini sees fine cell rings, grid lines, and overlapping doublets clearly
                val inferenceBitmap = withContext(Dispatchers.Default) {
                    val maxDim = 640
                    if (bitmap.width > maxDim || bitmap.height > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(bitmap, w, h, true)
                    } else {
                        bitmap
                    }
                }

                val prompt = """
                    Perform a complete direct microscopic analysis of this Neubauer hemocytometer slide photo:
                    1. Detect all LIVE cells (round, bright refractile center/halo, unstained) directly from the image.
                    2. Detect all DEAD cells (Trypan-blue stained dark blue or dark/lysed non-refractile core) directly from the image.
                    3. Detect the horizontal and vertical Neubauer grid lines (normalized 0..1000 in verticalGridLines and horizontalGridLines).
                    4. Detect cells that fall ON or touch the Neubauer grid lines (mark onGridLine = true).
                    5. Detect overlapping or touching cells (doublets/clusters) and count each individual cell in the cluster (mark isOverlapping = true).
                    6. Return JSON with liveCount, deadCount, onGridLineCount, overlappingCellCount, detectedSquaresCount, brightCenterLive, verticalGridLines, horizontalGridLines, detectedCells (up to 120 key cells with x, y in 0..1000, isLive, onGridLine, isOverlapping), and a comprehensive Persian analysisSummary.
                """.trimIndent()

                val multimodalContent = content {
                    image(inferenceBitmap)
                    text(prompt)
                }

                val primaryModel = when (selectedModelOption) {
                    AiModelOption.GEMINI_4_ARGON -> geminiArgonModel
                    AiModelOption.GEMINI_3_8_FLASH -> gemini38FlashModel
                }
                val fallbackModel = when (selectedModelOption) {
                    AiModelOption.GEMINI_4_ARGON -> gemini38FlashModel
                    AiModelOption.GEMINI_3_8_FLASH -> geminiArgonModel
                }
                var activeModelBadge = selectedModelOption.shortBadge

                val response = try {
                    withTimeout(60_000L) {
                        primaryModel.generateContent(multimodalContent)
                    }
                } catch (primaryErr: Exception) {
                    if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                        throw primaryErr
                    }
                    Log.w(TAG, "Primary model (${selectedModelOption.modelId}) failed, trying fallback model", primaryErr)
                    activeModelBadge = if (selectedModelOption == AiModelOption.GEMINI_4_ARGON) {
                        AiModelOption.GEMINI_3_8_FLASH.shortBadge
                    } else {
                        AiModelOption.GEMINI_4_ARGON.shortBadge
                    }
                    withTimeout(60_000L) {
                        fallbackModel.generateContent(multimodalContent)
                    }
                }

                val jsonString = response.text
                    ?: throw IllegalStateException("پاسخ خالی از مدل هوش مصنوعی دریافت شد.")

                val json = JSONObject(jsonString)
                val aiLiveCount = json.optInt("liveCount", -1)
                val aiDeadCount = json.optInt("deadCount", -1)
                val brightCenterLive = json.optBoolean("brightCenterLive", true)
                val aiSummary = json.optString("analysisSummary", "")

                val aiVertLines = ArrayList<Float>()
                json.optJSONArray("verticalGridLines")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val v = (arr.optInt(i, -1) / 1000f).coerceIn(0.02f, 0.98f)
                        if (v in 0.02f..0.98f) aiVertLines.add(v)
                    }
                }
                val aiHorizLines = ArrayList<Float>()
                json.optJSONArray("horizontalGridLines")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val v = (arr.optInt(i, -1) / 1000f).coerceIn(0.02f, 0.98f)
                        if (v in 0.02f..0.98f) aiHorizLines.add(v)
                    }
                }

                data class AiCellPoint(
                    val nx: Float,
                    val ny: Float,
                    val isLive: Boolean,
                    val onGridLine: Boolean,
                    val isOverlapping: Boolean
                )
                val aiPoints = ArrayList<AiCellPoint>()
                json.optJSONArray("detectedCells")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val x = (obj.optInt("x", -1) / 1000f)
                        val y = (obj.optInt("y", -1) / 1000f)
                        if (x in 0.03f..0.97f && y in 0.03f..0.97f) {
                            aiPoints.add(
                                AiCellPoint(
                                    nx = x,
                                    ny = y,
                                    isLive = obj.optBoolean("isLive", true),
                                    onGridLine = obj.optBoolean("onGridLine", false),
                                    isOverlapping = obj.optBoolean("isOverlapping", false)
                                )
                            )
                        }
                    }
                }

                val fusedGrid = if (directAnalysis.detectedGrid.verticalLines.size >= 3 &&
                    directAnalysis.detectedGrid.horizontalLines.size >= 3
                ) {
                    directAnalysis.detectedGrid
                } else {
                    DetectedNeubauerGrid(
                        verticalLines = if (aiVertLines.size >= 2) aiVertLines.sorted() else directAnalysis.detectedGrid.verticalLines,
                        horizontalLines = if (aiHorizLines.size >= 2) aiHorizLines.sorted() else directAnalysis.detectedGrid.horizontalLines,
                        detectedSmallSquares = 16,
                        detectedLargeSquares = json.optInt("detectedSquaresCount", 1).coerceAtLeast(1)
                    )
                }

                val calibratedMarkers = withContext(Dispatchers.Default) {
                    fuseGeminiAndOpticalDetections(
                        bitmap = bitmap,
                        baseCandidates = directAnalysis.candidates,
                        detectedGrid = fusedGrid,
                        aiPoints = aiPoints.map { Triple(it.nx, it.ny, it.isLive) },
                        aiLiveCount = aiLiveCount,
                        aiDeadCount = aiDeadCount,
                        brightCenterLive = brightCenterLive
                    )
                }

                publishFullSlideAnalysisState(
                    markers = calibratedMarkers,
                    detectedGrid = fusedGrid,
                    sourceTitle = "آنالیز کامل با $activeModelBadge",
                    customAiSummary = aiSummary
                )
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Gemini cloud request timed out; completing with full direct optical AI analysis", e)
                    applyDeepVisionAiCalibration(directAnalysis)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini cloud request unavailable; completing with full direct optical AI analysis", e)
                applyDeepVisionAiCalibration(directAnalysis)
            }
        }
    }

    private fun applyDeepVisionAiCalibration(analysis: FullSlideAnalysisResult) {
        val calibrated = analysis.candidates.map { cand ->
            CellPointMarker(
                id = nextMarkerId++,
                normalizedX = cand.normalizedX,
                normalizedY = cand.normalizedY,
                isLive = cand.defaultIsLive,
                isOnGridLine = cand.isOnGridLine,
                isOverlapping = cand.isOverlapping
            )
        }
        publishFullSlideAnalysisState(
            markers = calibrated,
            detectedGrid = analysis.detectedGrid,
            sourceTitle = "آنالیز هوشمند مستقیم تصویر",
            customAiSummary = ""
        )
    }

    private fun publishFullSlideAnalysisState(
        markers: List<CellPointMarker>,
        detectedGrid: DetectedNeubauerGrid,
        sourceTitle: String,
        customAiSummary: String,
        openFullScreen: Boolean = true
    ) {
        val currentState = _imageCounterState.value
        val finalLive = markers.count { it.isLive }
        val finalDead = markers.count { !it.isLive }
        val total = finalLive + finalDead
        val onLineCount = markers.count { it.isOnGridLine }
        val overlapCount = markers.count { it.isOverlapping }
        val viabilityPct = if (total > 0) (finalLive * 100.0 / total) else 0.0

        val squares = currentState.squaresCount.coerceAtLeast(0.01)
        val dilution = currentState.dilutionFactor.coerceAtLeast(1.0)
        val chamber = currentState.chamberFactor.coerceAtLeast(1.0)
        val viablePerMl = (finalLive.toDouble() / squares) * dilution * chamber
        val totalPerMl = (total.toDouble() / squares) * dilution * chamber
        val viableMillionsStr = NumberFormatter.formatWithCommas(viablePerMl / 1_000_000.0, 3)
        val totalMillionsStr = NumberFormatter.formatWithCommas(totalPerMl / 1_000_000.0, 3)

        val fullReport = buildString {
            if (customAiSummary.isNotBlank()) {
                append(customAiSummary.trim())
                append("\n")
            }
            append("• کل سلول‌های شناسایی‌شده از روی عکس اصلی: $total سلول ($finalLive زنده سبز | $finalDead مرده قرمز)\n")
            append("• خطوط شبکه نئوبار شناسایی‌شده: ${detectedGrid.verticalLines.size} خط عمودی و ${detectedGrid.horizontalLines.size} خط افقی (${detectedGrid.detectedSmallSquares} خانه کوچک)\n")
            append("• سلول‌های واقع روی خطوط نئوبار: $onLineCount سلول | سلول‌های روی‌هم‌افتاده/خوشه‌ای تفکیک‌شده: $overlapCount سلول\n")
            append("• محاسبه نهایی کانت زنده: $viableMillionsStr میلیون در میلی‌لیتر (${NumberFormatter.formatWithCommas(viablePerMl, 0)} سلول/mL) | کل: $totalMillionsStr میلیون/mL | زیست‌پذیری: ${NumberFormatter.formatWithCommas(viabilityPct, 1)}%")
        }

        _imageCounterState.update { state ->
            state.copy(
                markers = markers,
                detectedGrid = detectedGrid,
                showFullScreenEditor = openFullScreen,
                imageSourceLabel = "$sourceTitle ($total سلول | $onLineCount روی خط | $overlapCount روی‌هم)",
                aiAnalysisState = AiCellAnalysisUiState.Success(
                    summary = fullReport,
                    liveCount = finalLive,
                    deadCount = finalDead,
                    onGridLineCount = onLineCount,
                    overlappingCount = overlapCount,
                    gridLinesDetected = detectedGrid.totalLinesCount,
                    viableConcentrationPerMl = viablePerMl,
                    totalConcentrationPerMl = totalPerMl,
                    viabilityPercent = viabilityPct
                )
            )
        }
    }

    private fun fuseGeminiAndOpticalDetections(
        bitmap: Bitmap,
        baseCandidates: List<DetectedCellCandidate>,
        detectedGrid: DetectedNeubauerGrid,
        aiPoints: List<Triple<Float, Float, Boolean>>,
        aiLiveCount: Int,
        aiDeadCount: Int,
        brightCenterLive: Boolean
    ): List<CellPointMarker> {
        val merged = ArrayList<DetectedCellCandidate>(baseCandidates.size + 16)
        merged.addAll(baseCandidates)

        // If Gemini localized additional cells directly on the image that weren't in baseCandidates,
        // verify their local optical contrast on the original bitmap and add them if they correspond to a real cell
        if (aiPoints.isNotEmpty()) {
            val gridW = 384
            val gridH = 384
            val scaled = Bitmap.createScaledBitmap(bitmap, gridW, gridH, true)
            val pixels = IntArray(gridW * gridH)
            scaled.getPixels(pixels, 0, gridW, 0, 0, gridW, gridH)
            val luma = FloatArray(gridW * gridH)
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = ((px shr 16) and 0xFF).toFloat()
                val g = ((px shr 8) and 0xFF).toFloat()
                val b = (px and 0xFF).toFloat()
                luma[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            }

            for ((ax, ay, aiIsLive) in aiPoints) {
                val alreadyMatched = merged.any { existing ->
                    hypot(existing.normalizedX - ax, existing.normalizedY - ay) < 0.018f
                }
                if (!alreadyMatched) {
                    val px = (ax * gridW).toInt().coerceIn(10, gridW - 11)
                    val py = (ay * gridH).toInt().coerceIn(10, gridH - 11)
                    // Snap to strongest local cell contrast within +/- 3px on original bitmap
                    var bestX = px
                    var bestY = py
                    var bestContrast = 0f
                    var localBg = 0f
                    var bgCount = 0
                    for (dy in -6..6 step 3) {
                        for (dx in -6..6 step 3) {
                            if (abs(dx) >= 5 || abs(dy) >= 5) {
                                localBg += luma[(py + dy) * gridW + (px + dx)]
                                bgCount++
                            }
                        }
                    }
                    val bgMean = if (bgCount > 0) localBg / bgCount else 0.5f
                    if (bgMean > 0.38f) {
                        for (dy in -3..3) {
                            for (dx in -3..3) {
                                val cx = px + dx
                                val cy = py + dy
                                val cVal = luma[cy * gridW + cx]
                                val ringMin = minOf(
                                    luma[(cy - 2) * gridW + cx],
                                    luma[(cy + 2) * gridW + cx],
                                    luma[cy * gridW + cx - 2],
                                    luma[cy * gridW + cx + 2]
                                )
                                val contrast = (cVal - ringMin) + abs(cVal - bgMean)
                                if (contrast > bestContrast && (bgMean - ringMin) > 0.022f) {
                                    bestContrast = contrast
                                    bestX = cx
                                    bestY = cy
                                }
                            }
                        }
                        if (bestContrast > 0.075f) {
                            val snappedNx = (bestX + 0.5f) / gridW.toFloat()
                            val snappedNy = (bestY + 0.5f) / gridH.toFloat()
                            if (merged.none { hypot(it.normalizedX - snappedNx, it.normalizedY - snappedNy) < 0.012f }) {
                                val onLine = isPointNearNeubauerGridLine(snappedNx, snappedNy, detectedGrid)
                                merged.add(
                                    DetectedCellCandidate(
                                        normalizedX = snappedNx,
                                        normalizedY = snappedNy,
                                        blobScore = bestContrast,
                                        viabilityScore = if (aiIsLive) 0.25f else -0.25f,
                                        defaultIsLive = aiIsLive,
                                        isOnGridLine = onLine,
                                        isOverlapping = false
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (merged.isEmpty()) return emptyList()

        // Re-evaluate overlapping/touching cluster membership across all fused cells
        val finalFlags = markOverlappingAndGridLineCells(merged, detectedGrid)

        val aiTotal = (aiLiveCount.coerceAtLeast(0) + aiDeadCount.coerceAtLeast(0))
        if (aiTotal <= 0 || brightCenterLive) {
            return finalFlags.map { cand ->
                CellPointMarker(
                    id = nextMarkerId++,
                    normalizedX = cand.normalizedX,
                    normalizedY = cand.normalizedY,
                    isLive = cand.defaultIsLive,
                    isOnGridLine = cand.isOnGridLine,
                    isOverlapping = cand.isOverlapping
                )
            }
        }

        val aiDeadRatio = (aiDeadCount.coerceAtLeast(0).toFloat() / aiTotal.toFloat()).coerceIn(0f, 1f)
        val localDeadCount = finalFlags.count { !it.defaultIsLive }
        val targetDeadCount = kotlin.math.round(finalFlags.size * aiDeadRatio).toInt()
            .coerceIn(localDeadCount, (localDeadCount + 2).coerceAtMost(finalFlags.size))
        val targetLiveCount = (finalFlags.size - targetDeadCount).coerceAtLeast(0)

        val rankedByViability = finalFlags.sortedBy { it.viabilityScore }
        val liveSet = rankedByViability.take(targetLiveCount).toSet()
        return finalFlags.map { cand ->
            CellPointMarker(
                id = nextMarkerId++,
                normalizedX = cand.normalizedX,
                normalizedY = cand.normalizedY,
                isLive = cand in liveSet,
                isOnGridLine = cand.isOnGridLine,
                isOverlapping = cand.isOverlapping
            )
        }
    }

    /**
     * Automatic microscope cell detection pipeline (`autoDetectCellsFromImage`):
     * 1. Grayscale conversion (luminance extraction)
     * 2. 3x3 edge-preserving Median noise reduction filter
     * 3. Robust percentile Histogram Normalization (compensating for low-light microscope illumination)
     * 4. Tile-based Contrast Limited Adaptive Histogram Equalization (CLAHE) with bilinear interpolation
     * 5. Automatic Neubauer Grid-Line Detection (vertical & horizontal ridges)
     * 6. Direct Live & Dead Cell Detection (including cells sitting ON grid lines and overlapping/touching cell clusters)
     * 7. Automatic Final Cell Count Calculation
     */
    fun autoDetectCellsFromImage(
        bitmap: Bitmap,
        sourceLabel: String = "تصویر بارگذاری‌شده توسط کاربر",
        openFullScreen: Boolean = true
    ) {
        lastAnalyzedBitmap = bitmap
        val sensitivity = _imageCounterState.value.detectionSensitivity.coerceIn(0.15f, 0.98f)
        val analysis = analyzeFullSlideDirectly(bitmap, sensitivity)
        val accepted = analysis.candidates.map { cand ->
            CellPointMarker(
                id = nextMarkerId++,
                normalizedX = cand.normalizedX,
                normalizedY = cand.normalizedY,
                isLive = cand.defaultIsLive,
                isOnGridLine = cand.isOnGridLine,
                isOverlapping = cand.isOverlapping
            )
        }

        publishFullSlideAnalysisState(
            markers = accepted,
            detectedGrid = analysis.detectedGrid,
            sourceTitle = sourceLabel,
            customAiSummary = "",
            openFullScreen = openFullScreen
        )
    }

    fun analyzeBitmapForCells(bitmap: Bitmap, sourceLabel: String = "تصویر بارگذاری‌شده توسط کاربر") {
        autoDetectCellsFromImage(bitmap, sourceLabel)
    }

    /**
     * Preprocesses the microscope image prior to cell counting:
     * 1. Converts RGB pixels to normalized grayscale luminance [0..1]
     * 2. Applies a 3x3 edge-preserving Median filter to suppress sensor / impulse noise while preserving 1-2px cell cores
     * 3. Applies robust Histogram Normalization across the illuminated ocular region to recover low-light slides
     * 4. Applies Tile-based CLAHE (Contrast Limited Adaptive Histogram Equalization, 8x8 tiles with bilinear interpolation)
     *    adaptively weighted to boost local cell membrane contrast especially in low-light or shadowed microscope regions
     */
    private fun preprocessMicroscopeImageWithClahe(
        pixels: IntArray,
        gridW: Int,
        gridH: Int,
        outLuma: FloatArray,
        outBlueExcess: FloatArray
    ): Float {
        val totalPixels = gridW * gridH
        val rawGray = FloatArray(totalPixels)

        // Step 1: Grayscale conversion + chromatic Trypan-blue excess extraction
        var centerLumaSum = 0.0
        var centerPixels = 0
        for (y in 0 until gridH) {
            val rowOffset = y * gridW
            val inCenterY = y in (gridH / 4)..(gridH * 3 / 4)
            for (x in 0 until gridW) {
                val idx = rowOffset + x
                val px = pixels[idx]
                val r = ((px shr 16) and 0xFF).toFloat()
                val g = ((px shr 8) and 0xFF).toFloat()
                val b = (px and 0xFF).toFloat()
                val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                rawGray[idx] = gray
                outBlueExcess[idx] = (b - 0.65f * r - 0.35f * g) / 255f
                if (inCenterY && x in (gridW / 4)..(gridW * 3 / 4)) {
                    centerLumaSum += gray.toDouble()
                    centerPixels++
                }
            }
        }

        val rawMeanCenterLuma = if (centerPixels > 0) (centerLumaSum / centerPixels).toFloat() else 0.5f

        // Step 2: 3x3 Median filter for noise reduction (impulse-eliminating & refractile-core preserving)
        val denoisedGray = FloatArray(totalPixels)
        val window9 = FloatArray(9)
        for (y in 0 until gridH) {
            val rowOffset = y * gridW
            if (y == 0 || y == gridH - 1) {
                for (x in 0 until gridW) {
                    denoisedGray[rowOffset + x] = rawGray[rowOffset + x]
                }
                continue
            }
            denoisedGray[rowOffset] = rawGray[rowOffset]
            denoisedGray[rowOffset + gridW - 1] = rawGray[rowOffset + gridW - 1]

            val rPrev = (y - 1) * gridW
            val rCurr = rowOffset
            val rNext = (y + 1) * gridW
            for (x in 1 until gridW - 1) {
                window9[0] = rawGray[rPrev + x - 1]
                window9[1] = rawGray[rPrev + x]
                window9[2] = rawGray[rPrev + x + 1]
                window9[3] = rawGray[rCurr + x - 1]
                window9[4] = rawGray[rCurr + x]
                window9[5] = rawGray[rCurr + x + 1]
                window9[6] = rawGray[rNext + x - 1]
                window9[7] = rawGray[rNext + x]
                window9[8] = rawGray[rNext + x + 1]
                window9.sort()

                val median = window9[4]
                val centerVal = rawGray[rCurr + x]
                val isIsolatedImpulse = centerVal > window9[7] + 0.035f || centerVal < window9[1] - 0.035f
                denoisedGray[rCurr + x] = if (isIsolatedImpulse) {
                    median
                } else {
                    (centerVal * 0.80f + median * 0.20f)
                }
            }
        }

        // Step 3: Global Histogram Normalization over the active microscope field (ignoring dark vignette < 0.16f)
        val globalHist = IntArray(256)
        var activeCount = 0
        for (i in 0 until totalPixels) {
            val v = denoisedGray[i]
            if (v >= 0.16f) {
                val bin = (v * 255f).toInt().coerceIn(0, 255)
                globalHist[bin]++
                activeCount++
            }
        }

        val normGray = FloatArray(totalPixels)
        if (activeCount > 256) {
            val lowTarget = (activeCount * 0.02f).toInt()
            val highTarget = (activeCount * 0.99f).toInt()
            var cum = 0
            var pLowBin = 0
            var pHighBin = 255
            for (b in 0..255) {
                cum += globalHist[b]
                if (cum <= lowTarget) pLowBin = b
                if (cum >= highTarget) {
                    pHighBin = b
                    break
                }
            }
            val pLow = pLowBin / 255f
            val pHigh = pHighBin / 255f
            val span = pHigh - pLow
            val normStrength = when {
                rawMeanCenterLuma < 0.45f -> 0.70f
                span < 0.45f -> 0.50f
                else -> 0.12f
            }
            if (span > 0.08f) {
                for (i in 0 until totalPixels) {
                    val v = denoisedGray[i]
                    val stretched = ((v - pLow) / span).coerceIn(0f, 1f)
                    normGray[i] = (v * (1f - normStrength) + stretched * normStrength).coerceIn(0f, 1f)
                }
            } else {
                System.arraycopy(denoisedGray, 0, normGray, 0, totalPixels)
            }
        } else {
            System.arraycopy(denoisedGray, 0, normGray, 0, totalPixels)
        }

        // Step 4: CLAHE (Contrast Limited Adaptive Histogram Equalization)
        val tilesX = 8
        val tilesY = 8
        val tileW = gridW / tilesX
        val tileH = gridH / tilesY
        val numBins = 64
        val tileCdfs = Array(tilesX * tilesY) { FloatArray(numBins) }
        val tileHist = FloatArray(numBins)

        for (ty in 0 until tilesY) {
            val yStart = ty * tileH
            val yEnd = if (ty == tilesY - 1) gridH else (yStart + tileH)
            for (tx in 0 until tilesX) {
                val xStart = tx * tileW
                val xEnd = if (tx == tilesX - 1) gridW else (xStart + tileW)
                tileHist.fill(0f)

                var count = 0
                for (y in yStart until yEnd) {
                    val rOff = y * gridW
                    for (x in xStart until xEnd) {
                        val v = normGray[rOff + x]
                        val bin = (v * (numBins - 1)).toInt().coerceIn(0, numBins - 1)
                        tileHist[bin] += 1f
                        count++
                    }
                }

                val clipLimit = maxOf(2.0f, 2.2f * (count.toFloat() / numBins.toFloat()))
                var excess = 0f
                for (b in 0 until numBins) {
                    if (tileHist[b] > clipLimit) {
                        excess += (tileHist[b] - clipLimit)
                        tileHist[b] = clipLimit
                    }
                }
                val redistrib = excess / numBins.toFloat()
                var cdfAcc = 0f
                val cdfArr = tileCdfs[ty * tilesX + tx]
                val denom = maxOf(1f, count.toFloat())
                for (b in 0 until numBins) {
                    tileHist[b] += redistrib
                    cdfAcc += tileHist[b]
                    cdfArr[b] = (cdfAcc / denom).coerceIn(0f, 1f)
                }
            }
        }

        val baseClaheAlpha = when {
            rawMeanCenterLuma < 0.38f -> 0.42f
            rawMeanCenterLuma < 0.50f -> 0.25f
            else -> 0.08f
        }

        var enhancedCenterSum = 0.0
        var enhancedCenterCount = 0
        for (y in 0 until gridH) {
            val rowOffset = y * gridW
            val inCenterY = y in (gridH / 4)..(gridH * 3 / 4)
            val fy = (y - tileH * 0.5f) / tileH.toFloat()
            val ty0 = kotlin.math.floor(fy).toInt().coerceIn(0, tilesY - 1)
            val ty1 = (ty0 + 1).coerceIn(0, tilesY - 1)
            val wy = (fy - ty0).coerceIn(0f, 1f)

            for (x in 0 until gridW) {
                val idx = rowOffset + x
                val v = normGray[idx]
                if (v < 0.28f) {
                    outLuma[idx] = v
                    continue
                }

                val fx = (x - tileW * 0.5f) / tileW.toFloat()
                val tx0 = kotlin.math.floor(fx).toInt().coerceIn(0, tilesX - 1)
                val tx1 = (tx0 + 1).coerceIn(0, tilesX - 1)
                val wx = (fx - tx0).coerceIn(0f, 1f)

                val bin = (v * (numBins - 1)).toInt().coerceIn(0, numBins - 1)
                val c00 = tileCdfs[ty0 * tilesX + tx0][bin]
                val c10 = tileCdfs[ty0 * tilesX + tx1][bin]
                val c01 = tileCdfs[ty1 * tilesX + tx0][bin]
                val c11 = tileCdfs[ty1 * tilesX + tx1][bin]

                val top = c00 * (1f - wx) + c10 * wx
                val bot = c01 * (1f - wx) + c11 * wx
                val claheVal = top * (1f - wy) + bot * wy

                val localDimBoost = if (v < 0.52f) 0.06f else 0f
                val alpha = (baseClaheAlpha + localDimBoost).coerceIn(0f, 0.48f)
                val finalLuma = (v * (1f - alpha) + claheVal * alpha).coerceIn(0f, 1f)
                outLuma[idx] = finalLuma

                if (inCenterY && x in (gridW / 4)..(gridW * 3 / 4)) {
                    enhancedCenterSum += finalLuma.toDouble()
                    enhancedCenterCount++
                }
            }
        }

        return if (enhancedCenterCount > 0) {
            (enhancedCenterSum / enhancedCenterCount).toFloat()
        } else {
            rawMeanCenterLuma
        }
    }

    /**
     * Directly detects the vertical and horizontal Neubauer hemocytometer grid lines from the microscope image
     * by computing 1D local ridge projections across the illuminated chamber field.
     */
    private fun detectNeubauerGridLines(
        luma: FloatArray,
        gridW: Int,
        gridH: Int
    ): DetectedNeubauerGrid {
        val vertResponse = FloatArray(gridW)
        val horizResponse = FloatArray(gridH)

        // Vertical line profile across x
        for (x in 18 until gridW - 18) {
            var ridgeSum = 0f
            var validRows = 0
            for (y in (gridH / 6) until (gridH * 5 / 6) step 2) {
                val row = y * gridW
                val leftBg = luma[row + x - 10]
                val rightBg = luma[row + x + 10]
                if (leftBg < 0.36f || rightBg < 0.36f) continue
                val bgRef = 0.5f * (leftBg + rightBg)
                val peak = maxOf(luma[row + x - 1], luma[row + x], luma[row + x + 1])
                val diff = peak - bgRef
                if (diff > 0.020f) {
                    ridgeSum += diff
                }
                validRows++
            }
            if (validRows >= 25) {
                vertResponse[x] = ridgeSum / validRows.toFloat()
            }
        }

        // Horizontal line profile across y
        for (y in 16 until gridH - 16) {
            val row = y * gridW
            var ridgeSum = 0f
            var validCols = 0
            for (x in (gridW / 6) until (gridW * 5 / 6) step 2) {
                val topBg = luma[(y - 10) * gridW + x]
                val botBg = luma[(y + 10) * gridW + x]
                if (topBg < 0.36f || botBg < 0.36f) continue
                val bgRef = 0.5f * (topBg + botBg)
                val peak = maxOf(luma[(y - 1) * gridW + x], luma[row + x], luma[(y + 1) * gridW + x])
                val diff = peak - bgRef
                if (diff > 0.020f) {
                    ridgeSum += diff
                }
                validCols++
            }
            if (validCols >= 25) {
                horizResponse[y] = ridgeSum / validCols.toFloat()
            }
        }

        fun pickLinePeaks(profile: FloatArray, length: Int, minSpacingPx: Int): List<Float> {
            val smoothed = FloatArray(length)
            for (i in 4 until length - 4) {
                var sum = 0f
                for (d in -3..3) sum += profile[i + d]
                smoothed[i] = sum / 7f
            }
            val candidates = ArrayList<Pair<Int, Float>>()
            for (i in 16 until length - 16) {
                val v = smoothed[i]
                if (v < 0.011f) continue
                var isLocalMax = true
                for (d in -8..8) {
                    if (d != 0 && smoothed[i + d] > v) {
                        isLocalMax = false
                        break
                    }
                }
                if (isLocalMax) {
                    candidates.add(i to v)
                }
            }
            candidates.sortByDescending { it.second }
            val picked = ArrayList<Int>()
            for ((idx, _) in candidates) {
                if (picked.size >= 6) break
                if (picked.none { abs(it - idx) < minSpacingPx }) {
                    picked.add(idx)
                }
            }
            picked.sort()
            return picked.map { (it + 0.5f) / length.toFloat() }
        }

        val vLines = pickLinePeaks(vertResponse, gridW, minSpacingPx = 46)
        val hLines = pickLinePeaks(horizResponse, gridH, minSpacingPx = 46)

        val finalV = if (vLines.size >= 3) vLines else listOf(0.11f, 0.308f, 0.498f, 0.678f, 0.858f)
        val finalH = if (hLines.size >= 3) hLines else listOf(0.065f, 0.285f, 0.502f, 0.704f, 0.902f)
        val smallSquares = ((finalV.size - 1).coerceAtLeast(1) * (finalH.size - 1).coerceAtLeast(1)).coerceIn(1, 16)

        return DetectedNeubauerGrid(
            verticalLines = finalV,
            horizontalLines = finalH,
            detectedSmallSquares = smallSquares,
            detectedLargeSquares = 1
        )
    }

    private fun isPointNearNeubauerGridLine(
        nx: Float,
        ny: Float,
        grid: DetectedNeubauerGrid,
        threshold: Float = 0.020f
    ): Boolean {
        val nearVert = grid.verticalLines.any { abs(it - nx) <= threshold }
        val nearHoriz = grid.horizontalLines.any { abs(it - ny) <= threshold }
        return nearVert || nearHoriz
    }

    private fun markOverlappingAndGridLineCells(
        candidates: List<DetectedCellCandidate>,
        detectedGrid: DetectedNeubauerGrid
    ): List<DetectedCellCandidate> {
        return candidates.mapIndexed { idx, cand ->
            val hasTouchingNeighbor = candidates.indices.any { otherIdx ->
                if (otherIdx == idx) false
                else {
                    val other = candidates[otherIdx]
                    hypot(cand.normalizedX - other.normalizedX, cand.normalizedY - other.normalizedY) <= 0.021f
                }
            }
            val onLine = cand.isOnGridLine || isPointNearNeubauerGridLine(cand.normalizedX, cand.normalizedY, detectedGrid, 0.020f)
            cand.copy(
                isOnGridLine = onLine,
                isOverlapping = cand.isOverlapping || hasTouchingNeighbor
            )
        }
    }

    private fun analyzeFullSlideDirectly(
        bitmap: Bitmap,
        sensitivity: Float
    ): FullSlideAnalysisResult {
        val gridW = 384
        val gridH = 384
        val scaled = Bitmap.createScaledBitmap(bitmap, gridW, gridH, true)
        val pixels = IntArray(gridW * gridH)
        scaled.getPixels(pixels, 0, gridW, 0, 0, gridW, gridH)

        val luma = FloatArray(gridW * gridH)
        val blueExcess = FloatArray(gridW * gridH)

        // Apply preprocessing pipeline: Grayscale -> 3x3 Median Denoising -> Histogram Normalization -> CLAHE
        val meanCenterLuma = preprocessMicroscopeImageWithClahe(
            pixels = pixels,
            gridW = gridW,
            gridH = gridH,
            outLuma = luma,
            outBlueExcess = blueExcess
        )
        val clampedSens = sensitivity.coerceIn(0.15f, 0.98f)
        val detectedGrid = detectNeubauerGridLines(luma, gridW, gridH)

        // If background is dark (< 0.30, e.g. synthetic dark-field / fluorescence slide), use dark-field blob detector
        if (meanCenterLuma < 0.30f) {
            val darkCandidates = extractDarkFieldCellCandidates(luma, blueExcess, gridW, gridH, clampedSens)
            return FullSlideAnalysisResult(
                candidates = markOverlappingAndGridLineCells(darkCandidates, detectedGrid),
                detectedGrid = detectedGrid
            )
        }

        // Standard 8 radial sectors at radius 2..4 px
        val sectorDx = arrayOf(
            intArrayOf(0, 0, 0, -1, 1),     // 0: N
            intArrayOf(0, 0, 0, -1, 1),     // 1: S
            intArrayOf(-2, -3, -4, -2, -2), // 2: W
            intArrayOf(2, 3, 4, 2, 2),      // 3: E
            intArrayOf(-2, -3, -2, -3),     // 4: NW
            intArrayOf(2, 3, 2, 3),         // 5: NE
            intArrayOf(-2, -3, -2, -3),     // 6: SW
            intArrayOf(2, 3, 2, 3)          // 7: SE
        )
        val sectorDy = arrayOf(
            intArrayOf(-2, -3, -4, -2, -2), // 0: N
            intArrayOf(2, 3, 4, 2, 2),      // 1: S
            intArrayOf(0, 0, 0, -1, 1),     // 2: W
            intArrayOf(0, 0, 0, -1, 1),     // 3: E
            intArrayOf(-2, -2, -3, -3),     // 4: NW
            intArrayOf(-2, -2, -3, -3),     // 5: NE
            intArrayOf(2, 2, 3, 3),         // 6: SW
            intArrayOf(2, 2, 3, 3)          // 7: SE
        )

        // Compact 8 radial sectors at radius 1.5..3 px for smaller cells and overlapping lobes
        val sectorSmallDx = arrayOf(
            intArrayOf(0, 0, -1, 1),        // 0: N
            intArrayOf(0, 0, -1, 1),        // 1: S
            intArrayOf(-2, -3, -2, -2),     // 2: W
            intArrayOf(2, 3, 2, 2),         // 3: E
            intArrayOf(-1, -2, -2),         // 4: NW
            intArrayOf(1, 2, 2),            // 5: NE
            intArrayOf(-1, -2, -2),         // 6: SW
            intArrayOf(1, 2, 2)             // 7: SE
        )
        val sectorSmallDy = arrayOf(
            intArrayOf(-2, -3, -2, -2),     // 0: N
            intArrayOf(2, 3, 2, 2),         // 1: S
            intArrayOf(0, 0, -1, 1),        // 2: W
            intArrayOf(0, 0, -1, 1),        // 3: E
            intArrayOf(-2, -1, -2),         // 4: NW
            intArrayOf(-2, -1, -2),         // 5: NE
            intArrayOf(2, 1, 2),            // 6: SW
            intArrayOf(2, 1, 2)             // 7: SE
        )

        val bgRingDx = intArrayOf(0, 0, -7, 7, -5, 5, -5, 5, 0, 0, -11, 11, -8, 8, -8, 8)
        val bgRingDy = intArrayOf(-7, 7, 0, 0, -5, -5, 5, 5, -11, 11, 0, 0, -8, -8, 8, 8)

        val vigGuardDx = intArrayOf(-16, 16, 0, 0, -12, 12, -12, 12)
        val vigGuardDy = intArrayOf(0, 0, -16, 16, -12, -12, 12, 12)

        val outer5Dx = intArrayOf(0, 0, -5, 5, -4, 4, -4, 4)
        val outer5Dy = intArrayOf(-5, 5, 0, 0, -4, -4, 4, 4)

        val scores = FloatArray(gridW * gridH)
        val isLiveMap = BooleanArray(gridW * gridH) { true }
        val isOnLineMap = BooleanArray(gridW * gridH)
        val viabilityMap = FloatArray(gridW * gridH)

        val dropThreshold = (0.112f - clampedSens * 0.072f).coerceIn(0.040f, 0.105f)
        val moatThreshold = (0.032f - clampedSens * 0.022f).coerceIn(0.010f, 0.029f)
        val minPeakScore = (0.132f - clampedSens * 0.082f).coerceIn(0.048f, 0.125f)

        val bgVals = FloatArray(16)
        val rawSecA = FloatArray(8)
        val rawSecB = FloatArray(8)
        val sectorMins = FloatArray(8)
        val sectorSmallMins = FloatArray(8)
        val offLineMins = FloatArray(6)
        val outer5Vals = FloatArray(8)

        val margin = 16
        for (y in margin until gridH - margin) {
            val row = y * gridW
            for (x in margin until gridW - margin) {
                // 1. Ocular vignette guard: skip pixels near the dark microscope eyepiece border
                var nearDarkBorder = false
                for (k in 0 until 8) {
                    if (luma[(y + vigGuardDy[k]) * gridW + (x + vigGuardDx[k])] < 0.35f) {
                        nearDarkBorder = true
                        break
                    }
                }
                if (nearDarkBorder) continue
                if (x >= 22 && luma[row + x - 22] < 0.30f) continue
                if (y >= 22 && luma[(y - 22) * gridW + x] < 0.30f) continue

                // 2. Local background ring statistics (16 points at r=7..11 px)
                var bgBlueSum = 0f
                for (k in 0 until 16) {
                    val bIdx = (y + bgRingDy[k]) * gridW + (x + bgRingDx[k])
                    bgVals[k] = luma[bIdx]
                    bgBlueSum += blueExcess[bIdx]
                }
                bgVals.sort()
                val bgMin = bgVals[0]
                val bgLow = bgVals[4]  // 25th percentile (immune to nearby bright grid lines)
                val bgBase = bgVals[6] // 40th percentile
                val bgMed = bgVals[8]
                val bgHigh = bgVals[13]
                if (bgBase < 0.43f || bgMin < 0.35f) continue
                if (bgHigh - bgBase > 0.085f && bgMin < 0.42f) continue

                val bgBlue = bgBlueSum / 16f
                val idx = row + x
                val c0 = luma[idx]
                var cCore = c0
                if (luma[idx - 1] > cCore) cCore = luma[idx - 1]
                if (luma[idx + 1] > cCore) cCore = luma[idx + 1]
                if (luma[idx - gridW] > cCore) cCore = luma[idx - gridW]
                if (luma[idx + gridW] > cCore) cCore = luma[idx + gridW]

                // 3. Neubauer Grid-Line Ridge Analysis at d=5, 8, 11 px
                var nRidgeSum = 0f
                var sRidgeSum = 0f
                var wRidgeSum = 0f
                var eRidgeSum = 0f
                val ridgeOffsets = intArrayOf(5, 8, 11)
                for (rIdx in 0 until 3) {
                    val d = ridgeOffsets[rIdx]
                    val nyUp = (y - d).coerceAtLeast(0) * gridW
                    val nyDn = (y + d).coerceAtMost(gridH - 1) * gridW
                    val nxLt = (x - d).coerceAtLeast(0)
                    val nxRt = (x + d).coerceAtMost(gridW - 1)

                    nRidgeSum += maxOf(luma[nyUp + x - 1], luma[nyUp + x], luma[nyUp + x + 1])
                    sRidgeSum += maxOf(luma[nyDn + x - 1], luma[nyDn + x], luma[nyDn + x + 1])
                    wRidgeSum += maxOf(luma[(y - 1) * gridW + nxLt], luma[row + nxLt], luma[(y + 1) * gridW + nxLt])
                    eRidgeSum += maxOf(luma[(y - 1) * gridW + nxRt], luma[row + nxRt], luma[(y + 1) * gridW + nxRt])
                }
                val nRidge = nRidgeSum / 3f
                val sRidge = sRidgeSum / 3f
                val wRidge = wRidgeSum / 3f
                val eRidge = eRidgeSum / 3f

                val onVertLine = nRidge > bgBase + 0.032f && sRidge > bgBase + 0.032f
                val onHorizLine = wRidge > bgBase + 0.032f && eRidge > bgBase + 0.032f

                // Compute 8-sector membrane minimums at both scales
                for (sec in 0 until 8) {
                    val sDx = sectorDx[sec]
                    val sDy = sectorDy[sec]
                    var minVal = 1f
                    for (m in sDx.indices) {
                        val v = luma[(y + sDy[m]) * gridW + (x + sDx[m])]
                        if (v < minVal) minVal = v
                    }
                    rawSecA[sec] = minVal
                    sectorMins[sec] = minVal

                    val smDx = sectorSmallDx[sec]
                    val smDy = sectorSmallDy[sec]
                    var minSmVal = 1f
                    for (m in smDx.indices) {
                        val v = luma[(y + smDy[m]) * gridW + (x + smDx[m])]
                        if (v < minSmVal) minSmVal = v
                    }
                    rawSecB[sec] = minSmVal
                    sectorSmallMins[sec] = minSmVal
                }
                sectorMins.sort()
                sectorSmallMins.sort()

                if (!onVertLine && !onHorizLine) {
                    // 4A. Interior Cell Detection (off grid lines)
                    val ring6thA = sectorMins[5]
                    val ring7thA = sectorMins[6]
                    val dropA = cCore - ring6thA
                    val moatA = bgBase - ring6thA
                    val validA = ring7thA < cCore - 0.038f &&
                        ring7thA < bgMed + 0.015f &&
                        dropA > dropThreshold &&
                        moatA > moatThreshold

                    val ring6thB = sectorSmallMins[5]
                    val ring7thB = sectorSmallMins[6]
                    val dropB = cCore - ring6thB
                    val moatB = bgBase - ring6thB
                    val validB = ring7thB < cCore - 0.038f &&
                        ring7thB < bgMed + 0.015f &&
                        dropB > dropThreshold &&
                        moatB > moatThreshold

                    if ((validA || validB) && cCore > bgBase - 0.02f) {
                        val scoreA = if (validA) (dropA * 0.65f + moatA * 0.95f + maxOf(0f, cCore - bgBase) * 0.35f) else 0f
                        val scoreB = if (validB) (dropB * 0.65f + moatB * 0.95f + maxOf(0f, cCore - bgBase) * 0.35f) else 0f
                        val bestScore = maxOf(scoreA, scoreB)
                        val bestDrop = maxOf(dropA, dropB)
                        val bestMoat = maxOf(moatA, moatB)

                        scores[idx] = bestScore

                        val headroom = maxOf(0.15f, 1.0f - bgLow)
                        val normPeak = (cCore - bgLow) / headroom
                        val relBlue = blueExcess[idx] - bgBlue
                        val isDarkBlotch = (c0 < bgBase - 0.06f) || ((cCore - bgBase) < 0.045f && bestMoat > 0.06f)
                        val isLive = (normPeak >= 0.16f) && (bestDrop >= 0.085f) && (relBlue < 0.065f) && !isDarkBlotch

                        isLiveMap[idx] = isLive
                        isOnLineMap[idx] = false
                        viabilityMap[idx] = normPeak + bestDrop * 0.5f - maxOf(0f, relBlue) * 2.0f
                    }
                } else if (onVertLine xor onHorizLine) {
                    // 4B. Cells Sitting Directly ON a Neubauer Grid Line:
                    // Distinguishes true cells sitting on a grid line from bare lines or tiny glass etch specks:
                    // - All 6 off-line sectors (4 diagonals + 2 perpendiculars) must have a genuine circular dark membrane moat
                    // - Along the line itself (N/S for vertical, W/E for horizontal), the cell's dark membrane ring must dip below the bright ridge
                    var offIdx = 0
                    for (sec in 0 until 8) {
                        val isLineAxis = if (onVertLine) (sec == 0 || sec == 1) else (sec == 2 || sec == 3)
                        if (!isLineAxis) {
                            offLineMins[offIdx++] = minOf(rawSecA[sec], rawSecB[sec])
                        }
                    }
                    offLineMins.sort()
                    val offLine5th = offLineMins[4]
                    val offLine6th = offLineMins[5]
                    val lineAxisMin = if (onVertLine) {
                        maxOf(minOf(rawSecA[0], rawSecB[0]), minOf(rawSecA[1], rawSecB[1]))
                    } else {
                        maxOf(minOf(rawSecA[2], rawSecB[2]), minOf(rawSecA[3], rawSecB[3]))
                    }
                    val ridgeRef = if (onVertLine) maxOf(nRidge, sRidge) else maxOf(wRidge, eRidge)

                    val dropOffLine = cCore - offLine5th
                    val moatOffLine = bgLow - offLine5th
                    val dipAcrossLine = ridgeRef - lineAxisMin

                    // A real cell on a grid line has a wide circular dark membrane ring extending into the chamber on both sides of the line
                    // AND cuts across the bright grid line (dipAcrossLine > 0.055f)
                    if (offLine6th < bgLow - 0.025f &&
                        dropOffLine > dropThreshold * 1.35f &&
                        moatOffLine > moatThreshold * 1.45f &&
                        dipAcrossLine > 0.055f &&
                        cCore > ridgeRef - 0.015f
                    ) {
                        val lineCellScore = dropOffLine * 0.60f + moatOffLine * 0.85f + dipAcrossLine * 0.50f
                        scores[idx] = lineCellScore
                        val headroom = maxOf(0.15f, 1.0f - bgLow)
                        val normPeak = (cCore - bgLow) / headroom
                        val relBlue = blueExcess[idx] - bgBlue
                        val isLive = (normPeak >= 0.16f) && (relBlue < 0.065f)
                        isLiveMap[idx] = isLive
                        isOnLineMap[idx] = true
                        viabilityMap[idx] = normPeak + dropOffLine * 0.4f - maxOf(0f, relBlue) * 2.0f
                    }
                }

                // 5. Check Dead / Trypan-blue dark-core circular cell (compact dark cell body surrounded by chamber background)
                if (!onVertLine && !onHorizLine && c0 < bgBase - 0.080f && (bgHigh - bgBase) < 0.050f) {
                    for (k in 0 until 8) {
                        outer5Vals[k] = luma[(y + outer5Dy[k]) * gridW + (x + outer5Dx[k])]
                    }
                    outer5Vals.sort()
                    if (outer5Vals[7] < bgMed + 0.028f) {
                        val coreMean = (c0 + luma[idx - 1] + luma[idx + 1] + luma[idx - gridW] + luma[idx + gridW]) * 0.2f
                        val darkContrast = outer5Vals[1] - coreMean
                        if (darkContrast > 0.075f) {
                            val relBlue = maxOf(0f, blueExcess[idx] - bgBlue)
                            val dScore = darkContrast + relBlue * 0.6f
                            if (dScore > scores[idx]) {
                                scores[idx] = dScore
                                isLiveMap[idx] = false
                                viabilityMap[idx] = -darkContrast - relBlue
                            }
                        }
                    }
                }
            }
        }

        data class RawPeak(
            val x: Int,
            val y: Int,
            val score: Float,
            val viabilityScore: Float,
            val isLive: Boolean,
            val isOnLine: Boolean
        )
        val livePeaks = ArrayList<RawPeak>(256)
        val deadPeaks = ArrayList<RawPeak>(128)

        // Use a 3x3 local peak window so closely overlapping/touching cell doublets (2.2..3.5 px apart) produce distinct peaks
        for (y in margin until gridH - margin) {
            val row = y * gridW
            for (x in margin until gridW - margin) {
                val idx = row + x
                val s = scores[idx]
                if (s < minPeakScore) continue

                var isMax = true
                for (dy in -1..1) {
                    val rOff = (y + dy) * gridW
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (scores[rOff + x + dx] > s) {
                            isMax = false
                            break
                        }
                    }
                    if (!isMax) break
                }
                if (isMax) {
                    val peak = RawPeak(
                        x = x,
                        y = y,
                        score = s,
                        viabilityScore = viabilityMap[idx],
                        isLive = isLiveMap[idx],
                        isOnLine = isOnLineMap[idx]
                    )
                    if (peak.isLive) {
                        livePeaks.add(peak)
                    } else {
                        deadPeaks.add(peak)
                    }
                }
            }
        }

        // Two-stage NMS with Overlapping / Touching Doublet Saddle Verification:
        livePeaks.sortByDescending { it.score }
        val accepted = ArrayList<DetectedCellCandidate>(minOf(livePeaks.size + deadPeaks.size, 600))

        for (peak in livePeaks) {
            if (accepted.size >= 600) break
            val nx = (peak.x + 0.5f) / gridW.toFloat()
            val ny = (peak.y + 0.5f) / gridH.toFloat()

            var blockedBySameCell = false
            var isOverlappingPartner = false
            for (existing in accepted) {
                val exPx = existing.normalizedX * gridW - 0.5f
                val exPy = existing.normalizedY * gridH - 0.5f
                val dist = hypot(exPx - peak.x, exPy - peak.y)
                if (dist < 2.15f) {
                    blockedBySameCell = true
                    break
                } else if (dist < 3.75f) {
                    // Check if there is a membrane dip / saddle between the two close peaks (overlapping doublet!)
                    val midX = ((exPx + peak.x) * 0.5f).toInt().coerceIn(1, gridW - 2)
                    val midY = ((exPy + peak.y) * 0.5f).toInt().coerceIn(1, gridH - 2)
                    val l1 = luma[exPy.toInt().coerceIn(0, gridH - 1) * gridW + exPx.toInt().coerceIn(0, gridW - 1)]
                    val l2 = luma[peak.y * gridW + peak.x]
                    val lMid = luma[midY * gridW + midX]
                    val sMid = scores[midY * gridW + midX]
                    val hasSaddleDip = (lMid < minOf(l1, l2) - 0.015f) || (sMid < minOf(existing.blobScore, peak.score) * 0.84f)
                    if (hasSaddleDip && peak.score >= minPeakScore * 1.05f) {
                        isOverlappingPartner = true
                    } else {
                        blockedBySameCell = true
                        break
                    }
                } else if (dist <= 6.8f) {
                    isOverlappingPartner = true
                }
            }

            if (!blockedBySameCell) {
                val nearGridLine = peak.isOnLine || isPointNearNeubauerGridLine(nx, ny, detectedGrid, 0.020f)
                accepted.add(
                    DetectedCellCandidate(
                        normalizedX = nx,
                        normalizedY = ny,
                        blobScore = peak.score,
                        viabilityScore = peak.viabilityScore,
                        defaultIsLive = true,
                        isOnGridLine = nearGridLine,
                        isOverlapping = isOverlappingPartner
                    )
                )
            }
        }

        // Secondary Figure-8 / Tightly Overlapping Doublet Lobe Deconvolver:
        // When two cells overlap closely, 2-3 sectors of the second lobe face the first cell's bright core.
        // We inspect the r=2.3..4.5 px ring around each accepted cell for a secondary refractile core with >=5 of 8 dark sectors.
        val additionalDoubletLobes = ArrayList<DetectedCellCandidate>()
        for (parent in accepted) {
            val px = (parent.normalizedX * gridW).toInt().coerceIn(margin, gridW - margin - 1)
            val py = (parent.normalizedY * gridH).toInt().coerceIn(margin, gridH - margin - 1)
            val parentLuma = luma[py * gridW + px]
            var bestLobeX = -1
            var bestLobeY = -1
            var bestLobeScore = 0f

            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = hypot(dx.toFloat(), dy.toFloat())
                    if (dist < 2.2f || dist > 4.6f) continue
                    val cx = px + dx
                    val cy = py + dy
                    val cVal = luma[cy * gridW + cx]
                    if (cVal < 0.52f) continue

                    // Require a darker membrane waist / saddle between parent and secondary lobe
                    val midX = px + dx / 2
                    val midY = py + dy / 2
                    val midVal = luma[midY * gridW + midX]
                    if (midVal >= minOf(parentLuma, cVal) - 0.016f) continue

                    // Check if (cx, cy) has a dark membrane ring in at least 5 of 8 sectors (since 2-3 sectors face the parent cell)
                    for (sec in 0 until 8) {
                        val smDx = sectorSmallDx[sec]
                        val smDy = sectorSmallDy[sec]
                        var minSmVal = 1f
                        for (m in smDx.indices) {
                            val v = luma[(cy + smDy[m]) * gridW + (cx + smDx[m])]
                            if (v < minSmVal) minSmVal = v
                        }
                        sectorSmallMins[sec] = minSmVal
                    }
                    sectorSmallMins.sort()
                    val ring5th = sectorSmallMins[4]
                    val drop5 = cVal - ring5th
                    if (drop5 > dropThreshold * 1.10f && drop5 > bestLobeScore) {
                        val nx = (cx + 0.5f) / gridW.toFloat()
                        val ny = (cy + 0.5f) / gridH.toFloat()
                        val alreadyCovered = accepted.any { existing ->
                            existing !== parent && hypot((existing.normalizedX - nx) * gridW, (existing.normalizedY - ny) * gridH) < 2.8f
                        } || additionalDoubletLobes.any { existing ->
                            hypot((existing.normalizedX - nx) * gridW, (existing.normalizedY - ny) * gridH) < 2.8f
                        }
                        if (!alreadyCovered) {
                            bestLobeScore = drop5
                            bestLobeX = cx
                            bestLobeY = cy
                        }
                    }
                }
            }

            if (bestLobeX >= 0 && bestLobeY >= 0) {
                val nx = (bestLobeX + 0.5f) / gridW.toFloat()
                val ny = (bestLobeY + 0.5f) / gridH.toFloat()
                val nearLine = isPointNearNeubauerGridLine(nx, ny, detectedGrid, 0.020f)
                additionalDoubletLobes.add(
                    DetectedCellCandidate(
                        normalizedX = nx,
                        normalizedY = ny,
                        blobScore = bestLobeScore,
                        viabilityScore = parent.viabilityScore,
                        defaultIsLive = true,
                        isOnGridLine = nearLine,
                        isOverlapping = true
                    )
                )
            }
        }
        accepted.addAll(additionalDoubletLobes)

        // Stage 2: Accept Dead peaks only if >= 6.5px away from any Live cell and >= 3.8px from other Dead cells
        deadPeaks.sortByDescending { it.score }
        for (peak in deadPeaks) {
            if (accepted.size >= 600) break
            val nx = (peak.x + 0.5f) / gridW.toFloat()
            val ny = (peak.y + 0.5f) / gridH.toFloat()
            val tooClose = accepted.any { existing ->
                val dx = (existing.normalizedX - nx) * gridW
                val dy = (existing.normalizedY - ny) * gridH
                val minDist = if (existing.defaultIsLive) 6.5f else 3.8f
                hypot(dx, dy) < minDist
            }
            if (!tooClose) {
                val nearLine = peak.isOnLine || isPointNearNeubauerGridLine(nx, ny, detectedGrid, 0.020f)
                accepted.add(
                    DetectedCellCandidate(
                        normalizedX = nx,
                        normalizedY = ny,
                        blobScore = peak.score,
                        viabilityScore = peak.viabilityScore,
                        defaultIsLive = false,
                        isOnGridLine = nearLine,
                        isOverlapping = false
                    )
                )
            }
        }

        val finalCandidates = markOverlappingAndGridLineCells(accepted, detectedGrid)
        return FullSlideAnalysisResult(
            candidates = finalCandidates,
            detectedGrid = detectedGrid
        )
    }

    private fun extractCellCandidates(
        bitmap: Bitmap,
        sensitivity: Float
    ): List<DetectedCellCandidate> {
        return analyzeFullSlideDirectly(bitmap, sensitivity).candidates
    }

    private fun extractDarkFieldCellCandidates(
        luma: FloatArray,
        blueExcess: FloatArray,
        gridW: Int,
        gridH: Int,
        clampedSens: Float
    ): List<DetectedCellCandidate> {
        val scores = FloatArray(gridW * gridH)
        val isLiveMap = BooleanArray(gridW * gridH) { true }
        val viabilityMap = FloatArray(gridW * gridH)
        val ringDx = intArrayOf(0, 0, -7, 7, -5, 5, -5, 5)
        val ringDy = intArrayOf(-7, 7, 0, 0, -5, -5, 5, 5)
        val bgVals = FloatArray(8)

        val margin = 10
        for (y in margin until gridH - margin) {
            val row = y * gridW
            for (x in margin until gridW - margin) {
                val idx = row + x
                for (k in 0 until 8) {
                    bgVals[k] = luma[(y + ringDy[k]) * gridW + (x + ringDx[k])]
                }
                bgVals.sort()
                val bgMed = bgVals[3]
                val c0 = luma[idx]
                val diff = c0 - bgMed
                val bEx = blueExcess[idx]
                if (diff > 0.08f) {
                    scores[idx] = diff
                    isLiveMap[idx] = diff > 0.25f && bEx < 0.12f
                    viabilityMap[idx] = if (isLiveMap[idx]) diff else -diff
                } else if (bEx > 0.08f && abs(diff) > 0.025f) {
                    scores[idx] = bEx
                    isLiveMap[idx] = false
                    viabilityMap[idx] = -bEx
                }
            }
        }

        val threshold = (0.14f - clampedSens * 0.08f).coerceIn(0.05f, 0.15f)
        val accepted = ArrayList<DetectedCellCandidate>()
        for (y in margin until gridH - margin) {
            val row = y * gridW
            for (x in margin until gridW - margin) {
                val s = scores[row + x]
                if (s < threshold) continue
                var isMax = true
                for (dy in -3..3) {
                    val rOff = (y + dy) * gridW
                    for (dx in -3..3) {
                        if (dx == 0 && dy == 0) continue
                        if (scores[rOff + x + dx] > s) {
                            isMax = false
                            break
                        }
                    }
                    if (!isMax) break
                }
                if (isMax) {
                    val nx = (x + 0.5f) / gridW.toFloat()
                    val ny = (y + 0.5f) / gridH.toFloat()
                    if (accepted.none { hypot((it.normalizedX - nx) * gridW, (it.normalizedY - ny) * gridH) < 5.5f }) {
                        accepted.add(
                            DetectedCellCandidate(
                                normalizedX = nx,
                                normalizedY = ny,
                                blobScore = s,
                                viabilityScore = viabilityMap[row + x],
                                defaultIsLive = isLiveMap[row + x]
                            )
                        )
                    }
                }
            }
        }
        return accepted
    }

    fun updateImageDilutionFactor(raw: String) {
        _imageCounterState.update { it.copy(dilutionFactorText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateImageSquaresCount(raw: String) {
        _imageCounterState.update { it.copy(squaresCountText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateImageChamberFactor(raw: String) {
        _imageCounterState.update { it.copy(chamberFactorText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateImageSampleVolume(raw: String) {
        _imageCounterState.update { it.copy(sampleVolumeText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun toggleImageSampleVolumeUnit() {
        _imageCounterState.update { state ->
            val currentMl = state.sampleVolumeMl
            val nextUnit = state.sampleVolumeUnit.toggle()
            state.copy(
                sampleVolumeUnit = nextUnit,
                sampleVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleImageResultCountUnit() {
        _imageCounterState.update { state ->
            state.copy(resultCountUnit = state.resultCountUnit.toggle())
        }
    }

    fun transferImageCountToDilution() {
        val img = _imageCounterState.value
        val v1Ml = img.sampleVolumeMl.coerceAtLeast(1.0)
        val c1Raw = img.viableCellsPerMl
        if (c1Raw <= 0.0) return

        _dilutionState.update { state ->
            val unit = img.resultCountUnit
            val targetRaw = (c1Raw / 2.0).coerceAtLeast(100_000.0)
            val updated = state.copy(
                currentVolumeText = NumberFormatter.formatClean(state.v1Unit.fromMl(v1Ml), 5),
                currentCountText = NumberFormatter.formatCountForUnit(c1Raw, unit),
                targetCountText = NumberFormatter.formatCountForUnit(targetRaw, unit),
                c1Unit = unit,
                c2Unit = unit,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT
            )
            val v2Ml = (c1Raw * v1Ml) / targetRaw
            updateVolumesFromV1AndV2(updated, v1Ml, v2Ml)
        }
        _selectedTab.value = AppTab.DILUTION_SERUM
    }

    fun addImageCountToMixer() {
        val img = _imageCounterState.value
        val vMl = img.sampleVolumeMl.coerceAtLeast(1.0)
        val cRaw = img.viableCellsPerMl
        if (cRaw <= 0.0) return

        val id = nextSampleId++
        val unit = img.resultCountUnit
        _mixerState.update { state ->
            state.copy(
                samples = state.samples + CellSampleItem(
                    id = id,
                    label = "شمارش عکس (${NumberFormatter.formatWithCommas(img.viabilityPercent, 1)}٪ زنده)",
                    volumeText = NumberFormatter.formatClean(img.sampleVolumeUnit.fromMl(vMl), 5),
                    countText = NumberFormatter.formatCountForUnit(cRaw, unit),
                    volumeUnit = img.sampleVolumeUnit,
                    countUnit = unit
                )
            )
        }
        _selectedTab.value = AppTab.CELL_MIXER
    }

    // =========================================================================
    // TAB 4: NEUBAUER HEMOCYTOMETER & VIABILITY COUNTER
    // =========================================================================

    fun updateNeubauerLive(index: Int, raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw).filter { it.isDigit() }
        _neubauerState.update { state ->
            state.copy(
                squares = state.squares.map {
                    if (it.index == index) it.copy(liveCountText = clean) else it
                }
            )
        }
    }

    fun updateNeubauerDead(index: Int, raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw).filter { it.isDigit() }
        _neubauerState.update { state ->
            state.copy(
                squares = state.squares.map {
                    if (it.index == index) it.copy(deadCountText = clean) else it
                }
            )
        }
    }

    fun incrementNeubauerLive(index: Int, delta: Int) {
        _neubauerState.update { state ->
            state.copy(
                squares = state.squares.map {
                    if (it.index == index) {
                        val next = (it.liveCount + delta).coerceAtLeast(0)
                        it.copy(liveCountText = next.toString())
                    } else it
                }
            )
        }
    }

    fun incrementNeubauerDead(index: Int, delta: Int) {
        _neubauerState.update { state ->
            state.copy(
                squares = state.squares.map {
                    if (it.index == index) {
                        val next = (it.deadCount + delta).coerceAtLeast(0)
                        it.copy(deadCountText = next.toString())
                    } else it
                }
            )
        }
    }

    fun updateTrypanDilutionFactor(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _neubauerState.update { it.copy(trypanDilutionFactorText = clean) }
    }

    fun updateNeubauerSampleVolume(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _neubauerState.update { it.copy(sampleVolumeText = clean) }
    }

    fun toggleNeubauerSampleVolumeUnit() {
        _neubauerState.update { state ->
            val currentMl = state.sampleVolumeMl
            val nextUnit = state.sampleVolumeUnit.toggle()
            state.copy(
                sampleVolumeUnit = nextUnit,
                sampleVolumeText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun toggleNeubauerResultCountUnit() {
        _neubauerState.update { state ->
            state.copy(resultCountUnit = state.resultCountUnit.toggle())
        }
    }

    fun resetNeubauerCounts() {
        _neubauerState.update { state ->
            state.copy(
                squares = state.squares.map { it.copy(liveCountText = "0", deadCountText = "0") }
            )
        }
    }

    fun transferNeubauerToDilution() {
        val n = _neubauerState.value
        val v1Ml = n.sampleVolumeMl.coerceAtLeast(1.0)
        val c1Raw = n.viableCellsPerMl
        if (c1Raw <= 0.0) return

        _dilutionState.update { state ->
            val unit = n.resultCountUnit
            val targetRaw = (c1Raw / 2.0).coerceAtLeast(100_000.0)
            val updated = state.copy(
                currentVolumeText = NumberFormatter.formatClean(state.v1Unit.fromMl(v1Ml), 5),
                currentCountText = NumberFormatter.formatCountForUnit(c1Raw, unit),
                targetCountText = NumberFormatter.formatCountForUnit(targetRaw, unit),
                c1Unit = unit,
                c2Unit = unit,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT
            )
            val v2Ml = (c1Raw * v1Ml) / targetRaw
            updateVolumesFromV1AndV2(updated, v1Ml, v2Ml)
        }
        _selectedTab.value = AppTab.DILUTION_SERUM
    }

    fun addNeubauerResultToMixer() {
        val n = _neubauerState.value
        val vMl = n.sampleVolumeMl.coerceAtLeast(1.0)
        val cRaw = n.viableCellsPerMl
        if (cRaw <= 0.0) return

        val id = nextSampleId++
        val unit = n.resultCountUnit
        _mixerState.update { state ->
            state.copy(
                samples = state.samples + CellSampleItem(
                    id = id,
                    label = "شمارش نئوبار (${NumberFormatter.formatWithCommas(n.viabilityPercent, 1)}٪ زنده)",
                    volumeText = NumberFormatter.formatClean(n.sampleVolumeUnit.fromMl(vMl), 5),
                    countText = NumberFormatter.formatCountForUnit(cRaw, unit),
                    volumeUnit = n.sampleVolumeUnit,
                    countUnit = unit
                )
            )
        }
        _selectedTab.value = AppTab.CELL_MIXER
    }

    // =========================================================================
    // TAB 5: PLATE / FLASK SEEDING & SAVED CALCULATIONS
    // =========================================================================

    fun selectVesselPreset(preset: CultureVesselPreset) {
        _seedingState.update { state ->
            val rawCellsPerWell = preset.defaultTargetCellsPerWellMillion * 1_000_000.0
            state.copy(
                selectedPreset = preset,
                wellCountText = preset.defaultWellCount.toString(),
                volumePerWellText = NumberFormatter.formatClean(state.volumePerWellUnit.fromMl(preset.defaultMediaPerWellMl), 4),
                targetCellsPerWellText = NumberFormatter.formatCountForUnit(rawCellsPerWell, state.targetCellsPerWellUnit)
            )
        }
    }

    fun updateSeedingWellCount(raw: String) {
        _seedingState.update { it.copy(wellCountText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateSeedingVolumePerWell(raw: String) {
        _seedingState.update { it.copy(volumePerWellText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun toggleSeedingVolumePerWellUnit() {
        _seedingState.update { state ->
            val currentMl = state.volumePerWellMl
            val nextUnit = state.volumePerWellUnit.toggle()
            state.copy(
                volumePerWellUnit = nextUnit,
                volumePerWellText = NumberFormatter.formatClean(nextUnit.fromMl(currentMl), 5)
            )
        }
    }

    fun updateSeedingTargetCellsPerWell(raw: String) {
        _seedingState.update { it.copy(targetCellsPerWellText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun toggleSeedingTargetCellsUnit() {
        _seedingState.update { state ->
            val rawCells = state.targetCellsPerWellRaw
            val nextUnit = state.targetCellsPerWellUnit.toggle()
            state.copy(
                targetCellsPerWellUnit = nextUnit,
                targetCellsPerWellText = NumberFormatter.formatCountForUnit(rawCells, nextUnit)
            )
        }
    }

    fun updateSeedingStockCount(raw: String) {
        _seedingState.update { it.copy(stockCountText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun toggleSeedingStockCountUnit() {
        _seedingState.update { state ->
            val rawCells = state.stockCountRawPerMl
            val nextUnit = state.stockCountUnit.toggle()
            state.copy(
                stockCountUnit = nextUnit,
                stockCountText = NumberFormatter.formatCountForUnit(rawCells, nextUnit)
            )
        }
    }

    fun updateSeedingSerumPercent(raw: String) {
        _seedingState.update { it.copy(serumPercentText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateSeedingOverfillPercent(raw: String) {
        _seedingState.update { it.copy(overfillPercentText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun deleteSavedRecord(id: Long) {
        _savedRecords.update { list -> list.filterNot { it.id == id } }
    }
}
