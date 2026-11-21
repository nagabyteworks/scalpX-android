package com.nagabyte.scalpx.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.PendingOrderConfirmation
import com.nagabyte.scalpx.ui.theme.ScalperTheme

/**
 * Confirmation dialog shown before dispatching orders when "One click: Enabled" is unchecked.
 */
@Composable
fun OrderConfirmationDialog(
    confirmation: PendingOrderConfirmation,
    onDismiss: () -> Unit
) {
    val colors = ScalperTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = confirmation.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary
            )
        },
        text = {
            Text(
                text = confirmation.message,
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    confirmation.onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.buyGreen,
                    contentColor = Color.White
                )
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceCard
    )
}
