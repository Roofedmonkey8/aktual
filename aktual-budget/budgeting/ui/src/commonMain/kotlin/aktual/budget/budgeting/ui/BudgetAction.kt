package aktual.budget.budgeting.ui

import aktual.budget.budgeting.domain.Direction
import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.budgeting.vm.DeleteTarget
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

  /** Changes to the categories and groups themselves */
  sealed interface Manage : BudgetAction

  data class CreateGroup(val name: String) : Manage

  data class RenameGroup(val id: CategoryGroupId, val name: String) : Manage

  data class SetGroupHidden(val id: CategoryGroupId, val hidden: Boolean) : Manage

  data class MoveGroup(val id: CategoryGroupId, val direction: Direction) : Manage

  data class CreateCategory(val name: String, val group: CategoryGroupId) : Manage

  data class RenameCategory(val id: CategoryId, val name: String) : Manage

  data class SetCategoryHidden(val id: CategoryId, val hidden: Boolean) : Manage

  data class MoveCategory(val id: CategoryId, val direction: Direction) : Manage

  data class MoveCategoryToGroup(val id: CategoryId, val group: CategoryGroupId) : Manage

  data class RequestDelete(val target: DeleteTarget) : Manage

  data class Delete(val target: DeleteTarget, val transferTo: CategoryId?) : Manage
}

@Immutable
internal fun interface BudgetActionHandler {
  operator fun invoke(action: BudgetAction)
}
