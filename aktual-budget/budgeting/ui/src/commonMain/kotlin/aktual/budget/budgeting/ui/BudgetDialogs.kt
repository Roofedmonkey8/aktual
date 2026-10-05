package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.BudgetContent
import aktual.budget.budgeting.vm.BudgetState
import aktual.budget.budgeting.vm.BudgetSummary
import aktual.budget.budgeting.vm.DeleteTarget
import aktual.budget.budgeting.vm.GroupState
import aktual.core.l10n.Strings
import aktual.core.ui.stringLong
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Composable
internal fun BudgetDialogs(
  dialog: BudgetDialog?,
  state: BudgetState,
  onAction: BudgetActionHandler,
) {
  val content = state.content as? BudgetContent.Loaded
  if (dialog != null && content != null) {
    LoadedDialog(dialog = dialog, state = state, content = content, onAction = onAction)
  }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun LoadedDialog(
  dialog: BudgetDialog,
  state: BudgetState,
  content: BudgetContent.Loaded,
  onAction: BudgetActionHandler,
) {
  val onOpen = { next: BudgetDialog? -> onAction(BudgetAction.Open(next)) }
  val dismiss = { onOpen(null) }
  val groups = (content.expenseGroups + content.incomeGroups).toImmutableList()
  val categories = groups.flatMap { it.categories }.associateBy { it.id }
  val groupsById = groups.associateBy { it.id }
  val envelope = content.summary as? BudgetSummary.Envelope
  val monthName = state.month.stringLong()

  when (dialog) {
    is BudgetDialog.Category -> {
      categories[dialog.id]?.let { category ->
        EditBudgetSheet(
          category = category,
          month = state.month,
          isEnvelope = envelope != null,
          showBudget = envelope == null || !category.isIncome,
          onDismiss = dismiss,
          onSave = { amount -> onAction(BudgetAction.SetBudget(category.id, amount)) },
          onMore = { action -> onCategoryAction(action, onAction, onOpen) },
        )
      }
    }

    is BudgetDialog.Move -> {
      val from = dialog.from?.let(categories::get)
      val available = if (dialog.from != null) from?.balance else envelope?.toBudget
      if (available != null) {
        MoveMoneySheet(
          from = from,
          available = available,
          groups = groups,
          onMove = { to, amount -> onAction(BudgetAction.MoveMoney(dialog.from, to, amount)) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.Cover -> {
      val category = categories[dialog.id]
      if (category != null && envelope != null) {
        CoverSheet(
          category = category,
          monthName = monthName,
          toBudget = envelope.toBudget,
          groups = groups,
          onCover = { from -> onAction(BudgetAction.CoverOverspending(category.id, from)) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.Notes -> {
      val category = dialog.id?.let(categories::get)
      NotesSheet(
        title = category?.name ?: monthName,
        note = if (dialog.id != null) category?.note else state.monthNote,
        onSave = { note -> onAction(BudgetAction.SetNote(dialog.id, note)) },
        onDismiss = dismiss,
      )
    }

    BudgetDialog.ToBudget -> {
      envelope?.let { summary ->
        ToBudgetSheet(
          toBudget = summary.toBudget,
          held = summary.forNextMonth,
          onMove = { onOpen(BudgetDialog.Move(from = null)) },
          onHold = { onOpen(BudgetDialog.Hold) },
          onResetHold = { onAction(BudgetAction.ResetHold) },
          onDismiss = dismiss,
        )
      }
    }

    BudgetDialog.Hold -> {
      envelope?.let { summary ->
        HoldSheet(
          toBudget = summary.toBudget,
          onHold = { amount -> onAction(BudgetAction.Hold(amount)) },
          onDismiss = dismiss,
        )
      }
    }

    BudgetDialog.CopyLastMonth -> {
      ConfirmDialog(
        title = Strings.budgetingCopyTitle,
        message = Strings.budgetingCopyMessage(monthName),
        confirm = Strings.budgetingConfirm,
        onConfirm = { onAction(BudgetAction.CopyLastMonth) },
        onDismiss = dismiss,
      )
    }

    BudgetDialog.NewGroup -> {
      NameSheet(
        title = Strings.budgetingNewGroupTitle,
        initial = "",
        confirm = Strings.budgetingAdd,
        onSave = { onAction(BudgetAction.CreateGroup(it)) },
        onDismiss = dismiss,
      )
    }

    is BudgetDialog.GroupOptions -> {
      groupsById[dialog.id]?.let { group ->
        GroupOptionsSheet(
          group = group,
          onAddCategory = { onOpen(BudgetDialog.NewCategory(group.id)) },
          onRename = { onOpen(BudgetDialog.RenameGroup(group.id)) },
          onVisibilityChange = { onAction(BudgetAction.SetGroupHidden(group.id, it)) },
          onReorder = { onAction(BudgetAction.MoveGroup(group.id, it)) },
          onDelete = { onAction(BudgetAction.RequestDelete(DeleteTarget.Group(group.id))) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.RenameGroup -> {
      groupsById[dialog.id]?.let { group ->
        NameSheet(
          title = Strings.budgetingRenameTitle(group.name),
          initial = group.name,
          confirm = Strings.budgetingEditSave,
          onSave = { onAction(BudgetAction.RenameGroup(group.id, it)) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.NewCategory -> {
      groupsById[dialog.group]?.let { group ->
        NameSheet(
          title = Strings.budgetingNewCategoryTitle(group.name),
          initial = "",
          confirm = Strings.budgetingAdd,
          onSave = { onAction(BudgetAction.CreateCategory(it, group.id)) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.RenameCategory -> {
      categories[dialog.id]?.let { category ->
        NameSheet(
          title = Strings.budgetingRenameTitle(category.name),
          initial = category.name,
          confirm = Strings.budgetingEditSave,
          onSave = { onAction(BudgetAction.RenameCategory(category.id, it)) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.PickGroup -> {
      categories[dialog.id]?.let { category ->
        PickGroupSheet(
          category = category,
          groups = groups,
          onPick = { onAction(BudgetAction.MoveCategoryToGroup(category.id, it)) },
          onDismiss = dismiss,
        )
      }
    }

    is BudgetDialog.Delete -> {
      DeleteDialog(dialog = dialog, groups = groups, onAction = onAction, onDismiss = dismiss)
    }

    BudgetDialog.SetZero -> {
      ConfirmDialog(
        title = Strings.budgetingZeroTitle,
        message = Strings.budgetingZeroMessage(monthName),
        confirm = Strings.budgetingClearConfirm,
        onConfirm = { onAction(BudgetAction.SetAllToZero) },
        onDismiss = dismiss,
      )
    }
  }
}

private fun onCategoryAction(
  action: CategoryAction,
  onAction: BudgetActionHandler,
  onOpen: (BudgetDialog?) -> Unit,
) {
  when (action) {
    is CategoryAction.Move -> {
      onOpen(BudgetDialog.Move(action.category.id))
    }
    is CategoryAction.Cover -> {
      onOpen(BudgetDialog.Cover(action.category.id))
    }
    is CategoryAction.Notes -> {
      onOpen(BudgetDialog.Notes(action.category.id))
    }
    is CategoryAction.Rename -> {
      onOpen(BudgetDialog.RenameCategory(action.category.id))
    }
    is CategoryAction.SetHidden -> {
      onAction(BudgetAction.SetCategoryHidden(action.category.id, action.hidden))
      onOpen(null)
    }
    is CategoryAction.Reorder -> {
      onAction(BudgetAction.MoveCategory(action.category.id, action.direction))
    }
    is CategoryAction.MoveToGroup -> {
      onOpen(BudgetDialog.PickGroup(action.category.id))
    }
    is CategoryAction.Delete -> {
      onAction(BudgetAction.RequestDelete(DeleteTarget.Category(action.category.id)))
    }
    is CategoryAction.SetRollover -> {
      onAction(BudgetAction.SetRollover(action.category.id, action.rollover))
      onOpen(null)
    }
  }
}

@Composable
private fun DeleteDialog(
  dialog: BudgetDialog.Delete,
  groups: ImmutableList<GroupState>,
  onAction: BudgetActionHandler,
  onDismiss: () -> Unit,
) {
  val target = dialog.target
  val group =
    when (target) {
      is DeleteTarget.Group -> groups.firstOrNull { it.id == target.id }
      is DeleteTarget.Category ->
        groups.firstOrNull { g -> g.categories.any { it.id == target.id } }
    }
  val category =
    (target as? DeleteTarget.Category)?.let { t ->
      group?.categories?.firstOrNull { it.id == t.id }
    }
  val name = category?.name ?: group?.name
  if (group != null && name != null) {
    DeleteSheet(
      name = name,
      isGroup = target is DeleteTarget.Group,
      isIncome = category?.isIncome ?: group.isIncome,
      needsTransfer = dialog.needsTransfer,
      // Money can't go to what's being deleted
      exclude =
        if (target is DeleteTarget.Group) {
          group.categories.map { it.id }.toImmutableSet()
        } else {
          listOfNotNull(category?.id).toImmutableSet()
        },
      groups = groups,
      onDelete = { to -> onAction(BudgetAction.Delete(target, to)) },
      onDismiss = onDismiss,
    )
  }
}
