package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.model.Amount
import aktual.budget.model.parseAmountInput
import aktual.budget.model.toInputText
import aktual.core.l10n.Strings
import aktual.core.ui.AktualTextField
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.BareTextButton
import aktual.core.ui.NormalTextButton
import aktual.core.ui.PrimaryTextButton
import aktual.core.ui.formattedString
import aktual.core.ui.stringLong
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

@Composable
internal fun EditBudgetSheet(
  category: CategoryState,
  month: YearMonth,
  isEnvelope: Boolean,
  showBudget: Boolean,
  onDismiss: () -> Unit,
  onSave: (Amount) -> Unit,
  onMore: (CategoryAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Fully open only, so the sheet stays above the keyboard
  val sheetState =
    rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded))
  val scope = rememberCoroutineScope()
  val field = rememberTextFieldState(initialText = category.budgeted.toInputText())
  val typed = parseAmountInput(field.text.toString())

  fun close() {
    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
  }

  fun save() {
    val amount = typed ?: return
    if (amount != category.budgeted) onSave(amount)
    close()
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
        Text(text = category.name, style = typography.headlineSmall)
        Text(
          text = Strings.budgetingEditHint(month.stringLong()),
          style = typography.bodyMedium,
          color = colors.pageTextSubdued,
        )
      }

      // Envelope income isn't budgeted, so only its options show
      if (showBudget) {
        BudgetEntry(
          category = category,
          field = field,
          canSave = typed != null,
          onSave = ::save,
          onCancel = ::close,
        )
      }

      CategoryActions(
        category = category,
        isEnvelope = isEnvelope,
        onAction = { action ->
          // Reordering keeps the sheet open, so a category can be moved several places
          if (action is CategoryAction.Reorder) {
            onMore(action)
          } else {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onMore(action) }
          }
        },
      )
    }
  }
}

@Composable
private fun BudgetEntry(
  category: CategoryState,
  field: TextFieldState,
  canSave: Boolean,
  onSave: () -> Unit,
  onCancel: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(BudgetDS.sheetSpacing)) {
    AktualTextField(
      modifier = Modifier.fillMaxWidth(),
      state = field,
      placeholderText = ZERO_PLACEHOLDER,
      singleLine = true,
      keyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
      onKeyboardAction = { onSave() },
      textStyle = typography.headlineSmall.tabularFigures().copy(color = colors.pageText),
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      if (!category.isIncome) {
        Text(
          text = Strings.budgetingEditSpent(category.spent.formattedString()),
          style = typography.bodySmall,
          color = colors.pageTextSubdued,
        )
      }
      Text(
        text = Strings.budgetingEditLastMonth(category.lastMonthBudgeted.formattedString()),
        style = typography.bodySmall,
        color = colors.pageTextSubdued,
      )
    }

    // Shortcuts upstream offers from a category's budget menu
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BareTextButton(
        text = Strings.budgetingEditUseLastMonth,
        isEnabled = category.lastMonthBudgeted != Amount.Zero,
        onClick = { field.setTextAndPlaceCursorAtEnd(category.lastMonthBudgeted.toInputText()) },
      )
      if (!category.isIncome) {
        BareTextButton(
          text = Strings.budgetingEditCoverSpending,
          isEnabled = category.spent > Amount.Zero,
          onClick = { field.setTextAndPlaceCursorAtEnd(category.spent.toInputText()) },
        )
      }
      BareTextButton(
        text = Strings.budgetingEditClear,
        onClick = { field.setTextAndPlaceCursorAtEnd("") },
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      NormalTextButton(
        modifier = Modifier.weight(1f),
        text = Strings.budgetingEditCancel,
        onClick = onCancel,
      )
      PrimaryTextButton(
        modifier = Modifier.weight(1f),
        text = Strings.budgetingEditSave,
        isEnabled = canSave,
        onClick = onSave,
      )
    }
  }
}

internal const val ZERO_PLACEHOLDER = "0.00"
