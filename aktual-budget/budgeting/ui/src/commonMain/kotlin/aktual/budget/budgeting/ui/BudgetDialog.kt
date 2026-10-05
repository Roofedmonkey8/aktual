package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.DeleteTarget
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import androidx.compose.runtime.Immutable

/** The sheet or dialog open over the budget. Categories are held by ID, so they stay current. */
@Immutable
internal sealed interface BudgetDialog {
  data class Category(val id: CategoryId) : BudgetDialog

  data class Move(val from: CategoryId?) : BudgetDialog

  data class Cover(val id: CategoryId) : BudgetDialog

  data class Notes(val id: CategoryId?) : BudgetDialog

  data object ToBudget : BudgetDialog

  data object Hold : BudgetDialog

  data object CopyLastMonth : BudgetDialog

  data object SetZero : BudgetDialog

  data object NewGroup : BudgetDialog

  data class GroupOptions(val id: CategoryGroupId) : BudgetDialog

  data class RenameGroup(val id: CategoryGroupId) : BudgetDialog

  data class NewCategory(val group: CategoryGroupId) : BudgetDialog

  data class RenameCategory(val id: CategoryId) : BudgetDialog

  data class PickGroup(val id: CategoryId) : BudgetDialog

  data class Delete(val target: DeleteTarget, val needsTransfer: Boolean) : BudgetDialog
}
