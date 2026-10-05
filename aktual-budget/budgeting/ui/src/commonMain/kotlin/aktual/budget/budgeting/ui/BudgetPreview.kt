@file:Suppress("MagicNumber")

package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.BudgetContent
import aktual.budget.budgeting.vm.BudgetState
import aktual.budget.budgeting.vm.BudgetSummary
import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.budgeting.vm.GroupState
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.YearMonth

private fun category(
  name: String,
  budgeted: Double,
  spent: Double,
  balance: Double,
  isIncome: Boolean = false,
  isHidden: Boolean = false,
) =
  CategoryState(
    id = CategoryId(name),
    name = name,
    isIncome = isIncome,
    isHidden = isHidden,
    budgeted = Amount(budgeted),
    spent = Amount(spent),
    balance = Amount(balance),
    lastMonthBudgeted = Amount(budgeted),
  )

private fun group(name: String, vararg categories: CategoryState, isCollapsed: Boolean = false) =
  GroupState(
    id = CategoryGroupId(name),
    name = name,
    isIncome = categories.all { it.isIncome },
    isHidden = false,
    isCollapsed = isCollapsed,
    budgeted = categories.fold(Amount.Zero) { sum, c -> sum + c.budgeted },
    spent = categories.fold(Amount.Zero) { sum, c -> sum + c.spent },
    balance = categories.fold(Amount.Zero) { sum, c -> sum + c.balance },
    categories = categories.toList().toImmutableList(),
  )

private val PREVIEW_EXPENSES =
  persistentListOf(
    group(
      "Usual Expenses",
      category("Food", 450.0, 312.48, 137.52),
      category("General", 200.0, 248.10, -48.10),
      category("Bills", 1250.0, 1250.0, 0.0),
      category("Savings", 300.0, 0.0, 1200.0),
    ),
    group("Fun", category("Eating out", 120.0, 64.20, 55.80), isCollapsed = true),
  )

private val PREVIEW_INCOME =
  persistentListOf(group("Income", category("Salary", 3200.0, 3200.0, 0.0, isIncome = true)))

internal val PREVIEW_ENVELOPE_STATE =
  BudgetState(
    month = YearMonth(2026, 10),
    isCurrentMonth = true,
    showHidden = false,
    isRefreshing = false,
    content =
      BudgetContent.Loaded(
        summary =
          BudgetSummary.Envelope(
            toBudget = Amount(380.0),
            availableFunds = Amount(2700.0),
            overspentLastMonth = Amount(0.0),
            budgeted = Amount(2320.0),
            forNextMonth = Amount(0.0),
          ),
        expenseGroups = PREVIEW_EXPENSES,
        incomeGroups = PREVIEW_INCOME,
      ),
  )

internal val PREVIEW_TRACKING_STATE =
  PREVIEW_ENVELOPE_STATE.copy(
    isCurrentMonth = false,
    content =
      BudgetContent.Loaded(
        summary =
          BudgetSummary.Tracking(
            expectedIncome = Amount(3200.0),
            received = Amount(3200.0),
            budgeted = Amount(2320.0),
            spent = Amount(1874.78),
          ),
        expenseGroups = PREVIEW_EXPENSES,
        incomeGroups = PREVIEW_INCOME,
      ),
  )
