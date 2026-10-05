package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.CategoryState

/** What can be done to a category beyond setting its budget */
internal sealed interface CategoryAction {
  data class Move(val category: CategoryState) : CategoryAction

  data class Cover(val category: CategoryState) : CategoryAction

  data class SetRollover(val category: CategoryState, val rollover: Boolean) : CategoryAction

  data class Notes(val category: CategoryState) : CategoryAction
}
