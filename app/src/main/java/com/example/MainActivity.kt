package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.ui.screens.CellMixerScreen
import com.example.ui.screens.DilutionSerumScreen
import com.example.ui.screens.NeubauerCounterScreen
import com.example.ui.screens.SeedingAndHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CellCalculatorViewModel

import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.screens.WebAppPreviewDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CellCountLabApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellCountLabApp(
    viewModel: CellCalculatorViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val dilutionState by viewModel.dilutionState.collectAsStateWithLifecycle()
    val mixerState by viewModel.mixerState.collectAsStateWithLifecycle()
    val neubauerState by viewModel.neubauerState.collectAsStateWithLifecycle()
    val seedingState by viewModel.seedingState.collectAsStateWithLifecycle()
    val savedRecords by viewModel.savedRecords.collectAsStateWithLifecycle()
    var showWebAppDialog by remember { mutableStateOf(false) }

    if (showWebAppDialog) {
        WebAppPreviewDialog(onDismiss = { showWebAppDialog = false })
    }

    // Ensure system Back returns to primary Dilution & Serum tab when on secondary tabs
    if (selectedTab != AppTab.DILUTION_SERUM) {
        BackHandler {
            viewModel.selectTab(AppTab.DILUTION_SERUM)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Biotech,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(id = R.string.app_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showWebAppDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_open_webapp")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "نسخه وب‌اپ",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "وب‌اپ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.DILUTION_SERUM,
                    onClick = { viewModel.selectTab(AppTab.DILUTION_SERUM) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.DILUTION_SERUM) {
                                Icons.Filled.Science
                            } else {
                                Icons.Outlined.Science
                            },
                            contentDescription = stringResource(R.string.tab_dilution)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_dilution)) },
                    modifier = Modifier.testTag("nav_tab_dilution")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.CELL_MIXER,
                    onClick = { viewModel.selectTab(AppTab.CELL_MIXER) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.CELL_MIXER) {
                                Icons.Filled.MergeType
                            } else {
                                Icons.Outlined.MergeType
                            },
                            contentDescription = stringResource(R.string.tab_mixer)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_mixer)) },
                    modifier = Modifier.testTag("nav_tab_mixer")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.NEUBAUER_COUNTER,
                    onClick = { viewModel.selectTab(AppTab.NEUBAUER_COUNTER) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.NEUBAUER_COUNTER) {
                                Icons.Filled.GridOn
                            } else {
                                Icons.Outlined.GridOn
                            },
                            contentDescription = stringResource(R.string.tab_hemocytometer)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_hemocytometer)) },
                    modifier = Modifier.testTag("nav_tab_neubauer")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.SEEDING_AND_HISTORY,
                    onClick = { viewModel.selectTab(AppTab.SEEDING_AND_HISTORY) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.SEEDING_AND_HISTORY) {
                                Icons.Filled.ViewModule
                            } else {
                                Icons.Outlined.ViewModule
                            },
                            contentDescription = stringResource(R.string.tab_seeding)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_seeding)) },
                    modifier = Modifier.testTag("nav_tab_seeding")
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                ) {
                    when (selectedTab) {
                        AppTab.DILUTION_SERUM -> {
                            DilutionSerumScreen(
                                state = dilutionState,
                                onCurrentVolumeChanged = viewModel::onCurrentVolumeChanged,
                                onCurrentCountChanged = viewModel::onCurrentCountChanged,
                                onTargetCountChanged = viewModel::onTargetCountChanged,
                                onFinalVolumeChanged = viewModel::onFinalVolumeChanged,
                                onSerumPercentChanged = viewModel::onSerumPercentChanged,
                                onInitialSerumPercentChanged = viewModel::onInitialSerumPercentChanged,
                                onMediaVolumeChanged = viewModel::onMediaVolumeChanged,
                                onSerumVolumeChanged = viewModel::onSerumVolumeChanged,
                                onTotalAddedVolumeChanged = viewModel::onTotalAddedVolumeChanged,
                                onUnitMultiplierSelected = viewModel::setUnitMultiplier,
                                onSerumBasisSelected = viewModel::setSerumBasis,
                                onStockUsageModeSelected = viewModel::setStockUsageMode,
                                onTargetAnchorSelected = viewModel::setTargetAnchor,
                                onSaveCalculation = viewModel::saveCurrentDilutionToHistory
                            )
                        }

                        AppTab.CELL_MIXER -> {
                            CellMixerScreen(
                                state = mixerState,
                                onAddSample = viewModel::addMixerSample,
                                onRemoveSample = viewModel::removeMixerSample,
                                onSampleVolumeChanged = viewModel::updateMixerSampleVolume,
                                onSampleCountChanged = viewModel::updateMixerSampleCount,
                                onSampleLabelChanged = viewModel::updateMixerSampleLabel,
                                onExtraMediaChanged = viewModel::updateExtraMediaInMixer,
                                onUnitMultiplierSelected = viewModel::setMixerUnitMultiplier,
                                onTransferMixToDilution = viewModel::transferMixToDilutionCalculator,
                                onSaveMixToHistory = viewModel::saveCurrentMixToHistory
                            )
                        }

                        AppTab.NEUBAUER_COUNTER -> {
                            NeubauerCounterScreen(
                                state = neubauerState,
                                onLiveCountChanged = viewModel::updateNeubauerLive,
                                onDeadCountChanged = viewModel::updateNeubauerDead,
                                onIncrementLive = viewModel::incrementNeubauerLive,
                                onIncrementDead = viewModel::incrementNeubauerDead,
                                onDilutionFactorChanged = viewModel::updateTrypanDilutionFactor,
                                onSampleVolumeChanged = viewModel::updateNeubauerSampleVolume,
                                onResetCounts = viewModel::resetNeubauerCounts,
                                onSendToDilution = viewModel::transferNeubauerToDilution,
                                onSendToMixer = viewModel::addNeubauerResultToMixer
                            )
                        }

                        AppTab.SEEDING_AND_HISTORY -> {
                            SeedingAndHistoryScreen(
                                seedingState = seedingState,
                                savedRecords = savedRecords,
                                onSelectPreset = viewModel::selectVesselPreset,
                                onWellCountChanged = viewModel::updateSeedingWellCount,
                                onVolumePerWellChanged = viewModel::updateSeedingVolumePerWell,
                                onTargetCellsPerWellChanged = viewModel::updateSeedingTargetCellsPerWell,
                                onStockCountChanged = viewModel::updateSeedingStockCount,
                                onSerumPercentChanged = viewModel::updateSeedingSerumPercent,
                                onOverfillPercentChanged = viewModel::updateSeedingOverfillPercent,
                                onDeleteRecord = viewModel::deleteSavedRecord
                            )
                        }
                    }
                }
            }
        }
    }
}
