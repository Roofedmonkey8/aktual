package aktual.budget.budgeting.domain

import aktual.budget.BudgetSyncController
import aktual.budget.db.dao.BudgetDao
import aktual.budget.db.dao.DatabaseTables.REFLECT_BUDGETS
import aktual.budget.db.dao.DatabaseTables.ZERO_BUDGETS
import aktual.budget.db.dao.PreferencesDao
import aktual.budget.model.Amount
import aktual.budget.model.BudgetType
import aktual.budget.model.CategoryId
import aktual.budget.model.LocalChange
import aktual.budget.model.MessageValue
import aktual.budget.model.SyncedPrefKey
import aktual.di.BudgetScope
import dev.zacsweers.metro.ContributesBinding
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number

interface BudgetWriter {
  /** Sets how much is budgeted to [category] in [month], replacing what was there */
  suspend fun setBudget(month: YearMonth, category: CategoryId, amount: Amount)
}

/**
 * packages/loot-core/src/server/budget/actions.ts setBudget(). Envelope budgets live in
 * zero_budgets and tracking budgets in reflect_budgets, one row per month and category. An existing
 * row only has its amount changed, a new one is keyed "YYYYMM-category" like upstream's.
 */
@ContributesBinding(BudgetScope::class)
class BudgetWriterImpl(
  private val budgetDao: BudgetDao,
  private val preferencesDao: PreferencesDao,
  private val syncController: BudgetSyncController,
) : BudgetWriter {
  override suspend fun setBudget(month: YearMonth, category: CategoryId, amount: Amount) {
    // Upstream falls back to envelope budgeting
    val type =
      BudgetType.from(preferencesDao[SyncedPrefKey.Global.BudgetType]) ?: BudgetType.Envelope
    val table = if (type == Tracking) REFLECT_BUDGETS else ZERO_BUDGETS
    val existing =
      when (type) {
        Envelope -> budgetDao.envelopeBudgetId(month, category)
        Tracking -> budgetDao.trackingBudgetId(month, category)
      }
    syncController.syncChanges(budgetChanges(table, existing, month, category, amount))
  }
}

internal fun budgetChanges(
  table: String,
  existingId: String?,
  month: YearMonth,
  category: CategoryId,
  amount: Amount,
): List<LocalChange> {
  val amountValue = MessageValue.Number(amount.toLong())
  if (existingId != null) return listOf(LocalChange(table, existingId, "amount", amountValue))

  val dbMonth = month.dbMonth()
  val row = "$dbMonth-${category.value}"
  return listOf(
    LocalChange(table, row, "month", MessageValue.Number(dbMonth)),
    LocalChange(table, row, "category", MessageValue.String(category.value)),
    LocalChange(table, row, "amount", amountValue),
  )
}

// packages/loot-core/src/server/budget/util.ts dbMonth(): "2026-03" is stored as 202603
private fun YearMonth.dbMonth(): Long = year * YEAR_FACTOR + month.number

private const val YEAR_FACTOR = 100L
