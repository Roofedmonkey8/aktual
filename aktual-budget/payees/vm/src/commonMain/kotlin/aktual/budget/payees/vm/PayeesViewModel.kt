package aktual.budget.payees.vm

import aktual.budget.model.PayeeId
import aktual.budget.transactions.domain.ManagedPayee
import aktual.budget.transactions.domain.PayeeChangeException
import aktual.budget.transactions.domain.PayeeManager
import aktual.di.BudgetScope
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat

@Immutable
sealed interface PayeesState {
  data object Loading : PayeesState

  /** [payees] is filtered by [query]; [all] is every payee, to pick a merge target from */
  data class Loaded(
    val query: String,
    val payees: ImmutableList<ManagedPayee>,
    val all: ImmutableList<ManagedPayee>,
  ) : PayeesState
}

sealed interface PayeesEvent {
  /** Something was saved, so a sheet open for it can close */
  data object Saved : PayeesEvent

  data class DuplicateName(val name: String) : PayeesEvent

  data object BlankName : PayeesEvent

  data object Failed : PayeesEvent
}

@Stable
@ViewModelKey
@ContributesIntoMap(BudgetScope::class)
class PayeesViewModel(private val manager: PayeeManager) : ViewModel() {
  private val mutableQuery = MutableStateFlow("")

  private val mutableEvents =
    MutableSharedFlow<PayeesEvent>(
      replay = 0,
      extraBufferCapacity = 1,
      onBufferOverflow = DROP_OLDEST,
    )
  val events: SharedFlow<PayeesEvent> = mutableEvents.asSharedFlow()

  val state: StateFlow<PayeesState> =
    combine(manager.observe(), mutableQuery) { payees, query ->
        val trimmed = query.trim()
        PayeesState.Loaded(
          query = query,
          payees =
            payees
              .filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
              .toImmutableList(),
          all = payees.toImmutableList(),
        )
      }
      .stateIn(viewModelScope, WhileSubscribed(STOP_TIMEOUT_MS), PayeesState.Loading)

  fun search(query: String) = mutableQuery.update { query }

  fun create(name: String) = run { manager.create(name) }

  fun rename(id: PayeeId, name: String) = run { manager.rename(id, name) }

  fun setFavorite(id: PayeeId, favorite: Boolean) =
    run(closes = false) { manager.setFavorite(id, favorite) }

  fun setLearnCategories(id: PayeeId, learn: Boolean) =
    run(closes = false) {
      manager.setLearnCategories(id, learn)
    }

  fun merge(id: PayeeId, into: PayeeId) = run { manager.merge(target = into, ids = listOf(id)) }

  fun delete(id: PayeeId) = run { manager.delete(listOf(id)) }

  // A toggle leaves its sheet open; anything else closes the sheet it came from
  private fun run(closes: Boolean = true, block: suspend () -> Unit) {
    viewModelScope.launch {
      val event =
        try {
          block()
          if (closes) PayeesEvent.Saved else null
        } catch (e: CancellationException) {
          throw e
        } catch (e: PayeeChangeException.DuplicateName) {
          PayeesEvent.DuplicateName(e.name)
        } catch (_: PayeeChangeException.BlankName) {
          PayeesEvent.BlankName
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
          logcat.w(e) { "Failed changing payees" }
          PayeesEvent.Failed
        }
      event?.let { mutableEvents.emit(it) }
    }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
