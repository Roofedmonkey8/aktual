package aktual.budget.budgeting.vm

import aktual.budget.budgeting.domain.BudgetGroup
import aktual.budget.budgeting.domain.BudgetMonth
import aktual.budget.budgeting.domain.BudgetOverview
import aktual.budget.budgeting.domain.CategoryMonth
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.YearMonth

internal class BudgetContentTest {
  @Test
  fun `Spending shows as positive and income as received`() {
    val content =
      overview(FOOD_SPENT_50, SALARY).toContent(PREVIOUS, emptySet(), showHidden = false)

    assertThat(content).isInstanceOf<BudgetContent.Loaded>().all {
      prop(BudgetContent.Loaded::expenseGroups)
        .transform { it.single().categories.single() }
        .all {
          prop(CategoryState::spent).isEqualTo(Amount(50L))
          prop(CategoryState::balance).isEqualTo(Amount(50L))
          prop(CategoryState::lastMonthBudgeted).isEqualTo(Amount(80L))
        }
      prop(BudgetContent.Loaded::incomeGroups)
        .transform { it.single().categories.single() }
        .all {
          prop(CategoryState::spent).isEqualTo(Amount(3_000L))
          prop(CategoryState::lastMonthBudgeted).isEqualTo(Amount.Zero)
        }
    }
  }

  @Test
  fun `Envelope summary turns overspending into a positive figure`() {
    val content =
      overview(FOOD_SPENT_50, SALARY).toContent(PREVIOUS, emptySet(), showHidden = false)

    assertThat(content)
      .isInstanceOf<BudgetContent.Loaded>()
      .prop(BudgetContent.Loaded::summary)
      .isInstanceOf<BudgetSummary.Envelope>()
      .all {
        prop(BudgetSummary.Envelope::toBudget).isEqualTo(Amount(2_900L))
        prop(BudgetSummary.Envelope::overspentLastMonth).isEqualTo(Amount(20L))
        prop(BudgetSummary.Envelope::availableFunds).isEqualTo(Amount(3_000L))
      }
  }

  @Test
  fun `Hidden categories and groups are left out unless asked for`() {
    val hidden = FOOD_SPENT_50.copy(id = CategoryId("secret"), isHidden = true)
    val month = overview(FOOD_SPENT_50, hidden, SALARY, hiddenGroup = SALARY.group)

    val hiding = month.toContent(PREVIOUS, emptySet(), showHidden = false)
    assertThat(hiding).isInstanceOf<BudgetContent.Loaded>().all {
      prop(BudgetContent.Loaded::expenseGroups)
        .transform { groups -> groups.flatMap { it.categories }.map { it.id } }
        .containsExactly(FOOD)
      prop(BudgetContent.Loaded::incomeGroups).transform { it.size }.isEqualTo(0)
    }

    val showing = month.toContent(PREVIOUS, emptySet(), showHidden = true)
    assertThat(showing).isInstanceOf<BudgetContent.Loaded>().all {
      prop(BudgetContent.Loaded::expenseGroups)
        .transform { groups -> groups.flatMap { it.categories }.map { it.id } }
        .containsExactly(FOOD, CategoryId("secret"))
      prop(BudgetContent.Loaded::incomeGroups).transform { it.size }.isEqualTo(1)
    }
  }

  @Test
  fun `Collapsed groups are marked`() {
    val content =
      overview(FOOD_SPENT_50, SALARY).toContent(PREVIOUS, setOf(BILLS), showHidden = false)

    assertThat(content).isInstanceOf<BudgetContent.Loaded>().all {
      prop(BudgetContent.Loaded::expenseGroups)
        .transform { it.single().isCollapsed }
        .isEqualTo(true)
      prop(BudgetContent.Loaded::incomeGroups)
        .transform { it.single().isCollapsed }
        .isEqualTo(false)
    }
  }

  @Test
  fun `A budget without categories is empty`() {
    val empty =
      BudgetOverview(
        month = envelope(),
        groups = persistentListOf(BudgetGroup(BILLS, "Bills", false, false, persistentListOf())),
      )
    assertThat(empty.toContent(PREVIOUS, emptySet(), showHidden = false))
      .isEqualTo(BudgetContent.Empty)
  }

  private fun overview(
    vararg categories: CategoryMonth,
    hiddenGroup: CategoryGroupId? = null,
  ): BudgetOverview {
    val month = envelope(*categories)
    val groups =
      categories
        .groupBy { it.group }
        .map { (id, rows) ->
          BudgetGroup(
            id = id,
            name = id.value,
            isIncome = rows.all { it.isIncome },
            isHidden = id == hiddenGroup,
            categories = rows.toImmutableList(),
          )
        }
    return BudgetOverview(month, groups.toImmutableList())
  }

  private fun envelope(vararg categories: CategoryMonth) =
    BudgetMonth.Envelope(
      month = MONTH,
      toBudget = Amount(2_900L),
      budgeted = Amount(100L),
      spent = Amount(-50L),
      balance = Amount(50L),
      income = Amount(3_000L),
      fromLastMonth = Amount.Zero,
      lastMonthOverspent = Amount(-20L),
      buffered = Amount.Zero,
      categories = categories.toList().toImmutableList(),
    )

  private companion object {
    val MONTH = YearMonth(2026, 3)
    val BILLS = CategoryGroupId("bills")
    val FOOD = CategoryId("food")

    val FOOD_SPENT_50 =
      CategoryMonth(
        id = FOOD,
        name = "Food",
        group = BILLS,
        isIncome = false,
        isHidden = false,
        budgeted = Amount(100L),
        spent = Amount(-50L),
        balance = Amount(50L),
        carryover = false,
      )

    val SALARY =
      CategoryMonth(
        id = CategoryId("salary"),
        name = "Salary",
        group = CategoryGroupId("income"),
        isIncome = true,
        isHidden = false,
        budgeted = Amount.Zero,
        spent = Amount(3_000L),
        balance = Amount.Zero,
        carryover = false,
      )

    val PREVIOUS =
      BudgetOverview(
        month =
          BudgetMonth.Envelope(
            month = YearMonth(2026, 2),
            toBudget = Amount.Zero,
            budgeted = Amount(80L),
            spent = Amount.Zero,
            balance = Amount.Zero,
            income = Amount.Zero,
            fromLastMonth = Amount.Zero,
            lastMonthOverspent = Amount.Zero,
            buffered = Amount.Zero,
            categories = persistentListOf(FOOD_SPENT_50.copy(budgeted = Amount(80L))),
          ),
        groups = persistentListOf(),
      )
  }
}
