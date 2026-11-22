package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.fragment.app.FragmentActivity
import com.example.data.security.BiometricLockHelper
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AdminSubSection
import com.example.ui.FinanceViewModel
import com.example.ui.MainTab
import com.example.ui.components.FinanceBottomNavigationBar
import com.example.ui.components.FinanceNavigationRail
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.BalancesScreen
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.ScheduledScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MisFinanzasTheme
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val requiresLock = BiometricLockHelper.canAuthenticate(this)
    setContent {
      MisFinanzasTheme {
        var isUnlocked by remember { mutableStateOf(!requiresLock) }
        var authError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(requiresLock) {
          if (requiresLock && !isUnlocked) {
            BiometricLockHelper.authenticate(
              activity = this@MainActivity,
              onSuccess = {
                authError = null
                isUnlocked = true
              },
              onFailure = { msg -> authError = msg },
            )
          }
        }

        if (isUnlocked) {
          val initialTabExtra =
            intent?.getStringExtra(
              com.example.data.notifications.AppNotificationHelper.EXTRA_OPEN_TAB
            )
          MisFinanzasApp(initialTabExtra = initialTabExtra)
        } else {
          Surface(modifier = Modifier.fillMaxSize()) {
            Column(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "Mis Finanzas bloqueado",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = authError ?: "Usa tu huella, rostro o PIN del dispositivo para continuar.",
                style = MaterialTheme.typography.bodyMedium,
              )
              Button(
                onClick = {
                  BiometricLockHelper.authenticate(
                    activity = this@MainActivity,
                    onSuccess = {
                      authError = null
                      isUnlocked = true
                    },
                    onFailure = { msg -> authError = msg },
                  )
                }
              ) {
                Text("Desbloquear")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MisFinanzasApp(
  initialTabExtra: String? = null,
  viewModel: FinanceViewModel = viewModel(),
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var minSplashElapsed by remember { mutableStateOf(false) }

  // Request strictly minimal runtime permission: POST_NOTIFICATIONS on Android 13+ (API 33+)
  val notificationPermissionLauncher =
    androidx.activity.compose.rememberLauncherForActivityResult(
      contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (!granted) {
        viewModel.notifyMessage(
          "Notificaciones desactivadas: puedes activarlas en Ajustes para recibir avisos de programados y Wi-Fi"
        )
      }
    }

  LaunchedEffect(Unit) {
    delay(950L)
    minSplashElapsed = true
    if (
      android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        !com.example.data.notifications.AppNotificationHelper.hasNotificationPermission(context)
    ) {
      notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(initialTabExtra) {
    when (initialTabExtra) {
      com.example.data.notifications.AppNotificationHelper.TAB_PROGRAMADOS ->
        viewModel.selectTab(MainTab.PROGRAMADOS)
      com.example.data.notifications.AppNotificationHelper.TAB_ADMIN_SYNC ->
        viewModel.openAdminSection(AdminSubSection.BACKUP)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.snackbarMessages.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  val showSplash = !state.isLoaded || !minSplashElapsed

  Crossfade(
    targetState = showSplash,
    animationSpec = tween(durationMillis = 320),
    label = "app_splash_crossfade",
  ) { isShowingSplash ->
    if (isShowingSplash) {
      SplashScreen()
    } else {
      if (state.currentTab != MainTab.CAPTURAR && state.adminSubSection == AdminSubSection.HUB) {
        BackHandler { viewModel.selectTab(MainTab.CAPTURAR) }
      }

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          bottomBar = {
            if (!useNavigationRail) {
              FinanceBottomNavigationBar(
                currentTab = state.currentTab,
                pendingCount = state.pendingScheduledRules.size,
                onSelectTab = viewModel::selectTab,
              )
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (useNavigationRail) {
              FinanceNavigationRail(
                currentTab = state.currentTab,
                pendingCount = state.pendingScheduledRules.size,
                onSelectTab = viewModel::selectTab,
              )
            }
            Box(
              modifier = Modifier.weight(1f).fillMaxSize(),
              contentAlignment = Alignment.TopCenter,
            ) {
              Box(modifier = Modifier.fillMaxSize().widthIn(max = 1040.dp)) {
                when (state.currentTab) {
                  MainTab.CAPTURAR ->
                    CaptureScreen(
                      state = state,
                      onSelectType = viewModel::selectType,
                      onSelectClassification = viewModel::selectClassification,
                      onSelectDetail = viewModel::selectDetail,
                      onSelectAccount = viewModel::selectAccount,
                      onSelectDate = viewModel::setOperationDate,
                      onSelectMerchant = viewModel::setMerchantName,
                      onToggleNoteField = viewModel::toggleNoteField,
                      onNoteChange = viewModel::setNote,
                      onDigit = viewModel::onKeypadDigit,
                      onDecimal = viewModel::onKeypadDecimal,
                      onBackspace = viewModel::onKeypadBackspace,
                      onClearAmount = viewModel::clearAmount,
                      onApplyIncomeShortcut = viewModel::applyIncomeShortcut,
                      onSaveTransaction = viewModel::saveCapturedTransaction,
                      onConfirmPendingRule = { pending -> viewModel.confirmPendingRule(pending) },
                      onOpenScheduledTab = { viewModel.selectTab(MainTab.PROGRAMADOS) },
                      onOpenAccountsAdmin = { viewModel.openAdminSection(AdminSubSection.ACCOUNTS) },
                      onOpenSyncAdmin = { viewModel.openAdminSection(AdminSubSection.BACKUP) },
                      onQuickCreateClassification = viewModel::quickCreateClassification,
                      onQuickCreateDetail = viewModel::quickCreateDetail,
                      onTogglePinClassification = viewModel::togglePinClassification,
                      onTogglePinDetail = viewModel::togglePinDetail,
                      onTogglePinAccount = viewModel::togglePinAccount,
                      onTogglePinMerchant = viewModel::togglePinMerchant,
                    )

                  MainTab.SALDOS ->
                    BalancesScreen(
                      state = state,
                      onPrevPeriod = viewModel::previousPeriod,
                      onNextPeriod = viewModel::nextPeriod,
                      onToggleAccountFilter = viewModel::toggleAccountFilter,
                      onSearchQueryChange = viewModel::setSearchQuery,
                      onToggleStatsIncome = viewModel::setStatsShowIncome,
                      onUpdateTransaction = viewModel::updateExistingTransaction,
                      onDeleteTransaction = viewModel::deleteExistingTransaction,
                      onNavigateToCapture = { viewModel.selectTab(MainTab.CAPTURAR) },
                    )

                  MainTab.PROGRAMADOS ->
                    ScheduledScreen(
                      state = state,
                      onConfirmPending = viewModel::confirmPendingRule,
                      onSnoozePending = viewModel::snoozePendingRule24h,
                      onConfirmRuleNow = { rule -> viewModel.confirmRuleImmediately(rule) },
                      onToggleRuleActive = viewModel::toggleScheduledRuleActive,
                      onSaveRule = viewModel::saveScheduledRule,
                      onDeleteRule = viewModel::deleteScheduledRule,
                    )

                  MainTab.ADMIN ->
                    AdminScreen(
                      state = state,
                      onSelectSubSection = viewModel::openAdminSection,
                      onOpenScheduledTab = { viewModel.selectTab(MainTab.PROGRAMADOS) },
                      onSaveClassification = viewModel::saveClassification,
                      onDeleteClassification = viewModel::deleteClassification,
                      onSaveDetail = viewModel::saveDetail,
                      onDeleteDetail = viewModel::deleteDetail,
                      onSaveAccount = viewModel::saveAccount,
                      onDeleteAccount = viewModel::deleteAccount,
                      onSaveMerchant = viewModel::saveMerchant,
                      onDeleteMerchant = viewModel::deleteMerchant,
                      onSaveSyncConfig = viewModel::saveSyncConfig,
                      onToggleAutoWifiSync = viewModel::toggleAutoWifiSync,
                      onTriggerManualSync = viewModel::triggerManualWifiSync,
                      onTriggerFullHistorySync = viewModel::triggerFullHistorySync,
                      onLoadSyncBatchPreview = viewModel::loadSyncBatchPreviewJson,
                      onGenerateAdminConfigJson = viewModel::generateAdminConfigJson,
                      onGenerateCapturedDataJson = viewModel::generateCapturedDataJson,
                      onGenerateCsv = viewModel::generateCsvContent,
                      onGenerateJson = viewModel::generateJsonContent,
                      onResetAllData = viewModel::resetAllData,
                      onNotify = viewModel::notifyMessage,
                    )
                }
              }
            }
          }
        }
      }
    }
  }
}
