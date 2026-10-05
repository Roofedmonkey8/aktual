package aktual.budget.transactions.domain

import aktual.budget.db.dao.BudgetStructureDao
import aktual.budget.model.AccountId
import aktual.budget.model.Amount
import alakazam.test.TestCoroutineContexts
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate

internal class PayeeManagerTest {
  @Test
  fun `Payees are listed with their transaction counts, transfers left out`() = runPayeeTest {
    val shop = manager.create("Shop")
    writer.write { insertPayee("", transferAccount = CHECKING) }
    writer.write { insert(NewTransaction(account = CHECKING, date = DATE, payee = shop)) }

    assertThat(manager.observe().first().map { it.name to it.transactionCount })
      .containsExactly("Shop" to 1L)
  }

  @Test
  fun `Names have to be unique`() = runPayeeTest {
    manager.create("Shop")
    val other = manager.create("Cafe")
    assertFailure { manager.create("shop") }.isInstanceOf<PayeeChangeException.DuplicateName>()
    assertFailure { manager.rename(other, " SHOP ") }
      .isInstanceOf<PayeeChangeException.DuplicateName>()
    assertFailure { manager.create(" ") }.isInstanceOf<PayeeChangeException.BlankName>()
  }

  @Test
  fun `Renaming, favouriting and learning are saved`() = runPayeeTest {
    val id = manager.create("Shop")
    manager.rename(id, "Corner shop")
    manager.setFavorite(id, true)
    manager.setLearnCategories(id, false)

    val payee = manager.observe().first().single()
    assertThat(payee)
      .isEqualTo(ManagedPayee(id, "Corner shop", isFavorite = true, learnsCategories = false, 0L))
  }

  @Test
  fun `Merging moves transactions to the target and deletes the rest`() = runPayeeTest {
    val shop = manager.create("Shop")
    val shop2 = manager.create("Shop 2")
    val old = manager.create("Old shop")
    // Old shop was already merged into Shop 2, so it follows it
    manager.merge(shop2, listOf(old))
    val transaction = writer.write {
      insert(NewTransaction(account = CHECKING, date = DATE, amount = Amount(-1), payee = old))
    }

    manager.merge(shop, listOf(shop2))

    assertThat(manager.observe().first().map { it.name to it.transactionCount })
      .containsExactly("Shop" to 1L)
    assertThat(transactionDao.row(transaction)?.description).isEqualTo(old)
  }

  @Test
  fun `Transfer payees can't be deleted`() = runPayeeTest {
    val transfer = writer.write { insertPayee("", transferAccount = CHECKING) }
    val shop = manager.create("Shop")
    manager.delete(listOf(transfer, shop))

    assertThat(payeeDao[transfer]?.tombstone).isEqualTo(false)
    assertThat(payeeDao[shop]?.tombstone).isEqualTo(true)
  }

  private class PayeeTestScope(scope: WriterTestScope, val manager: PayeeManager) {
    val writer = scope.writer
    val payeeDao = scope.payeeDao
    val transactionDao = scope.transactionDao
  }

  private fun runPayeeTest(action: suspend PayeeTestScope.() -> Unit) = runWriterTest {
    insertAccount(CHECKING)
    val manager =
      PayeeManager(
        dao = BudgetStructureDao(database, TestCoroutineContexts(UnconfinedTestDispatcher())),
        payeeDao = payeeDao,
        writer = writer,
        syncController = this,
      )
    PayeeTestScope(this, manager).action()
  }

  private companion object {
    val CHECKING = AccountId("checking")
    val DATE = LocalDate(2026, 10, 1)
  }
}
