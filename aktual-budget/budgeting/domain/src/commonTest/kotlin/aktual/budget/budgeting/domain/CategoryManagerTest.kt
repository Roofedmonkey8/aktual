package aktual.budget.budgeting.domain

import aktual.budget.BudgetSyncController
import aktual.budget.db.buildDatabase
import aktual.budget.db.dao.BudgetStructureDao
import aktual.budget.db.dao.PreferencesDao
import aktual.budget.db.dao.SyncDao
import aktual.budget.model.Amount
import aktual.budget.model.BudgetId
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.budget.model.LocalChange
import aktual.test.inMemoryDriverFactory
import alakazam.test.TestCoroutineContexts
import app.cash.sqldelight.db.SqlDriver
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth

internal class CategoryManagerTest {
  @Test
  fun `A new category goes at the top of its group with a mapping to itself`() = runManagerTest {
    val id = manager.createCategory("  Pets ", USUAL)

    assertThat(dao.categoriesOf(USUAL).map { it.name }).containsExactly("Pets", "Food", "Fun")
    assertThat(dao.category(id)?.is_income).isEqualTo(false)
    assertThat(mappingOf(id)).isEqualTo(id.value)
  }

  @Test
  fun `A category in an income group is an income category`() = runManagerTest {
    val id = manager.createCategory("Bonus", INCOME)
    assertThat(dao.category(id)?.is_income).isEqualTo(true)
  }

  @Test
  fun `Names must be unique within the group and not blank`() = runManagerTest {
    assertFailure { manager.createCategory("food", USUAL) }
      .isInstanceOf<CategoryChangeException.DuplicateName>()
    assertFailure { manager.renameCategory(FUN, "FOOD") }
      .isInstanceOf<CategoryChangeException.DuplicateName>()
    assertFailure { manager.createGroup("usual") }
      .isInstanceOf<CategoryChangeException.DuplicateName>()
    assertFailure { manager.createCategory("  ", USUAL) }
      .isInstanceOf<CategoryChangeException.BlankName>()
  }

  @Test
  fun `Categories move within their group`() = runManagerTest {
    manager.moveCategory(FUN, Direction.Up)
    assertThat(dao.categoriesOf(USUAL).map { it.name }).containsExactly("Fun", "Food")

    manager.moveCategory(FUN, Direction.Down)
    assertThat(dao.categoriesOf(USUAL).map { it.name }).containsExactly("Food", "Fun")
  }

  @Test
  fun `Categories move to the end of another group`() = runManagerTest {
    val group = manager.createGroup("Bills")
    manager.moveCategoryToGroup(FUN, group)

    assertThat(dao.categoriesOf(group).map { it.name }).containsExactly("Fun")
    assertThat(dao.categoriesOf(USUAL).map { it.name }).containsExactly("Food")
  }

  @Test
  fun `Groups move among groups of the same kind`() = runManagerTest {
    val bills = manager.createGroup("Bills")
    manager.moveGroup(bills, Direction.Up)
    assertThat(dao.groups().map { it.name }).containsExactly("Bills", "Usual", "Income")

    // The income group is last of its own kind, so there's nowhere to go
    manager.moveGroup(INCOME, Direction.Up)
    assertThat(dao.groups().map { it.name }).containsExactly("Bills", "Usual", "Income")
  }

  @Test
  fun `Deleting a category moves its budgets and transactions`() = runManagerTest {
    assertThat(manager.needsTransfer(FUN)).isTrue()

    manager.deleteCategory(FUN, transferTo = FOOD)

    assertThat(dao.isUsed(FUN)).isFalse()
    assertThat(mappingOf(FUN)).isEqualTo(FOOD.value)
    assertThat(dao.isUsed(FOOD)).isTrue()
    assertThat(dao.envelopeBudgets(FOOD).associate { it.month to it.amount })
      .isEqualTo(mapOf(YearMonth(2026, 3) to Amount(150L), YearMonth(2026, 4) to Amount(20L)))
    assertThat(dao.categoriesOf(USUAL).map { it.name }).containsExactly("Food")
  }

  @Test
  fun `Money can't move between income and expense categories`() = runManagerTest {
    assertFailure { manager.deleteCategory(FUN, transferTo = SALARY) }
      .isInstanceOf<CategoryChangeException.IncomeMismatch>()
  }

  @Test
  fun `An unused category doesn't need anywhere to go`() = runManagerTest {
    val id = manager.createCategory("Empty", USUAL)
    assertThat(manager.needsTransfer(id)).isFalse()
    manager.deleteCategory(id, transferTo = null)
    assertThat(dao.categoriesOf(USUAL).map { it.name }).containsExactly("Food", "Fun")
  }

  @Test
  fun `Deleting a group deletes its categories`() = runManagerTest {
    manager.deleteGroup(USUAL, transferTo = null)
    assertThat(dao.groups().map { it.name }).containsExactly("Income")
    assertThat(dao.categoryIdsOf(USUAL)).isEqualTo(emptyList<CategoryId>())
  }

  private class Scope(
    val manager: CategoryManager,
    val dao: BudgetStructureDao,
    val driver: SqlDriver,
  ) {
    suspend fun mappingOf(id: CategoryId): String? {
      var result: String? = null
      driver
        .executeQuery(
          identifier = null,
          sql = "SELECT transferId FROM category_mapping WHERE id = '${id.value}'",
          mapper = { cursor ->
            cursor.next()
            result = cursor.getString(0)
            app.cash.sqldelight.db.QueryResult.Unit
          },
          parameters = 0,
        )
        .await()
      return result
    }
  }

  private fun runManagerTest(action: suspend Scope.() -> Unit) = runTest {
    val driver = inMemoryDriverFactory().create(BudgetId("abc-123"))
    driver.use {
      val database = buildDatabase(driver)
      SETUP.forEach { sql -> driver.execute(null, sql, 0).await() }
      val syncDao = SyncDao(database, driver, Clock.System)
      val controller =
        object : BudgetSyncController {
          override suspend fun syncChanges(changes: List<LocalChange>) {
            syncDao.sendMessages(changes)
          }

          override fun schedule() = Unit
        }
      var next = 1
      val dao = BudgetStructureDao(database, contexts())
      val manager =
        CategoryManager(
          dao = dao,
          preferencesDao = PreferencesDao(database, contexts()),
          syncController = controller,
          uuidGenerator = { "new-${next++}" },
        )
      Scope(manager, dao, driver).action()
    }
  }

  private fun TestScope.contexts() = TestCoroutineContexts(StandardTestDispatcher(testScheduler))

  private companion object {
    val USUAL = CategoryGroupId("usual")
    val INCOME = CategoryGroupId("income")
    val FOOD = CategoryId("food")
    val FUN = CategoryId("fun")
    val SALARY = CategoryId("salary")

    @Suppress("MaxLineLength", "TrimMultilineRawString")
    val SETUP =
      listOf(
        "INSERT INTO category_groups(id, name, is_income, sort_order) VALUES ('usual', 'Usual', 0, 16384)",
        "INSERT INTO category_groups(id, name, is_income, sort_order) VALUES ('income', 'Income', 1, 32768)",
        "INSERT INTO categories(id, name, is_income, cat_group, sort_order) VALUES ('food', 'Food', 0, 'usual', 16384)",
        "INSERT INTO categories(id, name, is_income, cat_group, sort_order) VALUES ('fun', 'Fun', 0, 'usual', 32768)",
        "INSERT INTO categories(id, name, is_income, cat_group, sort_order) VALUES ('salary', 'Salary', 1, 'income', 16384)",
        "INSERT INTO category_mapping(id, transferId) VALUES ('food', 'food'), ('fun', 'fun'), ('salary', 'salary')",
        "INSERT INTO accounts(id, name, offbudget) VALUES ('on', 'On', 0)",
        "INSERT INTO transactions(id, acct, category, amount, date, tombstone) VALUES ('t1', 'on', 'fun', -500, 20260310, 0)",
        """
        INSERT INTO zero_budgets(id, month, category, amount) VALUES
          ('202603-food', 202603, 'food', 100),
          ('202603-fun', 202603, 'fun', 50),
          ('202604-fun', 202604, 'fun', 20)
        """,
      )
  }
}
