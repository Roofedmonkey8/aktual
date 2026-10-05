package aktual.budget.transactions.vm.edit

import aktual.budget.model.AccountId
import aktual.budget.model.Amount
import aktual.budget.model.CategoryId
import aktual.budget.model.PayeeId
import aktual.budget.model.TransactionId
import aktual.budget.transactions.domain.PickerAccount
import aktual.budget.transactions.domain.PickerCategory
import aktual.budget.transactions.domain.PickerPayee
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate

/** Which transaction the editor opens on */
@Immutable
sealed interface EditTransactionSpec {
  data class Create(val account: AccountId?) : EditTransactionSpec

  data class Edit(val id: TransactionId) : EditTransactionSpec
}

@Immutable
sealed interface EditTransactionState {
  data object Loading : EditTransactionState

  data object NotFound : EditTransactionState

  data class Editing(
    val isNew: Boolean,
    val accounts: ImmutableList<PickerAccount>,
    val payees: ImmutableList<PickerPayee>,
    val categories: ImmutableList<PickerCategory>,
    val form: TransactionForm,
    // Split transactions open read-only until splits can be edited here
    val isSplit: Boolean,
    val isReconciled: Boolean,
    val isSaving: Boolean,
  ) : EditTransactionState {
    val account: PickerAccount? = accounts.firstOrNull { it.id == form.account }
    val payee: PickerPayee? = payees.firstOrNull { it.id == form.payee }
    val category: PickerCategory? = categories.firstOrNull { it.id == form.category }
    val transferAccount: PickerAccount? =
      payee?.transferAccount?.let { id -> accounts.firstOrNull { it.id == id } }

    /**
     * Money moving between two budget accounts (or two off-budget ones) isn't spending, so it has
     * no category, and nothing in an off-budget account is budgeted either. As transfer.ts and the
     * desktop editor decide it.
     */
    val canHaveCategory: Boolean
      get() {
        val from = account ?: return true
        val to = transferAccount
        return if (to != null) from.isOffBudget != to.isOffBudget else !from.isOffBudget
      }

    val canSave: Boolean
      get() = !isSplit && !isSaving && form.account != null

    val hasPayee: Boolean
      get() = form.payee != null || !form.newPayeeName.isNullOrBlank()
  }
}

/** What's been entered so far. The amount is unsigned, [isDeposit] gives it a direction. */
@Immutable
data class TransactionForm(
  val account: AccountId?,
  val date: LocalDate,
  val amount: Amount,
  val isDeposit: Boolean,
  val payee: PayeeId?,
  val newPayeeName: String?,
  val category: CategoryId?,
  val notes: String,
  val cleared: Boolean,
) {
  val signedAmount: Amount
    get() = if (isDeposit) amount else -amount
}

sealed interface EditTransactionEvent {
  data object Saved : EditTransactionEvent

  data object Deleted : EditTransactionEvent

  data class Failed(val reason: String?) : EditTransactionEvent
}
