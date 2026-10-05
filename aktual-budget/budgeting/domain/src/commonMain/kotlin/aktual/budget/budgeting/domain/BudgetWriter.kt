package aktual.budget.budgeting.domain

import aktual.budget.BudgetSyncController
import aktual.budget.db.dao.BudgetDao
import aktual.budget.db.dao.DatabaseTables.NOTES
import aktual.budget.db.dao.DatabaseTables.REFLECT_BUDGETS
import aktual.budget.db.dao.DatabaseTables.ZERO_BUDGETS
import aktual.budget.db.dao.DatabaseTables.ZERO_BUDGET_MONTHS
import aktual.budget.db.dao.NotesDao
import aktual.budget.db.dao.PreferencesDao
import aktual.budget.model.Amount
import aktual.budget.model.BudgetType
import aktual.budget.model.CategoryId
import aktual.budget.model.LocalChange
import aktual.budget.model.MessageValue
import aktual.budget.model.SyncedPrefKey
import aktual.di.BudgetScope
import dev.zacsweers.metro.ContributesBinding
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.datetime.DateTimeUnit.Companion.MONTH
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.plus

interface BudgetWriter {
  /** Sets how much is budgeted to [category] in [month], replacing what was there */
  suspend fun setBudget(month: YearMonth, category: CategoryId, amount: Amount) =
    setBudgets(month, mapOf(category to amount))

  /**
   * Sets several categories' budgets for [month] in one sync. A [movement] is noted on the month,
   * as upstream's addMovementNotes() does when money's moved between categories.
   */
  suspend fun setBudgets(
    month: YearMonth,
    amounts: Map<CategoryId, Amount>,
    movement: Movement? = null,
  )

  /**
   * Turns rollover on or off for [category] from [startMonth] through [lastMonth], like
   * setCategoryCarryover()
   */
  suspend fun setCarryover(
    startMonth: YearMonth,
    lastMonth: YearMonth,
    category: CategoryId,
    flag: Boolean,
  )

  /** How much of [month]'s to-budget is held for the next month, as setBuffer() */
  suspend fun setHold(month: YearMonth, amount: Amount)

  /** A note on a category, account or budget month, by the ID notes are keyed on */
  suspend fun setNote(id: String, note: String)
}

/** Money reassigned between categories, or to and from To Budget when one side is null */
data class Movement(val amount: Amount, val from: String, val to: String, val date: LocalDate)

/**
 * packages/loot-core/src/server/budget/actions.ts. Envelope budgets live in zero_budgets and
 * tracking budgets in reflect_budgets, one row per month and category. An existing row only has its
 * changed fields written, a new one is keyed "YYYYMM-category" like upstream's.
 */
@ContributesBinding(BudgetScope::class)
class BudgetWriterImpl(
  private val budgetDao: BudgetDao,
  private val notesDao: NotesDao,
  private val preferencesDao: PreferencesDao,
  private val syncController: BudgetSyncController,
) : BudgetWriter {
  override suspend fun setBudgets(
    month: YearMonth,
    amounts: Map<CategoryId, Amount>,
    movement: Movement?,
  ) {
    val type = budgetType()
    val changes = amounts.flatMap { (category, amount) ->
      val existing =
        when (type) {
          Envelope -> budgetDao.envelopeBudgetId(month, category)
          Tracking -> budgetDao.trackingBudgetId(month, category)
        }
      budgetChanges(type.table, existing, month, category, amount)
    }
    val note = movement?.let { movementNoteChange(month, it) }
    syncController.syncChanges(changes + listOfNotNull(note))
  }

  override suspend fun setCarryover(
    startMonth: YearMonth,
    lastMonth: YearMonth,
    category: CategoryId,
    flag: Boolean,
  ) {
    val type = budgetType()
    val existing =
      when (type) {
        Envelope -> budgetDao.envelopeBudgetIdsFrom(category, startMonth)
        Tracking -> budgetDao.trackingBudgetIdsFrom(category, startMonth)
      }
    val changes =
      (startMonth..maxOf(startMonth, lastMonth)).flatMap { month ->
        carryoverChanges(type.table, existing[month], month, category, flag)
      }
    syncController.syncChanges(changes)
  }

  override suspend fun setHold(month: YearMonth, amount: Amount) {
    // Upstream keys the row by the month's "YYYY-MM", unlike the budget rows
    syncController.syncChanges(
      LocalChange(ZERO_BUDGET_MONTHS, month.toString(), "buffered", amount.messageValue())
    )
  }

  override suspend fun setNote(id: String, note: String) {
    syncController.syncChanges(LocalChange(NOTES, id, "note", MessageValue.String(note)))
  }

  private suspend fun budgetType(): BudgetType =
    // Upstream falls back to envelope budgeting
    BudgetType.from(preferencesDao[SyncedPrefKey.Global.BudgetType]) ?: BudgetType.Envelope

  // addMovementNotes() appends a line to the month's note
  private suspend fun movementNoteChange(month: YearMonth, movement: Movement): LocalChange {
    val id = monthNoteId(month)
    val existing = notesDao[id]?.takeIf { it.isNotEmpty() }?.let { "$it\n" }.orEmpty()
    return LocalChange(NOTES, id, "note", MessageValue.String(existing + "- " + movement.text()))
  }
}

/** The ID a budget month's notes are kept under */
fun monthNoteId(month: YearMonth): String = "budget-$month"

private val BudgetType.table: String
  get() = if (this == BudgetType.Tracking) REFLECT_BUDGETS else ZERO_BUDGETS

internal fun budgetChanges(
  table: String,
  existingId: String?,
  month: YearMonth,
  category: CategoryId,
  amount: Amount,
): List<LocalChange> {
  val amountValue = amount.messageValue()
  if (existingId != null) return listOf(LocalChange(table, existingId, "amount", amountValue))
  return newRow(table, month, category) +
    LocalChange(table, rowId(month, category), "amount", amountValue)
}

// setCarryover() writes 1 or 0 rather than a boolean
internal fun carryoverChanges(
  table: String,
  existingId: String?,
  month: YearMonth,
  category: CategoryId,
  flag: Boolean,
): List<LocalChange> {
  val value = MessageValue.Number(if (flag) 1L else 0L)
  if (existingId != null) return listOf(LocalChange(table, existingId, "carryover", value))
  return newRow(table, month, category) +
    LocalChange(table, rowId(month, category), "carryover", value)
}

private fun newRow(table: String, month: YearMonth, category: CategoryId): List<LocalChange> {
  val row = rowId(month, category)
  return listOf(
    LocalChange(table, row, "month", MessageValue.Number(month.dbMonth())),
    LocalChange(table, row, "category", MessageValue.String(category.value)),
  )
}

private fun rowId(month: YearMonth, category: CategoryId) = "${month.dbMonth()}-${category.value}"

// The wording upstream uses, so notes read the same whichever app moved the money
internal fun Movement.text(): String {
  val formatted =
    NumberFormat.getNumberInstance(Locale.US)
      .apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
      }
      .format(amount.toDouble().absoluteValue)
  val day =
    date.month.name.lowercase().replaceFirstChar { it.uppercase() } +
      " " +
      date.day.toString().padStart(2, '0')
  return "Reassigned $formatted from $from → $to on $day"
}

private fun Amount.messageValue() = MessageValue.Number(toLong())

// packages/loot-core/src/server/budget/util.ts dbMonth(): "2026-03" is stored as 202603
private fun YearMonth.dbMonth(): Long = year * YEAR_FACTOR + month.number

private const val YEAR_FACTOR = 100L

/** Budgets run a year past the current month, as getBudgetRange() sets them up */
fun lastBudgetMonth(today: YearMonth): YearMonth = today.plus(MONTHS_AHEAD, MONTH)

private const val MONTHS_AHEAD = 12
