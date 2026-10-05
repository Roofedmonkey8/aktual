package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import androidx.compose.runtime.Immutable

@Immutable
internal sealed interface BudgetAction {
  data object PreviousMonth : BudgetAction

  data object NextMonth : BudgetAction

  data object ThisMonth : BudgetAction

  data object Refresh : BudgetAction

  data class SetShowHidden(val show: Boolean) : BudgetAction

  data class ToggleGroup(val id: CategoryGroupId) : BudgetAction

  data class EditBudget(val category: CategoryState) : BudgetAction

  data class Open(val dialog: BudgetDialog?) : BudgetAction

  data class SetBudget(val category: CategoryId, val amount: Amount) : BudgetAction

  data class MoveMoney(val from: CategoryId?, val to: CategoryId?, val amount: Amount) :
    BudgetAction

  data class CoverOverspending(val category: CategoryId, val from: CategoryId?) : BudgetAction

  data class SetRollover(val category: CategoryId, val rollover: Boolean) : BudgetAction

  data class SetNote(val category: CategoryId?, val note: String) : BudgetAction

  data class Hold(val amount: Amount) : BudgetAction

  data object ResetHold : BudgetAction

  data object CopyLastMonth : BudgetAction

  data object SetAllToZero : BudgetAction
}

@Immutable
internal fun interface BudgetActionHandler {
  operator fun invoke(action: BudgetAction)
}
