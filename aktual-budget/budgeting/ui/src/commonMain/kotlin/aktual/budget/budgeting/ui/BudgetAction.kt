package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.model.CategoryGroupId
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
}

@Immutable
internal fun interface BudgetActionHandler {
  operator fun invoke(action: BudgetAction)
}
