package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AdminSubSection
import com.example.ui.FinanceViewModel
import com.example.ui.MainTab
import com.example.ui.components.FinanceBottomNavigationBar
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.BalancesScreen
import com.example.ui.screens.CaptureScreen
import com.example.ui.screens.ScheduledScreen
import com.example.ui.theme.MisFinanzasTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MisFinanzasTheme {
        MisFinanzasApp()
      }
    }
  }
}

@Composable
fun MisFinanzasApp(viewModel: FinanceViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(Unit) {
    viewModel.snackbarMessages.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  if (state.currentTab != MainTab.CAPTURAR && state.adminSubSection == AdminSubSection.HUB) {
    BackHandler { viewModel.selectTab(MainTab.CAPTURAR) }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    bottomBar = {
      FinanceBottomNavigationBar(
        currentTab = state.currentTab,
        pendingCount = state.pendingScheduledRules.size,
        onSelectTab = viewModel::selectTab,
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
            onGenerateCsv = viewModel::generateCsvContent,
            onGenerateJson = viewModel::generateJsonContent,
            onResetAllData = viewModel::resetAllData,
            onNotify = viewModel::notifyMessage,
          )
      }
    }
  }
}
