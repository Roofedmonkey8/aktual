package aktual.budget.budgeting.vm

import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.YearMonth

@Immutable
data class BudgetState(
  val month: YearMonth,
  val isCurrentMonth: Boolean,
  val showHidden: Boolean,
  val isRefreshing: Boolean,
  val content: BudgetContent,
  val monthNote: String? = null,
)

@Immutable
sealed interface BudgetContent {
  data object Loading : BudgetContent

  data object Empty : BudgetContent

  data class Loaded(
    val summary: BudgetSummary,
    val expenseGroups: ImmutableList<GroupState>,
    val incomeGroups: ImmutableList<GroupState>,
  ) : BudgetContent
}

/** The headline figures for the month. Amounts are as shown, so spending is positive. */
@Immutable
sealed interface BudgetSummary {
  data class Envelope(
    val toBudget: Amount,
    val availableFunds: Amount,
    val overspentLastMonth: Amount,
    val budgeted: Amount,
    val forNextMonth: Amount,
  ) : BudgetSummary

  data class Tracking(
    val expectedIncome: Amount,
    val received: Amount,
    val budgeted: Amount,
    val spent: Amount,
  ) : BudgetSummary {
    // What's left of what came in once spending is taken out
    val saved: Amount
      get() = received - spent
  }
}

@Immutable
data class GroupState(
  val id: CategoryGroupId,
  val name: String,
  val isIncome: Boolean,
  val isHidden: Boolean,
  val isCollapsed: Boolean,
  val budgeted: Amount,
  val spent: Amount,
  val balance: Amount,
  val categories: ImmutableList<CategoryState>,
)

/**
 * One category's month. [spent] is positive when money went out, or for income what was received.
 * [balance] is what's left to spend, or for tracking income what's still to come in.
 */
@Immutable
data class CategoryState(
  val id: CategoryId,
  val name: String,
  val isIncome: Boolean,
  val isHidden: Boolean,
  val budgeted: Amount,
  val spent: Amount,
  val balance: Amount,
  val lastMonthBudgeted: Amount,
  // Envelope: overspending carries into next month. Tracking: the balance does.
  val rollover: Boolean = false,
  val note: String? = null,
)

sealed interface BudgetEvent {
  data object SaveFailed : BudgetEvent

  data class DuplicateName(val name: String) : BudgetEvent

  data object BlankName : BudgetEvent

  data object IncomeMismatch : BudgetEvent

  /** Deleting [target] can go ahead, picking somewhere for its money first if [needsTransfer] */
  data class ConfirmDelete(val target: DeleteTarget, val needsTransfer: Boolean) : BudgetEvent
}

@Immutable
sealed interface DeleteTarget {
  data class Category(val id: CategoryId) : DeleteTarget

  data class Group(val id: CategoryGroupId) : DeleteTarget
}
