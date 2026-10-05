package aktual.budget.db.dao

import aktual.budget.db.BudgetDatabase
import aktual.budget.db.CategoryInfo
import aktual.budget.db.LiveCategoryOrder
import aktual.budget.db.LiveGroupOrder
import aktual.budget.db.ManagePayees
import aktual.budget.db.withResult
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.budget.model.PayeeId
import alakazam.kotlin.CoroutineContexts
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.YearMonth

/** A category's budget in one month, and the row it's kept in */
data class MonthBudget(val row: String, val month: YearMonth, val amount: Amount)

/**
 * What's needed to add, rename, move and delete categories, groups and payees, as upstream's
 * db/index.ts does it
 */
@Inject
class BudgetStructureDao(database: BudgetDatabase, private val contexts: CoroutineContexts) {
  private val categories = database.categoriesQueries
  private val groups = database.categoryGroupsQueries
  private val categoryMappings = database.categoryMappingQueries
  private val payees = database.payeesQueries
  private val payeeMappings = database.payeeMappingQueries
  private val budgets = database.budgetsQueries

  suspend fun groups(): List<LiveGroupOrder> = groups.withResult { liveGroupOrder().awaitAsList() }

  suspend fun categoriesOf(group: CategoryGroupId): List<LiveCategoryOrder> =
    categories.withResult {
      liveCategoryOrder(group).awaitAsList()
    }

  suspend fun categoryIdsOf(group: CategoryGroupId): List<CategoryId> = categories.withResult {
    liveCategoriesOfGroup(group).awaitAsList().map { it.id }
  }

  suspend fun category(id: CategoryId): CategoryInfo? = categories.withResult {
    categoryInfo(id).awaitAsOneOrNull()
  }

  suspend fun isUsed(id: CategoryId): Boolean = categories.withResult {
    isCategoryUsed(id).awaitAsOne()
  }

  /** Deleted categories that were folded into [id], and so follow it when it's deleted too */
  suspend fun mappedTo(id: CategoryId): List<CategoryId> = categoryMappings.withResult {
    mappedTo(id).awaitAsList()
  }

  suspend fun envelopeBudgets(category: CategoryId): List<MonthBudget> = budgets.withResult {
    zeroBudgetsOf(category).awaitAsList().mapNotNull { row ->
      row.month?.let { MonthBudget(row.id, it, row.amount ?: Amount.Zero) }
    }
  }

  suspend fun trackingBudgets(category: CategoryId): List<MonthBudget> = budgets.withResult {
    reflectBudgetsOf(category).awaitAsList().mapNotNull { row ->
      row.month?.let { MonthBudget(row.id, it, row.amount ?: Amount.Zero) }
    }
  }

  fun observePayees(): Flow<List<ManagePayees>> =
    payees.managePayees().asFlow().mapToList(contexts.default).distinctUntilChanged()

  /** Payees that were merged into [id], so they follow it when it's merged again */
  suspend fun payeesMappedTo(id: PayeeId): List<PayeeId> = payeeMappings.withResult {
    getIdsByTargetId(id).awaitAsList().mapNotNull { it }
  }
}
