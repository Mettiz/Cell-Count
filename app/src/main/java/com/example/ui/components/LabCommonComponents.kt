package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CountUnitMultiplier
import com.example.model.NumberFormatter
import com.example.model.VolumeUnit
import com.example.ui.theme.BioIndigoPrimary
import com.example.ui.theme.CellCyan
import com.example.ui.theme.MediaCoral
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SerumAmber

/**
 * VisualTransformation that formats numbers 3-by-3 with thousands separators (e.g. 2,500,000)
 * while maintaining exact LTR cursor OffsetMapping for smooth editing and deletion from the right.
 */
class ThousandsSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val dotIndex = raw.indexOf('.').let { if (it == -1) raw.length else it }
        if (dotIndex <= 3) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val out = StringBuilder(raw.length + raw.length / 3)
        val originalToTransformed = IntArray(raw.length + 1)
        val transformedToOriginal = ArrayList<Int>(raw.length + raw.length / 3 + 2)

        for (i in raw.indices) {
            if (i in 1 until dotIndex &&
                (dotIndex - i) % 3 == 0 &&
                raw[i].isDigit() &&
                raw[i - 1].isDigit()
            ) {
                out.append(',')
                transformedToOriginal.add(i)
            }
            originalToTransformed[i] = out.length
            out.append(raw[i])
            transformedToOriginal.add(i)
        }
        originalToTransformed[raw.length] = out.length
        transformedToOriginal.add(raw.length)

        val transformedString = out.toString()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, raw.length)
                return originalToTransformed[safe].coerceIn(0, transformedString.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, transformedString.length)
                return transformedToOriginal[safe].coerceIn(0, raw.length)
            }
        }

        return TransformedText(AnnotatedString(transformedString), mapping)
    }
}

private val SharedThousandsVisualTransformation = ThousandsSeparatorVisualTransformation()

@Composable
fun LabHeroBanner(
    title: String,
    subtitle: String,
    badgeText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF312E81),
                            BioIndigoPrimary
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE0E7FF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QuickUnitSwitchBar(
    activeVolumeUnit: VolumeUnit?,
    onSelectVolumeUnit: (VolumeUnit) -> Unit,
    activeCountUnit: CountUnitMultiplier?,
    onSelectCountUnit: (CountUnitMultiplier) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volume Unit Segment (mL / L)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "یکای حجم (تبدیل آنی)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VolumeUnit.entries.forEach { vUnit ->
                        val selected = activeVolumeUnit == vUnit
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onSelectVolumeUnit(vUnit) }
                                .testTag("global_vol_unit_${vUnit.name}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = if (vUnit == VolumeUnit.ML) "میلی‌لیتر (mL)" else "لیتر (L)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Count Unit Segment (میلیون / عدد کامل)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "یکای کانت (تبدیل آنی)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CountUnitMultiplier.entries.forEach { cUnit ->
                        val selected = activeCountUnit == cUnit
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onSelectCountUnit(cUnit) }
                                .testTag("unit_chip_${cUnit.name}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = cUnit.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Computes an adaptive font size (in sp) based on the formatted text length so long numbers
 * (e.g., 25,000,000) fit cleanly inside their container without clipping or overlapping.
 */
fun adaptiveMonospaceFontSize(
    text: String,
    baseSp: Float = 16f,
    minSp: Float = 11f
): TextUnit {
    val len = text.length
    val computed = when {
        len <= 6 -> baseSp
        len <= 8 -> baseSp - 1.2f
        len <= 10 -> baseSp - 2.4f
        len <= 12 -> baseSp - 3.5f
        len <= 15 -> baseSp - 4.5f
        else -> minSp
    }
    return computed.coerceAtLeast(minSp).sp
}

/**
 * Numeric input field where:
 * - The Title is on the top-right and the interactive Unit Pill (with ⇄ toggle) is on the top-left of the header row,
 *   so the unit NEVER blocks or overlaps the number inside the text field!
 * - Inside the OutlinedTextField, 100% of the horizontal width is dedicated to the number (LTR with 3-by-3 commas)
 *   with automatic font-size scaling for long numbers.
 */
@Composable
fun LabNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unitText: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onUnitClick: (() -> Unit)? = null,
    helperText: String? = null,
    badgeLabel: String? = null,
    onBadgeClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null
) {
    val formattedPreview = remember(value) { NumberFormatter.addThousandsSeparator(value) }
    val dynamicFontSize = adaptiveMonospaceFontSize(formattedPreview, baseSp = 16f, minSp = 11.5f)

    Column(modifier = modifier) {
        // Header Row: Label on Right (in RTL) + Unit Badge Pill on Left (in RTL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (badgeLabel != null) {
                    Surface(
                        color = accentColor.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = if (onBadgeClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onBadgeClick() }
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Dedicated Unit Pill in Header so it never covers digits inside the input box
                Surface(
                    color = accentColor.copy(alpha = if (onUnitClick != null) 0.16f else 0.10f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = accentColor.copy(alpha = if (onUnitClick != null) 0.48f else 0.25f)
                    ),
                    modifier = if (onUnitClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onUnitClick() }
                            .testTag("${testTag}_unit_toggle")
                    } else {
                        Modifier
                    }
                ) {
                    Text(
                        text = if (onUnitClick != null) "$unitText ⇄" else unitText,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Full-Width LTR OutlinedTextField: 100% of the field width shows the number clearly!
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = value,
                onValueChange = { newText ->
                    onValueChange(NumberFormatter.normalizeInputString(newText))
                },
                singleLine = true,
                visualTransformation = SharedThousandsVisualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = RobotoMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = dynamicFontSize,
                    textAlign = TextAlign.Left,
                    textDirection = TextDirection.Ltr
                ),
                leadingIcon = if (leadingIcon != null) {
                    {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = label,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = accentColor.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }

        if (!helperText.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "= $helperText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = RobotoMonoFontFamily,
                            textDirection = TextDirection.Ltr
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun TubeCompositionVisualBar(
    cellVolumeMl: Double,
    mediaVolumeMl: Double,
    serumVolumeMl: Double,
    serumPercent: Double,
    modifier: Modifier = Modifier
) {
    var displayUnit by remember { mutableStateOf(VolumeUnit.ML) }
    val total = (cellVolumeMl + mediaVolumeMl + serumVolumeMl).coerceAtLeast(0.0001)
    val cellFrac by animateFloatAsState(
        targetValue = (cellVolumeMl / total).toFloat().coerceIn(0.05f, 1f),
        animationSpec = spring(),
        label = "cellFrac"
    )
    val mediaFrac by animateFloatAsState(
        targetValue = (mediaVolumeMl / total).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "mediaFrac"
    )
    val serumFrac by animateFloatAsState(
        targetValue = (serumVolumeMl / total).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "serumFrac"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نسبت ترکیب نهایی",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { displayUnit = displayUnit.toggle() }
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = NumberFormatter.formatWithCommas(
                                    displayUnit.fromMl(cellVolumeMl + mediaVolumeMl + serumVolumeMl),
                                    4
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = RobotoMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${displayUnit.symbol} ⇄",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            ) {
                if (cellVolumeMl > 0) {
                    Box(
                        modifier = Modifier
                            .weight(cellFrac.coerceAtLeast(0.06f))
                            .fillMaxHeight()
                            .background(CellCyan)
                    )
                }
                if (mediaVolumeMl > 0) {
                    Box(
                        modifier = Modifier
                            .weight(mediaFrac.coerceAtLeast(0.06f))
                            .fillMaxHeight()
                            .background(MediaCoral)
                    )
                }
                if (serumVolumeMl > 0) {
                    Box(
                        modifier = Modifier
                            .weight(serumFrac.coerceAtLeast(0.05f))
                            .fillMaxHeight()
                            .background(SerumAmber)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LegendPill(
                    title = "سلول (V₁)",
                    volumeMl = cellVolumeMl,
                    unit = displayUnit,
                    onClick = { displayUnit = displayUnit.toggle() },
                    color = CellCyan,
                    modifier = Modifier.weight(1f)
                )
                LegendPill(
                    title = "مدیا پایه",
                    volumeMl = mediaVolumeMl,
                    unit = displayUnit,
                    onClick = { displayUnit = displayUnit.toggle() },
                    color = MediaCoral,
                    modifier = Modifier.weight(1f)
                )
                LegendPill(
                    title = "سرم (${NumberFormatter.formatWithCommas(serumPercent, 1)}٪)",
                    volumeMl = serumVolumeMl,
                    unit = displayUnit,
                    onClick = { displayUnit = displayUnit.toggle() },
                    color = SerumAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendPill(
    title: String,
    volumeMl: Double,
    unit: VolumeUnit,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val formattedNum = NumberFormatter.formatWithCommas(unit.fromMl(volumeMl), 4)
    val numFontSize = adaptiveMonospaceFontSize(formattedNum, baseSp = 13.5f, minSp = 10f)

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit.symbol,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            // Dedicated full-width row for the number so it never collides with unit
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedNum,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = RobotoMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = numFontSize
                        ),
                        color = color,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
