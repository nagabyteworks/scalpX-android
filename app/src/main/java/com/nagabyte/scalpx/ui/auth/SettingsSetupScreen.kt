package com.nagabyte.scalpx.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.data.remote.dto.AppConfigDto
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsSetupScreen(
    repository: TradingRepository,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val deviceId = remember(repository) { repository.configStore.getDeviceId() }
    var totp by remember { mutableStateOf("") }
    var authenticating by remember { mutableStateOf(false) }
    var authenticated by remember { mutableStateOf(repository.isUserAuthenticated()) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var appConfig by remember { mutableStateOf<AppConfigDto?>(null) }
    var backendStatus by remember { mutableStateOf("Checking") }

    LaunchedEffect(Unit) {
        while (true) {
            backendStatus = when (val health = repository.checkHealth()) {
                is ApiResult.Success -> "Online"
                is ApiResult.Error -> when (health.statusCode) {
                    502, 503, 504 -> "Gateway error (HTTP ${health.statusCode})"
                    null -> "Network unavailable"
                    else -> "HTTP error (${health.statusCode})"
                }
            }
            if (authenticated) {
                appConfig = (repository.getAppConfig() as? ApiResult.Success)?.data
            }
            delay(15_000)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF0F1117)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text("scalpX", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("LIVE trading cockpit", fontSize = 13.sp, color = Color(0xFF9AA4B2))

        StatusCard("Backend", backendStatus)

        if (!authenticated) {
            DeviceIdCard(
                deviceId = deviceId,
                onCopy = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId))
                    Toast.makeText(context, "Device ID copied", Toast.LENGTH_SHORT).show()
                }
            )
            Text("Authenticate device", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Enter the current six digit code for your backend configured authenticator. The TOTP secret stays on the backend.", color = Color(0xFFB7C0CD), fontSize = 13.sp)
            OutlinedTextField(
                value = totp,
                onValueChange = { totp = it.filter(Char::isDigit).take(6); error = null },
                label = { Text("6 digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
            Button(
                enabled = totp.length == 6 && !authenticating,
                onClick = {
                    authenticating = true
                    scope.launch {
                        when (val result = repository.authenticateDevice(totp)) {
                            is ApiResult.Success -> {
                                authenticated = true
                                totp = ""
                                notice = "Device authenticated. Broker authorization is a separate Flattrade browser step."
                                appConfig = (repository.getAppConfig() as? ApiResult.Success)?.data
                                onSaved()
                            }
                            is ApiResult.Error -> error = result.message
                        }
                        authenticating = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (authenticating) "Authenticating…" else "Authenticate / Connect") }
        } else {
            StatusCard("Device", appConfig?.device?.status ?: "Authenticated")
            StatusCard("Broker", if (appConfig?.broker?.connected == true) "Connected" else "${appConfig?.broker?.sessionValidity ?: "Not connected"}")
            StatusCard("Market feed", appConfig?.marketFeed?.status ?: "Unavailable")
            StatusCard("Order execution", if (appConfig?.trading?.enabled == true) "Enabled" else "Disabled")
            StatusCard("Trading authorization", if (appConfig?.trading?.authorized == true) "Authorized" else "Not authorized")
            notice?.let { Text(it, color = Color(0xFFB7C0CD), fontSize = 13.sp) }
            Button(
                onClick = {
                    scope.launch {
                        when (val result = repository.getLoginUrl()) {
                            is ApiResult.Success -> runCatching { uriHandler.openUri(result.data) }
                                .onFailure { error = "Could not open Flattrade authorization." }
                            is ApiResult.Error -> error = result.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Connect Flattrade in browser") }
            error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp) }
            Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Text("Open cockpit") }
        }
    }
}

@Composable
private fun DeviceIdCard(deviceId: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D27))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text("Device ID", color = Color(0xFFB7C0CD), fontSize = 12.sp)
                Text(deviceId, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 2)
            }
            TextButton(onClick = onCopy) {
                Text("Copy", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D27))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFB7C0CD), fontSize = 13.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
