package aktual.budget.transactions.vm.edit

import aktual.budget.model.AccountId
import aktual.budget.model.Amount
import aktual.budget.model.CategoryId
import aktual.budget.transactions.domain.EditableTransaction
import aktual.budget.transactions.domain.EditorOptions
import aktual.budget.transactions.domain.PickerPayee
import aktual.budget.transactions.domain.TransactionDraft
import aktual.budget.transactions.domain.TransactionEditor
import aktual.budget.transactions.vm.edit.EditTransactionState.Editing
import aktual.core.Calendar
import aktual.di.BudgetScope
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import logcat.logcat

@Stable
@AssistedInject
class EditTransactionViewModel(
  @Assisted private val spec: EditTransactionSpec,
  private val editor: TransactionEditor,
  private val calendar: Calendar,
) : ViewModel() {
  @AssistedFactory
  @ManualViewModelAssistedFactoryKey
  @ContributesIntoMap(BudgetScope::class)
  fun interface Factory : ManualViewModelAssistedFactory {
    fun create(@Assisted spec: EditTransactionSpec): EditTransactionViewModel
  }

  private val mutableState = MutableStateFlow<EditTransactionState>(EditTransactionState.Loading)
  val state: StateFlow<EditTransactionState> = mutableState.asStateFlow()

  // What the form held when it opened, to tell whether leaving would lose anything
  private var initialForm: TransactionForm? = null

  private val mutableEvents =
    MutableSharedFlow<EditTransactionEvent>(
      replay = 0,
      extraBufferCapacity = 1,
      onBufferOverflow = DROP_OLDEST,
    )
  val events: SharedFlow<EditTransactionEvent> = mutableEvents.asSharedFlow()

  init {
    viewModelScope.launch { load() }
  }

  val hasChanges: Boolean
    get() = (mutableState.value as? Editing)?.form != initialForm

  fun setAccount(account: AccountId) = updateForm { it.copy(account = account) }

  fun setDate(date: LocalDate) = updateForm { it.copy(date = date) }

  fun setAmount(amount: Amount) = updateForm { it.copy(amount = amount) }

  fun setDeposit(isDeposit: Boolean) = updateForm { it.copy(isDeposit = isDeposit) }

  fun setPayee(payee: PickerPayee) = updateForm { it.copy(payee = payee.id, newPayeeName = null) }

  /** A payee that doesn't exist yet, created when the transaction's saved */
  fun setNewPayee(name: String) = updateForm { form ->
    val trimmed = name.trim()
    val editing = mutableState.value as? Editing
    // Picking a name that's already there is the same as picking that payee
    val existing =
      editing?.payees?.firstOrNull {
        it.transferAccount == null && it.name.equals(trimmed, ignoreCase = true)
      }
    if (existing != null) {
      form.copy(payee = existing.id, newPayeeName = null)
    } else {
      form.copy(payee = null, newPayeeName = trimmed.ifEmpty { null })
    }
  }

  fun clearPayee() = updateForm { it.copy(payee = null, newPayeeName = null) }

  fun setCategory(category: CategoryId?) = updateForm { it.copy(category = category) }

  fun setNotes(notes: String) = updateForm { it.copy(notes = notes) }

  fun setCleared(cleared: Boolean) = updateForm { it.copy(cleared = cleared) }

  fun save() {
    val editing = mutableState.value as? Editing ?: return
    if (!editing.canSave) return
    val form = editing.form
    val account = form.account ?: return
    val draft =
      TransactionDraft(
        account = account,
        date = form.date,
        amount = form.signedAmount,
        payee = form.payee,
        newPayeeName = form.newPayeeName,
        // A category that no longer applies (say, after picking a transfer) isn't kept
        category = form.category.takeIf { editing.canHaveCategory },
        notes = form.notes.trim().ifEmpty { null },
        cleared = form.cleared,
      )

    runSaving(EditTransactionEvent.Saved) {
      when (spec) {
        is EditTransactionSpec.Create -> editor.create(draft)
        is EditTransactionSpec.Edit -> editor.update(spec.id, draft)
      }
    }
  }

  fun delete() {
    val id = (spec as? EditTransactionSpec.Edit)?.id ?: return
    runSaving(EditTransactionEvent.Deleted) { editor.delete(id) }
  }

  private fun runSaving(onSuccess: EditTransactionEvent, block: suspend () -> Unit) {
    mutableState.update { (it as? Editing)?.copy(isSaving = true) ?: it }
    viewModelScope.launch {
      try {
        block()
        initialForm = (mutableState.value as? Editing)?.form
        mutableEvents.emit(onSuccess)
      } catch (e: CancellationException) {
        throw e
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logcat.w(e) { "Failed saving transaction $spec" }
        mutableEvents.emit(EditTransactionEvent.Failed(e.message))
      } finally {
        mutableState.update { (it as? Editing)?.copy(isSaving = false) ?: it }
      }
    }
  }

  private fun updateForm(block: (TransactionForm) -> TransactionForm) =
    mutableState.update { state ->
      if (state is Editing && !state.isSplit) state.copy(form = block(state.form)) else state
    }

  private suspend fun load() {
    val options = editor.options()
    val state =
      when (spec) {
        is EditTransactionSpec.Create -> newTransaction(options, spec.account)
        is EditTransactionSpec.Edit -> editor.load(spec.id)?.let { existing(options, it) }
      }
    initialForm = state?.form
    mutableState.update { state ?: EditTransactionState.NotFound }
  }

  private fun newTransaction(options: EditorOptions, account: AccountId?): Editing {
    // Started from an account, it goes there; otherwise the first budget account, as upstream
    val defaultAccount = account ?: options.accounts.firstOrNull()?.id
    return editing(
      options = options,
      isNew = true,
      form =
        TransactionForm(
          account = defaultAccount,
          date = calendar.today(),
          amount = Amount.Zero,
          isDeposit = false,
          payee = null,
          newPayeeName = null,
          category = null,
          notes = "",
          cleared = true,
        ),
    )
  }

  private fun existing(options: EditorOptions, transaction: EditableTransaction): Editing =
    editing(
      options = options,
      isNew = false,
      isSplit = transaction.isSplit,
      isReconciled = transaction.reconciled,
      form =
        TransactionForm(
          account = transaction.account,
          date = transaction.date,
          amount =
            if (transaction.amount < Amount.Zero) -transaction.amount else transaction.amount,
          isDeposit = transaction.amount > Amount.Zero,
          payee = transaction.payee,
          newPayeeName = null,
          category = transaction.category,
          notes = transaction.notes.orEmpty(),
          cleared = transaction.cleared,
        ),
    )

  private fun editing(
    options: EditorOptions,
    isNew: Boolean,
    form: TransactionForm,
    isSplit: Boolean = false,
    isReconciled: Boolean = false,
  ) =
    Editing(
      isNew = isNew,
      accounts = options.accounts.toImmutableList(),
      payees = options.payees.toImmutableList(),
      categories = options.categories.toImmutableList(),
      form = form,
      isSplit = isSplit,
      isReconciled = isReconciled,
      isSaving = false,
    )
}
