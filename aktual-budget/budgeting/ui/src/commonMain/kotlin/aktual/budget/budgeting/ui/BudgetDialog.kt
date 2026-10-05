package aktual.budget.budgeting.ui

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
}
