package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.model.ActiveEditedField
import com.example.model.AppTab
import com.example.model.CellSampleItem
import com.example.model.CountUnitMultiplier
import com.example.model.CultureVesselPreset
import com.example.model.DilutionState
import com.example.model.MixerState
import com.example.model.NeubauerSquare
import com.example.model.NeubauerState
import com.example.model.NumberFormatter
import com.example.model.SavedCalculationRecord
import com.example.model.SeedingState
import com.example.model.SerumCalculationBasis
import com.example.model.StockUsageMode
import com.example.model.TargetAnchor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CellCalculatorViewModel : ViewModel() {

    private val _selectedTab = MutableStateFlow(AppTab.DILUTION_SERUM)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _dilutionState = MutableStateFlow(DilutionState())
    val dilutionState: StateFlow<DilutionState> = _dilutionState.asStateFlow()

    private val _mixerState = MutableStateFlow(MixerState())
    val mixerState: StateFlow<MixerState> = _mixerState.asStateFlow()

    private val _neubauerState = MutableStateFlow(NeubauerState())
    val neubauerState: StateFlow<NeubauerState> = _neubauerState.asStateFlow()

    private val _seedingState = MutableStateFlow(SeedingState())
    val seedingState: StateFlow<SeedingState> = _seedingState.asStateFlow()

    private val _savedRecords = MutableStateFlow(
        listOf(
            SavedCalculationRecord(
                id = 1L,
                title = "رقت سلول با ۳٪ سرم (نمونه پیش‌فرض)",
                summary = "V₁: 2 mL (کانت 2.5) ⟵ V₂: 10 mL (کانت تارگت 0.5)",
                details = "مدیا پایه: 7.76 mL (7760 µL) | سرم (۳٪): 0.24 mL (240 µL)",
                timestampLabel = "آماده در آزمایشگاه"
            )
        )
    )
    val savedRecords: StateFlow<List<SavedCalculationRecord>> = _savedRecords.asStateFlow()

    private var nextSampleId = 4
    private var nextRecordId = 2L

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    // =========================================================================
    // TAB 1: OMNI-DIRECTIONAL DILUTION, MEDIA & SERUM CALCULATOR
    // =========================================================================

    fun onCurrentVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val v1 = NumberFormatter.parseInput(clean)
            val c1 = state.currentCountVal
            val updated = state.copy(
                currentVolumeText = clean,
                lastEditedField = ActiveEditedField.CURRENT_VOLUME
            )
            recalculateFromStockChange(updated, v1, c1)
        }
    }

    fun onCurrentCountChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val c1 = NumberFormatter.parseInput(clean)
            val v1 = state.currentVolumeMl
            val updated = state.copy(
                currentCountText = clean,
                lastEditedField = ActiveEditedField.CURRENT_COUNT
            )
            recalculateFromStockChange(updated, v1, c1)
        }
    }

    fun onTargetCountChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val c2 = NumberFormatter.parseInput(clean)
            val c1 = state.currentCountVal
            val baseState = state.copy(
                targetCountText = clean,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT,
                lastEditedField = ActiveEditedField.TARGET_COUNT
            )
            if (c2 <= 0.0 || c1 <= 0.0) {
                baseState
            } else if (state.stockUsageMode == StockUsageMode.USE_ALL_CURRENT_VOLUME) {
                val v1 = state.currentVolumeMl
                val v2 = (c1 * v1) / c2
                updateVolumesFromV1AndV2(baseState, v1, v2)
            } else {
                // WITHDRAW_FROM_STOCK: V2 and C2 are fixed, solve V1 = (C2 * V2) / C1
                val v2 = state.finalVolumeMl
                val v1 = (c2 * v2) / c1
                updateVolumesFromV1AndV2(
                    baseState.copy(currentVolumeText = NumberFormatter.formatClean(v1)),
                    v1,
                    v2
                )
            }
        }
    }

    fun onFinalVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val v2 = NumberFormatter.parseInput(clean)
            val c1 = state.currentCountVal
            val baseState = state.copy(
                finalVolumeText = clean,
                targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                lastEditedField = ActiveEditedField.FINAL_VOLUME
            )
            if (v2 <= 0.0 || c1 <= 0.0) {
                baseState
            } else if (state.stockUsageMode == StockUsageMode.USE_ALL_CURRENT_VOLUME) {
                // Solve Target Count C2 = (C1 * V1) / V2
                val v1 = state.currentVolumeMl
                val c2 = (c1 * v1) / v2
                val withTarget = baseState.copy(
                    targetCountText = NumberFormatter.formatClean(c2, 4)
                )
                updateMediaAndSerumFromV1AndV2(withTarget, v1, v2)
            } else {
                // WITHDRAW_FROM_STOCK: Keep C2 fixed, solve V1 = (C2 * V2) / C1
                val c2 = state.targetCountVal
                val v1 = if (c1 > 0.0) (c2 * v2) / c1 else 0.0
                val withV1 = baseState.copy(
                    currentVolumeText = NumberFormatter.formatClean(v1)
                )
                updateMediaAndSerumFromV1AndV2(withV1, v1, v2)
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

    /**
     * When the user directly edits Media Volume (V_media), automatically compute
     * Serum Volume (according to Serum %), Total Added Volume, Final Volume (V2), and Target Count (C2)!
     */
    fun onMediaVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val vMedia = NumberFormatter.parseInput(clean)
            val v1 = state.currentVolumeMl
            val c1 = state.currentCountVal
            val sFraction = (state.serumPercent / 100.0).coerceIn(0.0, 0.9999)

            val vSerum: Double
            val vAdd: Double
            val v2: Double

            if (state.serumBasis == SerumCalculationBasis.ADDED_DILUENT) {
                // vMedia = vAdd * (1 - sFraction) => vAdd = vMedia / (1 - sFraction)
                vAdd = if (1.0 - sFraction > 0.0) vMedia / (1.0 - sFraction) else vMedia
                vSerum = (vAdd - vMedia).coerceAtLeast(0.0)
                v2 = v1 + vAdd
            } else {
                // FINAL_TOTAL_VOLUME basis:
                // vSerum = (v1 + vMedia + vSerum) * sFraction - v1 * initFraction
                // vSerum * (1 - sFraction) = (v1 + vMedia) * sFraction - v1 * initFraction
                val initFrac = (state.initialSerumPercent / 100.0).coerceIn(0.0, 1.0)
                val numerator = (v1 + vMedia) * sFraction - (v1 * initFrac)
                vSerum = if (1.0 - sFraction > 0.0) (numerator / (1.0 - sFraction)).coerceAtLeast(0.0) else 0.0
                vAdd = vMedia + vSerum
                v2 = v1 + vAdd
            }

            val c2 = if (v2 > 0.0) (c1 * v1) / v2 else 0.0

            state.copy(
                mediaVolumeText = clean,
                serumVolumeText = NumberFormatter.formatClean(vSerum, 3),
                totalAddedVolumeText = NumberFormatter.formatClean(vAdd, 3),
                finalVolumeText = NumberFormatter.formatClean(v2, 3),
                targetCountText = NumberFormatter.formatClean(c2, 4),
                targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                lastEditedField = ActiveEditedField.MEDIA_VOLUME
            )
        }
    }

    /**
     * When the user directly edits Serum Volume (V_serum), automatically compute
     * Media Volume (to keep Serum % constant, or if Serum % is 0, adjust Serum %),
     * Final Volume (V2), and Target Count (C2)!
     */
    fun onSerumVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val vSerum = NumberFormatter.parseInput(clean)
            val v1 = state.currentVolumeMl
            val c1 = state.currentCountVal
            val sFraction = (state.serumPercent / 100.0).coerceIn(0.0, 1.0)

            if (sFraction > 0.0) {
                val vAdd: Double
                val vMedia: Double
                if (state.serumBasis == SerumCalculationBasis.ADDED_DILUENT) {
                    vAdd = vSerum / sFraction
                    vMedia = (vAdd - vSerum).coerceAtLeast(0.0)
                } else {
                    val initFrac = (state.initialSerumPercent / 100.0).coerceIn(0.0, 1.0)
                    // vSerum + v1 * initFrac = v2 * sFraction => v2 = (vSerum + v1 * initFrac) / sFraction
                    val v2Target = (vSerum + v1 * initFrac) / sFraction
                    vAdd = (v2Target - v1).coerceAtLeast(vSerum)
                    vMedia = (vAdd - vSerum).coerceAtLeast(0.0)
                }
                val v2 = v1 + vAdd
                val c2 = if (v2 > 0.0) (c1 * v1) / v2 else 0.0
                state.copy(
                    serumVolumeText = clean,
                    mediaVolumeText = NumberFormatter.formatClean(vMedia, 3),
                    totalAddedVolumeText = NumberFormatter.formatClean(vAdd, 3),
                    finalVolumeText = NumberFormatter.formatClean(v2, 3),
                    targetCountText = NumberFormatter.formatClean(c2, 4),
                    targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                    lastEditedField = ActiveEditedField.SERUM_VOLUME
                )
            } else {
                // If serum % was 0, keep mediaVolume as is and update serum %!
                val vMedia = state.mediaVolumeMl
                val vAdd = vMedia + vSerum
                val v2 = v1 + vAdd
                val c2 = if (v2 > 0.0) (c1 * v1) / v2 else 0.0
                val newSerumPct = if (vAdd > 0.0) (vSerum / vAdd) * 100.0 else 0.0
                state.copy(
                    serumVolumeText = clean,
                    serumPercentText = NumberFormatter.formatClean(newSerumPct, 2),
                    totalAddedVolumeText = NumberFormatter.formatClean(vAdd, 3),
                    finalVolumeText = NumberFormatter.formatClean(v2, 3),
                    targetCountText = NumberFormatter.formatClean(c2, 4),
                    lastEditedField = ActiveEditedField.SERUM_VOLUME
                )
            }
        }
    }

    /**
     * When the user directly edits Total Added Volume (V_add = Media + Serum),
     * automatically compute V2 = V1 + V_add, Target Count C2, Media Volume, and Serum Volume!
     */
    fun onTotalAddedVolumeChanged(raw: String) {
        val clean = NumberFormatter.normalizeInputString(raw)
        _dilutionState.update { state ->
            val vAdd = NumberFormatter.parseInput(clean)
            val v1 = state.currentVolumeMl
            val c1 = state.currentCountVal
            val v2 = v1 + vAdd
            val c2 = if (v2 > 0.0) (c1 * v1) / v2 else 0.0
            val updated = state.copy(
                totalAddedVolumeText = clean,
                finalVolumeText = NumberFormatter.formatClean(v2, 3),
                targetCountText = NumberFormatter.formatClean(c2, 4),
                targetAnchor = TargetAnchor.HOLD_FINAL_VOLUME,
                lastEditedField = ActiveEditedField.TOTAL_ADDED_VOLUME
            )
            updateMediaAndSerumFromV1AndV2(updated, v1, v2, preserveTotalAddedText = true)
        }
    }

    fun setUnitMultiplier(multiplier: CountUnitMultiplier) {
        _dilutionState.update { it.copy(unitMultiplier = multiplier) }
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
            recalculateFromStockChange(updated, updated.currentVolumeMl, updated.currentCountVal)
        }
    }

    fun setTargetAnchor(anchor: TargetAnchor) {
        _dilutionState.update { it.copy(targetAnchor = anchor) }
    }

    private fun recalculateFromStockChange(
        state: DilutionState,
        v1: Double,
        c1: Double
    ): DilutionState {
        if (c1 <= 0.0) return state
        return if (state.stockUsageMode == StockUsageMode.WITHDRAW_FROM_STOCK &&
            state.lastEditedField == ActiveEditedField.CURRENT_COUNT
        ) {
            // In WITHDRAW_FROM_STOCK mode, when C1 changes, recompute V1 = (C2 * V2) / C1
            val c2 = state.targetCountVal
            val v2 = state.finalVolumeMl
            val newV1 = (c2 * v2) / c1
            updateVolumesFromV1AndV2(
                state.copy(currentVolumeText = NumberFormatter.formatClean(newV1, 3)),
                newV1,
                v2
            )
        } else if (state.targetAnchor == TargetAnchor.HOLD_TARGET_COUNT) {
            val c2 = state.targetCountVal
            if (c2 <= 0.0) return state
            val v2 = (c1 * v1) / c2
            updateVolumesFromV1AndV2(state, v1, v2)
        } else {
            val v2 = state.finalVolumeMl
            if (v2 <= 0.0) return state
            val c2 = (c1 * v1) / v2
            val withTarget = state.copy(targetCountText = NumberFormatter.formatClean(c2, 4))
            updateMediaAndSerumFromV1AndV2(withTarget, v1, v2)
        }
    }

    private fun updateVolumesFromV1AndV2(
        state: DilutionState,
        v1: Double,
        v2: Double
    ): DilutionState {
        val withFinal = state.copy(finalVolumeText = NumberFormatter.formatClean(v2, 3))
        return updateMediaAndSerumFromV1AndV2(withFinal, v1, v2)
    }

    private fun updateMediaAndSerumFromV1AndV2(
        state: DilutionState,
        v1: Double,
        v2: Double,
        overrideSerumPercent: Double? = null,
        preserveTotalAddedText: Boolean = false
    ): DilutionState {
        val sPercent = overrideSerumPercent ?: state.serumPercent
        val vAdd = (v2 - v1).coerceAtLeast(0.0)

        val vSerum: Double
        val vMedia: Double

        if (state.serumBasis == SerumCalculationBasis.ADDED_DILUENT) {
            vSerum = vAdd * (sPercent / 100.0)
            vMedia = (vAdd - vSerum).coerceAtLeast(0.0)
        } else {
            val totalNeeded = v2 * (sPercent / 100.0)
            val alreadyInStock = v1 * (state.initialSerumPercent / 100.0)
            vSerum = (totalNeeded - alreadyInStock).coerceIn(0.0, vAdd)
            vMedia = (vAdd - vSerum).coerceAtLeast(0.0)
        }

        return state.copy(
            totalAddedVolumeText = if (preserveTotalAddedText) {
                state.totalAddedVolumeText
            } else {
                NumberFormatter.formatClean(vAdd, 3)
            },
            mediaVolumeText = NumberFormatter.formatClean(vMedia, 3),
            serumVolumeText = NumberFormatter.formatClean(vSerum, 3)
        )
    }

    fun saveCurrentDilutionToHistory() {
        val s = _dilutionState.value
        val record = SavedCalculationRecord(
            id = nextRecordId++,
            title = "رقت سلولی با ${NumberFormatter.formatClean(s.serumPercent, 1)}٪ سرم",
            summary = "V₁: ${NumberFormatter.formatClean(s.currentVolumeMl)} mL (کانت ${NumberFormatter.formatClean(s.currentCountVal)}) ⟵ V₂: ${NumberFormatter.formatClean(s.finalVolumeMl)} mL (کانت تارگت ${NumberFormatter.formatClean(s.targetCountVal)})",
            details = "مدیا: ${NumberFormatter.formatVolumeWithMicroliter(s.mediaVolumeMl)} | سرم: ${NumberFormatter.formatVolumeWithMicroliter(s.serumVolumeMl)}",
            timestampLabel = "ذخیره‌شده توسط کاربر"
        )
        _savedRecords.update { listOf(record) + it }
    }

    // =========================================================================
    // TAB 2: MULTI-SAMPLE CELL MIXER
    // =========================================================================

    fun addMixerSample() {
        val id = nextSampleId++
        _mixerState.update { state ->
            val newIndex = state.samples.size + 1
            state.copy(
                samples = state.samples + CellSampleItem(
                    id = id,
                    label = "نمونه سلولی $newIndex",
                    volumeText = "1",
                    countText = "1.0"
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

    fun setMixerUnitMultiplier(multiplier: CountUnitMultiplier) {
        _mixerState.update { it.copy(unitMultiplier = multiplier) }
    }

    /**
     * Transfers the resulting mixed volume and mixed cell count directly to Tab 1
     * so the user can immediately calculate media & serum for the mixed suspension!
     */
    fun transferMixToDilutionCalculator() {
        val mix = _mixerState.value
        val vMix = mix.totalFinalMixVolumeMl
        val cMix = mix.mixedCountVal
        if (vMix <= 0.0 || cMix <= 0.0) return

        _dilutionState.update { state ->
            val v1Str = NumberFormatter.formatClean(vMix, 3)
            val c1Str = NumberFormatter.formatClean(cMix, 4)
            val defaultTarget = if (state.targetCountVal in 0.0001..cMix) {
                state.targetCountVal
            } else {
                (cMix / 2.0)
            }
            val updated = state.copy(
                currentVolumeText = v1Str,
                currentCountText = c1Str,
                targetCountText = NumberFormatter.formatClean(defaultTarget, 4),
                unitMultiplier = mix.unitMultiplier,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT
            )
            val v2 = (cMix * vMix) / defaultTarget
            updateVolumesFromV1AndV2(updated, vMix, v2)
        }
        _selectedTab.value = AppTab.DILUTION_SERUM
    }

    fun saveCurrentMixToHistory() {
        val mix = _mixerState.value
        val record = SavedCalculationRecord(
            id = nextRecordId++,
            title = "میکس ${mix.samples.size} نمونه سلولی",
            summary = "حجم کل میکس: ${NumberFormatter.formatClean(mix.totalFinalMixVolumeMl, 3)} mL | کانت نهایی میکس: ${NumberFormatter.formatClean(mix.mixedCountVal, 3)} (${mix.unitMultiplier.shortLabel})",
            details = mix.samples.joinToString(" + ") {
                "${it.label}: ${NumberFormatter.formatClean(it.volumeMl)}mL×${NumberFormatter.formatClean(it.countVal)}"
            },
            timestampLabel = "میکس ذخیره‌شده"
        )
        _savedRecords.update { listOf(record) + it }
    }

    // =========================================================================
    // TAB 3: NEUBAUER HEMOCYTOMETER & VIABILITY COUNTER
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
        _neubauerState.update { it.copy(sampleVolumeMlText = clean) }
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
        val v1 = n.sampleVolumeMl.coerceAtLeast(1.0)
        val c1Million = n.viableCountInMillions
        if (c1Million <= 0.0) return

        _dilutionState.update { state ->
            val target = (c1Million / 2.0).coerceAtLeast(0.1)
            val updated = state.copy(
                currentVolumeText = NumberFormatter.formatClean(v1, 2),
                currentCountText = NumberFormatter.formatClean(c1Million, 3),
                targetCountText = NumberFormatter.formatClean(target, 3),
                unitMultiplier = CountUnitMultiplier.MILLION,
                targetAnchor = TargetAnchor.HOLD_TARGET_COUNT
            )
            val v2 = (c1Million * v1) / target
            updateVolumesFromV1AndV2(updated, v1, v2)
        }
        _selectedTab.value = AppTab.DILUTION_SERUM
    }

    fun addNeubauerResultToMixer() {
        val n = _neubauerState.value
        val v = n.sampleVolumeMl.coerceAtLeast(1.0)
        val cMillion = n.viableCountInMillions
        if (cMillion <= 0.0) return

        val id = nextSampleId++
        _mixerState.update { state ->
            state.copy(
                unitMultiplier = CountUnitMultiplier.MILLION,
                samples = state.samples + CellSampleItem(
                    id = id,
                    label = "شمارش نئوبار (${NumberFormatter.formatClean(n.viabilityPercent, 1)}٪ زنده)",
                    volumeText = NumberFormatter.formatClean(v, 2),
                    countText = NumberFormatter.formatClean(cMillion, 3)
                )
            )
        }
        _selectedTab.value = AppTab.CELL_MIXER
    }

    // =========================================================================
    // TAB 4: PLATE / FLASK SEEDING & SAVED CALCULATIONS
    // =========================================================================

    fun selectVesselPreset(preset: CultureVesselPreset) {
        _seedingState.update { state ->
            state.copy(
                selectedPreset = preset,
                wellCountText = preset.defaultWellCount.toString(),
                volumePerWellMlText = NumberFormatter.formatClean(preset.defaultMediaPerWellMl, 2),
                targetCellsPerWellMillionText = NumberFormatter.formatClean(preset.defaultTargetCellsPerWellMillion, 3)
            )
        }
    }

    fun updateSeedingWellCount(raw: String) {
        _seedingState.update { it.copy(wellCountText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateSeedingVolumePerWell(raw: String) {
        _seedingState.update { it.copy(volumePerWellMlText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateSeedingTargetCellsPerWell(raw: String) {
        _seedingState.update { it.copy(targetCellsPerWellMillionText = NumberFormatter.normalizeInputString(raw)) }
    }

    fun updateSeedingStockCount(raw: String) {
        _seedingState.update { it.copy(stockCountMillionText = NumberFormatter.normalizeInputString(raw)) }
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
