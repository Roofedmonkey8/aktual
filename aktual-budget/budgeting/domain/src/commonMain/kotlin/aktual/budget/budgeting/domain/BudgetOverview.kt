package aktual.budget.budgeting.domain

import aktual.budget.db.BudgetGroups
import aktual.budget.db.dao.BudgetDao
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.di.BudgetScope
import alakazam.kotlin.CoroutineContexts
import dev.zacsweers.metro.ContributesBinding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.YearMonth

/** A budget month with its categories laid out under their groups, as the budget page shows it */
data class BudgetOverview(val month: BudgetMonth, val groups: ImmutableList<BudgetGroup>)

// Amounts keep their transaction sign, as in BudgetMonth
data class BudgetGroup(
  val id: CategoryGroupId,
  val name: String,
  val isIncome: Boolean,
  val isHidden: Boolean,
  val categories: ImmutableList<CategoryMonth>,
) {
  val budgeted: Amount = categories.sumOf { it.budgeted }
  val spent: Amount = categories.sumOf { it.spent }
  val balance: Amount = categories.sumOf { it.balance }
}

interface BudgetOverviewLoader {
  fun observe(month: YearMonth): Flow<BudgetOverview>
}

@ContributesBinding(BudgetScope::class)
class BudgetOverviewLoaderImpl(
  private val calculator: BudgetMonthCalculator,
  private val budgetDao: BudgetDao,
  private val contexts: CoroutineContexts,
) : BudgetOverviewLoader {
  override fun observe(month: YearMonth): Flow<BudgetOverview> =
    combine(calculator.observe(month), budgetDao.observeGroups()) { budget, groups ->
        BudgetOverview(month = budget, groups = groupCategories(budget, groups))
      }
      .distinctUntilChanged()
      .flowOn(contexts.default)
}

// Both lists come out of the database income-last, so the groups keep that order
internal fun groupCategories(
  budget: BudgetMonth,
  groups: List<BudgetGroups>,
): ImmutableList<BudgetGroup> {
  val byGroup = budget.categories.groupBy { it.group }
  return groups
    .map { row ->
      BudgetGroup(
        id = row.id,
        name = row.name.orEmpty(),
        isIncome = row.is_income == true,
        isHidden = row.hidden,
        categories = byGroup[row.id].orEmpty().toImmutableList(),
      )
    }
    .toImmutableList()
}

private inline fun <T> Iterable<T>.sumOf(selector: (T) -> Amount): Amount =
  fold(Amount.Zero) { sum, item -> sum + selector(item) }
