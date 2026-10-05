package aktual.budget.transactions.domain

import aktual.budget.model.AccountId
import aktual.budget.model.Amount
import aktual.budget.model.CategoryId
import aktual.budget.model.PayeeId
import aktual.budget.model.TransactionId
import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import kotlin.test.Test
import kotlinx.datetime.LocalDate

internal class TransactionEditorTest {
  @Test
  fun `Create a plain transaction with a new payee`() = runEditorTest {
    val id = editor.create(draft(newPayeeName = "Corner shop", category = GROCERIES))

    val row = transactionDao.row(id)
    assertThat(row).isNotNull().all {
      prop("acct") { it.acct }.isEqualTo(CHECKING)
      prop("amount") { it.amount }.isEqualTo(Amount(-1_500))
      prop("category") { it.category }.isEqualTo(GROCERIES)
      prop("transferred_id") { it.transferred_id }.isNull()
    }
    assertThat(payeeDao.name(requireNotNull(row?.description))).isEqualTo("Corner shop")
  }

  @Test
  fun `A typed payee that already exists is reused`() = runEditorTest {
    val existing = writer.write { insertPayee("Corner Shop") }
    val id = editor.create(draft(newPayeeName = "corner shop"))
    assertThat(transactionDao.row(id)?.description).isEqualTo(existing)
  }

  @Test
  fun `A transfer between budget accounts creates the other side without categories`() =
    runEditorTest {
      val id = editor.create(draft(payee = toSavings, category = GROCERIES))

      val row = requireNotNull(transactionDao.row(id))
      val other = requireNotNull(row.transferred_id)
      assertThat(row.category).isNull()
      assertThat(transactionDao.row(other)).isNotNull().all {
        prop("acct") { it.acct }.isEqualTo(SAVINGS)
        prop("amount") { it.amount }.isEqualTo(Amount(1_500))
        prop("description") { it.description }.isEqualTo(toChecking)
        prop("transferred_id") { it.transferred_id }.isEqualTo(id)
        prop("category") { it.category }.isNull()
        prop("cleared") { it.cleared }.isEqualTo(false)
      }
    }

  @Test
  fun `A transfer to an off-budget account keeps its category`() = runEditorTest {
    val id = editor.create(draft(payee = toInvestments, category = GROCERIES))
    val row = requireNotNull(transactionDao.row(id))
    assertThat(row.category).isEqualTo(GROCERIES)
    assertThat(row.transferred_id).isNotNull()
  }

  @Test
  fun `Changing a transfer's amount updates the other side`() = runEditorTest {
    val id = editor.create(draft(payee = toSavings))
    editor.update(id, draft(payee = toSavings, amount = Amount(-2_000), notes = "Rainy day"))

    val other = requireNotNull(transactionDao.row(id)?.transferred_id)
    assertThat(transactionDao.row(other)).isNotNull().all {
      prop("amount") { it.amount }.isEqualTo(Amount(2_000))
      prop("notes") { it.notes }.isEqualTo("Rainy day")
    }
  }

  @Test
  fun `Switching away from a transfer payee deletes the other side`() = runEditorTest {
    val id = editor.create(draft(payee = toSavings))
    val other = requireNotNull(transactionDao.row(id)?.transferred_id)

    editor.update(id, draft(newPayeeName = "Corner shop"))

    assertThat(transactionDao.row(id)?.transferred_id).isNull()
    assertThat(transactionDao.row(other)?.tombstone).isEqualTo(true)
  }

  @Test
  fun `Deleting a transfer deletes both sides`() = runEditorTest {
    val id = editor.create(draft(payee = toSavings))
    val other = requireNotNull(transactionDao.row(id)?.transferred_id)

    editor.delete(id)

    assertThat(transactionDao.row(id)?.tombstone).isEqualTo(true)
    assertThat(transactionDao.row(other)?.tombstone).isEqualTo(true)
  }

  @Test
  fun `Load reports transfers and splits`() = runEditorTest {
    val transfer = editor.create(draft(payee = toSavings))
    val parent = writer.write {
      insert(NewTransaction(account = CHECKING, date = DATE, amount = Amount(-10), isParent = true))
    }

    assertThat(editor.load(transfer)).isNotNull().all {
      prop(EditableTransaction::isTransfer).isEqualTo(true)
      prop(EditableTransaction::isSplit).isEqualTo(false)
    }
    assertThat(editor.load(parent)).isNotNull().prop(EditableTransaction::isSplit).isEqualTo(true)
    assertThat(editor.load(TransactionId("missing"))).isNull()
  }

  private fun draft(
    payee: PayeeId? = null,
    newPayeeName: String? = null,
    category: CategoryId? = null,
    amount: Amount = Amount(-1_500),
    notes: String? = null,
  ) =
    TransactionDraft(
      account = CHECKING,
      date = DATE,
      amount = amount,
      payee = payee,
      newPayeeName = newPayeeName,
      category = category,
      notes = notes,
    )
}

private val CHECKING = AccountId("checking")
private val SAVINGS = AccountId("savings")
private val INVESTMENTS = AccountId("investments")
private val GROCERIES = CategoryId("groceries")
private val DATE = LocalDate(2026, 10, 1)

private class EditorTestScope(
  scope: WriterTestScope,
  val toSavings: PayeeId,
  val toChecking: PayeeId,
  val toInvestments: PayeeId,
) {
  val writer = scope.writer
  val transactionDao = scope.transactionDao
  val payeeDao = scope.payeeDao
  val editor =
    TransactionEditor(
      writer = scope.writer,
      accountDao = scope.accountDao,
      categoryDao = scope.categoryDao,
      payeeDao = scope.payeeDao,
      transactionDao = scope.transactionDao,
    )
}

private fun runEditorTest(action: suspend EditorTestScope.() -> Unit) = runWriterTest {
  insertAccount(CHECKING)
  insertAccount(SAVINGS)
  insertAccount(INVESTMENTS, offBudget = true)
  insertCategory(GROCERIES, name = "Groceries")
  val scope = writer.write {
    EditorTestScope(
      scope = this@runWriterTest,
      toSavings = insertPayee("", transferAccount = SAVINGS),
      toChecking = insertPayee("", transferAccount = CHECKING),
      toInvestments = insertPayee("", transferAccount = INVESTMENTS),
    )
  }
  scope.action()
}
