package com.nagabyte.scalpx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.LadderDraftUi
import com.nagabyte.scalpx.ui.theme.ScalperTheme
import com.nagabyte.scalpx.ui.theme.TradingTypography
import kotlinx.collections.immutable.ImmutableList

/**
 * Zone 2: Parameter selector grid and ratchet SL settings.
 * Can be collapsed into a single compact summary chip row to maximize chart/ladder space.
 */
@Composable
fun Zone2ParameterGrid(
    selectedIndex: String,
    selectedExpiry: String,
    availableExpiries: ImmutableList<String>,
    product: String,
    selectedCallStrike: Double?,
    selectedPutStrike: Double?,
    selectedLots: Int,
    lotSize: Int,
    ladderDraft: LadderDraftUi,
    isCollapsed: Boolean,
    onSelectIndex: (String) -> Unit,
    onSelectExpiry: (String) -> Unit,
    onSelectProduct: (String) -> Unit,
    onStepCallStrike: (Int) -> Unit,
    onStepPutStrike: (Int) -> Unit,
    onStepLots: (Int) -> Unit,
    onToggleCollapse: (Boolean) -> Unit,
    onOpenRatchetConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (isCollapsed) {
                // Collapsed Summary Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val summaryText = buildString {
                        append(selectedIndex)
                        if (selectedExpiry.isNotBlank()) append(" | ").append(selectedExpiry)
                        append(" | ").append(product)
                        append(" | CE: ").append(selectedCallStrike?.toInt() ?: "-")
                        append(" | PE: ").append(selectedPutStrike?.toInt() ?: "-")
                        append(" | ").append(selectedLots).append(" L (").append(selectedLots * lotSize).append(")")
                    }

                    Text(
                        text = summaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { onToggleCollapse(false) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand Parameters",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // Expanded View: Row 1 (Dropdowns)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Index Selector (Weight 1.2)
                    IndexDropdown(
                        selectedIndex = selectedIndex,
                        onSelectIndex = onSelectIndex,
                        modifier = Modifier.weight(1.2f)
                    )

                    // Expiry Selector (Weight 1.4)
                    ExpiryDropdown(
                        selectedExpiry = selectedExpiry,
                        availableExpiries = availableExpiries,
                        onSelectExpiry = onSelectExpiry,
                        modifier = Modifier.weight(1.4f)
                    )

                    // Product Selector (Weight 0.9)
                    ProductToggle(
                        product = product,
                        onSelectProduct = onSelectProduct,
                        modifier = Modifier.weight(0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Expanded View: Row 2 (Call Strike, Put Strike, Qty Steppers)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Strike Stepper
                    StepperBox(
                        label = "Call Strike",
                        value = selectedCallStrike?.toInt()?.toString() ?: "---",
                        onDecrement = { onStepCallStrike(-1) },
                        onIncrement = { onStepCallStrike(1) },
                        modifier = Modifier.weight(1.1f)
                    )

                    // Put Strike Stepper
                    StepperBox(
                        label = "Put Strike",
                        value = selectedPutStrike?.toInt()?.toString() ?: "---",
                        onDecrement = { onStepPutStrike(-1) },
                        onIncrement = { onStepPutStrike(1) },
                        modifier = Modifier.weight(1.1f)
                    )

                    // Lots Stepper
                    StepperBox(
                        label = "Qty (Lots)",
                        value = "$selectedLots (${selectedLots * lotSize})",
                        onDecrement = { onStepLots(-1) },
                        onIncrement = { onStepLots(1) },
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Expanded View: Row 3 (Ratchet Config Pill + Collapse)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ratchet Pill Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenRatchetConfig() },
                        color = colors.surfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Ratchet Settings",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "SL: ${ladderDraft.firstTargetPts.toInt()}pt / ${ladderDraft.stepPts.toInt()}pt",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = { onToggleCollapse(true) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Collapse Parameters",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexDropdown(
    selectedIndex: String,
    onSelectIndex: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val indices = listOf("BANKNIFTY", "NIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX")

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true },
            color = colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedIndex,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Index",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            indices.forEach { idx ->
                DropdownMenuItem(
                    text = { Text(idx, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    onClick = {
                        onSelectIndex(idx)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExpiryDropdown(
    selectedExpiry: String,
    availableExpiries: ImmutableList<String>,
    onSelectExpiry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { if (availableExpiries.isNotEmpty()) expanded = true },
            color = colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedExpiry.ifBlank { "Select Expiry" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Expiry",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableExpiries.forEach { exp ->
                DropdownMenuItem(
                    text = { Text(exp, fontSize = 11.sp) },
                    onClick = {
                        onSelectExpiry(exp)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ProductToggle(
    product: String,
    onSelectProduct: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val isMis = product.equals("MIS", ignoreCase = true)

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true },
            color = colors.surfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isMis) "MIS" else "NRML",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Product",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("MIS (Intraday)", fontSize = 11.sp) },
                onClick = {
                    onSelectProduct("MIS")
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("NRML (Carryforward)", fontSize = 11.sp) },
                onClick = {
                    onSelectProduct("NRML")
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun StepperBox(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ScalperTheme.colors

    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement Button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp)
                    .clickable { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrement $label",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Central Value + Sub-label
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = TradingTypography.TabularDigitsBold,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }

            // Increment Button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(28.dp)
                    .clickable { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increment $label",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
