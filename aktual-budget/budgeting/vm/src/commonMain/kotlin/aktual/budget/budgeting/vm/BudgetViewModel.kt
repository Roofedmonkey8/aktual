package aktual.budget.budgeting.vm

import aktual.budget.BudgetSyncController
import aktual.budget.SyncStateHolder
import aktual.budget.budgeting.domain.BudgetGroup
import aktual.budget.budgeting.domain.BudgetMonth
import aktual.budget.budgeting.domain.BudgetOverview
import aktual.budget.budgeting.domain.BudgetOverviewLoader
import aktual.budget.budgeting.domain.BudgetWriter
import aktual.budget.budgeting.domain.CategoryMonth
import aktual.budget.budgeting.domain.Movement
import aktual.budget.budgeting.domain.lastBudgetMonth
import aktual.budget.budgeting.domain.monthNoteId
import aktual.budget.db.dao.NotesDao
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
  private val notesDao: NotesDao,
  private val syncController: BudgetSyncController,
  private val calendar: Calendar,
  syncStateHolder: SyncStateHolder,
) : ViewModel() {
  private val today = calendar.today().yearMonth
  private val mutableMonth = MutableStateFlow(today)
  private val mutableCollapsed = MutableStateFlow(emptySet<CategoryGroupId>())
  private val mutableShowHidden = MutableStateFlow(false)

  // The month on screen, for the actions that work from what's shown
  private var loaded: LoadedMonth? = null

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
        monthNote = overview?.notes?.get(monthNoteId(month)),
        content =
          when (val loaded = overview) {
            null -> BudgetContent.Loading
            else -> loaded.toContent(collapsed, showHidden)
          },
      )
    }

  // Last month's budgets come too, as a starting point when editing, and the notes on the month and
  // its categories
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeMonth(month: YearMonth): Flow<LoadedMonth?> =
    combine(loader.observe(month), loader.observe(month.minus(1, MONTH))) { current, previous ->
        current to previous
      }
      .flatMapLatest { (current, previous) ->
        val ids = current.month.categories.map { it.id.value } + monthNoteId(month)
        notesDao.observe(ids).map { notes -> LoadedMonth(current, previous, notes) }
      }
      .onEach { loaded = it }

  fun previousMonth() = mutableMonth.update { it.minus(1, MONTH) }

  fun nextMonth() = mutableMonth.update { it.plus(1, MONTH) }

  fun thisMonth() = mutableMonth.update { calendar.today().yearMonth }

  fun toggleGroup(id: CategoryGroupId) = mutableCollapsed.update {
    if (id in it) it - id else it + id
  }

  fun setShowHidden(show: Boolean) = mutableShowHidden.update { show }

  fun refresh() = syncController.schedule()

  fun setBudget(category: CategoryId, amount: Amount) = write { month ->
    writer.setBudget(month, category, amount)
  }

  /**
   * Moves money between two categories, or to or from To Budget when one side is null, like
   * transferCategory() and transferAvailable(). Taking from To Budget never takes more than it has.
   */
  fun moveMoney(from: CategoryId?, to: CategoryId?, amount: Amount) = write { month ->
    val current = loaded?.current ?: return@write
    val move = current.month.move(from, to, amount) ?: return@write
    writer.setBudgets(month, move.budgets, movement(current, move.amount, from, to))
  }

  /**
   * coverOverspending(): takes what [category] is overspent by from [from] (or To Budget), but
   * never more than [from] has left
   */
  fun coverOverspending(category: CategoryId, from: CategoryId?) {
    val amount = loaded?.current?.month?.coverAmount(category, from) ?: return
    moveMoney(from, category, amount)
  }

  /** Rolls a category's overspending (or for tracking budgets, its balance) into later months */
  fun setRollover(category: CategoryId, rollover: Boolean) = write { month ->
    writer.setCarryover(month, lastBudgetMonth(today), category, rollover)
  }

  /** copyPreviousMonth(): last month's budgets, leaving out hidden categories */
  fun copyLastMonth() = write { month ->
    val (current, previous) = loaded ?: return@write
    val isEnvelope = current.month is BudgetMonth.Envelope
    val hiddenGroups = current.groups.filter { it.isHidden }.map { it.id }.toSet()
    val amounts =
      previous.month.categories
        .filter { !it.isHidden && it.group !in hiddenGroups && !(isEnvelope && it.isIncome) }
        .associate { it.id to it.budgeted }
    writer.setBudgets(month, amounts)
  }

  /** setZero() */
  fun setAllToZero() = write { month ->
    val current = loaded?.current ?: return@write
    val isEnvelope = current.month is BudgetMonth.Envelope
    val amounts =
      current.month.categories
        .filter { !(isEnvelope && it.isIncome) }
        .associate { it.id to Amount.Zero }
    writer.setBudgets(month, amounts)
  }

  /** holdForNextMonth(): holds up to what's left to budget, adding to what's held already */
  fun holdForNextMonth(amount: Amount) = write { month ->
    val envelope = loaded?.current?.month as? BudgetMonth.Envelope ?: return@write
    envelope.heldAfter(amount)?.let { writer.setHold(month, it) }
  }

  fun resetHold() = write { month -> writer.setHold(month, Amount.Zero) }

  /** Notes on a category, or on the month when [category] is null */
  fun setNote(category: CategoryId?, note: String) = write { month ->
    writer.setNote(category?.value ?: monthNoteId(month), note)
  }

  private fun movement(
    current: BudgetOverview,
    amount: Amount,
    from: CategoryId?,
    to: CategoryId?,
  ): Movement {
    val names = current.month.categories.associate { it.id to it.name }
    return Movement(
      amount = amount,
      from = from?.let(names::get) ?: TO_BUDGET,
      to = to?.let(names::get) ?: TO_BUDGET,
      date = calendar.today(),
    )
  }

  private fun write(block: suspend (YearMonth) -> Unit) {
    val month = mutableMonth.value
    viewModelScope.launch {
      try {
        block(month)
      } catch (e: CancellationException) {
        throw e
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logcat.w(e) { "Failed changing the budget for $month" }
        mutableEvents.emit(BudgetEvent.SaveFailed)
      }
    }
  }
}

internal data class LoadedMonth(
  val current: BudgetOverview,
  val previous: BudgetOverview,
  val notes: Map<String, String> = emptyMap(),
)

// Upstream writes this in English whatever the app's language, so notes match across apps
private const val TO_BUDGET = "To Budget"

internal fun LoadedMonth.toContent(
  collapsed: Set<CategoryGroupId>,
  showHidden: Boolean,
): BudgetContent {
  if (current.groups.all { it.categories.isEmpty() }) return BudgetContent.Empty
  val lastMonth = previous.month.categories.associate { it.id to it.budgeted }
  val visible =
    current.groups
      .filter { showHidden || !it.isHidden }
      .map { it.toState(lastMonth, notes, isCollapsed = it.id in collapsed, showHidden) }
  return BudgetContent.Loaded(
    summary = current.month.toSummary(),
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
  notes: Map<String, String>,
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
        .map { it.toState(lastMonth[it.id] ?: Amount.Zero, notes[it.id.value]) }
        .toImmutableList(),
  )

private fun CategoryMonth.toState(lastMonthBudgeted: Amount, note: String?) =
  CategoryState(
    id = id,
    name = name,
    isIncome = isIncome,
    isHidden = isHidden,
    budgeted = budgeted,
    spent = if (isIncome) spent else -spent,
    balance = balance,
    lastMonthBudgeted = lastMonthBudgeted,
    rollover = carryover,
    note = note,
  )

private val NOT_LOADED: LoadedMonth? = null

/** What a move does: how much actually moves, and the budgets it leaves the categories with */
internal data class Move(val amount: Amount, val budgets: Map<CategoryId, Amount>)

/**
 * transferCategory() and transferAvailable(): moves [amount] from [from] to [to], where null is To
 * Budget. Taking from To Budget never takes more than it has. Null when nothing would move.
 */
internal fun BudgetMonth.move(from: CategoryId?, to: CategoryId?, amount: Amount): Move? {
  if (from == to || amount <= Amount.Zero) return null
  val budgeted = categories.associate { it.id to it.budgeted }
  val known = listOfNotNull(from, to).all { it in budgeted }
  if (!known) return null
  val available = (this as? BudgetMonth.Envelope)?.toBudget
  val cap = if (from == null && available != null) maxOf(available, Amount.Zero) else amount
  val moved = minOf(amount, cap)
  if (moved <= Amount.Zero) return null
  val budgets = buildMap {
    from?.let { put(it, budgeted.getValue(it) - moved) }
    to?.let { put(it, budgeted.getValue(it) + moved) }
  }
  return Move(moved, budgets)
}

/**
 * coverOverspending(): what [category] is overspent by, capped at what [from] (or To Budget) has
 * left. Null when there's nothing to cover or nothing to cover it with.
 */
internal fun BudgetMonth.coverAmount(category: CategoryId, from: CategoryId?): Amount? {
  val overspent = categories.firstOrNull { it.id == category }?.balance ?: return null
  val leftover =
    if (from == null) {
      (this as? BudgetMonth.Envelope)?.toBudget
    } else {
      categories.firstOrNull { it.id == from }?.balance
    }
  val amount = leftover?.let { minOf(-overspent, it) }
  return amount?.takeIf { overspent < Amount.Zero && it > Amount.Zero }
}

/**
 * holdForNextMonth() and calcBufferedAmount(): adds [amount] to what's held, without holding more
 * than is left to budget. Null when there's nothing left to budget.
 */
internal fun BudgetMonth.Envelope.heldAfter(amount: Amount): Amount? {
  if (toBudget <= Amount.Zero) return null
  return buffered + minOf(maxOf(amount, -buffered), toBudget)
}
