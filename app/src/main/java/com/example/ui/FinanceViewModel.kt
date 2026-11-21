package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.ClassificationEntity
import com.example.data.local.DetailItemEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.MerchantEntity
import com.example.data.local.ScheduledRuleEntity
import com.example.data.local.SyncEntityTypes
import com.example.data.local.SyncQueueItemEntity
import com.example.data.local.SyncSettingsEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TransactionTypeEntity
import com.example.data.local.TransactionTypes
import com.example.data.repository.FinanceRepository
import com.example.data.sync.ConnectivityMonitor
import com.example.data.sync.OkHttpSyncTransport
import com.example.data.sync.SyncManager
import com.example.data.sync.SyncPhase
import com.example.data.sync.SyncRuntimeStatus
import com.example.data.sync.WifiConnectivityObserver
import com.example.data.sync.WifiConnectivityState
import com.example.domain.FiscalPeriod
import com.example.domain.FiscalPeriodCalculator
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab {
  CAPTURAR,
  SALDOS,
  PROGRAMADOS,
  ADMIN,
}

enum class AdminSubSection {
  HUB,
  CLASSIFICATIONS,
  ACCOUNTS,
  MERCHANTS,
  SCHEDULED,
  BACKUP,
}

data class CaptureFormState(
  val typeId: String = TransactionTypes.GASTO_UUID,
  val amountInput: String = "",
  val operationEpochDay: Long = FiscalPeriodCalculator.todayEpochDay(),
  val selectedClassificationId: String? = null,
  val selectedDetailId: String? = null,
  val selectedAccountId: String? = null,
  val merchantName: String = "",
  val note: String = "",
  val showNoteField: Boolean = false,
) {
  val amountCents: Long
    get() = FiscalPeriodCalculator.parseAmountToCents(amountInput)

  val fiscalPeriod: FiscalPeriod
    get() = FiscalPeriodCalculator.computeFiscalPeriod(operationEpochDay)

  val isCutoffApplied: Boolean
    get() = FiscalPeriodCalculator.isCutoffApplied(operationEpochDay)
}

data class TransactionDisplay(
  val entity: FinanceTransactionEntity,
  val classificationName: String,
  val detailName: String,
  val accountName: String,
  val accountShortLabel: String,
)

data class PendingRuleDisplay(
  val rule: ScheduledRuleEntity,
  val classificationName: String,
  val detailName: String,
  val accountShortLabel: String,
  val targetEpochDay: Long,
  val targetFiscalPeriod: FiscalPeriod,
  val scheduleDescription: String,
)

data class CategoryStat(
  val classificationId: String,
  val classificationName: String,
  val totalCents: Long,
  val percentage: Float,
  val monthlyBudgetCents: Long?,
) {
  val budgetRatio: Float?
    get() =
      if (monthlyBudgetCents != null && monthlyBudgetCents > 0L) {
        totalCents.toFloat() / monthlyBudgetCents.toFloat()
      } else null
}

data class FinanceUiState(
  val isLoaded: Boolean = false,
  val currentTab: MainTab = MainTab.CAPTURAR,
  val adminSubSection: AdminSubSection = AdminSubSection.HUB,
  val capture: CaptureFormState = CaptureFormState(),
  val allTypes: List<TransactionTypeEntity> = emptyList(),
  val allClassifications: List<ClassificationEntity> = emptyList(),
  val allDetails: List<DetailItemEntity> = emptyList(),
  val allAccounts: List<AccountEntity> = emptyList(),
  val allMerchants: List<MerchantEntity> = emptyList(),
  val allScheduledRules: List<ScheduledRuleEntity> = emptyList(),
  val allTransactions: List<FinanceTransactionEntity> = emptyList(),
  val selectedPeriod: FiscalPeriod =
    FiscalPeriodCalculator.computeFiscalPeriod(FiscalPeriodCalculator.todayEpochDay()),
  val selectedAccountFilterId: String? = null,
  val searchQuery: String = "",
  val statsShowIncome: Boolean = false,
  val wifiState: WifiConnectivityState = WifiConnectivityState(),
  val syncSettings: SyncSettingsEntity = SyncSettingsEntity(),
  val pendingSyncQueue: List<SyncQueueItemEntity> = emptyList(),
  val syncQueueHistory: List<SyncQueueItemEntity> = emptyList(),
  val syncRuntimeStatus: SyncRuntimeStatus = SyncRuntimeStatus(),
) {
  val pendingSyncCount: Int
    get() {
      val queuedTxIds =
        pendingSyncQueue
          .filter { it.entityType == SyncEntityTypes.TRANSACTION }
          .map { it.entityId }
          .toSet()
      val unqueuedUnsyncedTx =
        allTransactions.count {
          it.syncStatus != SyncStatus.SYNCED && it.id !in queuedTxIds
        }
      return pendingSyncQueue.size + unqueuedUnsyncedTx
    }

  private val classificationUsageStats: Map<String, Pair<Int, Long>>
    get() {
      val map = HashMap<String, Pair<Int, Long>>()
      allTransactions.forEach { tx ->
        val prev = map[tx.classificationId]
        val count = (prev?.first ?: 0) + 1
        val last = maxOf(prev?.second ?: 0L, tx.createdAtMillis)
        map[tx.classificationId] = count to last
      }
      return map
    }

  private val detailUsageStats: Map<String, Pair<Int, Long>>
    get() {
      val map = HashMap<String, Pair<Int, Long>>()
      allTransactions.forEach { tx ->
        val prev = map[tx.detailId]
        val count = (prev?.first ?: 0) + 1
        val last = maxOf(prev?.second ?: 0L, tx.createdAtMillis)
        map[tx.detailId] = count to last
      }
      return map
    }

  private val accountUsageStats: Map<String, Pair<Int, Long>>
    get() {
      val map = HashMap<String, Pair<Int, Long>>()
      allTransactions.forEach { tx ->
        val prev = map[tx.accountId]
        val count = (prev?.first ?: 0) + 1
        val last = maxOf(prev?.second ?: 0L, tx.createdAtMillis)
        map[tx.accountId] = count to last
      }
      return map
    }

  private val merchantUsageStats: Map<String, Pair<Int, Long>>
    get() {
      val map = HashMap<String, Pair<Int, Long>>()
      allTransactions.forEach { tx ->
        val key = tx.merchantName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@forEach
        val prev = map[key]
        val count = (prev?.first ?: 0) + 1
        val last = maxOf(prev?.second ?: 0L, tx.createdAtMillis)
        map[key] = count to last
      }
      return map
    }

  fun classificationUsageCount(id: String): Int = classificationUsageStats[id]?.first ?: 0

  fun detailUsageCount(id: String): Int = detailUsageStats[id]?.first ?: 0

  fun accountUsageCount(id: String): Int = accountUsageStats[id]?.first ?: 0

  fun merchantUsageCount(name: String): Int =
    merchantUsageStats[name.trim().lowercase()]?.first ?: 0

  val activeClassificationsForCapture: List<ClassificationEntity>
    get() {
      val stats = classificationUsageStats
      val targetCode = TransactionTypes.codeFor(capture.typeId)
      return allClassifications
        .filter { it.isActive && TransactionTypes.codeFor(it.typeId) == targetCode }
        .sortedWith(
          compareByDescending<ClassificationEntity> { it.isPinned }
            .thenByDescending { stats[it.id]?.first ?: 0 }
            .thenByDescending { stats[it.id]?.second ?: 0L }
            .thenBy { it.sortOrder }
            .thenBy { it.name }
        )
    }

  val effectiveClassificationId: String?
    get() {
      val list = activeClassificationsForCapture
      return list.firstOrNull { it.id == capture.selectedClassificationId }?.id
        ?: list.firstOrNull()?.id
    }

  val activeDetailsForCapture: List<DetailItemEntity>
    get() {
      val classId = effectiveClassificationId ?: return emptyList()
      val stats = detailUsageStats
      return allDetails
        .filter { it.isActive && it.classificationId == classId }
        .sortedWith(
          compareByDescending<DetailItemEntity> { it.isPinned }
            .thenByDescending { stats[it.id]?.first ?: 0 }
            .thenByDescending { stats[it.id]?.second ?: 0L }
            .thenBy { it.sortOrder }
            .thenBy { it.name }
        )
    }

  val effectiveDetailId: String?
    get() {
      val list = activeDetailsForCapture
      return list.firstOrNull { it.id == capture.selectedDetailId }?.id ?: list.firstOrNull()?.id
    }

  val activeAccounts: List<AccountEntity>
    get() {
      val stats = accountUsageStats
      return allAccounts
        .filter { it.isActive }
        .sortedWith(
          compareByDescending<AccountEntity> { it.isPinned }
            .thenByDescending { stats[it.id]?.first ?: 0 }
            .thenByDescending { stats[it.id]?.second ?: 0L }
            .thenBy { it.sortOrder }
            .thenBy { it.name }
        )
    }

  val effectiveAccountId: String?
    get() {
      val list = activeAccounts
      return list.firstOrNull { it.id == capture.selectedAccountId }?.id ?: list.firstOrNull()?.id
    }

  val activeMerchants: List<MerchantEntity>
    get() {
      val stats = merchantUsageStats
      return allMerchants
        .filter { it.isActive }
        .sortedWith(
          compareByDescending<MerchantEntity> { it.isPinned }
            .thenByDescending { stats[it.name.trim().lowercase()]?.first ?: 0 }
            .thenByDescending { stats[it.name.trim().lowercase()]?.second ?: 0L }
            .thenBy { it.sortOrder }
            .thenBy { it.name }
        )
    }

  val canSaveCapture: Boolean
    get() =
      capture.amountCents > 0L &&
        effectiveClassificationId != null &&
        effectiveDetailId != null &&
        effectiveAccountId != null

  val totalBalanceCents: Long
    get() = activeAccounts.sumOf { it.balanceCents }

  val classificationsById: Map<String, ClassificationEntity>
    get() = allClassifications.associateBy { it.id }

  val detailsById: Map<String, DetailItemEntity>
    get() = allDetails.associateBy { it.id }

  val accountsById: Map<String, AccountEntity>
    get() = allAccounts.associateBy { it.id }

  val periodTransactions: List<TransactionDisplay>
    get() {
      val classMap = classificationsById
      val detMap = detailsById
      val accMap = accountsById
      val query = searchQuery.trim().lowercase()
      return allTransactions
        .filter { it.fiscalPeriodKey == selectedPeriod.key }
        .filter { selectedAccountFilterId == null || it.accountId == selectedAccountFilterId }
        .map { tx ->
          TransactionDisplay(
            entity = tx,
            classificationName = classMap[tx.classificationId]?.name ?: "Sin clasificación",
            detailName = detMap[tx.detailId]?.name ?: "Sin detalle",
            accountName = accMap[tx.accountId]?.name ?: "Cuenta",
            accountShortLabel = accMap[tx.accountId]?.shortLabel ?: "Cuenta",
          )
        }
        .filter { d ->
          if (query.isEmpty()) true
          else {
            d.classificationName.lowercase().contains(query) ||
              d.detailName.lowercase().contains(query) ||
              d.accountName.lowercase().contains(query) ||
              (d.entity.merchantName?.lowercase()?.contains(query) == true) ||
              (d.entity.note?.lowercase()?.contains(query) == true)
          }
        }
    }

  val periodIncomeCents: Long
    get() =
      allTransactions
        .filter { it.fiscalPeriodKey == selectedPeriod.key && TransactionTypes.isIncome(it.typeId) }
        .sumOf { it.amountCents }

  val periodExpenseCents: Long
    get() =
      allTransactions
        .filter { it.fiscalPeriodKey == selectedPeriod.key && TransactionTypes.isExpense(it.typeId) }
        .sumOf { it.amountCents }

  val expenseBreakdown: List<CategoryStat>
    get() = buildBreakdown(TransactionTypes.GASTO_UUID, periodExpenseCents)

  val incomeBreakdown: List<CategoryStat>
    get() = buildBreakdown(TransactionTypes.INGRESO_UUID, periodIncomeCents)

  val mayorRubro: CategoryStat?
    get() = expenseBreakdown.firstOrNull()

  private fun buildBreakdown(typeId: String, totalForType: Long): List<CategoryStat> {
    val classMap = classificationsById
    val targetCode = TransactionTypes.codeFor(typeId)
    val grouped =
      allTransactions
        .filter {
          it.fiscalPeriodKey == selectedPeriod.key && TransactionTypes.codeFor(it.typeId) == targetCode
        }
        .groupBy { it.classificationId }
    return grouped
      .map { (classId, txs) ->
        val sum = txs.sumOf { it.amountCents }
        val pct = if (totalForType > 0L) (sum.toFloat() / totalForType.toFloat()) * 100f else 0f
        val cls = classMap[classId]
        CategoryStat(
          classificationId = classId,
          classificationName = cls?.name ?: "Otro",
          totalCents = sum,
          percentage = pct,
          monthlyBudgetCents = cls?.monthlyBudgetCents,
        )
      }
      .sortedByDescending { it.totalCents }
  }

  val pendingScheduledRules: List<PendingRuleDisplay>
    get() {
      val todayEpoch = FiscalPeriodCalculator.todayEpochDay()
      val todayYmd = FiscalPeriodCalculator.epochDayToYmd(todayEpoch)
      val daysInMonth = FiscalPeriodCalculator.daysInMonth(todayYmd.year, todayYmd.month)
      val classMap = classificationsById
      val detMap = detailsById
      val accMap = accountsById

      return allScheduledRules
        .filter { it.isActive }
        .mapNotNull { rule ->
          val targetDay =
            if (rule.isEndOfMonth) daysInMonth else rule.dayOfMonth.coerceIn(1, daysInMonth)
          val targetEpoch =
            FiscalPeriodCalculator.ymdToEpochDay(todayYmd.year, todayYmd.month, targetDay)
          val targetPeriod = FiscalPeriodCalculator.computeFiscalPeriod(targetEpoch)
          val isConfirmed = rule.lastConfirmedPeriodKey == targetPeriod.key
          val isSnoozed =
            rule.snoozedUntilEpochDay != null && rule.snoozedUntilEpochDay > todayEpoch
          val isWithinWindow =
            targetDay <= todayYmd.day + 2 || rule.isEndOfMonth && todayYmd.day >= daysInMonth - 2

          if (!isConfirmed && !isSnoozed && isWithinWindow) {
            val dayDesc =
              if (rule.isEndOfMonth) "Fin de mes"
              else if (rule.dayOfMonth == 15) "Día 15 quincenal"
              else "Día ${rule.dayOfMonth} mensual"
            PendingRuleDisplay(
              rule = rule,
              classificationName = classMap[rule.classificationId]?.name ?: "",
              detailName = detMap[rule.detailId]?.name ?: "",
              accountShortLabel = accMap[rule.accountId]?.shortLabel ?: "Cuenta",
              targetEpochDay = targetEpoch,
              targetFiscalPeriod = targetPeriod,
              scheduleDescription = dayDesc,
            )
          } else null
        }
    }
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
  private val dao = AppDatabase.getInstance(application).financeDao()
  private val repository = FinanceRepository(dao)
  private val connectivityMonitor: ConnectivityMonitor = WifiConnectivityObserver(application)
  private val syncManager = SyncManager(dao = dao, transport = OkHttpSyncTransport())

  private val currentTab = MutableStateFlow(MainTab.CAPTURAR)
  private val adminSubSection = MutableStateFlow(AdminSubSection.HUB)
  private val captureForm = MutableStateFlow(CaptureFormState())
  private val selectedPeriod =
    MutableStateFlow(
      FiscalPeriodCalculator.computeFiscalPeriod(FiscalPeriodCalculator.todayEpochDay())
    )
  private val selectedAccountFilterId = MutableStateFlow<String?>(null)
  private val searchQuery = MutableStateFlow("")
  private val statsShowIncome = MutableStateFlow(false)

  private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

  private var wasWifiConnected = false
  private var wifiSessionEpoch = 0
  private var lastAutoSyncAttemptKey: String? = null

  private val catalogFlows =
    combine(
      repository.types,
      repository.classifications,
      repository.details,
      repository.accounts,
      combine(repository.merchants, repository.scheduledRules) { m, r -> m to r },
    ) { types, classifications, details, accounts, merchantsAndRules ->
      CatalogSnapshot(
        types = types,
        classifications = classifications,
        details = details,
        accounts = accounts,
        merchants = merchantsAndRules.first,
        rules = merchantsAndRules.second,
      )
    }

  private val uiControlFlows =
    combine(
      currentTab,
      adminSubSection,
      captureForm,
      selectedPeriod,
      combine(selectedAccountFilterId, searchQuery, statsShowIncome) { accId, q, showInc ->
        Triple(accId, q, showInc)
      },
    ) { tab, adminSub, capture, period, filters ->
      UiControlSnapshot(tab, adminSub, capture, period, filters.first, filters.second, filters.third)
    }

  private val syncFlows =
    combine(
      connectivityMonitor.connectivityState,
      repository.syncSettings,
      repository.pendingSyncQueue,
      repository.allSyncQueue,
      syncManager.runtimeStatus,
    ) { wifi, settings, pendingQueue, queueHistory, runtime ->
      SyncSnapshot(
        wifiState = wifi,
        settings = settings ?: SyncSettingsEntity(),
        pendingQueue = pendingQueue,
        queueHistory = queueHistory,
        runtimeStatus = runtime,
      )
    }

  val uiState: StateFlow<FinanceUiState> =
    combine(catalogFlows, repository.transactions, uiControlFlows, syncFlows) {
        catalogs,
        txs,
        controls,
        sync ->
        FinanceUiState(
          isLoaded = true,
          currentTab = controls.tab,
          adminSubSection = controls.adminSub,
          capture = controls.capture,
          allTypes = catalogs.types,
          allClassifications = catalogs.classifications,
          allDetails = catalogs.details,
          allAccounts = catalogs.accounts,
          allMerchants = catalogs.merchants,
          allScheduledRules = catalogs.rules,
          allTransactions = txs,
          selectedPeriod = controls.period,
          selectedAccountFilterId = controls.accountFilterId,
          searchQuery = controls.searchQuery,
          statsShowIncome = controls.statsShowIncome,
          wifiState = sync.wifiState,
          syncSettings = sync.settings,
          pendingSyncQueue = sync.pendingQueue,
          syncQueueHistory = sync.queueHistory,
          syncRuntimeStatus = sync.runtimeStatus,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FinanceUiState(),
      )

  init {
    viewModelScope.launch { repository.ensureSeeded() }
    observeWifiForAutoSync()
  }

  private fun observeWifiForAutoSync() {
    viewModelScope.launch {
      combine(
          connectivityMonitor.connectivityState,
          repository.syncSettings,
          repository.pendingSyncQueue,
          repository.transactions,
        ) { wifi, settings, pendingQueue, txs ->
          val effectiveSettings = settings ?: SyncSettingsEntity()
          val queuedTxIds =
            pendingQueue
              .filter { it.entityType == SyncEntityTypes.TRANSACTION }
              .map { it.entityId }
              .toSet()
          val unqueuedUnsyncedTx =
            txs.count { it.syncStatus != SyncStatus.SYNCED && it.id !in queuedTxIds }
          val pendingCount = pendingQueue.size + unqueuedUnsyncedTx
          val maxQueueId = pendingQueue.maxOfOrNull { it.id } ?: 0L
          AutoSyncTriggerState(
            isWifiConnected = wifi.isWifiConnected,
            autoSyncOnWifi = effectiveSettings.autoSyncOnWifi,
            apiEndpointUrl = effectiveSettings.apiEndpointUrl,
            pendingCount = pendingCount,
            maxQueueId = maxQueueId,
          )
        }
        .distinctUntilChanged()
        .collect { trigger ->
          if (trigger.isWifiConnected && !wasWifiConnected) {
            wifiSessionEpoch++
          }
          wasWifiConnected = trigger.isWifiConnected

          syncManager.refreshIdleStatus(
            isWifiConnected = trigger.isWifiConnected,
            pendingCount = trigger.pendingCount,
            apiUrlConfigured = trigger.apiEndpointUrl.isNotBlank(),
          )

          if (
            trigger.isWifiConnected &&
              trigger.autoSyncOnWifi &&
              trigger.pendingCount > 0 &&
              trigger.apiEndpointUrl.isNotBlank()
          ) {
            val attemptKey =
              "${wifiSessionEpoch}_${trigger.maxQueueId}_${trigger.pendingCount}_${trigger.apiEndpointUrl}"
            if (attemptKey != lastAutoSyncAttemptKey) {
              lastAutoSyncAttemptKey = attemptKey
              val result = syncManager.syncPendingIfReady(isWifiConnected = true)
              if (result.phase == SyncPhase.SYNCED && result.lastSentCount > 0) {
                _snackbarMessages.tryEmit(
                  "Sincronización Wi-Fi automática: ${result.lastSentCount} registros enviados"
                )
              }
            }
          }
        }
    }
  }

  override fun onCleared() {
    connectivityMonitor.stop()
    super.onCleared()
  }

  // --- Navigation ---
  fun selectTab(tab: MainTab) {
    currentTab.value = tab
    if (tab != MainTab.ADMIN) {
      adminSubSection.value = AdminSubSection.HUB
    }
  }

  fun openAdminSection(section: AdminSubSection) {
    currentTab.value = MainTab.ADMIN
    adminSubSection.value = section
  }

  // --- Capture Actions ---
  fun selectType(typeId: String) {
    val resolvedTypeId = TransactionTypes.uuidForCode(TransactionTypes.codeFor(typeId))
    val targetCode = TransactionTypes.codeFor(resolvedTypeId)
    captureForm.update { form ->
      val firstClass =
        uiState.value.allClassifications
          .firstOrNull { it.isActive && TransactionTypes.codeFor(it.typeId) == targetCode }
          ?.id
      val firstDetail =
        firstClass?.let { cid ->
          uiState.value.allDetails.firstOrNull { it.isActive && it.classificationId == cid }?.id
        }
      form.copy(
        typeId = resolvedTypeId,
        selectedClassificationId = firstClass,
        selectedDetailId = firstDetail,
      )
    }
  }

  fun selectClassification(classificationId: String) {
    val firstDetail =
      uiState.value.allDetails
        .firstOrNull { it.isActive && it.classificationId == classificationId }
        ?.id
    captureForm.update {
      it.copy(selectedClassificationId = classificationId, selectedDetailId = firstDetail)
    }
  }

  fun selectDetail(detailId: String) {
    captureForm.update { it.copy(selectedDetailId = detailId) }
  }

  fun selectAccount(accountId: String) {
    captureForm.update { it.copy(selectedAccountId = accountId) }
  }

  fun setOperationDate(epochDay: Long) {
    captureForm.update { it.copy(operationEpochDay = epochDay) }
  }

  fun setMerchantName(name: String) {
    captureForm.update { it.copy(merchantName = name) }
  }

  fun toggleNoteField(show: Boolean) {
    captureForm.update { it.copy(showNoteField = show) }
  }

  fun setNote(note: String) {
    captureForm.update { it.copy(note = note) }
  }

  fun onKeypadDigit(digit: Char) {
    captureForm.update { form ->
      val cur = form.amountInput
      val dotIdx = cur.indexOf('.')
      if (dotIdx >= 0 && cur.length - dotIdx - 1 >= 2) return@update form
      if (cur.length >= 10) return@update form
      val next =
        when {
          cur == "0" && digit == '0' -> "0"
          cur == "0" && digit != '0' -> digit.toString()
          else -> cur + digit
        }
      form.copy(amountInput = next)
    }
  }

  fun onKeypadDecimal() {
    captureForm.update { form ->
      if (form.amountInput.contains('.')) form
      else if (form.amountInput.isEmpty()) form.copy(amountInput = "0.")
      else form.copy(amountInput = form.amountInput + ".")
    }
  }

  fun onKeypadBackspace() {
    captureForm.update { form ->
      if (form.amountInput.isEmpty()) form
      else form.copy(amountInput = form.amountInput.dropLast(1))
    }
  }

  fun clearAmount() {
    captureForm.update { it.copy(amountInput = "") }
  }

  fun applyIncomeShortcut(
    amountText: String,
    classificationName: String,
    detailName: String,
    accountKind: String?,
  ) {
    val state = uiState.value
    val cls =
      state.allClassifications.firstOrNull {
        it.isActive &&
          TransactionTypes.isIncome(it.typeId) &&
          it.name.equals(classificationName, ignoreCase = true)
      }
    val det =
      cls?.let { c ->
        state.allDetails.firstOrNull {
          it.isActive && it.classificationId == c.id && it.name.equals(detailName, ignoreCase = true)
        }
      }
    val acc =
      accountKind?.let { kind ->
        state.activeAccounts.firstOrNull { it.accountKind == kind }
      }
    captureForm.update { form ->
      form.copy(
        typeId = TransactionTypes.INGRESO_UUID,
        amountInput = amountText,
        selectedClassificationId = cls?.id ?: form.selectedClassificationId,
        selectedDetailId = det?.id ?: form.selectedDetailId,
        selectedAccountId = acc?.id ?: form.selectedAccountId,
      )
    }
  }

  fun saveCapturedTransaction() {
    val state = uiState.value
    if (!state.canSaveCapture) return
    val classId = state.effectiveClassificationId ?: return
    val detailId = state.effectiveDetailId ?: return
    val accountId = state.effectiveAccountId ?: return
    val amountCents = state.capture.amountCents
    val fiscalPeriod = state.capture.fiscalPeriod
    val merchant = state.capture.merchantName.trim().ifEmpty { null }
    val note = state.capture.note.trim().ifEmpty { null }
    val now = System.currentTimeMillis()
    val resolvedTypeId = TransactionTypes.uuidForCode(TransactionTypes.codeFor(state.capture.typeId))

    val tx =
      FinanceTransactionEntity(
        id = UUID.randomUUID().toString(),
        createdAtMillis = now,
        operationEpochDay = state.capture.operationEpochDay,
        fiscalPeriodKey = fiscalPeriod.key,
        fiscalPeriodLabel = fiscalPeriod.label,
        typeId = resolvedTypeId,
        classificationId = classId,
        detailId = detailId,
        accountId = accountId,
        merchantName = merchant,
        amountCents = amountCents,
        note = note,
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
      )

    viewModelScope.launch {
      if (merchant != null) {
        repository.ensureMerchantExists(merchant)
      }
      repository.recordTransaction(tx)
      val prefix = if (TransactionTypes.isExpense(tx.typeId)) "Gasto" else "Ingreso"
      val formatted = FiscalPeriodCalculator.formatMxn(amountCents)
      _snackbarMessages.tryEmit("$prefix guardado ($formatted • ${fiscalPeriod.label})")
      captureForm.update {
        it.copy(amountInput = "", merchantName = "", note = "", showNoteField = false)
      }
      selectedPeriod.value = fiscalPeriod
    }
  }

  // --- Scheduled Rules Actions ---
  fun confirmPendingRule(pending: PendingRuleDisplay, overrideAmountCents: Long? = null) {
    val amount = overrideAmountCents ?: pending.rule.amountCents
    if (amount <= 0L) return
    val now = System.currentTimeMillis()
    val tx =
      FinanceTransactionEntity(
        id = UUID.randomUUID().toString(),
        createdAtMillis = now,
        operationEpochDay = pending.targetEpochDay,
        fiscalPeriodKey = pending.targetFiscalPeriod.key,
        fiscalPeriodLabel = pending.targetFiscalPeriod.label,
        typeId = pending.rule.typeId,
        classificationId = pending.rule.classificationId,
        detailId = pending.rule.detailId,
        accountId = pending.rule.accountId,
        merchantName = pending.rule.merchantName ?: pending.rule.title,
        amountCents = amount,
        note = "Confirmado de programado: ${pending.rule.title}",
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
      )
    viewModelScope.launch {
      repository.confirmScheduledRule(pending.rule, tx, pending.targetFiscalPeriod.key)
      _snackbarMessages.tryEmit(
        "Confirmado: ${pending.rule.title} (${FiscalPeriodCalculator.formatMxn(amount)})"
      )
      selectedPeriod.value = pending.targetFiscalPeriod
    }
  }

  fun confirmRuleImmediately(rule: ScheduledRuleEntity, overrideAmountCents: Long? = null) {
    val amount = overrideAmountCents ?: rule.amountCents
    if (amount <= 0L) return
    val today = FiscalPeriodCalculator.todayEpochDay()
    val period = FiscalPeriodCalculator.computeFiscalPeriod(today)
    val now = System.currentTimeMillis()
    val tx =
      FinanceTransactionEntity(
        id = UUID.randomUUID().toString(),
        createdAtMillis = now,
        operationEpochDay = today,
        fiscalPeriodKey = period.key,
        fiscalPeriodLabel = period.label,
        typeId = rule.typeId,
        classificationId = rule.classificationId,
        detailId = rule.detailId,
        accountId = rule.accountId,
        merchantName = rule.merchantName ?: rule.title,
        amountCents = amount,
        note = "Confirmado de programado: ${rule.title}",
        updatedAtMillis = now,
        syncStatus = SyncStatus.PENDING,
        syncedAtMillis = null,
      )
    viewModelScope.launch {
      repository.confirmScheduledRule(rule, tx, period.key)
      _snackbarMessages.tryEmit(
        "Confirmado: ${rule.title} (${FiscalPeriodCalculator.formatMxn(amount)})"
      )
    }
  }

  fun snoozePendingRule24h(rule: ScheduledRuleEntity) {
    val tomorrow = FiscalPeriodCalculator.todayEpochDay() + 1L
    viewModelScope.launch {
      repository.snoozeScheduledRule(rule, tomorrow)
      _snackbarMessages.tryEmit("${rule.title} pospuesto 24h")
    }
  }

  fun saveScheduledRule(rule: ScheduledRuleEntity) {
    viewModelScope.launch {
      repository.saveScheduledRule(rule)
      _snackbarMessages.tryEmit("Regla guardada: ${rule.title}")
    }
  }

  fun toggleScheduledRuleActive(rule: ScheduledRuleEntity) {
    viewModelScope.launch { repository.saveScheduledRule(rule.copy(isActive = !rule.isActive)) }
  }

  fun deleteScheduledRule(rule: ScheduledRuleEntity) {
    viewModelScope.launch {
      repository.deleteScheduledRule(rule)
      _snackbarMessages.tryEmit("Regla eliminada")
    }
  }

  // --- Balances & Transactions Actions ---
  fun previousPeriod() {
    selectedPeriod.update { it.previous() }
  }

  fun nextPeriod() {
    selectedPeriod.update { it.next() }
  }

  fun toggleAccountFilter(accountId: String) {
    selectedAccountFilterId.update { cur -> if (cur == accountId) null else accountId }
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun setStatsShowIncome(showIncome: Boolean) {
    statsShowIncome.value = showIncome
  }

  fun updateExistingTransaction(
    oldTx: FinanceTransactionEntity,
    newTx: FinanceTransactionEntity,
  ) {
    viewModelScope.launch {
      if (!newTx.merchantName.isNullOrBlank()) {
        repository.ensureMerchantExists(newTx.merchantName)
      }
      repository.updateTransaction(oldTx, newTx)
      _snackbarMessages.tryEmit("Movimiento actualizado")
    }
  }

  fun deleteExistingTransaction(tx: FinanceTransactionEntity) {
    viewModelScope.launch {
      repository.deleteTransaction(tx)
      _snackbarMessages.tryEmit("Movimiento eliminado y saldo ajustado")
    }
  }

  // --- Admin Catalog CRUD ---
  fun saveClassification(item: ClassificationEntity) {
    viewModelScope.launch {
      repository.saveClassification(item)
      _snackbarMessages.tryEmit("Clasificación guardada")
    }
  }

  fun deleteClassification(item: ClassificationEntity) {
    viewModelScope.launch {
      repository.deleteClassification(item)
      _snackbarMessages.tryEmit("Clasificación eliminada")
    }
  }

  fun saveDetail(item: DetailItemEntity) {
    viewModelScope.launch {
      repository.saveDetail(item)
      _snackbarMessages.tryEmit("Detalle guardado")
    }
  }

  fun deleteDetail(item: DetailItemEntity) {
    viewModelScope.launch {
      repository.deleteDetail(item)
      _snackbarMessages.tryEmit("Detalle eliminado")
    }
  }

  fun saveAccount(account: AccountEntity) {
    viewModelScope.launch {
      repository.saveAccount(account)
      _snackbarMessages.tryEmit("Cuenta guardada: ${account.shortLabel}")
    }
  }

  fun deleteAccount(account: AccountEntity) {
    viewModelScope.launch {
      repository.deleteAccount(account)
      _snackbarMessages.tryEmit("Cuenta eliminada")
    }
  }

  fun saveMerchant(merchant: MerchantEntity) {
    viewModelScope.launch {
      repository.saveMerchant(merchant)
      _snackbarMessages.tryEmit("Comercio guardado: ${merchant.name}")
    }
  }

  fun deleteMerchant(merchant: MerchantEntity) {
    viewModelScope.launch {
      repository.deleteMerchant(merchant)
      _snackbarMessages.tryEmit("Comercio eliminado")
    }
  }

  // --- Inline Quick-Create & Pin Favorites (v1.1 Ergonomics) ---
  fun quickCreateClassification(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val state = uiState.value
    val typeId = TransactionTypes.uuidForCode(TransactionTypes.codeFor(state.capture.typeId))
    val targetCode = TransactionTypes.codeFor(typeId)
    val existing =
      state.allClassifications.firstOrNull {
        TransactionTypes.codeFor(it.typeId) == targetCode && it.name.equals(trimmed, ignoreCase = true)
      }
    viewModelScope.launch {
      if (existing != null) {
        val reactivated = if (!existing.isActive) existing.copy(isActive = true) else existing
        if (reactivated != existing) repository.saveClassification(reactivated)
        selectClassification(reactivated.id)
        _snackbarMessages.tryEmit("Clasificación \"${reactivated.name}\" seleccionada")
        return@launch
      }
      val nextOrder = (state.allClassifications.maxOfOrNull { it.sortOrder } ?: 0) + 1
      val newClassId =
        repository.saveClassification(
          ClassificationEntity(
            id = "",
            typeId = typeId,
            name = trimmed,
            sortOrder = nextOrder,
            isActive = true,
            isPinned = true,
            isDefault = false,
          )
        )
      val newDetailId =
        repository.saveDetail(
          DetailItemEntity(
            id = "",
            classificationId = newClassId,
            name = "General",
            sortOrder = 1,
            isActive = true,
            isDefault = false,
          )
        )
      captureForm.update {
        it.copy(selectedClassificationId = newClassId, selectedDetailId = newDetailId)
      }
      _snackbarMessages.tryEmit("Clasificación \"$trimmed\" creada")
    }
  }

  fun quickCreateDetail(name: String) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val state = uiState.value
    val classId = state.effectiveClassificationId ?: return
    val existing =
      state.allDetails.firstOrNull {
        it.classificationId == classId && it.name.equals(trimmed, ignoreCase = true)
      }
    viewModelScope.launch {
      if (existing != null) {
        val reactivated = if (!existing.isActive) existing.copy(isActive = true) else existing
        if (reactivated != existing) repository.saveDetail(reactivated)
        selectDetail(reactivated.id)
        _snackbarMessages.tryEmit("Detalle \"${reactivated.name}\" seleccionado")
        return@launch
      }
      val nextOrder =
        (state.allDetails.filter { it.classificationId == classId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
      val newDetailId =
        repository.saveDetail(
          DetailItemEntity(
            id = "",
            classificationId = classId,
            name = trimmed,
            sortOrder = nextOrder,
            isActive = true,
            isPinned = true,
            isDefault = false,
          )
        )
      captureForm.update { it.copy(selectedDetailId = newDetailId) }
      _snackbarMessages.tryEmit("Detalle \"$trimmed\" creado")
    }
  }

  fun togglePinClassification(classificationId: String) {
    val item = uiState.value.allClassifications.firstOrNull { it.id == classificationId } ?: return
    viewModelScope.launch {
      val updated = item.copy(isPinned = !item.isPinned)
      repository.saveClassification(updated)
      _snackbarMessages.tryEmit(
        if (updated.isPinned) "${updated.name} fijado en accesos rápidos"
        else "${updated.name} desfijado"
      )
    }
  }

  fun togglePinDetail(detailId: String) {
    val item = uiState.value.allDetails.firstOrNull { it.id == detailId } ?: return
    viewModelScope.launch {
      val updated = item.copy(isPinned = !item.isPinned)
      repository.saveDetail(updated)
      _snackbarMessages.tryEmit(
        if (updated.isPinned) "${updated.name} fijado en accesos rápidos"
        else "${updated.name} desfijado"
      )
    }
  }

  fun togglePinAccount(accountId: String) {
    val item = uiState.value.allAccounts.firstOrNull { it.id == accountId } ?: return
    viewModelScope.launch {
      val updated = item.copy(isPinned = !item.isPinned)
      repository.saveAccount(updated)
      _snackbarMessages.tryEmit(
        if (updated.isPinned) "${updated.shortLabel} fijada en accesos rápidos"
        else "${updated.shortLabel} desfijada"
      )
    }
  }

  fun togglePinMerchant(merchantId: String) {
    val item = uiState.value.allMerchants.firstOrNull { it.id == merchantId } ?: return
    viewModelScope.launch {
      val updated = item.copy(isPinned = !item.isPinned)
      repository.saveMerchant(updated)
      _snackbarMessages.tryEmit(
        if (updated.isPinned) "${updated.name} fijado en favoritos"
        else "${updated.name} desfijado"
      )
    }
  }

  // --- Sync & API Settings ---
  fun saveSyncConfig(
    endpointUrl: String,
    authToken: String,
    autoSyncOnWifi: Boolean,
    deviceLabel: String,
  ) {
    viewModelScope.launch {
      val current = uiState.value.syncSettings
      val updated =
        current.copy(
          apiEndpointUrl = endpointUrl.trim(),
          apiAuthToken = authToken.trim(),
          autoSyncOnWifi = autoSyncOnWifi,
          deviceLabel = deviceLabel.trim().ifEmpty { "Android" },
        )
      repository.saveSyncSettings(updated)
      _snackbarMessages.tryEmit("Configuración de sincronización guardada")
    }
  }

  fun triggerManualWifiSync() {
    connectivityMonitor.refreshNow()
    viewModelScope.launch {
      val wifiConnected = connectivityMonitor.connectivityState.value.isWifiConnected
      val status = syncManager.syncPendingIfReady(isWifiConnected = wifiConnected)
      _snackbarMessages.tryEmit(status.message)
    }
  }

  fun loadSyncBatchPreviewJson(onLoaded: (String) -> Unit) {
    viewModelScope.launch {
      val payload = syncManager.previewPendingBatchPayload()
      onLoaded(payload.jsonString)
    }
  }

  // --- Manual Exports (JSON Admin Config, JSON Captured Data, Full JSON, CSV) & Reset ---
  fun generateAdminConfigJson(): String {
    val state = uiState.value
    return repository.buildAdminConfigJson(
      types = state.allTypes,
      classifications = state.allClassifications,
      details = state.allDetails,
      accounts = state.allAccounts,
      merchants = state.allMerchants,
      scheduledRules = state.allScheduledRules,
    )
  }

  fun generateCapturedDataJson(): String {
    val state = uiState.value
    return repository.buildCapturedDataJson(
      transactions = state.allTransactions,
      classificationsById = state.classificationsById,
      detailsById = state.detailsById,
      accountsById = state.accountsById,
    )
  }

  fun generateCsvContent(): String {
    val state = uiState.value
    return repository.buildCsvBackup(
      transactions = state.allTransactions,
      classificationsById = state.classificationsById,
      detailsById = state.detailsById,
      accountsById = state.accountsById,
    )
  }

  fun generateJsonContent(): String {
    val state = uiState.value
    return repository.buildJsonBackup(
      transactions = state.allTransactions,
      accounts = state.allAccounts,
      classifications = state.allClassifications,
      details = state.allDetails,
      merchants = state.allMerchants,
      scheduledRules = state.allScheduledRules,
    )
  }

  fun resetAllData() {
    viewModelScope.launch {
      repository.resetDatabaseToSeed()
      captureForm.value = CaptureFormState()
      selectedAccountFilterId.value = null
      searchQuery.value = ""
      _snackbarMessages.tryEmit("Catálogos restablecidos y movimientos limpiados")
    }
  }

  fun notifyMessage(msg: String) {
    _snackbarMessages.tryEmit(msg)
  }
}

private data class CatalogSnapshot(
  val types: List<TransactionTypeEntity>,
  val classifications: List<ClassificationEntity>,
  val details: List<DetailItemEntity>,
  val accounts: List<AccountEntity>,
  val merchants: List<MerchantEntity>,
  val rules: List<ScheduledRuleEntity>,
)

private data class UiControlSnapshot(
  val tab: MainTab,
  val adminSub: AdminSubSection,
  val capture: CaptureFormState,
  val period: FiscalPeriod,
  val accountFilterId: String?,
  val searchQuery: String,
  val statsShowIncome: Boolean,
)

private data class SyncSnapshot(
  val wifiState: WifiConnectivityState,
  val settings: SyncSettingsEntity,
  val pendingQueue: List<SyncQueueItemEntity>,
  val queueHistory: List<SyncQueueItemEntity>,
  val runtimeStatus: SyncRuntimeStatus,
)

private data class AutoSyncTriggerState(
  val isWifiConnected: Boolean,
  val autoSyncOnWifi: Boolean,
  val apiEndpointUrl: String,
  val pendingCount: Int,
  val maxQueueId: Long,
)
