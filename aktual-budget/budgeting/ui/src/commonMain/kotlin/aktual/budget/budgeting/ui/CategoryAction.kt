package aktual.budget.budgeting.ui

import aktual.budget.budgeting.domain.Direction
import aktual.budget.budgeting.vm.CategoryState

/** What can be done to a category beyond setting its budget */
internal sealed interface CategoryAction {
  data class Move(val category: CategoryState) : CategoryAction

  data class Cover(val category: CategoryState) : CategoryAction

  data class SetRollover(val category: CategoryState, val rollover: Boolean) : CategoryAction

  data class Notes(val category: CategoryState) : CategoryAction

  data class Rename(val category: CategoryState) : CategoryAction

  data class SetHidden(val category: CategoryState, val hidden: Boolean) : CategoryAction

  data class Reorder(val category: CategoryState, val direction: Direction) : CategoryAction

  data class MoveToGroup(val category: CategoryState) : CategoryAction

  data class Delete(val category: CategoryState) : CategoryAction
}
