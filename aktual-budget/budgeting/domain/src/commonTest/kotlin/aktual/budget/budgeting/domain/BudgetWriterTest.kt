package aktual.budget.budgeting.domain

import aktual.budget.db.BudgetGroups
import aktual.budget.db.dao.DatabaseTables.REFLECT_BUDGETS
import aktual.budget.db.dao.DatabaseTables.ZERO_BUDGETS
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.budget.model.LocalChange
import aktual.budget.model.MessageValue
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.YearMonth

internal class BudgetWriterTest {
  @Test
  fun `A new budget row is keyed by month and category like upstream's`() {
    val changes =
      budgetChanges(ZERO_BUDGETS, existingId = null, MARCH, FOOD, amount = Amount(12_345L))

    assertThat(changes)
      .containsExactly(
        LocalChange(ZERO_BUDGETS, "202603-food", "month", MessageValue.Number(202603L)),
        LocalChange(ZERO_BUDGETS, "202603-food", "category", MessageValue.String("food")),
        LocalChange(ZERO_BUDGETS, "202603-food", "amount", MessageValue.Number(12_345L)),
      )
  }

  @Test
  fun `An existing budget row only has its amount changed`() {
    val changes =
      budgetChanges(REFLECT_BUDGETS, existingId = "some-row", MARCH, FOOD, amount = Amount.Zero)

    assertThat(changes)
      .containsExactly(LocalChange(REFLECT_BUDGETS, "some-row", "amount", MessageValue.Number(0L)))
  }

  @Test
  fun `December is stored as month 12`() {
    val changes = budgetChanges(ZERO_BUDGETS, null, YearMonth(2025, 12), FOOD, Amount(1L))
    assertThat(changes.first().row).isEqualTo("202512-food")
  }

  @Test
  fun `Categories are laid out under their groups in group order`() {
    val month =
      BudgetMonth.Tracking(
        month = MARCH,
        budgeted = Amount(300L),
        spent = Amount(-50L),
        balance = Amount(250L),
        income = Amount.Zero,
        incomeBudgeted = Amount.Zero,
        categories =
          persistentListOf(
            category("food", group = "bills", budgeted = 100L, spent = -50L),
            category("rent", group = "bills", budgeted = 200L, spent = 0L),
          ),
      )
    val groups =
      listOf(
        BudgetGroups(CategoryGroupId("bills"), "Bills", is_income = false, hidden = false),
        BudgetGroups(CategoryGroupId("empty"), null, is_income = true, hidden = true),
      )

    val result = groupCategories(month, groups)

    assertThat(result.map { it.name }).containsExactly("Bills", "")
    assertThat(result.first()).all {
      prop(BudgetGroup::budgeted).isEqualTo(Amount(300L))
      prop(BudgetGroup::spent).isEqualTo(Amount(-50L))
      prop(BudgetGroup::balance).isEqualTo(Amount(250L))
    }
    assertThat(result[1]).all {
      prop(BudgetGroup::isIncome).isEqualTo(true)
      prop(BudgetGroup::isHidden).isEqualTo(true)
      prop(BudgetGroup::categories).isEmpty()
    }
    assertThat(groupCategories(month, groups.take(1)).single().categories.map { it.id })
      .containsExactly(FOOD, CategoryId("rent"))
    assertThat(groupCategories(month, emptyList())).isEmpty()
  }

  private fun category(id: String, group: String, budgeted: Long, spent: Long) =
    CategoryMonth(
      id = CategoryId(id),
      name = id,
      group = CategoryGroupId(group),
      isIncome = false,
      isHidden = false,
      budgeted = Amount(budgeted),
      spent = Amount(spent),
      balance = Amount(budgeted + spent),
      carryover = false,
    )

  private companion object {
    val MARCH = YearMonth(2026, 3)
    val FOOD = CategoryId("food")
  }
}
