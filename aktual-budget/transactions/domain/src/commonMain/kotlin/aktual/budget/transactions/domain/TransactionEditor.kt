package aktual.budget.transactions.domain

import aktual.budget.db.Transactions
import aktual.budget.db.dao.AccountDao
import aktual.budget.db.dao.CategoryDao
import aktual.budget.db.dao.PayeeDao
import aktual.budget.db.dao.TransactionDao
import aktual.budget.model.AccountId
import aktual.budget.model.Amount
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.budget.model.PayeeId
import aktual.budget.model.TransactionId
import aktual.di.BudgetScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A transaction as the editor shows it */
data class EditableTransaction(
  val id: TransactionId,
  val account: AccountId,
  val date: LocalDate,
  val amount: Amount,
  val payee: PayeeId?,
  val category: CategoryId?,
  val notes: String?,
  val cleared: Boolean,
  val reconciled: Boolean,
  // Splits aren't editable yet, so the editor only shows them
  val isSplit: Boolean,
  val isTransfer: Boolean,
)

/** What the editor saves. A typed payee that doesn't exist yet is in [newPayeeName]. */
data class TransactionDraft(
  val account: AccountId,
  val date: LocalDate,
  val amount: Amount,
  val payee: PayeeId? = null,
  val newPayeeName: String? = null,
  val category: CategoryId? = null,
  val notes: String? = null,
  val cleared: Boolean = true,
)

data class PickerAccount(val id: AccountId, val name: String, val isOffBudget: Boolean)

/** A payee, or for transfers the account it moves money to or from */
data class PickerPayee(val id: PayeeId, val name: String, val transferAccount: AccountId?)

data class PickerCategory(
  val id: CategoryId,
  val name: String,
  val isIncome: Boolean,
  val group: CategoryGroupId,
  val groupName: String,
)

data class EditorOptions(
  val accounts: List<PickerAccount>,
  val payees: List<PickerPayee>,
  val categories: List<PickerCategory>,
)

/**
 * Saves transactions the way upstream's batchUpdateTransactions() does for one added, updated or
 * deleted transaction, including transactions/transfer.ts keeping the other side of a transfer in
 * step. Everything one call changes goes out in a single sync.
 */
@Inject
@SingleIn(BudgetScope::class)
class TransactionEditor(
  private val writer: TransactionWriter,
  private val accountDao: AccountDao,
  private val categoryDao: CategoryDao,
  private val payeeDao: PayeeDao,
  private val transactionDao: TransactionDao,
) {
  suspend fun options(): EditorOptions =
    EditorOptions(
      accounts =
        accountDao.pickerAccounts().map { row ->
          PickerAccount(row.id, row.name.orEmpty(), isOffBudget = row.offbudget == true)
        },
      payees =
        payeeDao.pickerPayees().map { row ->
          PickerPayee(row.id, row.name.orEmpty(), transferAccount = row.transfer_acct)
        },
      categories =
        categoryDao.pickerCategories().map { row ->
          PickerCategory(
            id = row.id,
            name = row.name.orEmpty(),
            isIncome = row.is_income == true,
            group = row.group_id,
            groupName = row.group_name.orEmpty(),
          )
        },
    )

  suspend fun load(id: TransactionId): EditableTransaction? {
    val row = transactionDao.row(id) ?: return null
    if (row.tombstone == true) return null
    return EditableTransaction(
      id = row.id,
      account = row.acct ?: return null,
      date = row.date ?: return null,
      amount = row.amount ?: Amount.Zero,
      payee = row.description,
      category = row.category,
      notes = row.notes,
      cleared = row.cleared != false,
      reconciled = row.reconciled == true,
      isSplit = row.isParent == true || row.isChild == true,
      isTransfer = row.transferred_id != null,
    )
  }

  /** Adds a transaction, and the other side of it when it's a transfer */
  suspend fun create(draft: TransactionDraft): TransactionId = writer.write {
    val payee = resolvePayee(draft)
    val id =
      insert(
        NewTransaction(
          account = draft.account,
          date = draft.date,
          amount = draft.amount,
          payee = payee,
          category = draft.category,
          notes = draft.notes,
          cleared = draft.cleared,
        )
      )
    val transferAccount = payee?.let { transferAccount(it) }
    if (transferAccount != null) {
      addTransfer(id, draft.copy(payee = payee), transferAccount)
    }
    id
  }

  /** Saves changes to a transaction and keeps the other side of a transfer in step */
  suspend fun update(id: TransactionId, draft: TransactionDraft): Unit = writer.write {
    val stored = transactionDao.row(id) ?: error("No transaction $id")
    val payee = resolvePayee(draft)
    update(
      TransactionUpdate(
        id = id,
        account = draft.account,
        date = draft.date,
        amount = draft.amount,
        cleared = draft.cleared,
        payee = Patch.To(payee),
        category = Patch.To(draft.category),
        notes = Patch.To(draft.notes),
      )
    )

    val transferAccount = payee?.let { transferAccount(it) }
    val existing = stored.transferred_id
    val saved = draft.copy(payee = payee)
    when {
      transferAccount != null && existing == null -> addTransfer(id, saved, transferAccount)
      transferAccount == null && existing != null -> removeTransfer(id, existing)
      transferAccount != null && existing != null ->
        updateTransfer(id, existing, saved, transferAccount)
    }
  }

  /** Deletes a transaction, with the other side of a transfer and the lines of a split */
  suspend fun delete(id: TransactionId): Unit = writer.write {
    val stored = transactionDao.row(id)
    val transfer = stored?.transferred_id
    if (transfer != null) removeTransfer(id, transfer, isDeleting = true)
    delete(id)
  }

  private suspend fun TransactionBatch.resolvePayee(draft: TransactionDraft): PayeeId? {
    val name = draft.newPayeeName?.trim()
    return if (!name.isNullOrEmpty()) createPayee(name) else draft.payee
  }

  private suspend fun transferAccount(payee: PayeeId): AccountId? =
    payeeDao[payee]?.takeIf { it.tombstone != true }?.transfer_acct

  // transfer.ts addTransfer(). Unlike upstream, when the other account already has the matching
  // transaction (say both banks reported their side), that one's linked instead of adding another
  private suspend fun TransactionBatch.addTransfer(
    id: TransactionId,
    draft: TransactionDraft,
    transferAccount: AccountId,
  ) {
    val fromPayee = payeeDao.transferPayee(draft.account) ?: return
    val match = findTransferMatch(id, draft, transferAccount)
    if (match != null) {
      update(
        TransactionUpdate(
          id = match.id,
          payee = Patch.To(fromPayee),
          transferId = Patch.To(id),
          // Its own notes are kept, unless it has none
          notes = if (match.notes.isNullOrBlank()) Patch.To(draft.notes) else Patch.Keep,
        )
      )
      update(TransactionUpdate(id = id, transferId = Patch.To(match.id)))
      clearCategory(id, match.id, draft.account, transferAccount)
      return
    }

    val other =
      insert(
        NewTransaction(
          account = transferAccount,
          date = draft.date,
          amount = -draft.amount,
          payee = fromPayee,
          notes = draft.notes,
          transferId = id,
          cleared = false,
        )
      )
    update(TransactionUpdate(id = id, transferId = Patch.To(other)))
    clearCategory(id, other, draft.account, transferAccount)
  }

  // transfer.ts removeTransfer(). A split line on the other side is kept as a normal transaction,
  // since deleting it would break the split. So is one a bank imported, since it really happened;
  // it just stops being a transfer.
  private suspend fun TransactionBatch.removeTransfer(
    id: TransactionId,
    other: TransactionId,
    isDeleting: Boolean = false,
  ) {
    val otherRow = transactionDao.row(other)
    if (otherRow != null && otherRow.tombstone != true) {
      if (otherRow.isChild == true || otherRow.financial_id != null) {
        update(TransactionUpdate(id = other, transferId = Patch.To(null), payee = Patch.To(null)))
      } else {
        delete(other)
      }
    }
    if (!isDeleting) update(TransactionUpdate(id = id, transferId = Patch.To(null)))
  }

  // transfer.ts updateTransfer(). As upstream, the other side keeps its own date
  private suspend fun TransactionBatch.updateTransfer(
    id: TransactionId,
    other: TransactionId,
    draft: TransactionDraft,
    transferAccount: AccountId,
  ) {
    val fromPayee = payeeDao.transferPayee(draft.account)
    update(
      TransactionUpdate(
        id = other,
        account = transferAccount,
        payee = Patch.To(fromPayee),
        notes = Patch.To(draft.notes),
        amount = -draft.amount,
      )
    )
    clearCategory(id, other, draft.account, transferAccount)
  }

  // transfer.ts clearCategory(): between two on-budget or two off-budget accounts, money only
  // moves, so neither side has a category
  private suspend fun TransactionBatch.clearCategory(
    id: TransactionId,
    other: TransactionId,
    account: AccountId,
    transferAccount: AccountId,
  ) {
    if (isOffBudget(account) == isOffBudget(transferAccount)) {
      update(TransactionUpdate(id = id, category = Patch.To(null)))
      update(TransactionUpdate(id = other, category = Patch.To(null)))
    }
  }

  /**
   * The transaction in [transferAccount] most likely to be the other side of this one: the opposite
   * amount, within [TRANSFER_MATCH_DAYS] days, and not already a transfer or part of a split. The
   * closest date wins, then one the bank imported.
   */
  private suspend fun findTransferMatch(
    id: TransactionId,
    draft: TransactionDraft,
    transferAccount: AccountId,
  ): Transactions? {
    if (draft.amount == Amount.Zero) return null
    val candidates =
      transactionDao.transferCandidates(
        account = transferAccount,
        amount = -draft.amount,
        start = draft.date.minus(TRANSFER_MATCH_DAYS, DateTimeUnit.DAY),
        end = draft.date.plus(TRANSFER_MATCH_DAYS, DateTimeUnit.DAY),
        exclude = id,
      )
    return candidates.minWithOrNull(
      compareBy<Transactions> { row ->
          row.date?.let { abs(it.toEpochDays() - draft.date.toEpochDays()) } ?: Long.MAX_VALUE
        }
        .thenBy { if (it.financial_id != null) 0 else 1 }
    )
  }

  private suspend fun isOffBudget(account: AccountId): Boolean =
    accountDao[account]?.offbudget == true
}

/** How far apart two sides of a transfer can be dated, since banks post them on different days */
internal const val TRANSFER_MATCH_DAYS = 5
