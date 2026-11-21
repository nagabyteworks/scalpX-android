package com.nagabyte.scalpx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PanicExitBar(
    totalUnrealizedPnlPaise: Long,
    totalRealizedPnlPaise: Long,
    inFlightCommands: Set<String>,
    modifier: Modifier = Modifier,
    onPanicExitAll: () -> Unit = {}
) {
    val totalNetPnlPaise = totalUnrealizedPnlPaise + totalRealizedPnlPaise
    val pnlColor = if (totalNetPnlPaise >= 0) TradingColors.ProfitGreen else TradingColors.LossRed
    val pnlSign = if (totalNetPnlPaise >= 0) "+" else ""

    val isExitAllInFlight = inFlightCommands.contains("EXIT_ALL")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(TradingColors.SurfaceCard)
            .border(width = 1.dp, color = TradingColors.Border),
        color = TradingColors.SurfaceCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Total Live MTM (Net = Realized + Unrealized)
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "TOTAL LIVE MTM",
                    color = TradingColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$pnlSign${MoneyFormatter.formatPaise(totalNetPnlPaise)}",
                    style = TradingTypography.PnlLarge,
                    color = pnlColor
                )
                Text(
                    text = "U: ${MoneyFormatter.formatPaise(totalUnrealizedPnlPaise)} | R: ${MoneyFormatter.formatPaise(totalRealizedPnlPaise)}",
                    style = TradingTypography.TabularDigits,
                    color = TradingColors.TextSecondary,
                    fontSize = 10.sp
                )
            }

            // Right: Slide-to-Exit-All Control (> 80% swipe threshold)
            SlideToExitAllControl(
                isExecuting = isExitAllInFlight,
                onTrigger = onPanicExitAll
            )
        }
    }
}

@Composable
private fun SlideToExitAllControl(
    isExecuting: Boolean,
    onTrigger: () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val knobSizeDp = 40.dp
    val knobSizePx = with(density) { knobSizeDp.toPx() }

    val dragOffset = remember { Animatable(0f) }

    val maxDragPx = (trackWidthPx - knobSizePx).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .width(185.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(TradingColors.SurfaceElevated)
            .border(1.dp, TradingColors.PanicRed.copy(alpha = 0.6f), RoundedCornerShape(23.dp))
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Red track progress fill
        val progressWidthDp = with(density) { (dragOffset.value + knobSizePx).toDp() }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(progressWidthDp)
                .background(TradingColors.PanicRed.copy(alpha = 0.35f))
        )

        // Center Track Instruction Text
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isExecuting) "EXITING..." else "SLIDE TO EXIT ALL >>",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }

        // Draggable Knob Handle
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(knobSizeDp)
                .padding(2.dp)
                .clip(CircleShape)
                .background(TradingColors.PanicRed)
                .pointerInput(isExecuting, maxDragPx) {
                    if (isExecuting || maxDragPx <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val progress = dragOffset.value / maxDragPx
                                if (progress >= 0.80f) {
                                    // Swipe completed beyond 80% threshold
                                    onTrigger()
                                }
                                // Snap back to start smoothly
                                dragOffset.animateTo(0f, animationSpec = tween(250))
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(0f, animationSpec = tween(250))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val target = (dragOffset.value + dragAmount).coerceIn(0f, maxDragPx)
                                dragOffset.snapTo(target)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isExecuting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowRight,
                    contentDescription = "Swipe Handle",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun PanicExitBarPreview() {
    PanicExitBar(
        totalUnrealizedPnlPaise = 152000L,
        totalRealizedPnlPaise = 45000L,
        inFlightCommands = emptySet()
    )
}
