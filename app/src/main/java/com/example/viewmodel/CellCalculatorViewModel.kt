package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv.CountThingsOpenCvEngine
import com.example.model.ActiveEditedField
import com.example.model.AiCellAnalysisUiState
import com.example.model.AiModelOption
import com.example.model.AppTab
import com.example.model.CellGrowthState
import com.example.model.CellLinePreset
import com.example.model.CellMarkerTool
import com.example.model.CellPointMarker
import com.example.model.CellSampleItem
import com.example.model.CountUnitMultiplier
import com.example.model.CountingEngineMode
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
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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

    private data class SlideContextCalibration(
        val microscopyMode: String,
        val liveCellSignature: String,
        val deadCellSignature: String,
        val artifactsToIgnore: String,
        val detectedGrid: DetectedNeubauerGrid,
        val analysisSummary: String
    )

    private data class QuadrantRoi(
        val index: Int,
        val label: String,
        val xStart: Float,
        val xEnd: Float,
        val yStart: Float,
        val yEnd: Float
    )

    private data class QuadrantDetectedCell(
        val quadrantIndex: Int,
        val normalizedX: Float,
        val normalizedY: Float,
        val isLive: Boolean,
        val onGridLine: Boolean,
        val isOverlapping: Boolean,
        val confidence: Int,
        val interiorScore: Float
    )

    private val aiSlideContextAndGridSchema = Schema.obj(
        properties = mapOf(
            "microscopyMode" to Schema.string(description = "Microscopy mode and staining visible, e.g. phase-contrast / brightfield Trypan Blue"),
            "liveCellVisualSignature" to Schema.string(description = "Visual signature of genuine LIVE cells in this slide (bright refractile center, thin dark membrane halo, diameter)"),
            "deadCellVisualSignature" to Schema.string(description = "Visual signature of genuine DEAD cells in this slide (dark blue/purple stained or dark solid cell body)"),
            "artifactsToIgnore" to Schema.string(description = "Non-cell artifacts present in this slide to exclude (grid intersections, scratches, dust fibers, bubbles, dark corners)"),
            "verticalGridLines" to Schema.array(
                items = Schema.integer(),
                description = "Sorted normalized X coordinates (0..1000) of the primary vertical Neubauer grid lines"
            ),
            "horizontalGridLines" to Schema.array(
                items = Schema.integer(),
                description = "Sorted normalized Y coordinates (0..1000) of the primary horizontal Neubauer grid lines"
            ),
            "detectedSquaresCount" to Schema.integer(description = "Number of main 4x4 Neubauer squares visible (typically 1)"),
            "analysisSummary" to Schema.string(description = "Concise Persian explanation of slide clarity, cell appearance, and grid quality")
        ),
        optionalProperties = listOf("verticalGridLines", "horizontalGridLines", "detectedSquaresCount")
    )

    private val aiCellItemSchema = Schema.obj(
        properties = mapOf(
            "x" to Schema.integer(description = "Exact normalized X coordinate (0..1000) of the cell center in THIS image"),
            "y" to Schema.integer(description = "Exact normalized Y coordinate (0..1000) of the cell center in THIS image"),
            "isLive" to Schema.boolean(description = "true if LIVE cell (bright/refractile core or unstained intact cell), false if DEAD (dark/Trypan-blue stained)"),
            "onGridLine" to Schema.boolean(description = "true if cell is sitting on or touching a Neubauer grid line"),
            "isOverlapping" to Schema.boolean(description = "true if cell is touching or overlapping another cell in a doublet or cluster"),
            "confidence" to Schema.integer(description = "Confidence 1..100 that this mark is a genuine biological cell and NOT an artifact")
        )
    )

    private val aiCellDetectionSchema = Schema.obj(
        properties = mapOf(
            "detectedCells" to Schema.array(
                items = aiCellItemSchema,
                description = "All genuine biological cells localized in THIS image"
            ),
            "regionNotes" to Schema.string(description = "Brief Persian summary of density, clusters, and excluded artifacts")
        ),
        optionalProperties = listOf("regionNotes")
    )

    private val stage1ContextSystemInstruction by lazy {
        content {
            text(
                """
                You are an expert hemocytometer cytopathology vision analyst inspecting a Neubauer microscope slide image.
                Perform ONLY global slide calibration and Neubauer grid line localization:
                1. Describe the exact appearance of LIVE viable cells vs DEAD (Trypan Blue stained / dark) cells in this specific image.
                2. Explicitly list non-cell artifacts present in this image so they are never counted:
                   - Bare Neubauer grid line intersections / bright cross junctions
                   - Elongated fibers, glass scratches, irregular debris, dust specks
                   - Hollow air bubbles with thick flat black rings and glare centers
                   - Dark circular ocular vignette corners outside the lit field
                3. Locate the normalized coordinates (0..1000) of the primary vertical and horizontal Neubauer grid lines. Include each multi-line triple-etched border once at its center line.
                4. Write analysisSummary in clear, accurate Persian.
                """.trimIndent()
            )
        }
    }

    private val stage2DetectionSystemInstruction by lazy {
        content {
            text(
                """
                You are an ultra-high-precision microscope cell localization model.
                Your sole task is to output the exact normalized center (x, y in 0..1000) of EVERY genuine biological cell in the provided image, while strictly rejecting all non-cell artifacts.

                STRICT RULES:
                1. MARK EVERY REAL CELL:
                   - Live cells (bright/refractile round center with circular membrane halo) -> isLive = true.
                   - Dead cells (dark Trypan-blue/purple stained or dark solid cell bodies) -> isLive = false.
                   - Include faint/slightly soft real cells that clearly have a round/ovoid cell membrane.
                   - Include real cells sitting directly ON a grid line (onGridLine = true).
                   - For touching/overlapping doublets or clusters, output a SEPARATE (x, y) point at the center of EACH individual cell in the cluster (isOverlapping = true).
                2. NEVER MARK NON-CELL ARTIFACTS (ZERO FALSE POSITIVES):
                   - NEVER mark empty intersections where horizontal and vertical grid lines cross!
                   - NEVER mark bare bright/white grid lines, scratches, fibers, irregular dirt/lint, or empty background noise.
                   - NEVER mark anything inside black ocular vignette corners outside the illuminated slide field.
                   - NEVER mark air bubbles (thick flat dark ring with hollow white center and no cell interior).
                3. CONFIDENCE CALIBRATION (1..100):
                   - 85..100: Unmistakable, crisp live or dead biological cell.
                   - 65..84: Valid biological cell that is fainter, slightly out-of-focus, on a grid line, or part of an overlapping cluster.
                   - 35..64: Uncertain / very faint spot that may be either a faint cell or glass debris.
                   - Below 35: Do not include unless doubtful edge case.
                """.trimIndent()
            )
        }
    }

    private fun createStructuredJsonModel(
        modelId: String,
        schema: Schema,
        sysInstruction: com.google.firebase.ai.type.Content
    ): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelId,
            generationConfig = generationConfig {
                temperature = 0.0f
                maxOutputTokens = 16384
                responseMimeType = "application/json"
                responseSchema = schema
            },
            systemInstruction = sysInstruction
        )
    }

    private val aiUnifiedSlideAnalysisSchema = Schema.obj(
        properties = mapOf(
            "microscopyMode" to Schema.string(description = "Microscopy mode and staining visible, e.g. phase-contrast / brightfield Trypan Blue"),
            "liveCellVisualSignature" to Schema.string(description = "Visual signature of genuine LIVE cells in this slide (bright refractile center, thin dark membrane halo)"),
            "deadCellVisualSignature" to Schema.string(description = "Visual signature of genuine DEAD cells in this slide (dark blue/purple stained or dark solid cell body)"),
            "artifactsToIgnore" to Schema.string(description = "Non-cell artifacts excluded in this slide (grid intersections, scratches, dust fibers, bubbles, dark corners)"),
            "verticalGridLines" to Schema.array(
                items = Schema.integer(),
                description = "Sorted normalized X coordinates (0..1000) of primary vertical Neubauer grid lines"
            ),
            "horizontalGridLines" to Schema.array(
                items = Schema.integer(),
                description = "Sorted normalized Y coordinates (0..1000) of primary horizontal Neubauer grid lines"
            ),
            "detectedSquaresCount" to Schema.integer(description = "Number of main 4x4 Neubauer squares visible (typically 1)"),
            "detectedCells" to Schema.array(
                items = aiCellItemSchema,
                description = "All genuine biological cells localized in this full slide image"
            ),
            "analysisSummary" to Schema.string(description = "Concise Persian summary describing slide quality, live/dead cells, and artifact exclusion")
        ),
        optionalProperties = listOf("verticalGridLines", "horizontalGridLines", "detectedSquaresCount")
    )

    private val unifiedSlideSystemInstruction by lazy {
        content {
            text(
                """
                You are an expert hemocytometer cytopathology vision analyst inspecting a Neubauer microscope slide image.
                Perform full slide calibration, grid line localization, and high-precision cell center localization in ONE pass.

                STRICT RULES:
                1. MARK EVERY REAL CELL IN detectedCells (x, y in 0..1000):
                   - Live cells (bright/refractile round center with circular membrane halo) -> isLive = true.
                   - Dead cells (dark Trypan-blue/purple stained or dark solid cell bodies) -> isLive = false.
                   - Include faint/slightly soft real cells that clearly have a round/ovoid cell membrane.
                   - Include real cells sitting directly ON a grid line (onGridLine = true).
                   - For touching/overlapping doublets or clusters, output a SEPARATE (x, y) point at the center of EACH individual cell in the cluster (isOverlapping = true).
                2. NEVER MARK NON-CELL ARTIFACTS (ZERO FALSE POSITIVES):
                   - NEVER mark empty intersections where horizontal and vertical grid lines cross!
                   - NEVER mark bare bright/white grid lines, scratches, fibers, irregular dirt/lint, or empty background noise.
                   - NEVER mark anything inside black ocular vignette corners outside the illuminated slide field.
                   - NEVER mark air bubbles (thick flat dark ring with hollow white center and no cell interior).
                3. CONFIDENCE CALIBRATION (1..100):
                   - 85..100: Unmistakable, crisp live or dead biological cell.
                   - 65..84: Valid biological cell that is fainter, slightly out-of-focus, on a grid line, or part of an overlapping cluster.
                   - 35..64: Uncertain / very faint spot that may be either a faint cell or glass debris.
                   - Below 35: Do not include unless doubtful edge case.
                4. Locate normalized coordinates (0..1000) of the primary vertical and horizontal Neubauer grid lines (include each multi-line border once at its center line) and write analysisSummary in clear, accurate Persian.
                """.trimIndent()
            )
        }
    }

    private val stage1ModelsByOption: Map<AiModelOption, GenerativeModel> by lazy {
        AiModelOption.entries.associateWith { opt ->
            createStructuredJsonModel(opt.modelId, aiSlideContextAndGridSchema, stage1ContextSystemInstruction)
        }
    }

    private val stage2ModelsByOption: Map<AiModelOption, GenerativeModel> by lazy {
        AiModelOption.entries.associateWith { opt ->
            createStructuredJsonModel(opt.modelId, aiCellDetectionSchema, stage2DetectionSystemInstruction)
        }
    }

    private val unifiedModelsByOption: Map<AiModelOption, GenerativeModel> by lazy {
        AiModelOption.entries.associateWith { opt ->
            createStructuredJsonModel(opt.modelId, aiUnifiedSlideAnalysisSchema, unifiedSlideSystemInstruction)
        }
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

    private val _growthState = MutableStateFlow(CellGrowthState())
    val growthState: StateFlow<CellGrowthState> = _growthState.asStateFlow()

    private val _savedRecords = MutableStateFlow<List<SavedCalculationRecord>>(emptyList())
    val savedRecords: StateFlow<List<SavedCalculationRecord>> = _savedRecords.asStateFlow()

    private var nextSampleId = 3
    private var nextRecordId = 1L
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
            state.copy(
                samples = state.samples + CellSampleItem(
                    id = id,
                    label = "نمونه سلولی $newIndex",
                    volumeText = "",
                    countText = "",
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

    fun toggleDeepQuadrantScan() {
        _imageCounterState.update { it.copy(deepQuadrantScanEnabled = !it.deepQuadrantScanEnabled) }
    }

    fun selectCountingEngineMode(mode: CountingEngineMode) {
        _imageCounterState.update { state ->
            val cleanTool = if (mode == CountingEngineMode.GEMINI_AI_CLOUD &&
                (state.activeTool == CellMarkerTool.SAMPLE_TEMPLATE_LIVE || state.activeTool == CellMarkerTool.SAMPLE_TEMPLATE_DEAD)
            ) {
                CellMarkerTool.ADD_LIVE
            } else {
                state.activeTool
            }
            state.copy(countingEngineMode = mode, activeTool = cleanTool)
        }
    }

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

    private fun confidenceThresholdFromSensitivity(sensitivity: Float): Int {
        return (sensitivity.coerceIn(0.15f, 0.95f) * 100f).toInt().coerceIn(15, 95)
    }

    fun onImageCanvasTapped(normX: Float, normY: Float) {
        val x = normX.coerceIn(0.005f, 0.995f)
        val y = normY.coerceIn(0.005f, 0.995f)
        val currentTool = _imageCounterState.value.activeTool

        if (currentTool == CellMarkerTool.SAMPLE_TEMPLATE_LIVE ||
            currentTool == CellMarkerTool.SAMPLE_TEMPLATE_DEAD
        ) {
            val bmp = lastAnalyzedBitmap
            if (bmp == null) {
                _imageCounterState.update {
                    it.copy(
                        aiAnalysisState = AiCellAnalysisUiState.Error(
                            "ابتدا یک عکس از گالری یا دوربین انتخاب کنید تا بتوانید سلول نمونه را مشخص کنید."
                        )
                    )
                }
                return
            }
            runCountThingsTemplateMatch(
                bitmap = bmp,
                tapNormX = x,
                tapNormY = y,
                isLiveTemplate = (currentTool == CellMarkerTool.SAMPLE_TEMPLATE_LIVE)
            )
            return
        }

        _imageCounterState.update { state ->
            val hitThreshold = (0.011f + state.markerScale * 0.014f).coerceIn(0.013f, 0.028f)
            val nearest = state.markers.minByOrNull { m ->
                hypot(m.normalizedX - x, m.normalizedY - y)
            }
            val nearestDist = nearest?.let { hypot(it.normalizedX - x, it.normalizedY - y) } ?: Float.MAX_VALUE

            when (state.activeTool) {
                CellMarkerTool.SAMPLE_TEMPLATE_LIVE,
                CellMarkerTool.SAMPLE_TEMPLATE_DEAD -> state
                CellMarkerTool.ERASE -> {
                    if (nearest != null && nearestDist <= hitThreshold * 1.6f) {
                        state.copy(
                            markers = state.markers.filterNot { it.id == nearest.id },
                            allAiMarkers = state.allAiMarkers.filterNot { it.id == nearest.id }
                        )
                    } else state
                }
                CellMarkerTool.ADD_LIVE -> {
                    if (nearest != null && nearestDist <= hitThreshold) {
                        if (nearest.isLive) {
                            state.copy(
                                markers = state.markers.filterNot { it.id == nearest.id },
                                allAiMarkers = state.allAiMarkers.filterNot { it.id == nearest.id }
                            )
                        } else {
                            state.copy(
                                markers = state.markers.map {
                                    if (it.id == nearest.id) it.copy(isLive = true, confidence = 100) else it
                                },
                                allAiMarkers = state.allAiMarkers.map {
                                    if (it.id == nearest.id) it.copy(isLive = true, confidence = 100) else it
                                }
                            )
                        }
                    } else {
                        val added = CellPointMarker(
                            id = nextMarkerId++,
                            normalizedX = x,
                            normalizedY = y,
                            isLive = true,
                            isOnGridLine = isPointNearNeubauerGridLine(x, y, state.detectedGrid, 0.018f),
                            confidence = 100
                        )
                        state.copy(
                            markers = state.markers + added,
                            allAiMarkers = state.allAiMarkers + added
                        )
                    }
                }
                CellMarkerTool.ADD_DEAD -> {
                    if (nearest != null && nearestDist <= hitThreshold) {
                        if (!nearest.isLive) {
                            state.copy(
                                markers = state.markers.filterNot { it.id == nearest.id },
                                allAiMarkers = state.allAiMarkers.filterNot { it.id == nearest.id }
                            )
                        } else {
                            state.copy(
                                markers = state.markers.map {
                                    if (it.id == nearest.id) it.copy(isLive = false, confidence = 100) else it
                                },
                                allAiMarkers = state.allAiMarkers.map {
                                    if (it.id == nearest.id) it.copy(isLive = false, confidence = 100) else it
                                }
                            )
                        }
                    } else {
                        val added = CellPointMarker(
                            id = nextMarkerId++,
                            normalizedX = x,
                            normalizedY = y,
                            isLive = false,
                            isOnGridLine = isPointNearNeubauerGridLine(x, y, state.detectedGrid, 0.018f),
                            confidence = 100
                        )
                        state.copy(
                            markers = state.markers + added,
                            allAiMarkers = state.allAiMarkers + added
                        )
                    }
                }
            }
        }
    }

    private fun runCountThingsTemplateMatch(
        bitmap: Bitmap,
        tapNormX: Float,
        tapNormY: Float,
        isLiveTemplate: Boolean
    ) {
        val typeLabel = if (isLiveTemplate) "سلول زنده" else "سلول مرده"
        viewModelScope.launch {
            _imageCounterState.update {
                it.copy(
                    aiAnalysisState = AiCellAnalysisUiState.Loading(
                        statusText = "در حال تطبیق الگوی CountThings بر اساس نمونه $typeLabel..."
                    )
                )
            }
            try {
                val otherTypeExisting = _imageCounterState.value.allAiMarkers.filter { it.isLive != isLiveTemplate }
                val startId = nextMarkerId
                val result = withContext(Dispatchers.Default) {
                    CountThingsOpenCvEngine.analyzeWithTappedTemplate(
                        sourceBitmap = bitmap,
                        tapNormX = tapNormX,
                        tapNormY = tapNormY,
                        isLiveTemplate = isLiveTemplate,
                        existingOtherTypeMarkers = otherTypeExisting,
                        nextIdStart = startId
                    )
                }
                nextMarkerId = (result.markers.maxOfOrNull { it.id } ?: startId) + 1L
                val minConf = confidenceThresholdFromSensitivity(_imageCounterState.value.detectionSensitivity)
                val visible = result.markers.filter { it.confidence >= minConf }

                _imageCounterState.update {
                    it.copy(
                        templateMatchInfo = "الگوی $typeLabel با تطبیق OpenCV فعال است",
                        activeTool = if (isLiveTemplate) CellMarkerTool.ADD_LIVE else CellMarkerTool.ADD_DEAD
                    )
                }

                publishFullSlideAnalysisState(
                    allAiMarkers = result.markers,
                    visibleMarkers = visible,
                    detectedGrid = result.detectedGrid,
                    sourceTitle = "CountThings تطبیق الگو ($typeLabel)",
                    customAiSummary = result.summaryPersian,
                    technicalDetails = result.technicalDetails,
                    technicalOutput = result.technicalOutput,
                    openFullScreen = _imageCounterState.value.showFullScreenEditor
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "CountThings template matching failed", e)
                _imageCounterState.update {
                    it.copy(
                        aiAnalysisState = AiCellAnalysisUiState.Error(
                            "خطا در تطبیق الگوی OpenCV: ${e.localizedMessage ?: "خطای نامشخص"}"
                        )
                    )
                }
            }
        }
    }

    fun analyzeBitmapWithOpenCvCountThings(
        bitmap: Bitmap,
        sourceLabel: String = "آنالیز آفلاین CountThings (OpenCV)"
    ) {
        lastAnalyzedBitmap = bitmap
        viewModelScope.launch {
            _imageCounterState.update {
                it.copy(
                    markers = emptyList(),
                    allAiMarkers = emptyList(),
                    detectedGrid = DetectedNeubauerGrid(),
                    templateMatchInfo = "",
                    aiAnalysisState = AiCellAnalysisUiState.Loading(
                        statusText = "در حال پردازش آفلاین سبک CountThings (OpenCV: حذف شبکه + تشخیص هاله سلول)..."
                    ),
                    imageSourceLabel = "$sourceLabel (در حال شمارش OpenCV...)"
                )
            }

            try {
                val startId = nextMarkerId
                val result = withContext(Dispatchers.Default) {
                    CountThingsOpenCvEngine.analyzeAuto(
                        sourceBitmap = bitmap,
                        nextIdStart = startId
                    )
                }
                nextMarkerId = (result.markers.maxOfOrNull { it.id } ?: startId) + 1L

                val minConf = confidenceThresholdFromSensitivity(_imageCounterState.value.detectionSensitivity)
                val visible = result.markers.filter { it.confidence >= minConf }

                publishFullSlideAnalysisState(
                    allAiMarkers = result.markers,
                    visibleMarkers = visible,
                    detectedGrid = result.detectedGrid,
                    sourceTitle = "CountThings OpenCV (آفلاین)",
                    customAiSummary = result.summaryPersian,
                    technicalDetails = result.technicalDetails,
                    technicalOutput = result.technicalOutput,
                    openFullScreen = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OpenCV auto cell detection failed", e)
                _imageCounterState.update {
                    it.copy(
                        aiAnalysisState = AiCellAnalysisUiState.Error(
                            "خطا در موتور آفلاین OpenCV: ${e.localizedMessage ?: "خطای پردازش تصویر"}"
                        )
                    )
                }
            }
        }
    }

    fun analyzeSlideWithSelectedEngine(
        bitmap: Bitmap,
        sourceLabel: String = "آنالیز تصویر لام نئوبار"
    ) {
        lastAnalyzedBitmap = bitmap
        when (_imageCounterState.value.countingEngineMode) {
            CountingEngineMode.OPENCV_COUNTTHINGS -> analyzeBitmapWithOpenCvCountThings(bitmap, sourceLabel)
            CountingEngineMode.GEMINI_AI_CLOUD -> analyzeBitmapWithGeminiAi(bitmap, sourceLabel)
        }
    }

    fun undoLastImageMarker() {
        _imageCounterState.update { state ->
            if (state.markers.isEmpty()) state
            else {
                val removedId = state.markers.last().id
                state.copy(
                    markers = state.markers.dropLast(1),
                    allAiMarkers = state.allAiMarkers.filterNot { it.id == removedId }
                )
            }
        }
    }

    fun clearAllImageMarkers() {
        _imageCounterState.update {
            it.copy(markers = emptyList(), allAiMarkers = emptyList())
        }
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
                allAiMarkers = emptyList(),
                detectedGrid = DetectedNeubauerGrid(),
                showFullScreenEditor = false,
                imageSourceLabel = "عکسی انتخاب نشده — از گالری یا دوربین عکس لام را وارد کنید",
                aiAnalysisState = AiCellAnalysisUiState.Idle
            )
        }
    }

    /**
     * Dynamically filters already-detected AI cells by their AI confidence score (15%..95%)
     * without invoking any local pixel heuristic or re-calling the AI API.
     */
    fun updateImageSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.15f, 0.95f)
        val minConf = confidenceThresholdFromSensitivity(clamped)
        _imageCounterState.update { state ->
            if (state.allAiMarkers.isEmpty()) {
                state.copy(detectionSensitivity = clamped)
            } else {
                val filteredMarkers = state.allAiMarkers.filter { it.confidence >= minConf }
                val live = filteredMarkers.count { it.isLive }
                val dead = filteredMarkers.count { !it.isLive }
                val total = live + dead
                val onLine = filteredMarkers.count { it.isOnGridLine }
                val overlap = filteredMarkers.count { it.isOverlapping }
                val viability = if (total > 0) (live * 100.0 / total) else 0.0
                val squares = state.squaresCount.coerceAtLeast(0.01)
                val dilution = state.dilutionFactor.coerceAtLeast(1.0)
                val chamber = state.chamberFactor.coerceAtLeast(1.0)
                val viablePerMl = (live.toDouble() / squares) * dilution * chamber
                val totalPerMl = (total.toDouble() / squares) * dilution * chamber

                val tempState = state.copy(markers = filteredMarkers)
                val grid4x4Live = tempState.computeGrid4x4Counts(isLiveFilter = true)
                val grid4x4Dead = tempState.computeGrid4x4Counts(isLiveFilter = false)

                val prevSuccess = state.aiAnalysisState as? AiCellAnalysisUiState.Success
                val updatedAiState = prevSuccess?.copy(
                    liveCount = live,
                    deadCount = dead,
                    onGridLineCount = onLine,
                    overlappingCount = overlap,
                    viableConcentrationPerMl = viablePerMl,
                    totalConcentrationPerMl = totalPerMl,
                    viabilityPercent = viability,
                    grid4x4Live = grid4x4Live,
                    grid4x4Dead = grid4x4Dead
                ) ?: state.aiAnalysisState

                state.copy(
                    detectionSensitivity = clamped,
                    markers = filteredMarkers,
                    aiAnalysisState = updatedAiState
                )
            }
        }
    }

    /**
     * Pure High-Precision Gemini Vision AI Pipeline:
     * - 100% of cell detection, live/dead classification, grid line localization, and artifact filtering
     *   is performed by Gemini AI (ZERO local pixel heuristic contamination).
     * - Default Mode (Single Unified Pass, 1792px): Performs full slide calibration + grid detection +
     *   exact cell localization in ONE single API request (safe for Free Tier 15 RPM quota!).
     * - Deep 4-Quadrant Mode (when deepQuadrantScanEnabled == true): Calibrates full slide first, then scans
     *   4 zoomed overlapping quadrants sequentially with cooldown pacing so crowded slides get 2x zoom without
     *   burst rate-limit spikes.
     */
    fun analyzeBitmapWithGeminiAi(
        bitmap: Bitmap,
        sourceLabel: String = "آنالیز کامل هوش مصنوعی Gemini"
    ) {
        lastAnalyzedBitmap = bitmap
        val selectedOption = _imageCounterState.value.selectedAiModel
        val useDeepQuadrants = _imageCounterState.value.deepQuadrantScanEnabled

        viewModelScope.launch {
            _imageCounterState.update {
                it.copy(
                    markers = emptyList(),
                    allAiMarkers = emptyList(),
                    detectedGrid = DetectedNeubauerGrid(),
                    aiAnalysisState = AiCellAnalysisUiState.Loading(
                        statusText = if (useDeepQuadrants) {
                            "مرحله ۱ از ۵: کالیبراسیون شبکه نئوبار و الگوی سلول‌ها با ${selectedOption.shortBadge}..."
                        } else {
                            "در حال ارسال تصویر با وضوح بالا (۱ درخواست) به ${selectedOption.shortBadge}..."
                        }
                    ),
                    imageSourceLabel = "$sourceLabel (در حال آنالیز با ${selectedOption.shortBadge}...)"
                )
            }

            try {
                val candidateOptions = buildModelFallbackChain(selectedOption)
                var activeModelOption = selectedOption
                var fallbackNotice: String? = null
                val calibration: SlideContextCalibration
                val rawDetections: List<QuadrantDetectedCell>

                if (!useDeepQuadrants) {
                    // Single unified high-resolution pass (1792px max side) -> ONLY 1 API call per slide
                    val unifiedBitmap = withContext(Dispatchers.Default) {
                        scaleBitmapPreserveAspect(bitmap, maxDim = 1792)
                    }

                    var unifiedResult: Pair<SlideContextCalibration, List<QuadrantDetectedCell>>? = null
                    var lastErr: Exception? = null

                    for ((idx, opt) in candidateOptions.withIndex()) {
                        try {
                            if (idx > 0) {
                                _imageCounterState.update {
                                    it.copy(
                                        aiAnalysisState = AiCellAnalysisUiState.Loading(
                                            statusText = "تلاش خودکار با مدل جایگزین ${opt.shortBadge}..."
                                        )
                                    )
                                }
                            }
                            unifiedResult = runUnifiedSinglePassAnalysis(
                                model = unifiedModelsByOption.getValue(opt),
                                scaledBitmap = unifiedBitmap
                            )
                            activeModelOption = opt
                            if (opt != selectedOption) {
                                fallbackNotice = "مدل ${selectedOption.shortBadge} در دسترس نبود (نیازمند پلن Blaze یا سهمیه پر) و آنالیز با ${opt.shortBadge} انجام شد."
                            }
                            break
                        } catch (err: Exception) {
                            if (err is CancellationException && err !is TimeoutCancellationException) {
                                throw err
                            }
                            lastErr = err
                            Log.w(TAG, "Unified pass failed on ${opt.modelId}", err)
                        }
                    }

                    val pair = unifiedResult ?: throw (
                        lastErr ?: IllegalStateException("خطا در دریافت پاسخ از مدل هوش مصنوعی")
                    )
                    calibration = pair.first
                    rawDetections = pair.second
                    if (activeModelOption != selectedOption) {
                        _imageCounterState.update { state ->
                            state.copy(selectedAiModel = activeModelOption)
                        }
                    }
                } else {
                    // Two-stage zoomed 4-quadrant pipeline (sequential pacing to stay gentle on rate limits)
                    val fullScaledBitmap = withContext(Dispatchers.Default) {
                        scaleBitmapPreserveAspect(bitmap, maxDim = 1536)
                    }

                    var stage1Result: SlideContextCalibration? = null
                    var lastStage1Error: Exception? = null

                    for ((idx, opt) in candidateOptions.withIndex()) {
                        try {
                            if (idx > 0) {
                                _imageCounterState.update {
                                    it.copy(
                                        aiAnalysisState = AiCellAnalysisUiState.Loading(
                                            statusText = "تلاش مرحله اول با مدل جایگزین ${opt.shortBadge}..."
                                        )
                                    )
                                }
                            }
                            stage1Result = runStage1SlideCalibration(
                                model = stage1ModelsByOption.getValue(opt),
                                fullScaledBitmap = fullScaledBitmap
                            )
                            activeModelOption = opt
                            if (opt != selectedOption) {
                                fallbackNotice = "مدل ${selectedOption.shortBadge} در دسترس نبود و اسکن با ${opt.shortBadge} انجام شد."
                            }
                            break
                        } catch (err: Exception) {
                            if (err is CancellationException && err !is TimeoutCancellationException) {
                                throw err
                            }
                            lastStage1Error = err
                            Log.w(TAG, "Stage 1 failed on model ${opt.modelId}", err)
                        }
                    }

                    calibration = stage1Result ?: throw (
                        lastStage1Error ?: IllegalStateException("خطا در مرحله اول آنالیز تصویر با هوش مصنوعی")
                    )

                    _imageCounterState.update { state ->
                        state.copy(
                            selectedAiModel = activeModelOption,
                            detectedGrid = calibration.detectedGrid
                        )
                    }

                    val stage2Model = stage2ModelsByOption.getValue(activeModelOption)
                    rawDetections = runStage2SequentialQuadrantDetection(
                        model = stage2Model,
                        activeModelBadge = activeModelOption.shortBadge,
                        originalBitmap = bitmap,
                        calibration = calibration
                    )
                }

                // Deduplicate cross-quadrant overlap seams and mark proximity to grid lines / overlapping clusters
                val cleanMarkers = withContext(Dispatchers.Default) {
                    finalizeAiDetectedMarkers(
                        detections = rawDetections,
                        detectedGrid = calibration.detectedGrid
                    )
                }

                val minConfidence = confidenceThresholdFromSensitivity(_imageCounterState.value.detectionSensitivity)
                val visibleMarkers = cleanMarkers.filter { it.confidence >= minConfidence }

                val scanModeLabel = if (useDeepQuadrants) "اسکن ۴ ناحیه‌ای ۲×" else "اسکن تک‌مرحله‌ای با وضوح بالا"
                val diagnosticsCode = buildAiDiagnosticsDetails(
                    modelOption = activeModelOption,
                    scanMode = scanModeLabel,
                    calibration = calibration,
                    totalRaw = cleanMarkers.size,
                    visibleCount = visibleMarkers.size,
                    minConfidence = minConfidence,
                    fallbackNotice = fallbackNotice
                )

                publishFullSlideAnalysisState(
                    allAiMarkers = cleanMarkers,
                    visibleMarkers = visibleMarkers,
                    detectedGrid = calibration.detectedGrid,
                    sourceTitle = "آنالیز ${activeModelOption.shortBadge} ($scanModeLabel)",
                    customAiSummary = calibration.analysisSummary,
                    technicalDetails = diagnosticsCode,
                    technicalOutput = "Model: ${activeModelOption.modelId} | Raw: ${cleanMarkers.size} | Filtered (>= $minConfidence%): ${visibleMarkers.size}",
                    openFullScreen = true
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini vision analysis timed out", e)
                _imageCounterState.update {
                    it.copy(
                        aiAnalysisState = AiCellAnalysisUiState.Error(
                            "زمان پاسخ‌گویی مدل هوش مصنوعی به پایان رسید. لطفاً اتصال اینترنت را بررسی کرده و دوباره تلاش کنید."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini vision analysis failed", e)
                _imageCounterState.update {
                    it.copy(
                        aiAnalysisState = AiCellAnalysisUiState.Error(
                            formatFriendlyAiError(e, selectedOption)
                        )
                    )
                }
            }
        }
    }

    private fun isQuotaOrBillingError(err: Throwable): Boolean {
        val msg = err.localizedMessage?.lowercase().orEmpty()
        return msg.contains("quota") ||
            msg.contains("rate-limit") ||
            msg.contains("rate limit") ||
            msg.contains("429") ||
            msg.contains("resource_exhausted") ||
            msg.contains("billing") ||
            msg.contains("blaze") ||
            msg.contains("free_tier") ||
            msg.contains("exceeded")
    }

    private fun formatFriendlyAiError(err: Throwable, selectedOption: AiModelOption): String {
        val raw = err.localizedMessage?.trim().orEmpty()
        val lower = raw.lowercase()
        val retrySecondsMatch = Regex("retry in ([0-9]+(?:\\.[0-9]+)?)s", RegexOption.IGNORE_CASE).find(raw)
        val retrySec = retrySecondsMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toInt() }

        return when {
            lower.contains("quota") || lower.contains("free_tier") || lower.contains("rate-limit") || lower.contains("429") || lower.contains("resource_exhausted") -> {
                buildString {
                    append("سهمیه درخواست رایگان در دقیقه (Free Tier Rate Limit) پر شده است.")
                    if (retrySec != null && retrySec > 0) {
                        append("\n• لطفاً حدود $retrySec ثانیه صبر کرده و دکمه «تلاش مجدد» را بزنید.")
                    } else {
                        append("\n• لطفاً ۱۵ تا ۶۰ ثانیه صبر کرده و دوباره تلاش کنید.")
                    }
                    append("\n• برای حذف محدودیت و استفاده بدون وقفه از مدل‌های پیشرفته (به‌ویژه Gemini 4 Argon)، باید در پنل Firebase / Google Cloud طرح پروژه به Blaze (پرداخت به ازای مصرف) ارتقا یابد.")
                }
            }
            lower.contains("billing") || lower.contains("blaze") -> {
                "مدل ${selectedOption.shortBadge} نیازمند فعال بودن پلن Blaze در Firebase است. می‌توانید از مدل رایگان Gemini 3.8 Flash استفاده کنید یا صورت‌حساب Blaze را فعال نمایید."
            }
            lower.contains("app check") || lower.contains("403") -> {
                "خطای دسترسی / App Check (کد ۴۰۳). لطفاً از اتصال اینترنت مطمئن شوید و نسخه جدید برنامه را دوباره باز کنید."
            }
            else -> {
                val cleanMsg = raw.ifBlank { "خطای نامشخص در ارتباط با سرور هوش مصنوعی" }
                "خطا در آنالیز تصویر با هوش مصنوعی: $cleanMsg"
            }
        }
    }

    private fun buildModelFallbackChain(selected: AiModelOption): List<AiModelOption> {
        val ordered = listOf(selected, AiModelOption.GEMINI_3_8_FLASH, AiModelOption.GEMINI_3_5_FLASH_LITE)
        return ordered.distinct()
    }

    private suspend fun scaleBitmapPreserveAspect(bitmap: Bitmap, maxDim: Int): Bitmap =
        withContext(Dispatchers.Default) {
            if (bitmap.width <= maxDim && bitmap.height <= maxDim) {
                bitmap
            } else {
                val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            }
        }

    private suspend fun runUnifiedSinglePassAnalysis(
        model: GenerativeModel,
        scaledBitmap: Bitmap
    ): Pair<SlideContextCalibration, List<QuadrantDetectedCell>> {
        val prompt = """
            این تصویر کامل یک لام نئوبار (Neubauer Hemocytometer) است.
            در همین یک پاسخ دقیق انجام بده:
            ۱. ویژگی ظاهری دقیق سلول‌های زنده (Live) و مرده / رنگ‌شده (Dead) را تشخیص بده و عوارض غیرسلولی (تقاطع خطوط شبکه، خط و خش، گردوغبار، حباب هوا، حاشیه تاریک گوشه‌ها) را حذف کن.
            ۲. مختصات نرمال (۰ تا ۱۰۰۰) خطوط عمودی (`verticalGridLines`) و افقی (`horizontalGridLines`) شبکه نئوبار را استخراج کن.
            ۳. مختصات دقیق مرکز تک‌تک سلول‌های واقعی داخل همین تصویر (`detectedCells` با `x` و `y` بین ۰ تا ۱۰۰۰، `isLive`, `onGridLine`, `isOverlapping`, `confidence`) را برگردان.
            ۴. سلول‌های کم‌نور، روی خطوط شبکه (`onGridLine = true`) و توده‌های روی‌هم‌افتاده (`isOverlapping = true` با نقطه مجزا برای هر سلول) را جا نینداز، اما تقاطع‌های خالی شبکه و حباب‌ها را اصلاً علامت نزن!
            ۵. خلاصه وضعیت لام را در `analysisSummary` به زبان فارسی روان بنویس.
        """.trimIndent()

        val response = withTimeout(75_000L) {
            model.generateContent(
                content {
                    image(scaledBitmap)
                    text(prompt)
                }
            )
        }
        val rawJson = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("پاسخ خالی از مدل هوش مصنوعی دریافت شد.")
        val json = JSONObject(rawJson)

        val calibration = parseSlideCalibrationFromJson(json)
        val list = ArrayList<QuadrantDetectedCell>()
        json.optJSONArray("detectedCells")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val x = obj.optInt("x", -1) / 1000f
                val y = obj.optInt("y", -1) / 1000f
                if (x in 0.005f..0.995f && y in 0.005f..0.995f) {
                    list.add(
                        QuadrantDetectedCell(
                            quadrantIndex = -1,
                            normalizedX = x,
                            normalizedY = y,
                            isLive = obj.optBoolean("isLive", true),
                            onGridLine = obj.optBoolean("onGridLine", false),
                            isOverlapping = obj.optBoolean("isOverlapping", false),
                            confidence = obj.optInt("confidence", 85).coerceIn(1, 100),
                            interiorScore = 1f
                        )
                    )
                }
            }
        }
        return calibration to list
    }

    private fun parseSlideCalibrationFromJson(json: JSONObject): SlideContextCalibration {
        val vLines = ArrayList<Float>()
        json.optJSONArray("verticalGridLines")?.let { arr ->
            for (i in 0 until arr.length()) {
                val v = arr.optInt(i, -1) / 1000f
                if (v in 0.02f..0.98f) vLines.add(v)
            }
        }
        val hLines = ArrayList<Float>()
        json.optJSONArray("horizontalGridLines")?.let { arr ->
            for (i in 0 until arr.length()) {
                val v = arr.optInt(i, -1) / 1000f
                if (v in 0.02f..0.98f) hLines.add(v)
            }
        }
        val sortedV = deduplicateGridLineCoords(vLines)
        val sortedH = deduplicateGridLineCoords(hLines)
        val smallSquares = if (sortedV.size >= 2 && sortedH.size >= 2) {
            ((sortedV.size - 1) * (sortedH.size - 1)).coerceIn(1, 16)
        } else {
            16
        }

        return SlideContextCalibration(
            microscopyMode = json.optString("microscopyMode", "Microscope Hemocytometer"),
            liveCellSignature = json.optString("liveCellVisualSignature", "Round bright/refractile core surrounded by dark circular cell membrane"),
            deadCellSignature = json.optString("deadCellVisualSignature", "Dark stained / opaque non-refractile round or ovoid cell"),
            artifactsToIgnore = json.optString("artifactsToIgnore", "Grid intersections, scratches, dust fibers, air bubbles, dark eyepiece corners"),
            detectedGrid = DetectedNeubauerGrid(
                verticalLines = sortedV,
                horizontalLines = sortedH,
                detectedSmallSquares = smallSquares,
                detectedLargeSquares = json.optInt("detectedSquaresCount", 1).coerceIn(1, 9)
            ),
            analysisSummary = json.optString("analysisSummary", "کالیبراسیون شبکه نئوبار و تشخیص ویژگی سلول‌ها با موفقیت انجام شد.")
        )
    }

    private suspend fun runStage1SlideCalibration(
        model: GenerativeModel,
        fullScaledBitmap: Bitmap
    ): SlideContextCalibration {
        val prompt = """
            این تصویر کامل یک لام نئوبار (Neubauer Hemocytometer) است.
            پیش از شمارش، ساختار کلی تصویر را دقیق کالیبره کن:
            ۱. ویژگی ظاهری دقیق «سلول‌های زنده» (Live) و «سلول‌های مرده / رنگ‌شده» (Dead) در همین عکس را شرح بده.
            ۲. تمام عوارض غیرسلولی موجود در این عکس که نباید به اشتباه شمارش شوند (مثل نقاط تقاطع خطوط سفید شبکه نئوبار، خط و خش شیشه، رشته‌ها، گردوغبار، حباب هوا، و گوشه‌های تیره چشمی میکروسکوپ) را مشخص کن.
            ۳. مختصات نرمال (۰ تا ۱۰۰۰) خطوط عمودی (`verticalGridLines`) و خطوط افقی (`horizontalGridLines`) شبکه نئوبار را با دقت بالا استخراج کن.
            ۴. خلاصه فارسی کوتاهی درباره کیفیت تصویر و نوع سلول‌ها بنویس.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    image(fullScaledBitmap)
                    text(prompt)
                }
            )
        }
        val rawJson = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("مدل در مرحله کالیبراسیون پاسخ خالی برگرداند.")
        return parseSlideCalibrationFromJson(JSONObject(rawJson))
    }

    private fun deduplicateGridLineCoords(coords: List<Float>): List<Float> {
        if (coords.isEmpty()) return emptyList()
        val sorted = coords.sorted()
        val merged = ArrayList<Float>(sorted.size)
        for (c in sorted) {
            if (merged.isEmpty() || abs(c - merged.last()) > 0.035f) {
                merged.add(c)
            }
        }
        return merged.take(8)
    }

    private suspend fun runStage2SequentialQuadrantDetection(
        model: GenerativeModel,
        activeModelBadge: String,
        originalBitmap: Bitmap,
        calibration: SlideContextCalibration
    ): List<QuadrantDetectedCell> {
        val rois = listOf(
            QuadrantRoi(0, "بالا-چپ", 0.000f, 0.555f, 0.000f, 0.555f),
            QuadrantRoi(1, "بالا-راست", 0.445f, 1.000f, 0.000f, 0.555f),
            QuadrantRoi(2, "پایین-چپ", 0.000f, 0.555f, 0.445f, 1.000f),
            QuadrantRoi(3, "پایین-راست", 0.445f, 1.000f, 0.445f, 1.000f)
        )

        val w = originalBitmap.width
        val h = originalBitmap.height
        val allResults = ArrayList<QuadrantDetectedCell>(256)

        for ((idx, roi) in rois.withIndex()) {
            _imageCounterState.update { state ->
                state.copy(
                    aiAnalysisState = AiCellAnalysisUiState.Loading(
                        statusText = "مرحله ${idx + 2} از ۵: اسکن دقیق ناحیه «${roi.label}» روی $activeModelBadge..."
                    )
                )
            }

            if (idx > 0) {
                delay(1200L)
            }

            val cropLeft = (roi.xStart * w).toInt().coerceIn(0, (w - 2).coerceAtLeast(0))
            val cropTop = (roi.yStart * h).toInt().coerceIn(0, (h - 2).coerceAtLeast(0))
            val cropRight = (roi.xEnd * w).toInt().coerceIn(cropLeft + 1, w)
            val cropBottom = (roi.yEnd * h).toInt().coerceIn(cropTop + 1, h)
            val cropW = (cropRight - cropLeft).coerceAtLeast(1)
            val cropH = (cropBottom - cropTop).toInt().coerceAtLeast(1)

            val actualXStart = cropLeft.toFloat() / w.toFloat()
            val actualXSpan = cropW.toFloat() / w.toFloat()
            val actualYStart = cropTop.toFloat() / h.toFloat()
            val actualYSpan = cropH.toFloat() / h.toFloat()

            val cropped = withContext(Dispatchers.Default) {
                val rawCrop = Bitmap.createBitmap(originalBitmap, cropLeft, cropTop, cropW, cropH)
                scaleBitmapPreserveAspect(rawCrop, maxDim = 1024)
            }

            val quadrantPrompt = """
                این تصویر بزرگ‌نمایی‌شده ناحیه «${roi.label}» از لام نئوبار است.
                کالیبراسیون استخراج‌شده از کل لام:
                • نوع تصویربرداری: ${calibration.microscopyMode}
                • ظاهر سلول‌های زنده (Live): ${calibration.liveCellSignature}
                • ظاهر سلول‌های مرده (Dead): ${calibration.deadCellSignature}
                • موارد غیرسلولی که مطلقاً نباید علامت‌گذاری شوند: ${calibration.artifactsToIgnore}

                تک‌تک سلول‌های واقعی داخل همین برش تصویر را با مختصات دقیق مرکز هر سلول (`x`, `y` بین ۰ تا ۱۰۰۰ نسبت به همین تصویر برش‌خورده) برگردان:
                - سلول‌های کم‌نور یا چسبیده به خطوط نئوبار (`onGridLine = true`) را جا نینداز.
                - در توده‌های روی‌هم‌افتاده، مرکز هر سلول مجزا را جداگانه مشخص کن (`isOverlapping = true`).
                - نقاط تقاطع خالی خطوط سفید شبکه، حباب هوا، خط‌وخش، و حاشیه سیاه بیرون میدان دید را اصلاً علامت نزن!
            """.trimIndent()

            val json = executeStage2WithSmartRetry(model, cropped, quadrantPrompt)
            json.optJSONArray("detectedCells")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val qxRaw = obj.optInt("x", -1)
                    val qyRaw = obj.optInt("y", -1)
                    if (qxRaw !in 0..1000 || qyRaw !in 0..1000) continue
                    val qxNorm = qxRaw / 1000f
                    val qyNorm = qyRaw / 1000f

                    val marginX = when (roi.index) {
                        0, 2 -> 1f - qxNorm
                        else -> qxNorm
                    }
                    val marginY = when (roi.index) {
                        0, 1 -> 1f - qyNorm
                        else -> qyNorm
                    }
                    val interiorScore = minOf(marginX, marginY)

                    val fullX = (actualXStart + qxNorm * actualXSpan).coerceIn(0.005f, 0.995f)
                    val fullY = (actualYStart + qyNorm * actualYSpan).coerceIn(0.005f, 0.995f)

                    allResults.add(
                        QuadrantDetectedCell(
                            quadrantIndex = roi.index,
                            normalizedX = fullX,
                            normalizedY = fullY,
                            isLive = obj.optBoolean("isLive", true),
                            onGridLine = obj.optBoolean("onGridLine", false),
                            isOverlapping = obj.optBoolean("isOverlapping", false),
                            confidence = obj.optInt("confidence", 85).coerceIn(1, 100),
                            interiorScore = interiorScore
                        )
                    )
                }
            }
        }
        return allResults
    }

    private suspend fun executeStage2WithSmartRetry(
        model: GenerativeModel,
        bitmap: Bitmap,
        prompt: String
    ): JSONObject {
        var lastErr: Exception? = null
        repeat(2) { attempt ->
            try {
                val response = withTimeout(75_000L) {
                    model.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }
                val rawText = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("پاسخ خالی از مدل هوش مصنوعی دریافت شد.")
                return JSONObject(rawText)
            } catch (err: Exception) {
                if (err is CancellationException && err !is TimeoutCancellationException) {
                    throw err
                }
                lastErr = err
                // Never immediately retry if blocked by quota/billing/rate-limit (avoids amplifying 429 errors)
                if (isQuotaOrBillingError(err)) {
                    throw err
                }
                Log.w(TAG, "Stage 2 detection attempt ${attempt + 1} failed (non-quota)", err)
                if (attempt == 0) delay(1000L)
            }
        }
        throw (lastErr ?: IllegalStateException("خطا در مرحله مکان‌یابی سلول‌ها"))
    }

    private fun finalizeAiDetectedMarkers(
        detections: List<QuadrantDetectedCell>,
        detectedGrid: DetectedNeubauerGrid
    ): List<CellPointMarker> {
        if (detections.isEmpty()) return emptyList()

        // Sort highest interiorScore (furthest from cut seams) and highest confidence first
        val ordered = detections.sortedByDescending { it.interiorScore * 100f + it.confidence.toFloat() }
        val kept = ArrayList<QuadrantDetectedCell>(ordered.size)

        for (cand in ordered) {
            val isDuplicate = kept.any { existing ->
                val dist = hypot(existing.normalizedX - cand.normalizedX, existing.normalizedY - cand.normalizedY)
                if (existing.quadrantIndex != cand.quadrantIndex) {
                    // Different quadrants overlapping in the center seam strip: deduplicate if within 1.5%
                    dist < 0.015f
                } else {
                    // Same quadrant: preserve distinct touching doublets unless virtually identical coordinate (< 0.35%)
                    dist < 0.0035f
                }
            }
            if (!isDuplicate) {
                kept.add(cand)
            }
        }

        // Compute isOverlapping from AI flag OR geometric proximity to another accepted cell (< 2.2%)
        // Compute isOnGridLine from AI flag OR geometric proximity to AI-detected grid lines (< 1.8%)
        return kept.mapIndexed { idx, cand ->
            val hasCloseNeighbor = kept.indices.any { otherIdx ->
                otherIdx != idx &&
                    hypot(kept[otherIdx].normalizedX - cand.normalizedX, kept[otherIdx].normalizedY - cand.normalizedY) <= 0.022f
            }
            val onLine = cand.onGridLine ||
                isPointNearNeubauerGridLine(cand.normalizedX, cand.normalizedY, detectedGrid, 0.018f)

            CellPointMarker(
                id = nextMarkerId++,
                normalizedX = cand.normalizedX,
                normalizedY = cand.normalizedY,
                isLive = cand.isLive,
                isOnGridLine = onLine,
                isOverlapping = cand.isOverlapping || hasCloseNeighbor,
                confidence = cand.confidence
            )
        }
    }

    private fun isPointNearNeubauerGridLine(
        nx: Float,
        ny: Float,
        grid: DetectedNeubauerGrid,
        threshold: Float = 0.018f
    ): Boolean {
        val nearVert = grid.verticalLines.any { abs(it - nx) <= threshold }
        val nearHoriz = grid.horizontalLines.any { abs(it - ny) <= threshold }
        return nearVert || nearHoriz
    }

    private fun buildAiDiagnosticsDetails(
        modelOption: AiModelOption,
        scanMode: String,
        calibration: SlideContextCalibration,
        totalRaw: Int,
        visibleCount: Int,
        minConfidence: Int,
        fallbackNotice: String? = null
    ): String {
        val headerNotice = if (!fallbackNotice.isNullOrBlank()) {
            "توجه: $fallbackNotice\n"
        } else {
            ""
        }
        return headerNotice + """
            مدل هوش مصنوعی فعال: ${modelOption.modelId} (دما = ۰، خروجی ساخت‌یافته JSON)
            حالت اسکن: $scanMode
            نوع میکروسکوپی: ${calibration.microscopyMode}
            الگوی سلول زنده (Live): ${calibration.liveCellSignature}
            الگوی سلول مرده (Dead): ${calibration.deadCellSignature}
            موارد غیرسلولی حذف‌شده: ${calibration.artifactsToIgnore}
            خطوط شبکه شناسایی‌شده: ${calibration.detectedGrid.verticalLines.size} خط عمودی × ${calibration.detectedGrid.horizontalLines.size} خط افقی
            کل تشخیص‌های خام هوش مصنوعی: $totalRaw | نمایش‌داده‌شده (اطمینان >= $minConfidence%): $visibleCount
        """.trimIndent()
    }

    private fun publishFullSlideAnalysisState(
        allAiMarkers: List<CellPointMarker>,
        visibleMarkers: List<CellPointMarker>,
        detectedGrid: DetectedNeubauerGrid,
        sourceTitle: String,
        customAiSummary: String,
        technicalDetails: String = "",
        technicalOutput: String = "",
        openFullScreen: Boolean = true
    ) {
        val currentState = _imageCounterState.value.copy(
            markers = visibleMarkers,
            allAiMarkers = allAiMarkers,
            detectedGrid = detectedGrid
        )
        val finalLive = visibleMarkers.count { it.isLive }
        val finalDead = visibleMarkers.count { !it.isLive }
        val total = finalLive + finalDead
        val onLineCount = visibleMarkers.count { it.isOnGridLine }
        val overlapCount = visibleMarkers.count { it.isOverlapping }
        val viabilityPct = if (total > 0) (finalLive * 100.0 / total) else 0.0

        val squares = currentState.squaresCount.coerceAtLeast(0.01)
        val dilution = currentState.dilutionFactor.coerceAtLeast(1.0)
        val chamber = currentState.chamberFactor.coerceAtLeast(1.0)
        val avgLivePerSquare = finalLive.toDouble() / squares
        val viablePerMl = avgLivePerSquare * dilution * chamber
        val totalPerMl = (total.toDouble() / squares) * dilution * chamber
        val viableMillionsStr = NumberFormatter.formatWithCommas(viablePerMl / 1_000_000.0, 3)
        val totalMillionsStr = NumberFormatter.formatWithCommas(totalPerMl / 1_000_000.0, 3)

        val grid4x4Live = currentState.computeGrid4x4Counts(isLiveFilter = true)
        val grid4x4Dead = currentState.computeGrid4x4Counts(isLiveFilter = false)

        val formulaPersian = buildString {
            append("فرمول استاندارد غلظت سلولی در لام نئوبار:\n")
            append("غلظت سلول زنده (Cells/mL) = (تعداد سلول زنده ÷ تعداد خانه بزرگ) × ضریب رقت × ۱۰⁴\n")
            append("جایگذاری اعداد:\n")
            append("• میانگین سلول زنده در هر خانه بزرگ = $finalLive ÷ ${NumberFormatter.formatClean(squares, 2)} = ${NumberFormatter.formatWithCommas(avgLivePerSquare, 2)}\n")
            append("• غلظت سلول زنده = ${NumberFormatter.formatWithCommas(avgLivePerSquare, 2)} × ${NumberFormatter.formatClean(dilution, 2)} × ${NumberFormatter.formatWithCommas(chamber, 0)} = ${NumberFormatter.formatWithCommas(viablePerMl, 0)} سلول/mL ($viableMillionsStr میلیون/mL)\n")
            append("• غلظت کل سلول‌ها = ${NumberFormatter.formatWithCommas(totalPerMl, 0)} سلول/mL ($totalMillionsStr میلیون/mL)\n")
            append("• درصد زیست‌پذیری (Viability) = ($finalLive ÷ ${total.coerceAtLeast(1)}) × ۱۰۰ = ${NumberFormatter.formatWithCommas(viabilityPct, 1)}%")
        }

        val fullReport = buildString {
            if (customAiSummary.isNotBlank()) {
                append(customAiSummary.trim())
                append("\n")
            }
            append("• کل سلول‌های شمارش‌شده: $total سلول ($finalLive زنده سبز | $finalDead مرده قرمز)\n")
            append("• خطوط شبکه نئوبار تشخیص‌داده‌شده: ${detectedGrid.verticalLines.size} خط عمودی و ${detectedGrid.horizontalLines.size} خط افقی\n")
            append("• سلول‌های روی خطوط نئوبار: $onLineCount سلول | سلول‌های روی‌هم‌افتاده: $overlapCount سلول\n")
            append("• کانت نهایی زنده: $viableMillionsStr میلیون در میلی‌لیتر (${NumberFormatter.formatWithCommas(viablePerMl, 0)} سلول/mL) | زیست‌پذیری: ${NumberFormatter.formatWithCommas(viabilityPct, 1)}%")
        }

        _imageCounterState.update { state ->
            state.copy(
                markers = visibleMarkers,
                allAiMarkers = allAiMarkers,
                detectedGrid = detectedGrid,
                showFullScreenEditor = openFullScreen,
                imageSourceLabel = "$sourceTitle ($total سلول)",
                aiAnalysisState = AiCellAnalysisUiState.Success(
                    summary = fullReport,
                    liveCount = finalLive,
                    deadCount = finalDead,
                    onGridLineCount = onLineCount,
                    overlappingCount = overlapCount,
                    gridLinesDetected = detectedGrid.totalLinesCount,
                    viableConcentrationPerMl = viablePerMl,
                    totalConcentrationPerMl = totalPerMl,
                    viabilityPercent = viabilityPct,
                    grid4x4Live = grid4x4Live,
                    grid4x4Dead = grid4x4Dead,
                    concentrationFormulaPersian = formulaPersian,
                    executedPythonCode = technicalDetails,
                    codeExecutionOutput = technicalOutput
                )
            )
        }
    }

    fun autoDetectCellsFromImage(
        bitmap: Bitmap,
        sourceLabel: String = "آنالیز تصویر لام نئوبار",
        @Suppress("UNUSED_PARAMETER") openFullScreen: Boolean = true
    ) {
        analyzeSlideWithSelectedEngine(bitmap, sourceLabel)
    }

    fun analyzeBitmapForCells(bitmap: Bitmap, sourceLabel: String = "آنالیز تصویر لام نئوبار") {
        analyzeSlideWithSelectedEngine(bitmap, sourceLabel)
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
                squares = state.squares.map { it.copy(liveCountText = "", deadCountText = "") }
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
            val preferredVolUnit = if (preset.isBioreactor) VolumeUnit.L else VolumeUnit.ML
            state.copy(
                selectedPreset = preset,
                wellCountText = preset.defaultWellCount.toString(),
                volumePerWellUnit = preferredVolUnit,
                volumePerWellText = NumberFormatter.formatClean(preferredVolUnit.fromMl(preset.defaultMediaPerWellMl), 4),
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

    // =========================================================================
    // TAB 6: CELL GROWTH & CONFLUENCY PREDICTOR (پیش‌بینی رشد سلول و کانفلوئنسی)
    // =========================================================================

    fun selectGrowthVesselPreset(preset: CultureVesselPreset) {
        _growthState.update { state ->
            val defaultN0Raw = preset.defaultTargetCellsPerWellMillion * 1_000_000.0
            val defaultKRaw = preset.maxConfluentCellsMillion * 1_000_000.0
            state.copy(
                selectedVessel = preset,
                initialCellsText = NumberFormatter.formatCountForUnit(defaultN0Raw, state.initialCellsUnit),
                carryingCapacityText = NumberFormatter.formatCountForUnit(defaultKRaw, state.carryingCapacityUnit)
            )
        }
    }

    fun selectGrowthCellLinePreset(cellLine: CellLinePreset) {
        _growthState.update { state ->
            state.copy(
                selectedCellLine = cellLine,
                doublingTimeHoursText = NumberFormatter.formatClean(cellLine.defaultDoublingTimeHours, 2),
                lagPhaseHoursText = NumberFormatter.formatClean(cellLine.defaultLagPhaseHours, 1)
            )
        }
    }

    fun updateGrowthInitialCells(raw: String) {
        _growthState.update {
            it.copy(initialCellsText = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun toggleGrowthInitialCellsUnit() {
        _growthState.update { state ->
            val rawCells = state.initialCellsRaw
            val nextUnit = state.initialCellsUnit.toggle()
            state.copy(
                initialCellsUnit = nextUnit,
                initialCellsText = NumberFormatter.formatCountForUnit(rawCells, nextUnit)
            )
        }
    }

    fun updateGrowthCarryingCapacity(raw: String) {
        _growthState.update {
            it.copy(carryingCapacityText = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun toggleGrowthCarryingCapacityUnit() {
        _growthState.update { state ->
            val rawCells = state.carryingCapacityRaw
            val nextUnit = state.carryingCapacityUnit.toggle()
            state.copy(
                carryingCapacityUnit = nextUnit,
                carryingCapacityText = NumberFormatter.formatCountForUnit(rawCells, nextUnit)
            )
        }
    }

    fun updateGrowthDoublingTime(raw: String) {
        _growthState.update {
            it.copy(
                selectedCellLine = CellLinePreset.CUSTOM,
                doublingTimeHoursText = NumberFormatter.normalizeInputString(raw)
            )
        }
    }

    fun updateGrowthLagPhase(raw: String) {
        _growthState.update {
            it.copy(lagPhaseHoursText = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun updateGrowthIncubationHours(raw: String) {
        _growthState.update {
            it.copy(incubationHoursText = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun updateGrowthTargetConfluency(raw: String) {
        _growthState.update {
            it.copy(targetConfluencyPercentText = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun toggleGrowthResultCountUnit() {
        _growthState.update { state ->
            state.copy(resultCountUnit = state.resultCountUnit.toggle())
        }
    }

    fun updateEmpiricalN1(raw: String) {
        _growthState.update {
            it.copy(empiricalN1Text = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun updateEmpiricalN2(raw: String) {
        _growthState.update {
            it.copy(empiricalN2Text = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun updateEmpiricalElapsedHours(raw: String) {
        _growthState.update {
            it.copy(empiricalElapsedHoursText = NumberFormatter.normalizeInputString(raw))
        }
    }

    fun toggleEmpiricalCountUnit() {
        _growthState.update { state ->
            val n1Raw = state.empiricalN1Raw
            val n2Raw = state.empiricalN2Raw
            val nextUnit = state.empiricalCountUnit.toggle()
            state.copy(
                empiricalCountUnit = nextUnit,
                empiricalN1Text = NumberFormatter.formatCountForUnit(n1Raw, nextUnit),
                empiricalN2Text = NumberFormatter.formatCountForUnit(n2Raw, nextUnit)
            )
        }
    }

    fun applyEmpiricalDoublingTimeToPredictor() {
        _growthState.update { state ->
            val td = state.calculatedEmpiricalDoublingTimeHours
            if (td <= 0.0) return@update state
            state.copy(
                selectedCellLine = CellLinePreset.CUSTOM,
                doublingTimeHoursText = NumberFormatter.formatClean(td, 2)
            )
        }
    }

    fun importSeedingToGrowthPredictor() {
        val seeding = _seedingState.value
        val preset = seeding.selectedPreset
        val n0Raw = seeding.targetCellsPerWellRaw
        val kRaw = preset.maxConfluentCellsMillion * 1_000_000.0
        _growthState.update { state ->
            state.copy(
                selectedVessel = preset,
                initialCellsText = NumberFormatter.formatCountForUnit(n0Raw, state.initialCellsUnit),
                carryingCapacityText = NumberFormatter.formatCountForUnit(kRaw, state.carryingCapacityUnit)
            )
        }
        _selectedTab.value = AppTab.GROWTH_PREDICTOR
    }

    fun saveGrowthPredictionToHistory() {
        val g = _growthState.value
        val record = SavedCalculationRecord(
            id = nextRecordId++,
            title = "پیش‌بینی رشد سلول (${g.selectedCellLine.shortName} در ${g.selectedVessel.title})",
            summary = "پس از ${NumberFormatter.formatWithCommas(g.incubationHours, 1)} ساعت: ${NumberFormatter.formatCountForUnitWithCommas(g.predictedLogisticCellsRaw, g.resultCountUnit)} (${g.resultCountUnit.shortLabel}) | کانفلوئنسی: ${NumberFormatter.formatWithCommas(g.predictedConfluencyPercent, 1)}٪",
            details = "کشت اولیه: ${NumberFormatter.formatCountForUnitWithCommas(g.initialCellsRaw, g.initialCellsUnit)} (${g.initialCellsUnit.shortLabel}) | زمان دو برابر شدن: ${NumberFormatter.formatWithCommas(g.doublingTimeHours, 1)} ساعت | رسیدن به ${NumberFormatter.formatWithCommas(g.targetConfluencyPercent, 0)}٪ کانفلوئنسی: ${NumberFormatter.formatWithCommas(g.hoursToTargetConfluencyLogistic, 1)} ساعت",
            timestampLabel = "پیش‌بینی رشد ذخیره‌شده"
        )
        _savedRecords.update { listOf(record) + it }
    }
}
