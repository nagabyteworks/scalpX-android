package com.nagabyte.scalpx

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.nagabyte.scalpx.core.enableSecureScreen
import com.nagabyte.scalpx.ui.auth.BiometricGate
import com.nagabyte.scalpx.ui.auth.BrokerLoginHelper
import com.nagabyte.scalpx.ui.auth.SettingsSetupScreen
import com.nagabyte.scalpx.ui.screens.ScalperCockpitScreen
import com.nagabyte.scalpx.ui.scalper.ScalperIntent
import com.nagabyte.scalpx.ui.scalper.ScalperUiEffect
import com.nagabyte.scalpx.ui.scalper.ScalperViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app by lazy { application as ScalperApplication }
    private val biometricGate = BiometricGate()

    val viewModel: ScalperViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScalperViewModel(app.tradingRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSecureScreen()

        // Handle initial deep link if app launched via URI
        intent?.data?.let { uri ->
            viewModel.handleIntent(ScalperIntent.DeepLinkReceived(uri))
        }

        observeEffectsAndWindowFlags()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isLocked by biometricGate.isLocked.collectAsState()

            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F1117)) {
                when {
                    isLocked -> {
                        BiometricLockScreen(
                            onUnlockClicked = {
                                biometricGate.promptUnlock(this@MainActivity) {}
                            }
                        )
                    }
                    uiState.isSettingsOpen || !app.tradingRepository.isUserAuthenticated() -> {
                        SettingsSetupScreen(
                            configStore = app.configStore,
                            repository = app.tradingRepository,
                            onSaved = {
                                viewModel.handleIntent(ScalperIntent.OpenSettings(false))
                                viewModel.refreshHealth()
                                viewModel.reconnectFeed()
                            }
                        )
                    }
                    else -> {
                        ScalperCockpitScreen(
                            state = uiState,
                            onIntent = viewModel::handleIntent
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        biometricGate.onAppForegrounded(this)
    }

    override fun onStop() {
        super.onStop()
        biometricGate.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            // Immediately notifies ScalperViewModel to refresh health, reconnect ws, and pull option chain
            viewModel.handleIntent(ScalperIntent.DeepLinkReceived(uri))
        }
    }

    private fun observeEffectsAndWindowFlags() {
        // Toggle KeepScreenOn based on trading state
        lifecycleScope.launch {
            viewModel.uiState
                .map { it.keepScreenOn }
                .distinctUntilChanged()
                .collect { keepOn ->
                    if (keepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }

        // Handle one-shot UI effects (Haptics, Toasts, Custom Tabs)
        lifecycleScope.launch {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is ScalperUiEffect.TriggerHaptic -> {
                        val flag = if (effect.isImportant) {
                            HapticFeedbackConstants.LONG_PRESS
                        } else {
                            HapticFeedbackConstants.KEYBOARD_TAP
                        }
                        window.decorView.performHapticFeedback(flag)
                    }
                    is ScalperUiEffect.ShowToast -> {
                        Toast.makeText(this@MainActivity, effect.message, Toast.LENGTH_SHORT).show()
                    }
                    is ScalperUiEffect.OpenCustomTab -> {
                        BrokerLoginHelper.openCustomTab(this@MainActivity, effect.url)
                    }
                    is ScalperUiEffect.PlaySound -> {
                        // Sound feedback
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockScreen(onUnlockClicked: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D13)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "scalpX Locked",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Session protected from background tap hazards",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onUnlockClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C6EF5))
            ) {
                Text("Unlock with Biometrics", color = Color.White)
            }
        }
    }
}


