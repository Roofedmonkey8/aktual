package aktual.budget.transactions.ui.edit

import aktual.budget.model.AccountId
import aktual.budget.model.Amount
import aktual.budget.model.CategoryId
import aktual.budget.transactions.domain.PickerPayee
import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

@Immutable
internal sealed interface EditTransactionAction {
  data object Leave : EditTransactionAction

  data object Save : EditTransactionAction

  data object Delete : EditTransactionAction

  data class Open(val sheet: Sheet) : EditTransactionAction

  data object CloseSheet : EditTransactionAction

  data class SetAmount(val amount: Amount) : EditTransactionAction

  data class SetDeposit(val isDeposit: Boolean) : EditTransactionAction

  data class SetPayee(val payee: PickerPayee) : EditTransactionAction

  data class CreatePayee(val name: String) : EditTransactionAction

  data class SetCategory(val category: CategoryId?) : EditTransactionAction

  data class SetAccount(val account: AccountId) : EditTransactionAction

  data class SetDate(val date: LocalDate) : EditTransactionAction

  data class SetNotes(val notes: String) : EditTransactionAction

  data class SetCleared(val cleared: Boolean) : EditTransactionAction
}

@Immutable
internal fun interface EditTransactionActionHandler {
  operator fun invoke(action: EditTransactionAction)
}

internal enum class Sheet {
  Payee,
  Category,
  Account,
  Date,
  DeleteConfirm,
  DiscardConfirm,
}
