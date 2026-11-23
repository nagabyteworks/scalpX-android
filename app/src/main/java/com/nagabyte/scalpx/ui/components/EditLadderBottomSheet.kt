package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.core.MoneyFormatter
import com.nagabyte.scalpx.ui.scalper.ActivePositionUi
import com.nagabyte.scalpx.ui.theme.TradingColors
import com.nagabyte.scalpx.ui.theme.TradingTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLadderBottomSheet(
    position: ActivePositionUi,
    onDismiss: () -> Unit,
    onSave: (token: String, nextMilestonePaise: Long?, stepSizePaise: Long?, breakoutBufferPaise: Long?, slippageBufferPaise: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var milestoneText by remember { mutableStateOf<String>(MoneyFormatter.fromPaise(position.localNextMilestonePaise)) }
    var stepText by remember { mutableStateOf<String>(MoneyFormatter.fromPaise(position.stepSizePaise)) }
    var breakoutText by remember { mutableStateOf<String>("1.00") }
    var slippageText by remember { mutableStateOf<String>("1.50") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TradingColors.SurfaceCard,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Edit Ratchet: ${position.symbol}",
                color = TradingColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Live LTP: ${MoneyFormatter.formatPaise(position.currentLtpPaise)}",
                style = TradingTypography.TabularDigitsBold,
                color = TradingColors.TextSecondary,
                fontSize = 12.sp
            )

            // Next Milestone Field
            OutlinedTextField(
                value = milestoneText,
                onValueChange = { milestoneText = it },
                label = { Text("Next Milestone Price (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TradingColors.ProfitGreen,
                    unfocusedBorderColor = TradingColors.Border,
                    focusedTextColor = TradingColors.TextPrimary,
                    unfocusedTextColor = TradingColors.TextPrimary
                )
            )

            // Step Size Field
            OutlinedTextField(
                value = stepText,
                onValueChange = { stepText = it },
                label = { Text("Step Size (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TradingColors.ProfitGreen,
                    unfocusedBorderColor = TradingColors.Border,
                    focusedTextColor = TradingColors.TextPrimary,
                    unfocusedTextColor = TradingColors.TextPrimary
                )
            )

            // Breakout Buffer Field
            OutlinedTextField(
                value = breakoutText,
                onValueChange = { breakoutText = it },
                label = { Text("Breakout Buffer (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TradingColors.ProfitGreen,
                    unfocusedBorderColor = TradingColors.Border,
                    focusedTextColor = TradingColors.TextPrimary,
                    unfocusedTextColor = TradingColors.TextPrimary
                )
            )

            // Slippage Buffer Field
            OutlinedTextField(
                value = slippageText,
                onValueChange = { slippageText = it },
                label = { Text("Slippage Buffer (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TradingColors.ProfitGreen,
                    unfocusedBorderColor = TradingColors.Border,
                    focusedTextColor = TradingColors.TextPrimary,
                    unfocusedTextColor = TradingColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Cancel", color = TradingColors.TextSecondary)
                }

                Button(
                    onClick = {
                        val milestonePaise = MoneyFormatter.rupeesTextToAlignedPaise(milestoneText, 5L)
                        val stepPaise = MoneyFormatter.rupeesTextToAlignedPaise(stepText, 5L)
                        val breakoutPaise = MoneyFormatter.rupeesTextToAlignedPaise(breakoutText, 5L)
                        val slippagePaise = MoneyFormatter.rupeesTextToAlignedPaise(slippageText, 5L)

                        onSave(position.token, milestonePaise, stepPaise, breakoutPaise, slippagePaise)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TradingColors.ProfitGreen),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Save Changes", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
fun EditLadderBottomSheetPreview() {
    EditLadderBottomSheet(
        position = ActivePositionUi(
            token = "",
            symbol = "",
            quantity = 0,
            entryPricePaise = 0L,
            currentLtpPaise = 0L,
            unrealizedPnlPaise = 0L,
            localNextMilestonePaise = 0L,
            stepSizePaise = 0L,
            ratchetPaused = false
        ),
        onDismiss = {},
        onSave = { _, _, _, _, _ -> }
    )
}
