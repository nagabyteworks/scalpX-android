package com.nagabyte.scalpx.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.ui.scalper.DailyOtpSheetState
import com.nagabyte.scalpx.ui.theme.TradingColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyOtpSheet(
    sheetState: DailyOtpSheetState,
    onFallbackBrowserClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!sheetState.isVisible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TradingColors.SurfaceCard,
        contentColor = TradingColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = TradingColors.Border) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Connect Flattrade", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Flattrade authentication opens in its official browser flow. Complete authorization there to reconnect the broker.",
                color = TradingColors.TextSecondary,
                fontSize = 13.sp
            )
            sheetState.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.Red, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onFallbackBrowserClick, modifier = Modifier.fillMaxWidth()) {
                Text("Open Flattrade authorization")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
