package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.budgeting.vm.GroupState
import aktual.budget.model.Amount
import aktual.budget.model.CategoryId
import aktual.budget.model.parseAmountInput
import aktual.budget.model.toInputText
import aktual.core.icons.material.ChevronRight
import aktual.core.icons.material.MaterialIcons
import aktual.core.l10n.Strings
import aktual.core.ui.AktualAlertDialog
import aktual.core.ui.AktualTextField
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.NormalTextButton
import aktual.core.ui.PrimaryTextButton
import aktual.core.ui.formattedString
import aktual.core.ui.switch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** The extra actions listed in a category's sheet, as upstream's category budget menu has */
@Composable
internal fun CategoryActions(
  category: CategoryState,
  isEnvelope: Boolean,
  onAction: (CategoryAction) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    if (isEnvelope && !category.isIncome) {
      ActionRow(
        text = Strings.budgetingMoveMoney,
        onClick = { onAction(CategoryAction.Move(category)) },
      )
      if (category.balance < Amount.Zero) {
        ActionRow(
          text = Strings.budgetingCover,
          onClick = { onAction(CategoryAction.Cover(category)) },
        )
      }
    }
    if (!category.isIncome || !isEnvelope) {
      SwitchRow(
        text = if (isEnvelope) Strings.budgetingRollover else Strings.budgetingRolloverTracking,
        hint = Strings.budgetingRolloverHint,
        checked = category.rollover,
        onCheckedChange = { onAction(CategoryAction.SetRollover(category, it)) },
      )
    }
    ActionRow(
      text = Strings.budgetingNotes,
      subtitle = category.note?.lineSequence()?.firstOrNull(),
      onClick = { onAction(CategoryAction.Notes(category)) },
    )
  }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit, subtitle: String? = null) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 8.dp),
    verticalAlignment = CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = text, style = typography.bodyLarge)
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = typography.bodySmall,
          color = colors.pageTextSubdued,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Icon(
      modifier = Modifier.size(18.dp),
      imageVector = MaterialIcons.ChevronRight,
      contentDescription = null,
      tint = colors.pageTextSubdued,
    )
  }
}

@Composable
private fun SwitchRow(
  text: String,
  hint: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(role = Role.Switch) { onCheckedChange(!checked) }
        .padding(vertical = 8.dp),
    verticalAlignment = CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = text, style = typography.bodyLarge)
      Text(text = hint, style = typography.bodySmall, color = colors.pageTextSubdued)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange, colors = colors.switch())
  }
}

/** A sheet that's fully open or closed, so it stays above the keyboard */
@Composable
internal fun BudgetSheetFrame(
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit,
) {
  val sheetState =
    rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded))
  val scope = rememberCoroutineScope()
  val close: (() -> Unit) -> Unit = { then ->
    scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
  }

  ModalBottomSheet(
    modifier = modifier,
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.modalBackground,
    contentColor = colors.pageText,
  ) {
    Column(
      modifier =
        Modifier.verticalScroll(rememberScrollState())
          .padding(horizontal = BudgetDS.sheetPadding)
          .padding(bottom = BudgetDS.sheetPadding),
      verticalArrangement = Arrangement.spacedBy(BudgetDS.sheetSpacing),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          modifier = Modifier.semantics { heading() },
          text = title,
          style = typography.headlineSmall,
        )
        if (subtitle != null) {
          Text(text = subtitle, style = typography.bodyMedium, color = colors.pageTextSubdued)
        }
      }
      content(close)
    }
  }
}

/**
 * Moves money out of [from] (null for To Budget) into a category picked here, or back to To Budget.
 * [available] is what [from] has to give, offered as the starting amount.
 */
@Composable
internal fun MoveMoneySheet(
  from: CategoryState?,
  available: Amount,
  groups: ImmutableList<GroupState>,
  onMove: (to: CategoryId?, amount: Amount) -> Unit,
  onDismiss: () -> Unit,
) {
  val field = rememberTextFieldState(initialText = available.positiveOrZero().toInputText())
  val typed = parseAmountInput(field.text.toString())
  var to by remember { mutableStateOf<Target?>(null) }
  val toBudget = Strings.budgetingToBudgetName

  BudgetSheetFrame(
    title = Strings.budgetingMoveTitle(from?.name ?: toBudget),
    subtitle = Strings.budgetingMoveAmountHint(available.formattedString()),
    onDismiss = onDismiss,
  ) { close ->
    AmountField(field)

    Text(
      text = Strings.budgetingMoveTo,
      style = typography.labelLarge,
      color = colors.pageTextSubdued,
    )
    TargetList(
      groups = groups,
      exclude = from?.id,
      includeToBudget = from != null,
      selected = to,
      onSelect = { to = it },
    )

    ConfirmButtons(
      confirm = Strings.budgetingMoveConfirm,
      canConfirm = to != null && typed != null && typed > Amount.Zero,
      onCancel = { close(onDismiss) },
      onConfirm = {
        val target = to ?: return@ConfirmButtons
        val amount = typed ?: return@ConfirmButtons
        close {
          onMove((target as? Target.Category)?.id, amount)
          onDismiss()
        }
      },
    )
  }
}

/** coverOverspending(): picks where the money comes from, only offering what has some left */
@Composable
internal fun CoverSheet(
  category: CategoryState,
  monthName: String,
  toBudget: Amount,
  groups: ImmutableList<GroupState>,
  onCover: (from: CategoryId?) -> Unit,
  onDismiss: () -> Unit,
) {
  var from by remember { mutableStateOf<Target?>(null) }
  val sources =
    groups
      .map { group ->
        group.copy(
          categories =
            group.categories
              .filter { it.balance > Amount.Zero && it.id != category.id }
              .toImmutableList()
        )
      }
      .toImmutableList()
  val hasSources = toBudget > Amount.Zero || sources.any { it.categories.isNotEmpty() }

  BudgetSheetFrame(
    title = Strings.budgetingCoverTitle((-category.balance).formattedString(), category.name),
    subtitle = monthName,
    onDismiss = onDismiss,
  ) { close ->
    if (hasSources) {
      Text(
        text = Strings.budgetingCoverFrom,
        style = typography.labelLarge,
        color = colors.pageTextSubdued,
      )
      TargetList(
        groups = sources,
        exclude = category.id,
        includeToBudget = toBudget > Amount.Zero,
        selected = from,
        onSelect = { from = it },
        showBalance = true,
        toBudgetBalance = toBudget,
      )
    } else {
      Text(text = Strings.budgetingNothingToCover, color = colors.pageTextSubdued)
    }

    ConfirmButtons(
      confirm = Strings.budgetingCover,
      canConfirm = from != null,
      onCancel = { close(onDismiss) },
      onConfirm = {
        val source = from ?: return@ConfirmButtons
        close {
          onCover((source as? Target.Category)?.id)
          onDismiss()
        }
      },
    )
  }
}

/** holdForNextMonth() */
@Composable
internal fun HoldSheet(toBudget: Amount, onHold: (Amount) -> Unit, onDismiss: () -> Unit) {
  val field = rememberTextFieldState(initialText = toBudget.positiveOrZero().toInputText())
  val typed = parseAmountInput(field.text.toString())
  BudgetSheetFrame(
    title = Strings.budgetingHoldTitle,
    subtitle = Strings.budgetingHoldHint(toBudget.formattedString()),
    onDismiss = onDismiss,
  ) { close ->
    AmountField(field)
    ConfirmButtons(
      confirm = Strings.budgetingHoldConfirm,
      canConfirm = typed != null && typed > Amount.Zero,
      onCancel = { close(onDismiss) },
      onConfirm = {
        val amount = typed ?: return@ConfirmButtons
        close {
          onHold(amount)
          onDismiss()
        }
      },
    )
  }
}

/** Notes on a category or a month, stored the way desktop stores them so both show the same */
@Composable
internal fun NotesSheet(
  title: String,
  note: String?,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val field = rememberTextFieldState(initialText = note.orEmpty())
  BudgetSheetFrame(title = Strings.budgetingNotesTitle(title), onDismiss = onDismiss) { close ->
    AktualTextField(
      modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
      state = field,
      placeholderText = Strings.budgetingNotesPlaceholder,
      textStyle = typography.bodyLarge.copy(color = colors.pageText),
    )
    ConfirmButtons(
      confirm = Strings.budgetingEditSave,
      canConfirm = field.text.toString() != note.orEmpty(),
      onCancel = { close(onDismiss) },
      onConfirm = {
        val text = field.text.toString()
        close {
          onSave(text)
          onDismiss()
        }
      },
    )
  }
}

/** Shown on tapping the to-budget summary: upstream's to-budget menu */
@Composable
internal fun ToBudgetSheet(
  toBudget: Amount,
  held: Amount,
  onMove: () -> Unit,
  onHold: () -> Unit,
  onResetHold: () -> Unit,
  onDismiss: () -> Unit,
) {
  BudgetSheetFrame(
    title = Strings.budgetingToBudget,
    subtitle = toBudget.formattedString(),
    onDismiss = onDismiss,
  ) { close ->
    Column {
      if (toBudget > Amount.Zero) {
        ActionRow(text = Strings.budgetingMoveFromToBudget, onClick = { close(onMove) })
        ActionRow(text = Strings.budgetingMenuHold, onClick = { close(onHold) })
      }
      if (held > Amount.Zero) {
        ActionRow(
          text = Strings.budgetingMenuResetHold,
          subtitle = held.formattedString(),
          onClick = {
            close {
              onResetHold()
              onDismiss()
            }
          },
        )
      }
    }
  }
}

@Composable
internal fun ConfirmDialog(
  title: String,
  message: String,
  confirm: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AktualAlertDialog(
    title = title,
    onDismissRequest = onDismiss,
    buttons = {
      TextButton(onClick = onDismiss) { Text(Strings.budgetingEditCancel) }
      TextButton(
        onClick = {
          onConfirm()
          onDismiss()
        }
      ) {
        Text(confirm, color = colors.errorText)
      }
    },
    content = { Text(message) },
  )
}

private sealed interface Target {
  data object ToBudget : Target

  data class Category(val id: CategoryId) : Target
}

@Composable
private fun TargetList(
  groups: ImmutableList<GroupState>,
  exclude: CategoryId?,
  includeToBudget: Boolean,
  selected: Target?,
  onSelect: (Target) -> Unit,
  showBalance: Boolean = false,
  toBudgetBalance: Amount = Amount.Zero,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    if (includeToBudget) {
      TargetRow(
        text = Strings.budgetingToBudgetName,
        balance = toBudgetBalance.takeIf { showBalance },
        isSelected = selected == Target.ToBudget,
        onClick = { onSelect(Target.ToBudget) },
      )
    }
    groups
      .filter { !it.isIncome && it.categories.any { c -> c.id != exclude } }
      .forEach { group ->
        Text(
          modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
          text = group.name.uppercase(),
          style = typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = colors.pageTextSubdued,
        )
        group.categories
          .filter { it.id != exclude }
          .forEach { category ->
            val target = Target.Category(category.id)
            TargetRow(
              text = category.name,
              balance = category.balance.takeIf { showBalance },
              isSelected = selected == target,
              onClick = { onSelect(target) },
            )
          }
      }
  }
}

@Composable
private fun TargetRow(text: String, balance: Amount?, isSelected: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .semantics { selected = isSelected }
        .background(
          if (isSelected) colors.tableRowBackgroundHighlight else colors.modalBackground,
          RoundedRow,
        )
        .clickable(role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 12.dp),
    verticalAlignment = CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = text,
      style = typography.bodyLarge,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (isSelected) colors.tableRowBackgroundHighlightText else colors.pageText,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (balance != null) {
      Text(
        text = balance.formattedString(),
        style = typography.bodyMedium.tabularFigures(),
        color = colors.pageTextSubdued,
      )
    }
  }
}

@Composable
private fun AmountField(field: TextFieldState) {
  AktualTextField(
    modifier = Modifier.fillMaxWidth(),
    state = field,
    placeholderText = ZERO_PLACEHOLDER,
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    textStyle = typography.headlineSmall.tabularFigures().copy(color = colors.pageText),
  )
}

@Composable
private fun ConfirmButtons(
  confirm: String,
  canConfirm: Boolean,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    NormalTextButton(
      modifier = Modifier.weight(1f),
      text = Strings.budgetingEditCancel,
      onClick = onCancel,
    )
    PrimaryTextButton(
      modifier = Modifier.weight(1f),
      text = confirm,
      isEnabled = canConfirm,
      onClick = onConfirm,
    )
  }
}

private fun Amount.positiveOrZero(): Amount = if (this > Amount.Zero) this else Amount.Zero

private val RoundedRow = RoundedCornerShape(8.dp)
