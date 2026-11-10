package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AiCellAnalysisUiState
import com.example.model.CellMarkerTool
import com.example.model.CellPointMarker
import com.example.model.CountUnitMultiplier
import com.example.model.DetectedNeubauerGrid
import com.example.model.ImageCounterState
import com.example.model.NumberFormatter
import com.example.ui.components.LabNumericField
import com.example.ui.components.adaptiveMonospaceFontSize
import com.example.ui.theme.BioIndigoPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.DeadCellRed
import com.example.ui.theme.SerumAmber
import com.example.ui.theme.ViableGreen
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageCellCounterScreen(
    state: ImageCounterState,
    onSelectTool: (CellMarkerTool) -> Unit,
    onToggleGrid: () -> Unit,
    onOpenFullScreenEditor: () -> Unit = {},
    onCloseFullScreenEditor: () -> Unit = {},
    onCanvasTap: (Float, Float) -> Unit,
    onUndoMarker: () -> Unit,
    onClearMarkers: () -> Unit,
    onResetSampleImage: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onMarkerScaleChange: (Float) -> Unit,
    onAnalyzeBitmap: (Bitmap, String) -> Unit,
    onAnalyzeBitmapWithAi: (Bitmap, String) -> Unit,
    onDilutionChange: (String) -> Unit,
    onSquaresCountChange: (String) -> Unit,
    onChamberFactorChange: (String) -> Unit,
    onSampleVolumeChange: (String) -> Unit,
    onToggleSampleVolumeUnit: () -> Unit,
    onToggleResultCountUnit: () -> Unit,
    onSendToDilution: () -> Unit,
    onSendToMixer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        selectedBitmap = decoded
                        onAnalyzeBitmapWithAi(decoded, "عکس گالری")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            onAnalyzeBitmapWithAi(bitmap, "عکس دوربین میکروسکوپ")
        }
    }

    // Full-Screen Interactive Cell Inspection & Editing Workspace (opens automatically after image analysis)
    if (state.showFullScreenEditor) {
        FullScreenSlideEditorDialog(
            bitmap = selectedBitmap,
            state = state,
            onSelectTool = onSelectTool,
            onToggleGrid = onToggleGrid,
            onCanvasTap = onCanvasTap,
            onUndoMarker = onUndoMarker,
            onClearMarkers = onClearMarkers,
            onMarkerScaleChange = onMarkerScaleChange,
            onSensitivityChange = onSensitivityChange,
            onToggleResultCountUnit = onToggleResultCountUnit,
            onDismiss = onCloseFullScreenEditor
        )
    }

    val viabilityColor = when {
        state.viabilityPercent >= 85.0 -> ViableGreen
        state.viabilityPercent >= 65.0 -> SerumAmber
        else -> DeadCellRed
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("image_counter_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Cell Count & Viability Result Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_counter_summary_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "نتیجه شمارش سلول از عکس",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = state.imageSourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Unit Toggle Pill (میلیون ⇄ عدد کامل)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleResultCountUnit() }
                                .testTag("image_result_unit_toggle"),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = state.resultCountUnit.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Filled.SwapHoriz,
                                    contentDescription = "تبدیل واحد کانت",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Main Viable Concentration Display (Unit in Header, Full-Width Number below)
                    val formattedViableCount = NumberFormatter.formatCountForUnitWithCommas(
                        state.viableCellsPerMl,
                        state.resultCountUnit
                    )
                    val heroNumFontSize = adaptiveMonospaceFontSize(
                        formattedViableCount,
                        baseSp = 22f,
                        minSp = 15f
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggleResultCountUnit() },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "غلظت سلول زنده (Viable Count)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = if (state.resultCountUnit == CountUnitMultiplier.MILLION) {
                                            "میلیون (×10⁶/mL) ⇄"
                                        } else {
                                            "عدد کامل (سلول/mL) ⇄"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                                        text = formattedViableCount,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = heroNumFontSize
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Left,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 6 Mini Stat Boxes: Live, Dead, Viability %, Total Cells, On Grid Lines, Overlapping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountBadgeTile(
                            title = "سلول زنده",
                            valueText = NumberFormatter.formatWithCommas(state.liveCount.toDouble(), 0),
                            unitText = "سلول",
                            accentColor = ViableGreen,
                            modifier = Modifier.weight(1f)
                        )
                        CountBadgeTile(
                            title = "سلول مرده",
                            valueText = NumberFormatter.formatWithCommas(state.deadCount.toDouble(), 0),
                            unitText = "سلول",
                            accentColor = DeadCellRed,
                            modifier = Modifier.weight(1f)
                        )
                        CountBadgeTile(
                            title = "زنده‌مانی",
                            valueText = NumberFormatter.formatWithCommas(state.viabilityPercent, 1),
                            unitText = "%",
                            accentColor = viabilityColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CountBadgeTile(
                            title = "کل سلول‌ها",
                            valueText = NumberFormatter.formatWithCommas(state.totalCount.toDouble(), 0),
                            unitText = "سلول",
                            accentColor = BioIndigoPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        CountBadgeTile(
                            title = "روی خطوط لام",
                            valueText = NumberFormatter.formatWithCommas(state.onGridLineCount.toDouble(), 0),
                            unitText = "سلول",
                            accentColor = SerumAmber,
                            modifier = Modifier.weight(1f)
                        )
                        CountBadgeTile(
                            title = "روی‌هم / خوشه‌ای",
                            valueText = NumberFormatter.formatWithCommas(state.overlappingCount.toDouble(), 0),
                            unitText = "سلول",
                            accentColor = CellCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (state.viabilityPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = viabilityColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Action Buttons: Transfer to Dilution & Serum or Mixer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSendToDilution,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_image_count_to_dilution"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "انتقال به رقت و سرم",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onSendToMixer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_image_count_to_mixer"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "+ افزودن به میکس",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Interactive Microscope / Slide Image Card + Gemini AI Vision
        item {
            ImageSectionCard(
                title = "آنالیز کامل تصویر لام نئوبار با هوش مصنوعی و پردازش مستقیم",
                subtitle = "تشخیص مستقیم کل سلول‌ها، زنده، مرده، خطوط لام نئوبار، سلول‌های روی خط، سلول‌های روی‌هم‌افتاده و محاسبه کانت نهایی",
                icon = Icons.Filled.AutoAwesome
            ) {
                // Image Source Buttons (Gallery & Camera only — Sample removed per user request)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_microscope_image_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "انتخاب عکس از گالری",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("انتخاب عکس از گالری", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { cameraPreviewLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("capture_microscope_camera_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "ثبت عکس با دوربین",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("عکس با دوربین", fontWeight = FontWeight.Bold)
                    }
                }

                // Dedicated Gemini AI Analysis Button + Full-Screen Editor Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val bmp = selectedBitmap
                            if (bmp != null) {
                                onAnalyzeBitmapWithAi(bmp, "آنالیز کامل تصویر لام نئوبار")
                            } else {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        },
                        enabled = state.aiAnalysisState !is AiCellAnalysisUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("analyze_with_gemini_ai_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (state.aiAnalysisState is AiCellAnalysisUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "در حال آنالیز کامل عکس...",
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedBitmap != null) "آنالیز مجدد و ویرایش تمام‌صفحه" else "انتخاب و آنالیز هوشمند عکس",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenFullScreenEditor,
                        modifier = Modifier.testTag("open_fullscreen_editor_btn"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "باز کردن ویرایشگر تمام‌صفحه",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تمام‌صفحه", fontWeight = FontWeight.Bold)
                    }
                }

                // Gemini AI Status / Result Banner
                when (val aiState = state.aiAnalysisState) {
                    is AiCellAnalysisUiState.Idle -> Unit
                    is AiCellAnalysisUiState.Loading -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_ai_loading_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "در حال آنالیز مستقیم عکس اصلی: تشخیص کل سلول‌ها، زنده و مرده، خطوط نئوبار، سلول‌های روی خط و روی‌هم‌افتاده و محاسبه کانت...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    is AiCellAnalysisUiState.Success -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_ai_success_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = ViableGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ViableGreen.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = ViableGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "گزارش آنالیز کامل عکس و محاسبه کانت:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ViableGreen
                                    )
                                }
                                Text(
                                    text = aiState.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    is AiCellAnalysisUiState.Error -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_ai_error_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = DeadCellRed.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, DeadCellRed.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = aiState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = DeadCellRed,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Marker Tool Selector Bar (Live / Dead / Erase)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.activeTool == CellMarkerTool.ADD_LIVE,
                        onClick = { onSelectTool(CellMarkerTool.ADD_LIVE) },
                        label = {
                            Text(
                                text = "سلول زنده (${state.liveCount})",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AddCircle,
                                contentDescription = null,
                                tint = ViableGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ViableGreen.copy(alpha = 0.18f),
                            selectedLabelColor = ViableGreen
                        ),
                        modifier = Modifier.testTag("tool_add_live_chip")
                    )

                    FilterChip(
                        selected = state.activeTool == CellMarkerTool.ADD_DEAD,
                        onClick = { onSelectTool(CellMarkerTool.ADD_DEAD) },
                        label = {
                            Text(
                                text = "سلول مرده (${state.deadCount})",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = DeadCellRed,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeadCellRed.copy(alpha = 0.18f),
                            selectedLabelColor = DeadCellRed
                        ),
                        modifier = Modifier.testTag("tool_add_dead_chip")
                    )

                    FilterChip(
                        selected = state.activeTool == CellMarkerTool.ERASE,
                        onClick = { onSelectTool(CellMarkerTool.ERASE) },
                        label = { Text("حذف نشانه", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.RemoveCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("tool_erase_chip")
                    )
                }

                // Interactive Microscope Viewport Canvas with compact cell-sized markers & detected grid lines
                MicroscopeSlideCanvas(
                    bitmap = selectedBitmap,
                    markers = state.markers,
                    detectedGrid = state.detectedGrid,
                    markerScale = state.markerScale,
                    showGridOverlay = state.showGridOverlay,
                    onTapNormalized = onCanvasTap,
                    onOpenFullScreen = onOpenFullScreenEditor
                )

                // Canvas Utility Row: Grid toggle, Undo, Clear All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = state.showGridOverlay,
                        onClick = onToggleGrid,
                        label = {
                            val totalLines = state.detectedGrid.verticalLines.size + state.detectedGrid.horizontalLines.size
                            Text(
                                text = if (totalLines > 0) "خطوط نئوبار ($totalLines خط)" else "خطوط لام نئوبار",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_neubauer_grid_chip")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onUndoMarker,
                            enabled = state.markers.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("undo_marker_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "واگرد",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("واگرد", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onClearMarkers,
                            enabled = state.markers.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("clear_markers_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "پاک کردن همه",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاک‌سازی", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Sliders for Marker Size & Auto-Detection Sensitivity (always available)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Marker Size Slider (اندازه نشانه‌های زنده و مرده روی سلول)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اندازه نشانه زنده و مرده روی سلول",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(state.markerScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.markerScale,
                        onValueChange = onMarkerScaleChange,
                        valueRange = 0.20f..1.0f,
                        modifier = Modifier.testTag("marker_scale_slider")
                    )

                    // 2. Local Contrast Sensitivity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حساسیت تشخیص کنتراست محلی",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(state.detectionSensitivity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.detectionSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 0.15f..0.98f,
                        modifier = Modifier.testTag("detection_sensitivity_slider")
                    )
                }
            }
        }

        // 3. Dilution Factor & Chamber Parameters Card
        item {
            ImageSectionCard(
                title = "ضریب رقت و تنظیمات شمارش لام",
                subtitle = "ضریب رقت (مثلاً با تریپان بلو) و تعداد خانه‌های شمارش‌شده در عکس را وارد کنید",
                icon = Icons.Filled.GridOn
            ) {
                // Quick Dilution Chips (1x, 2x, 4x, 5x, 10x)
                Text(
                    text = "انتخاب سریع ضریب رقت (Dilution Factor):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "1" to "بدون رقت (1x)",
                        "2" to "تریپان بلو ۱:۱ (2x)",
                        "4" to "رقت ۱:۴ (4x)",
                        "5" to "رقت ۱:۵ (5x)",
                        "10" to "رقت ۱:۱۰ (10x)"
                    ).forEach { (factorVal, label) ->
                        FilterChip(
                            selected = state.dilutionFactorText.trim() == factorVal,
                            onClick = { onDilutionChange(factorVal) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("image_dilution_chip_$factorVal")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.dilutionFactorText,
                        onValueChange = onDilutionChange,
                        label = "ضریب رقت نمونه",
                        unitText = "برابر (×)",
                        accentColor = SerumAmber,
                        testTag = "image_dilution_input",
                        modifier = Modifier.weight(1f)
                    )
                    LabNumericField(
                        value = state.squaresCountText,
                        onValueChange = onSquaresCountChange,
                        label = "تعداد خانه در عکس",
                        unitText = "خانه بزرگ",
                        accentColor = BioIndigoPrimary,
                        testTag = "image_squares_input",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabNumericField(
                        value = state.sampleVolumeText,
                        onValueChange = onSampleVolumeChange,
                        label = "حجم سوسپانسیون",
                        unitText = state.sampleVolumeUnit.symbol,
                        accentColor = CellCyan,
                        testTag = "image_sample_volume_input",
                        modifier = Modifier.weight(1f),
                        onUnitClick = onToggleSampleVolumeUnit,
                        helperText = NumberFormatter.formatOppositeUnitHelper(
                            state.sampleVolumeMl,
                            state.sampleVolumeUnit
                        )
                    )
                    LabNumericField(
                        value = state.chamberFactorText,
                        onValueChange = onChamberFactorChange,
                        label = "ضریب حجمی لام",
                        unitText = "×10⁴/mL",
                        accentColor = ViableGreen,
                        testTag = "image_chamber_factor_input",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Formula & Total Cells Explanation Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = NumberFormatter.formatWithCommas(state.averageLivePerSquare, 2),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "میانگین سلول زنده در هر خانه",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${NumberFormatter.formatWithCommas(state.totalViableCellsInSample / 1_000_000.0, 3)} ×10⁶ (${NumberFormatter.formatWithCommas(state.totalViableCellsInSample, 0)})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ViableGreen
                                )
                                Text(
                                    text = "کل سلول زنده در ظرف",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "فرمول: (تعداد سلول زنده ÷ تعداد خانه) × ضریب رقت × ضریب لام (${NumberFormatter.formatWithCommas(state.chamberFactor, 0)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun CountBadgeTile(
    title: String,
    valueText: String,
    unitText: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val tileFontSize = adaptiveMonospaceFontSize(valueText, baseSp = 15f, minSp = 11f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1
                )
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
                        text = valueText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = tileFontSize
                        ),
                        color = accentColor,
                        textAlign = TextAlign.Left,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MicroscopeSlideCanvas(
    bitmap: Bitmap?,
    markers: List<CellPointMarker>,
    detectedGrid: DetectedNeubauerGrid,
    markerScale: Float,
    showGridOverlay: Boolean,
    onTapNormalized: (Float, Float) -> Unit,
    onOpenFullScreen: (() -> Unit)? = null
) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1B2A))
            .testTag("microscope_slide_canvas")
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    if (size.width > 0 && size.height > 0) {
                        val normX = (tapOffset.x / size.width.toFloat()).coerceIn(0.005f, 0.995f)
                        val normY = (tapOffset.y / size.height.toFloat()).coerceIn(0.005f, 0.995f)
                        onTapNormalized(normX, normY)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (imageBitmap != null) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.toInt(), h.toInt())
                )
            } else {
                // Simulated Brightfield / Phase-Contrast Hemocytometer Field
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E3A5F),
                            Color(0xFF13263E),
                            Color(0xFF0B1727)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.65f
                    ),
                    size = Size(w, h)
                )

                // Microscope circular field vignette ring
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.22f),
                    radius = w * 0.46f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = 2.5f)
                )
            }

            // Detected Neubauer Grid Lines Overlay (or fallback 4x4 grid when no lines detected)
            if (showGridOverlay) {
                val hasDetectedLines = detectedGrid.verticalLines.isNotEmpty() || detectedGrid.horizontalLines.isNotEmpty()
                if (hasDetectedLines) {
                    val detectedLineColor = Color(0xFF38BDF8).copy(alpha = 0.68f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    detectedGrid.verticalLines.forEach { normX ->
                        val x = (normX * w).coerceIn(0f, w)
                        drawLine(
                            color = detectedLineColor,
                            start = Offset(x, h * 0.03f),
                            end = Offset(x, h * 0.97f),
                            strokeWidth = 2.2f,
                            pathEffect = dashEffect
                        )
                    }
                    detectedGrid.horizontalLines.forEach { normY ->
                        val y = (normY * h).coerceIn(0f, h)
                        drawLine(
                            color = detectedLineColor,
                            start = Offset(w * 0.03f, y),
                            end = Offset(w * 0.97f, y),
                            strokeWidth = 2.2f,
                            pathEffect = dashEffect
                        )
                    }
                } else {
                    val padX = w * 0.08f
                    val padY = h * 0.08f
                    val gridW = w - 2 * padX
                    val gridH = h - 2 * padY
                    val gridColor = Color(0xFFE0F2FE).copy(alpha = 0.40f)
                    val majorStroke = 2f
                    val minorStroke = 1f

                    drawRect(
                        color = gridColor,
                        topLeft = Offset(padX, padY),
                        size = Size(gridW, gridH),
                        style = Stroke(width = majorStroke)
                    )
                    for (i in 1..3) {
                        val x = padX + (gridW / 4f) * i
                        val y = padY + (gridH / 4f) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(x, padY),
                            end = Offset(x, padY + gridH),
                            strokeWidth = if (i == 2) majorStroke else minorStroke,
                            pathEffect = if (i == 2) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(padX, y),
                            end = Offset(padX + gridW, y),
                            strokeWidth = if (i == 2) majorStroke else minorStroke,
                            pathEffect = if (i == 2) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    }
                }
            }

            // Compact, Cell-Sized Live and Dead Markers + On-Line & Overlapping Rings
            val markerRadius = (w * (0.0052f + markerScale * 0.0115f)).coerceIn(3.5f, 13.5f)
            val ringStroke = (markerRadius * 0.25f).coerceIn(1.3f, 2.3f)
            val centerDotRadius = (markerRadius * 0.22f).coerceIn(1.1f, 2.1f)

            markers.forEach { marker ->
                val cx = marker.normalizedX * w
                val cy = marker.normalizedY * h
                val center = Offset(cx, cy)

                // Outer Amber Halo for cells sitting directly ON a Neubauer grid line
                if (marker.isOnGridLine) {
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = markerRadius * 1.42f,
                        center = center,
                        style = Stroke(
                            width = (ringStroke * 0.9f).coerceAtLeast(1.3f),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                        )
                    )
                }

                // Outer Cyan Halo for overlapping / doublet cells resolved into individual counts
                if (marker.isOverlapping) {
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = markerRadius * 1.32f,
                        center = center,
                        style = Stroke(width = (ringStroke * 0.85f).coerceAtLeast(1.2f))
                    )
                }

                if (marker.isLive) {
                    if (imageBitmap == null) {
                        drawCircle(
                            color = Color(0xFFE0F2FE).copy(alpha = 0.85f),
                            radius = markerRadius * 0.72f,
                            center = center
                        )
                    }
                    // Crisp compact green ring for Live cell
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                        radius = markerRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = markerRadius,
                        center = center,
                        style = Stroke(width = ringStroke)
                    )
                    drawCircle(
                        color = Color(0xFF34D399),
                        radius = centerDotRadius,
                        center = center
                    )
                } else {
                    if (imageBitmap == null) {
                        drawCircle(
                            color = Color(0xFF1E3A8A).copy(alpha = 0.9f),
                            radius = markerRadius * 0.75f,
                            center = center
                        )
                    }
                    // Crisp compact red ring + cross for Dead cell
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.18f),
                        radius = markerRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = markerRadius,
                        center = center,
                        style = Stroke(width = ringStroke)
                    )
                    val d = markerRadius * 0.45f
                    drawLine(
                        color = Color(0xFFF87171),
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                        strokeWidth = ringStroke
                    )
                    drawLine(
                        color = Color(0xFFF87171),
                        start = Offset(cx + d, cy - d),
                        end = Offset(cx - d, cy + d),
                        strokeWidth = ringStroke
                    )
                }
            }
        }

        // Top Overlay Legend Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.68f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "زنده: ${markers.count { it.isLive }}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Text(
                        text = "مرده: ${markers.count { !it.isLive }}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Text(
                        text = "روی خط: ${markers.count { it.isOnGridLine }}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Text(
                        text = "روی‌هم: ${markers.count { it.isOverlapping }}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top-End Full-Screen Expand Button on Canvas
        if (onOpenFullScreen != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenFullScreen() },
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = "بزرگ‌نمایی و ویرایش تمام‌صفحه",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ویرایش تمام‌صفحه",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Full-Screen Interactive Cell Inspection & Editing Workspace:
 * Opens automatically after analyzing a microscope slide photo so the user can inspect all detected
 * live cells, dead cells, Neubauer grid lines, on-line cells, and overlapping cells with zoom/pan,
 * and tap directly on any cell to add, remove, or flip Live ⇄ Dead.
 */
@Composable
private fun FullScreenSlideEditorDialog(
    bitmap: Bitmap?,
    state: ImageCounterState,
    onSelectTool: (CellMarkerTool) -> Unit,
    onToggleGrid: () -> Unit,
    onCanvasTap: (Float, Float) -> Unit,
    onUndoMarker: () -> Unit,
    onClearMarkers: () -> Unit,
    onMarkerScaleChange: (Float) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onToggleResultCountUnit: () -> Unit,
    onDismiss: () -> Unit
) {
    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isPanMode by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_slide_editor_dialog"),
            color = Color(0xFF070E17)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Header Bar: Live Stats + Confirm Button
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF111C2D)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بررسی و ویرایش تمام‌صفحه سلول‌های تشخیص‌داده‌شده",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "روی هر سلول بزنید تا اضافه، حذف یا بین زنده و مرده ویرایش شود",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ViableGreen
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_confirm_fullscreen_edits")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تأیید و ثبت",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Live Count Strip in Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FullScreenStatPill(
                                label = "زنده",
                                value = "${state.liveCount}",
                                color = ViableGreen
                            )
                            FullScreenStatPill(
                                label = "مرده",
                                value = "${state.deadCount}",
                                color = DeadCellRed
                            )
                            FullScreenStatPill(
                                label = "کل",
                                value = "${state.totalCount}",
                                color = Color(0xFF818CF8)
                            )
                            FullScreenStatPill(
                                label = "روی خط",
                                value = "${state.onGridLineCount}",
                                color = SerumAmber
                            )
                            FullScreenStatPill(
                                label = "روی‌هم",
                                value = "${state.overlappingCount}",
                                color = CellCyan
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, ViableGreen.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleResultCountUnit() }
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "${NumberFormatter.formatCountForUnitWithCommas(state.viableCellsPerMl, state.resultCountUnit)} ${state.resultCountUnit.shortLabel} ⇄",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Center Interactive Full-Screen Zoomable & Pannable Microscope Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableEditableSlideCanvas(
                        bitmap = bitmap,
                        markers = state.markers,
                        detectedGrid = state.detectedGrid,
                        markerScale = state.markerScale,
                        showGridOverlay = state.showGridOverlay,
                        zoom = zoom,
                        panOffset = panOffset,
                        isPanMode = isPanMode,
                        onZoomPanChange = { newZoom, newPan ->
                            zoom = newZoom
                            panOffset = newPan
                        },
                        onTapNormalized = onCanvasTap
                    )
                }

                // 3. Bottom Editing & Zoom Control Panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF111C2D)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tool Selector Row: Add Live / Add Dead / Erase / Pan Mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !isPanMode && state.activeTool == CellMarkerTool.ADD_LIVE,
                                onClick = {
                                    isPanMode = false
                                    onSelectTool(CellMarkerTool.ADD_LIVE)
                                },
                                label = {
                                    Text(
                                        text = "+ زنده (${state.liveCount})",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.AddCircle,
                                        contentDescription = null,
                                        tint = ViableGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ViableGreen.copy(alpha = 0.22f),
                                    selectedLabelColor = ViableGreen,
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.testTag("fs_tool_live_chip")
                            )

                            FilterChip(
                                selected = !isPanMode && state.activeTool == CellMarkerTool.ADD_DEAD,
                                onClick = {
                                    isPanMode = false
                                    onSelectTool(CellMarkerTool.ADD_DEAD)
                                },
                                label = {
                                    Text(
                                        text = "+ مرده (${state.deadCount})",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Cancel,
                                        contentDescription = null,
                                        tint = DeadCellRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeadCellRed.copy(alpha = 0.22f),
                                    selectedLabelColor = DeadCellRed,
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.testTag("fs_tool_dead_chip")
                            )

                            FilterChip(
                                selected = !isPanMode && state.activeTool == CellMarkerTool.ERASE,
                                onClick = {
                                    isPanMode = false
                                    onSelectTool(CellMarkerTool.ERASE)
                                },
                                label = {
                                    Text(
                                        text = "حذف نشانه",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.RemoveCircleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFF87171).copy(alpha = 0.22f),
                                    selectedLabelColor = Color(0xFFF87171),
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.testTag("fs_tool_erase_chip")
                            )

                            FilterChip(
                                selected = isPanMode,
                                onClick = { isPanMode = !isPanMode },
                                label = {
                                    Text(
                                        text = "جابجایی تصویر",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.OpenWith,
                                        contentDescription = null,
                                        tint = CellCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CellCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = CellCyan,
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.testTag("fs_tool_pan_chip")
                            )
                        }

                        // Row 2: Zoom Presets (1x, 1.75x, 2.5x, 3.5x) + Grid Toggle + Undo + Clear
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1f to "1x", 1.75f to "1.75x", 2.5f to "2.5x", 3.5f to "3.5x").forEach { (zVal, zLabel) ->
                                val selectedZoom = kotlin.math.abs(zoom - zVal) < 0.15f
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedZoom) CellCyan else Color(0xFF1E293B),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            zoom = zVal
                                            if (zVal == 1f) panOffset = Offset.Zero
                                        }
                                ) {
                                    Text(
                                        text = "بزرگ‌نمایی $zLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedZoom) Color(0xFF070E17) else Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.showGridOverlay) Color(0xFF0284C7) else Color(0xFF1E293B),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleGrid() }
                            ) {
                                Text(
                                    text = "خطوط نئوبار",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = state.markers.isNotEmpty()) { onUndoMarker() }
                            ) {
                                Text(
                                    text = "واگرد",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DeadCellRed.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = state.markers.isNotEmpty()) { onClearMarkers() }
                            ) {
                                Text(
                                    text = "پاک‌سازی همه",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF87171),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Row 3: Compact Marker Size & Sensitivity Sliders inside Full-Screen Editor
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "اندازه نشانه: ${(state.markerScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Slider(
                                    value = state.markerScale,
                                    onValueChange = onMarkerScaleChange,
                                    valueRange = 0.20f..1.0f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "حساسیت تشخیص: ${(state.detectionSensitivity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCBD5E1)
                                )
                                Slider(
                                    value = state.detectionSensitivity,
                                    onValueChange = onSensitivityChange,
                                    valueRange = 0.15f..0.98f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenStatPill(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$label: $value",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ZoomableEditableSlideCanvas(
    bitmap: Bitmap?,
    markers: List<CellPointMarker>,
    detectedGrid: DetectedNeubauerGrid,
    markerScale: Float,
    showGridOverlay: Boolean,
    zoom: Float,
    panOffset: Offset,
    isPanMode: Boolean,
    onZoomPanChange: (Float, Offset) -> Unit,
    onTapNormalized: (Float, Float) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1B2A))
            .border(BorderStroke(1.5.dp, Color(0xFF334155)), RoundedCornerShape(18.dp))
            .testTag("fullscreen_zoomable_canvas")
            .pointerInput(zoom, panOffset, isPanMode) {
                if (isPanMode) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 4.5f)
                        val maxPanX = (size.width * (newZoom - 1f)) * 0.5f
                        val maxPanY = (size.height * (newZoom - 1f)) * 0.5f
                        val newPan = if (newZoom <= 1.01f) {
                            Offset.Zero
                        } else {
                            Offset(
                                x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        }
                        onZoomPanChange(newZoom, newPan)
                    }
                } else {
                    detectTapGestures { tapOffset ->
                        if (size.width > 0 && size.height > 0) {
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val normX = (((tapOffset.x - w * 0.5f - panOffset.x) / (w * zoom)) + 0.5f)
                                .coerceIn(0.005f, 0.995f)
                            val normY = (((tapOffset.y - h * 0.5f - panOffset.y) / (h * zoom)) + 0.5f)
                                .coerceIn(0.005f, 0.995f)
                            onTapNormalized(normX, normY)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            fun toScreenX(normX: Float): Float = (normX - 0.5f) * w * zoom + w * 0.5f + panOffset.x
            fun toScreenY(normY: Float): Float = (normY - 0.5f) * h * zoom + h * 0.5f + panOffset.y

            if (imageBitmap != null) {
                val left = toScreenX(0f).toInt()
                val top = toScreenY(0f).toInt()
                val scaledW = (w * zoom).toInt().coerceAtLeast(1)
                val scaledH = (h * zoom).toInt().coerceAtLeast(1)
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(scaledW, scaledH)
                )
            } else {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E3A5F),
                            Color(0xFF13263E),
                            Color(0xFF0B1727)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.65f
                    ),
                    size = Size(w, h)
                )
            }

            if (showGridOverlay) {
                val hasDetectedLines = detectedGrid.verticalLines.isNotEmpty() || detectedGrid.horizontalLines.isNotEmpty()
                if (hasDetectedLines) {
                    val detectedLineColor = Color(0xFF38BDF8).copy(alpha = 0.72f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    detectedGrid.verticalLines.forEach { normX ->
                        val sx = toScreenX(normX)
                        drawLine(
                            color = detectedLineColor,
                            start = Offset(sx, toScreenY(0.02f)),
                            end = Offset(sx, toScreenY(0.98f)),
                            strokeWidth = 2.2f,
                            pathEffect = dashEffect
                        )
                    }
                    detectedGrid.horizontalLines.forEach { normY ->
                        val sy = toScreenY(normY)
                        drawLine(
                            color = detectedLineColor,
                            start = Offset(toScreenX(0.02f), sy),
                            end = Offset(toScreenX(0.98f), sy),
                            strokeWidth = 2.2f,
                            pathEffect = dashEffect
                        )
                    }
                }
            }

            val baseRadius = (w * (0.0052f + markerScale * 0.0115f) * kotlin.math.sqrt(zoom)).coerceIn(4f, 22f)
            val ringStroke = (baseRadius * 0.25f).coerceIn(1.5f, 3.2f)
            val centerDotRadius = (baseRadius * 0.22f).coerceIn(1.2f, 2.8f)

            markers.forEach { marker ->
                val cx = toScreenX(marker.normalizedX)
                val cy = toScreenY(marker.normalizedY)
                if (cx < -baseRadius * 2 || cx > w + baseRadius * 2 ||
                    cy < -baseRadius * 2 || cy > h + baseRadius * 2
                ) {
                    return@forEach
                }
                val center = Offset(cx, cy)

                if (marker.isOnGridLine) {
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = baseRadius * 1.42f,
                        center = center,
                        style = Stroke(
                            width = (ringStroke * 0.9f).coerceAtLeast(1.4f),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                        )
                    )
                }

                if (marker.isOverlapping) {
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = baseRadius * 1.32f,
                        center = center,
                        style = Stroke(width = (ringStroke * 0.85f).coerceAtLeast(1.3f))
                    )
                }

                if (marker.isLive) {
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        radius = baseRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = ringStroke)
                    )
                    drawCircle(
                        color = Color(0xFF34D399),
                        radius = centerDotRadius,
                        center = center
                    )
                } else {
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.20f),
                        radius = baseRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = ringStroke)
                    )
                    val d = baseRadius * 0.45f
                    drawLine(
                        color = Color(0xFFF87171),
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                        strokeWidth = ringStroke
                    )
                    drawLine(
                        color = Color(0xFFF87171),
                        start = Offset(cx + d, cy - d),
                        end = Offset(cx - d, cy + d),
                        strokeWidth = ringStroke
                    )
                }
            }
        }
    }
}


