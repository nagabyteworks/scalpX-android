package com.nagabyte.scalpx.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.DailyOtpSheetState
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyOtpSheet(
    sheetState: DailyOtpSheetState,
    brokerMode: String,
    onOtpDigitsChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onFallbackBrowserClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        // Small delay to ensure bottom sheet enters window before requesting focus
        delay(150)
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalSheetState,
        containerColor = TradingColors.SurfaceCard,
        contentColor = TradingColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = TradingColors.Border)
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Title + Environment Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Flattrade Daily Login",
                        color = TradingColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Enter 6-digit TOTP / SMS OTP",
                        color = TradingColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Mode Pill (LIVE / PAPER)
                val isLive = brokerMode.equals("LIVE", ignoreCase = true)
                Surface(
                    color = if (isLive) TradingColors.LiveMode else TradingColors.PaperMode,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "PAPER",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Hidden BasicTextField for receiving keyboard input and clipboard paste
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interactionSource, indication = null) {
                        focusRequester.requestFocus()
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = sheetState.otpDigits,
                    onValueChange = onOtpDigitsChanged,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (sheetState.otpDigits.length == 6 && !sheetState.isSubmitting) {
                                onSubmit()
                            }
                        }
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .alpha(0.01f) // Keep clickable and focusable while visually invisible
                        .size(1.dp)
                )

                // 6 Segmented OTP Boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (i in 0 until 6) {
                        val digit = sheetState.otpDigits.getOrNull(i)?.toString() ?: ""
                        val isCurrentBox = sheetState.otpDigits.length == i || (sheetState.otpDigits.length == 6 && i == 5)
                        val borderColor = when {
                            sheetState.errorMessage != null -> Color(0xFFFF3B30)
                            isCurrentBox -> TradingColors.ProfitGreen
                            digit.isNotEmpty() -> TradingColors.TextPrimary
                            else -> TradingColors.Border
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TradingColors.SurfaceElevated)
                                .border(BorderStroke(if (isCurrentBox) 2.dp else 1.dp, borderColor), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = digit,
                                color = TradingColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Inline Error Message
            if (sheetState.errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = sheetState.errorMessage,
                    color = Color(0xFFFF3B30),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Full-Width "Generate Token" Button
            Button(
                onClick = onSubmit,
                enabled = sheetState.otpDigits.length == 6 && !sheetState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TradingColors.ProfitGreen,
                    disabledContainerColor = TradingColors.SurfaceElevated,
                    contentColor = Color.Black,
                    disabledContentColor = TradingColors.TextMuted
                )
            ) {
                if (sheetState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Generate Token",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sheetState.otpDigits.length == 6) Color.Black else TradingColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Bottom Action: "Fallback: Login via Browser"
            val isFallbackSuggested = sheetState.suggestBrowserFallback
            OutlinedButton(
                onClick = onFallbackBrowserClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isFallbackSuggested) TradingColors.WarningAmber.copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isFallbackSuggested) TradingColors.WarningAmber else TradingColors.TextSecondary
                ),
                border = BorderStroke(
                    width = if (isFallbackSuggested) 1.5.dp else 1.dp,
                    color = if (isFallbackSuggested) TradingColors.WarningAmber else TradingColors.Border
                )
            ) {
                Text(
                    text = "Fallback: Login via Browser",
                    fontSize = 12.sp,
                    fontWeight = if (isFallbackSuggested) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
