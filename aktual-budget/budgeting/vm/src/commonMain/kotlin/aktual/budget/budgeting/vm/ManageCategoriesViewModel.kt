package aktual.budget.budgeting.vm

import aktual.budget.budgeting.domain.CategoryChangeException
import aktual.budget.budgeting.domain.CategoryManager
import aktual.budget.budgeting.domain.Direction
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.di.BudgetScope
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import logcat.logcat

/** Adds, renames, hides, moves and deletes the budget's categories and groups */
@Stable
@ViewModelKey
@ContributesIntoMap(BudgetScope::class)
class ManageCategoriesViewModel(private val categories: CategoryManager) : ViewModel() {
  private val mutableEvents =
    MutableSharedFlow<BudgetEvent>(
      replay = 0,
      extraBufferCapacity = 1,
      onBufferOverflow = DROP_OLDEST,
    )
  val events: SharedFlow<BudgetEvent> = mutableEvents.asSharedFlow()

  fun createGroup(name: String) = change { categories.createGroup(name) }

  fun renameGroup(id: CategoryGroupId, name: String) = change { categories.renameGroup(id, name) }

  fun setGroupHidden(id: CategoryGroupId, hidden: Boolean) = change {
    categories.setGroupHidden(id, hidden)
  }

  fun moveGroup(id: CategoryGroupId, direction: Direction) = change {
    categories.moveGroup(id, direction)
  }

  fun createCategory(name: String, group: CategoryGroupId) = change {
    categories.createCategory(name, group)
  }

  fun renameCategory(id: CategoryId, name: String) = change { categories.renameCategory(id, name) }

  fun setCategoryHidden(id: CategoryId, hidden: Boolean) = change {
    categories.setCategoryHidden(id, hidden)
  }

  fun moveCategory(id: CategoryId, direction: Direction) = change {
    categories.moveCategory(id, direction)
  }

  fun moveCategoryToGroup(id: CategoryId, group: CategoryGroupId) = change {
    categories.moveCategoryToGroup(id, group)
  }

  /** Asks whether deleting needs somewhere for the transactions and budgets to go */
  fun requestDelete(target: DeleteTarget) = change {
    val needsTransfer =
      when (target) {
        is DeleteTarget.Category -> categories.needsTransfer(target.id)
        is DeleteTarget.Group -> categories.groupNeedsTransfer(target.id)
      }
    mutableEvents.emit(BudgetEvent.ConfirmDelete(target, needsTransfer))
  }

  fun delete(target: DeleteTarget, transferTo: CategoryId?) = change {
    when (target) {
      is DeleteTarget.Category -> categories.deleteCategory(target.id, transferTo)
      is DeleteTarget.Group -> categories.deleteGroup(target.id, transferTo)
    }
  }

  private fun change(block: suspend () -> Unit) {
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: CategoryChangeException.DuplicateName) {
        mutableEvents.emit(BudgetEvent.DuplicateName(e.name))
      } catch (_: CategoryChangeException.BlankName) {
        mutableEvents.emit(BudgetEvent.BlankName)
      } catch (_: CategoryChangeException.IncomeMismatch) {
        mutableEvents.emit(BudgetEvent.IncomeMismatch)
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logcat.w(e) { "Failed changing categories" }
        mutableEvents.emit(BudgetEvent.SaveFailed)
      }
    }
  }
}
