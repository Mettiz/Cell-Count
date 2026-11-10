package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.ui.screens.CellMixerScreen
import com.example.ui.screens.DilutionSerumScreen
import com.example.ui.screens.ImageCellCounterScreen
import com.example.ui.screens.NeubauerCounterScreen
import com.example.ui.screens.PlayStoreDescriptionDialog
import com.example.ui.screens.SeedingAndHistoryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CellCalculatorViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val viewModel: CellCalculatorViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkMode) {
                CellCountLabApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellCountLabApp(
    viewModel: CellCalculatorViewModel = viewModel()
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val dilutionState by viewModel.dilutionState.collectAsStateWithLifecycle()
    val mixerState by viewModel.mixerState.collectAsStateWithLifecycle()
    val imageCounterState by viewModel.imageCounterState.collectAsStateWithLifecycle()
    val neubauerState by viewModel.neubauerState.collectAsStateWithLifecycle()
    val seedingState by viewModel.seedingState.collectAsStateWithLifecycle()
    val savedRecords by viewModel.savedRecords.collectAsStateWithLifecycle()
    var showPlayDescDialog by remember { mutableStateOf(false) }

    if (showPlayDescDialog) {
        PlayStoreDescriptionDialog(onDismiss = { showPlayDescDialog = false })
    }

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
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showPlayDescDialog = true }
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.author_name),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("author_badge_text")
                                    )
                                }
                            }
                            Text(
                                text = "By M. Hsn • " + if (isDarkMode) "حالت شب" else "حالت روز",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Info / Google Play Store Description & Signature Button
                    FilledTonalIconButton(
                        onClick = { showPlayDescDialog = true },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("btn_open_play_desc")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "معرفی اپلیکیشن و توضیحات گوگل‌پلی"
                        )
                    }

                    // Day / Night Mode Toggle Button
                    FilledTonalIconButton(
                        onClick = viewModel::toggleDarkMode,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_toggle_theme")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkMode) "تغییر به حالت روز" else "تغییر به حالت شب"
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
                tonalElevation = 4.dp
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
                    label = {
                        Text(
                            text = stringResource(R.string.tab_dilution),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("nav_tab_dilution")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.IMAGE_COUNTER,
                    onClick = { viewModel.selectTab(AppTab.IMAGE_COUNTER) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.IMAGE_COUNTER) {
                                Icons.Filled.CameraAlt
                            } else {
                                Icons.Outlined.CameraAlt
                            },
                            contentDescription = stringResource(R.string.tab_image_counter)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_image_counter),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("nav_tab_image_counter")
                )

                NavigationBarItem(
                    selected = selectedTab == AppTab.CELL_MIXER,
                    onClick = { viewModel.selectTab(AppTab.CELL_MIXER) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AppTab.CELL_MIXER) {
                                Icons.AutoMirrored.Filled.MergeType
                            } else {
                                Icons.AutoMirrored.Outlined.MergeType
                            },
                            contentDescription = stringResource(R.string.tab_mixer)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_mixer),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                    label = {
                        Text(
                            text = stringResource(R.string.tab_hemocytometer),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                    label = {
                        Text(
                            text = stringResource(R.string.tab_seeding),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                                onSetAllVolumeUnits = viewModel::setAllDilutionVolumeUnits,
                                onSetAllCountUnits = viewModel::setAllDilutionCountUnits,
                                onToggleV1Unit = viewModel::toggleV1Unit,
                                onToggleV2Unit = viewModel::toggleV2Unit,
                                onToggleMediaUnit = viewModel::toggleMediaUnit,
                                onToggleSerumUnit = viewModel::toggleSerumUnit,
                                onToggleAddedUnit = viewModel::toggleAddedUnit,
                                onToggleC1Unit = viewModel::toggleC1Unit,
                                onToggleC2Unit = viewModel::toggleC2Unit,
                                onSerumBasisSelected = viewModel::setSerumBasis,
                                onStockUsageModeSelected = viewModel::setStockUsageMode,
                                onTargetAnchorSelected = viewModel::setTargetAnchor,
                                onSaveCalculation = viewModel::saveCurrentDilutionToHistory
                            )
                        }

                        AppTab.IMAGE_COUNTER -> {
                            ImageCellCounterScreen(
                                state = imageCounterState,
                                onSelectTool = viewModel::selectImageMarkerTool,
                                onToggleGrid = viewModel::toggleImageGridOverlay,
                                onOpenFullScreenEditor = viewModel::openFullScreenImageEditor,
                                onCloseFullScreenEditor = viewModel::closeFullScreenImageEditor,
                                onCanvasTap = viewModel::onImageCanvasTapped,
                                onUndoMarker = viewModel::undoLastImageMarker,
                                onClearMarkers = viewModel::clearAllImageMarkers,
                                onResetSampleImage = viewModel::resetSampleImageMarkers,
                                onSensitivityChange = viewModel::updateImageSensitivity,
                                onMarkerScaleChange = viewModel::updateImageMarkerScale,
                                onAnalyzeBitmap = viewModel::autoDetectCellsFromImage,
                                onAnalyzeBitmapWithAi = viewModel::analyzeBitmapWithGeminiAi,
                                onDilutionChange = viewModel::updateImageDilutionFactor,
                                onSquaresCountChange = viewModel::updateImageSquaresCount,
                                onChamberFactorChange = viewModel::updateImageChamberFactor,
                                onSampleVolumeChange = viewModel::updateImageSampleVolume,
                                onToggleSampleVolumeUnit = viewModel::toggleImageSampleVolumeUnit,
                                onToggleResultCountUnit = viewModel::toggleImageResultCountUnit,
                                onSendToDilution = viewModel::transferImageCountToDilution,
                                onSendToMixer = viewModel::addImageCountToMixer
                            )
                        }

                        AppTab.CELL_MIXER -> {
                            CellMixerScreen(
                                state = mixerState,
                                onAddSample = viewModel::addMixerSample,
                                onRemoveSample = viewModel::removeMixerSample,
                                onSampleVolumeChanged = viewModel::updateMixerSampleVolume,
                                onToggleSampleVolumeUnit = viewModel::toggleMixerSampleVolumeUnit,
                                onToggleSampleCountUnit = viewModel::toggleMixerSampleCountUnit,
                                onSetAllMixerVolumeUnits = viewModel::setAllMixerVolumeUnits,
                                onSetAllMixerCountUnits = viewModel::setAllMixerCountUnits,
                                onSampleCountChanged = viewModel::updateMixerSampleCount,
                                onSampleLabelChanged = viewModel::updateMixerSampleLabel,
                                onExtraMediaChanged = viewModel::updateExtraMediaInMixer,
                                onToggleExtraMediaUnit = viewModel::toggleExtraMediaUnit,
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
                                onToggleSampleVolumeUnit = viewModel::toggleNeubauerSampleVolumeUnit,
                                onToggleResultCountUnit = viewModel::toggleNeubauerResultCountUnit,
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
                                onToggleVolumePerWellUnit = viewModel::toggleSeedingVolumePerWellUnit,
                                onTargetCellsPerWellChanged = viewModel::updateSeedingTargetCellsPerWell,
                                onToggleTargetCellsUnit = viewModel::toggleSeedingTargetCellsUnit,
                                onStockCountChanged = viewModel::updateSeedingStockCount,
                                onToggleStockCountUnit = viewModel::toggleSeedingStockCountUnit,
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
