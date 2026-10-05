package aktual.budget.budgeting.vm

import aktual.budget.BudgetSyncController
import aktual.budget.SyncStateHolder
import aktual.budget.budgeting.domain.BudgetGroup
import aktual.budget.budgeting.domain.BudgetMonth
import aktual.budget.budgeting.domain.BudgetOverview
import aktual.budget.budgeting.domain.BudgetOverviewLoader
import aktual.budget.budgeting.domain.BudgetWriter
import aktual.budget.budgeting.domain.CategoryMonth
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.budget.model.SyncState
import aktual.core.Calendar
import aktual.di.BudgetScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.launchMolecule
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit.Companion.MONTH
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import logcat.logcat

@Stable
@ViewModelKey
@ContributesIntoMap(BudgetScope::class)
class BudgetViewModel(
  private val loader: BudgetOverviewLoader,
  private val writer: BudgetWriter,
  private val syncController: BudgetSyncController,
  private val calendar: Calendar,
  syncStateHolder: SyncStateHolder,
) : ViewModel() {
  private val today = calendar.today().yearMonth
  private val mutableMonth = MutableStateFlow(today)
  private val mutableCollapsed = MutableStateFlow(emptySet<CategoryGroupId>())
  private val mutableShowHidden = MutableStateFlow(false)

  private val mutableEvents =
    MutableSharedFlow<BudgetEvent>(
      replay = 0,
      extraBufferCapacity = 1,
      onBufferOverflow = DROP_OLDEST,
    )
  val events: SharedFlow<BudgetEvent> = mutableEvents.asSharedFlow()

  val state: StateFlow<BudgetState> =
    viewModelScope.launchMolecule(Immediate) {
      val month by mutableMonth.collectAsState()
      val collapsed by mutableCollapsed.collectAsState()
      val showHidden by mutableShowHidden.collectAsState()
      val syncState by syncStateHolder.collectAsState()

      // Keyed on the month, so it starts from loading rather than showing the last month's figures
      // under the new month's title
      val overviewFlow = remember(month) { observeMonth(month) }
      val overview by overviewFlow.collectAsState(initial = NOT_LOADED)

      BudgetState(
        month = month,
        isCurrentMonth = month == today,
        showHidden = showHidden,
        isRefreshing = syncState == SyncState.Syncing,
        content =
          when (val loaded = overview) {
            null -> BudgetContent.Loading
            else -> loaded.first.toContent(loaded.second, collapsed, showHidden)
          },
      )
    }

  // Last month's budgets come too, as a starting point when editing
  private fun observeMonth(month: YearMonth): Flow<Pair<BudgetOverview, BudgetOverview>?> =
    combine(loader.observe(month), loader.observe(month.minus(1, MONTH))) { current, previous ->
      current to previous
    }

  fun previousMonth() = mutableMonth.update { it.minus(1, MONTH) }

  fun nextMonth() = mutableMonth.update { it.plus(1, MONTH) }

  fun thisMonth() = mutableMonth.update { calendar.today().yearMonth }

  fun toggleGroup(id: CategoryGroupId) = mutableCollapsed.update {
    if (id in it) it - id else it + id
  }

  fun setShowHidden(show: Boolean) = mutableShowHidden.update { show }

  fun refresh() = syncController.schedule()

  fun setBudget(category: CategoryId, amount: Amount) {
    val month = mutableMonth.value
    viewModelScope.launch {
      try {
        writer.setBudget(month, category, amount)
      } catch (e: CancellationException) {
        throw e
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logcat.w(e) { "Failed setting budget for $category in $month" }
        mutableEvents.emit(BudgetEvent.SaveFailed)
      }
    }
  }
}

internal fun BudgetOverview.toContent(
  previous: BudgetOverview,
  collapsed: Set<CategoryGroupId>,
  showHidden: Boolean,
): BudgetContent {
  if (groups.all { it.categories.isEmpty() }) return BudgetContent.Empty
  val lastMonth = previous.month.categories.associate { it.id to it.budgeted }
  val visible =
    groups
      .filter { showHidden || !it.isHidden }
      .map { it.toState(lastMonth, isCollapsed = it.id in collapsed, showHidden) }
  return BudgetContent.Loaded(
    summary = month.toSummary(),
    expenseGroups = visible.filterNot { it.isIncome }.toImmutableList(),
    incomeGroups = visible.filter { it.isIncome }.toImmutableList(),
  )
}

private fun BudgetMonth.toSummary(): BudgetSummary =
  when (this) {
    is BudgetMonth.Envelope ->
      BudgetSummary.Envelope(
        toBudget = toBudget,
        availableFunds = availableFunds,
        overspentLastMonth = -lastMonthOverspent,
        budgeted = budgeted,
        forNextMonth = buffered,
      )

    is BudgetMonth.Tracking ->
      BudgetSummary.Tracking(
        expectedIncome = incomeBudgeted,
        received = income,
        budgeted = budgeted,
        spent = -spent,
      )
  }

private fun BudgetGroup.toState(
  lastMonth: Map<CategoryId, Amount>,
  isCollapsed: Boolean,
  showHidden: Boolean,
) =
  GroupState(
    id = id,
    name = name,
    isIncome = isIncome,
    isHidden = isHidden,
    isCollapsed = isCollapsed,
    budgeted = budgeted,
    spent = if (isIncome) spent else -spent,
    balance = balance,
    categories =
      categories
        .filter { showHidden || !it.isHidden }
        .map { it.toState(lastMonth[it.id] ?: Amount.Zero) }
        .toImmutableList(),
  )

private fun CategoryMonth.toState(lastMonthBudgeted: Amount) =
  CategoryState(
    id = id,
    name = name,
    isIncome = isIncome,
    isHidden = isHidden,
    budgeted = budgeted,
    spent = if (isIncome) spent else -spent,
    balance = balance,
    lastMonthBudgeted = lastMonthBudgeted,
  )

private val NOT_LOADED: Pair<BudgetOverview, BudgetOverview>? = null
