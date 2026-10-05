package aktual.budget.budgeting.vm

import aktual.budget.budgeting.domain.BudgetMonth
import aktual.budget.budgeting.domain.CategoryMonth
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.YearMonth

internal class BudgetMovesTest {
  @Test
  fun `Moving between categories takes from one and adds to the other`() {
    assertThat(month(toBudget = 0).move(FOOD, FUN, Amount(30L)))
      .isEqualTo(Move(Amount(30L), mapOf(FOOD to Amount(70L), FUN to Amount(50L))))
  }

  @Test
  fun `Moving back to To Budget only lowers the category`() {
    assertThat(month(toBudget = 0).move(FOOD, null, Amount(30L)))
      .isEqualTo(Move(Amount(30L), mapOf(FOOD to Amount(70L))))
  }

  @Test
  fun `Budgeting from To Budget never takes more than it has`() {
    assertThat(month(toBudget = 25).move(null, FUN, Amount(100L)))
      .isEqualTo(Move(Amount(25L), mapOf(FUN to Amount(45L))))
    assertThat(month(toBudget = -10).move(null, FUN, Amount(100L))).isNull()
  }

  @Test
  fun `Nothing moves to itself, for nothing, or for an unknown category`() {
    val month = month(toBudget = 50)
    assertThat(month.move(FOOD, FOOD, Amount(10L))).isNull()
    assertThat(month.move(FOOD, FUN, Amount.Zero)).isNull()
    assertThat(month.move(FOOD, CategoryId("gone"), Amount(10L))).isNull()
  }

  @Test
  fun `Covering takes the overspending, capped at what the source has`() {
    // Fun is 15 overspent, Food has 40 left, To Budget has 10
    val month = month(toBudget = 10)
    assertThat(month.coverAmount(FUN, FOOD)).isEqualTo(Amount(15L))
    assertThat(month.coverAmount(FUN, null)).isEqualTo(Amount(10L))
    assertThat(month.coverAmount(FOOD, FUN)).isNull()
  }

  @Test
  fun `Holding adds to what's held, up to what's left to budget`() {
    assertThat(month(toBudget = 50, buffered = 20).heldAfter(Amount(100L))).isEqualTo(Amount(70L))
    assertThat(month(toBudget = 50, buffered = 20).heldAfter(Amount(-100L))).isEqualTo(Amount.Zero)
    assertThat(month(toBudget = 0, buffered = 20).heldAfter(Amount(10L))).isNull()
  }

  private fun month(toBudget: Long, buffered: Long = 0) =
    BudgetMonth.Envelope(
      month = YearMonth(2026, 10),
      toBudget = Amount(toBudget),
      budgeted = Amount(120L),
      spent = Amount(-95L),
      balance = Amount(25L),
      income = Amount.Zero,
      fromLastMonth = Amount.Zero,
      lastMonthOverspent = Amount.Zero,
      buffered = Amount(buffered),
      categories =
        persistentListOf(
          category(FOOD, budgeted = 100L, spent = -60L),
          category(FUN, budgeted = 20L, spent = -35L),
        ),
    )

  private fun category(id: CategoryId, budgeted: Long, spent: Long) =
    CategoryMonth(
      id = id,
      name = id.value,
      group = CategoryGroupId("group"),
      isIncome = false,
      isHidden = false,
      budgeted = Amount(budgeted),
      spent = Amount(spent),
      balance = Amount(budgeted + spent),
      carryover = false,
    )

  private companion object {
    val FOOD = CategoryId("food")
    val FUN = CategoryId("fun")
  }
}
