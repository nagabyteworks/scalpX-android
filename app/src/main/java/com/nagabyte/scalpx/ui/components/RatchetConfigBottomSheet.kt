package com.nagabyte.scalpx.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.LadderDraftUi
import com.nagabyte.scalpx.ui.theme.ScalperTheme

/**
 * Bottom sheet to configure default ratcheting SL parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatchetConfigBottomSheet(
    draft: LadderDraftUi,
    onSave: (LadderDraftUi) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ScalperTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var targetPts by remember { mutableStateOf(draft.firstTargetPts.toString()) }
    var stepPts by remember { mutableStateOf(draft.stepPts.toString()) }
    var breakoutPts by remember { mutableStateOf(draft.breakoutBufferPts.toString()) }
    var slippagePts by remember { mutableStateOf(draft.slippageBufferPts.toString()) }
    var initialSlPts by remember { mutableStateOf(draft.initialSlPts.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceCard,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Ratcheting Stop-Loss Parameters",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Configures automated trailing milestones and slippage buffers for new entries.",
                fontSize = 11.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. First Target
            OutlinedTextField(
                value = targetPts,
                onValueChange = { targetPts = it },
                label = { Text("First Target (Points)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Step Size
            OutlinedTextField(
                value = stepPts,
                onValueChange = { stepPts = it },
                label = { Text("Milestone Step Size (Points)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Breakout Buffer
            OutlinedTextField(
                value = breakoutPts,
                onValueChange = { breakoutPts = it },
                label = { Text("Breakout Buffer (Points)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Slippage Buffer
            OutlinedTextField(
                value = slippagePts,
                onValueChange = { slippagePts = it },
                label = { Text("Slippage Buffer (Points)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Initial SL
            OutlinedTextField(
                value = initialSlPts,
                onValueChange = { initialSlPts = it },
                label = { Text("Initial Stop-Loss (Points)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }

                Button(
                    onClick = {
                        val newDraft = LadderDraftUi(
                            firstTargetPts = targetPts.toDoubleOrNull() ?: draft.firstTargetPts,
                            stepPts = stepPts.toDoubleOrNull() ?: draft.stepPts,
                            breakoutBufferPts = breakoutPts.toDoubleOrNull() ?: draft.breakoutBufferPts,
                            slippageBufferPts = slippagePts.toDoubleOrNull() ?: draft.slippageBufferPts,
                            initialSlPts = initialSlPts.toDoubleOrNull() ?: draft.initialSlPts
                        )
                        onSave(newDraft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.buyGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Save Config", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
