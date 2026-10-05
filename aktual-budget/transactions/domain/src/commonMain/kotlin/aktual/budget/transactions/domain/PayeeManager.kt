package aktual.budget.transactions.domain

import aktual.budget.BudgetSyncController
import aktual.budget.db.dao.BudgetStructureDao
import aktual.budget.db.dao.DatabaseTables.PAYEES
import aktual.budget.db.dao.DatabaseTables.PAYEE_MAPPING
import aktual.budget.db.dao.PayeeDao
import aktual.budget.model.LocalChange
import aktual.budget.model.MessageValue
import aktual.budget.model.PayeeId
import aktual.budget.model.tombstone
import aktual.di.BudgetScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** A payee as the payees screen lists it */
data class ManagedPayee(
  val id: PayeeId,
  val name: String,
  val isFavorite: Boolean,
  val learnsCategories: Boolean,
  val transactionCount: Long,
)

/** Why a change to a payee was refused */
sealed class PayeeChangeException(message: String) : IllegalArgumentException(message) {
  class DuplicateName(val name: String) : PayeeChangeException("'$name' already exists")

  class BlankName : PayeeChangeException("A name is needed")
}

/**
 * Adds, renames, deletes and merges payees, as upstream's db/index.ts and payees/app.ts do.
 * Transfer payees stand for accounts, so they're left alone.
 */
@Inject
@SingleIn(BudgetScope::class)
class PayeeManager(
  private val dao: BudgetStructureDao,
  private val payeeDao: PayeeDao,
  private val writer: TransactionWriter,
  private val syncController: BudgetSyncController,
) {
  fun observe(): Flow<List<ManagedPayee>> =
    dao.observePayees().map { rows ->
      rows.map { row ->
        ManagedPayee(
          id = row.id,
          name = row.name,
          isFavorite = row.favorite == true,
          // Upstream treats a missing value as learning
          learnsCategories = row.learn_categories != 0L,
          transactionCount = row.transactions,
        )
      }
    }

  /** insertPayee() */
  suspend fun create(name: String): PayeeId {
    val trimmed = validName(name, except = null)
    return writer.write { insertPayee(trimmed) }
  }

  suspend fun rename(id: PayeeId, name: String) {
    val trimmed = validName(name, except = id)
    sync(listOf(LocalChange(PAYEES, id.value, "name", MessageValue.String(trimmed))))
  }

  suspend fun setFavorite(id: PayeeId, favorite: Boolean) =
    sync(listOf(LocalChange(PAYEES, id.value, "favorite", favorite.number())))

  suspend fun setLearnCategories(id: PayeeId, learn: Boolean) =
    sync(listOf(LocalChange(PAYEES, id.value, "learn_categories", learn.number())))

  /** deletePayee(): transactions keep pointing at a deleted payee, as upstream leaves them */
  suspend fun delete(ids: Collection<PayeeId>) {
    val deletable = ids.filter { payeeDao[it]?.transfer_acct == null }
    sync(deletable.map { tombstone(PAYEES, it.value) })
  }

  /**
   * mergePayees(): every transaction of [ids] (and of payees already merged into them) now shows
   * [target], and [ids] are deleted
   */
  suspend fun merge(target: PayeeId, ids: Collection<PayeeId>) {
    if (payeeDao[target]?.transfer_acct != null) return
    val merging = ids.filter { it != target && payeeDao[it]?.transfer_acct == null }
    val targetValue = MessageValue.String(target.value)
    val changes = merging.flatMap { id ->
      dao.payeesMappedTo(id).map { mapped ->
        LocalChange(PAYEE_MAPPING, mapped.value, "targetId", targetValue)
      } +
        LocalChange(PAYEE_MAPPING, id.value, "targetId", targetValue) +
        tombstone(PAYEES, id.value)
    }
    sync(changes.distinct())
  }

  private suspend fun validName(name: String, except: PayeeId?): String {
    val trimmed = name.trim().ifEmpty { throw PayeeChangeException.BlankName() }
    val clash =
      dao.observePayees().first().any {
        it.id != except && it.name.equals(trimmed, ignoreCase = true)
      }
    if (clash) throw PayeeChangeException.DuplicateName(trimmed)
    return trimmed
  }

  private suspend fun sync(changes: List<LocalChange>) {
    if (changes.isNotEmpty()) syncController.syncChanges(changes)
  }
}

private fun Boolean.number() = MessageValue.Number(if (this) 1 else 0)
