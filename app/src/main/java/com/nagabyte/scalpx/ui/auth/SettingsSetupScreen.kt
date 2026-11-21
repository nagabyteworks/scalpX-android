package com.nagabyte.scalpx.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nagabyte.scalpx.data.local.ConfigStore
import com.nagabyte.scalpx.data.remote.dto.HealthDto
import com.nagabyte.scalpx.data.repository.ApiResult
import com.nagabyte.scalpx.data.repository.TradingRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsSetupScreen(
    configStore: ConfigStore,
    repository: TradingRepository,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var baseUrl by remember { mutableStateOf(configStore.getServerBaseUrl()) }
    var jwtToken by remember { mutableStateOf(configStore.getJwtToken() ?: "") }
    var isTokenVisible by remember { mutableStateOf(false) }

    var flattradeUserId by remember { mutableStateOf(configStore.getFlattradeUserId() ?: "") }
    var flattradePassword by remember { mutableStateOf(configStore.getFlattradePassword() ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var isTestingConnection by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<HealthDto?>(null) }
    var healthError by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1117))
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "scalpX Configuration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "Server Authoritative Personal Scalper",
            fontSize = 12.sp,
            color = Color(0xFF8E95A5),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Server Base URL Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D27))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Server Base URL",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://trade.domain.com or http://10.0.2.2:3000", color = Color.Gray, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF4C6EF5),
                        unfocusedBorderColor = Color(0xFF2C3242)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Personal JWT Token",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = jwtToken,
                    onValueChange = { jwtToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("HS256 Bearer Token", color = Color.Gray) },
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Text(
                                text = if (isTokenVisible) "HIDE" else "SHOW",
                                fontSize = 11.sp,
                                color = Color(0xFF4C6EF5)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF4C6EF5),
                        unfocusedBorderColor = Color(0xFF2C3242)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Device ID: ${configStore.getDeviceId()}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Flattrade Broker Credentials Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D27))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Flattrade Broker Password",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    if (flattradePassword.isNotBlank()) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Flattrade Password", flattradePassword)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Password copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("COPY", color = Color(0xFF4C6EF5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Saved securely in Android Keystore. Auto-copied to clipboard when launching OAuth.",
                    fontSize = 11.sp,
                    color = Color(0xFF8E95A5)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "User ID",
                    fontWeight = FontWeight.Medium,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = flattradeUserId,
                    onValueChange = { flattradeUserId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. FT012345", color = Color.Gray, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF4C6EF5),
                        unfocusedBorderColor = Color(0xFF2C3242)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Password",
                    fontWeight = FontWeight.Medium,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = flattradePassword,
                    onValueChange = { flattradePassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Enter your Flattrade password", color = Color.Gray, fontSize = 12.sp) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Text(
                                text = if (isPasswordVisible) "HIDE" else "SHOW",
                                fontSize = 11.sp,
                                color = Color(0xFF4C6EF5)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF4C6EF5),
                        unfocusedBorderColor = Color(0xFF2C3242)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Test Result Card
        if (healthResult != null || healthError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (healthResult != null) Color(0xFF13221B) else Color(0xFF2A1517)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (healthResult != null) {
                        val hr = healthResult!!
                        val isLive = hr.brokerMode.equals("live", ignoreCase = true)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "CONNECTION VERIFIED",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676),
                                fontSize = 14.sp
                            )
                            Surface(
                                color = if (isLive) Color(0xFFD32F2F) else Color(0xFFFFB300),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = hr.brokerMode.uppercase(),
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Broker Connected: ${if (hr.brokerConnected) "YES" else "NO"}",
                            color = if (hr.brokerConnected) Color(0xFF00E676) else Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "WS Feed Healthy: ${if (hr.wsFeedHealthy) "YES" else "NO"}",
                            color = if (hr.wsFeedHealthy) Color(0xFF00E676) else Color(0xFFFF5252),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Server Memory RSS: ${hr.memoryRssMb} MB | Uptime: ${hr.uptimeSec}s",
                            color = Color(0xFF8E95A5),
                            fontSize = 11.sp
                        )
                    } else if (healthError != null) {
                        Text(
                            text = "CONNECTION FAILED",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = healthError!!,
                            color = Color(0xFFFFB4AB),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    coroutineScope.launch {
                        isTestingConnection = true
                        healthResult = null
                        healthError = null
                        configStore.setServerBaseUrl(baseUrl.trim())
                        configStore.setJwtToken(jwtToken.trim())

                        when (val result = repository.checkHealth()) {
                            is ApiResult.Success -> {
                                healthResult = result.data
                            }
                            is ApiResult.Error -> {
                                healthError = result.message
                            }
                        }
                        isTestingConnection = false
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isTestingConnection && baseUrl.isNotBlank()
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Test Connection", color = Color(0xFF4C6EF5), fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    configStore.setServerBaseUrl(baseUrl.trim())
                    configStore.setJwtToken(jwtToken.trim())
                    configStore.setFlattradeUserId(flattradeUserId.trim().ifEmpty { null })
                    configStore.setFlattradePassword(flattradePassword.ifEmpty { null })
                    statusMessage = "Configuration Saved!"
                    onSaved()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C6EF5)),
                enabled = baseUrl.isNotBlank()
            ) {
                Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = statusMessage!!, color = Color(0xFF00E676), fontSize = 12.sp)
        }
    }
}
