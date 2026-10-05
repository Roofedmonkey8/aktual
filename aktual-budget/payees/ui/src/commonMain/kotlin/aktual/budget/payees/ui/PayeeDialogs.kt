package aktual.budget.payees.ui

import aktual.budget.model.PayeeId
import aktual.budget.transactions.domain.ManagedPayee
import aktual.core.icons.material.ChevronRight
import aktual.core.icons.material.MaterialIcons
import aktual.core.l10n.Strings
import aktual.core.ui.AktualAlertDialog
import aktual.core.ui.AktualTextField
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.NormalTextButton
import aktual.core.ui.PrimaryTextButton
import aktual.core.ui.switch
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal sealed interface PayeeDialog {
  data object New : PayeeDialog

  data class Options(val id: PayeeId) : PayeeDialog

  data class Rename(val id: PayeeId) : PayeeDialog

  data class Merge(val id: PayeeId) : PayeeDialog

  data class ConfirmMerge(val id: PayeeId, val into: PayeeId) : PayeeDialog

  data class Delete(val id: PayeeId) : PayeeDialog
}

@Immutable
internal sealed interface PayeeAction {
  data class Search(val query: String) : PayeeAction

  data class Open(val dialog: PayeeDialog?) : PayeeAction

  data class Create(val name: String) : PayeeAction

  data class Rename(val id: PayeeId, val name: String) : PayeeAction

  data class SetFavorite(val id: PayeeId, val favorite: Boolean) : PayeeAction

  data class SetLearn(val id: PayeeId, val learn: Boolean) : PayeeAction

  data class Merge(val id: PayeeId, val into: PayeeId) : PayeeAction

  data class Delete(val id: PayeeId) : PayeeAction
}

@Immutable
internal fun interface PayeeActionHandler {
  operator fun invoke(action: PayeeAction)
}

@Composable
internal fun PayeeDialogs(
  dialog: PayeeDialog?,
  all: ImmutableList<ManagedPayee>,
  onAction: PayeeActionHandler,
) {
  val close = { onAction(PayeeAction.Open(null)) }
  val open = { next: PayeeDialog -> onAction(PayeeAction.Open(next)) }
  fun payee(id: PayeeId) = all.firstOrNull { it.id == id }

  when (dialog) {
    null -> {}
    PayeeDialog.New -> {
      NameSheet(
        title = Strings.payeesNewTitle,
        initial = "",
        confirm = Strings.payeesCreate,
        onSave = { onAction(PayeeAction.Create(it)) },
        onDismiss = close,
      )
    }
    is PayeeDialog.Options -> {
      payee(dialog.id)?.let { payee ->
        OptionsSheet(payee = payee, onAction = onAction, onOpen = open, onDismiss = close)
      }
    }
    is PayeeDialog.Rename -> {
      payee(dialog.id)?.let { payee ->
        NameSheet(
          title = payee.name,
          initial = payee.name,
          confirm = Strings.payeesSave,
          onSave = { onAction(PayeeAction.Rename(payee.id, it)) },
          onDismiss = close,
        )
      }
    }
    is PayeeDialog.Merge -> {
      payee(dialog.id)?.let { payee ->
        MergeSheet(
          payee = payee,
          all = all,
          onPick = { into -> open(PayeeDialog.ConfirmMerge(payee.id, into)) },
          onDismiss = close,
        )
      }
    }
    is PayeeDialog.ConfirmMerge -> {
      val payee = payee(dialog.id)
      val into = payee(dialog.into)
      if (payee != null && into != null) {
        Confirm(
          title = Strings.payeesMergeTitle(payee.name),
          message = Strings.payeesMergeMessage(payee.name, into.name),
          confirm = Strings.payeesMergeConfirm,
          onConfirm = { onAction(PayeeAction.Merge(payee.id, into.id)) },
          onDismiss = close,
        )
      }
    }
    is PayeeDialog.Delete -> {
      payee(dialog.id)?.let { payee ->
        Confirm(
          title = Strings.payeesDeleteTitle(payee.name),
          message = Strings.payeesDeleteMessage,
          confirm = Strings.payeesDeleteConfirm,
          onConfirm = { onAction(PayeeAction.Delete(payee.id)) },
          onDismiss = close,
        )
      }
    }
  }
}

@Composable
private fun OptionsSheet(
  payee: ManagedPayee,
  onAction: PayeeActionHandler,
  onOpen: (PayeeDialog) -> Unit,
  onDismiss: () -> Unit,
) {
  SheetFrame(title = payee.name, onDismiss = onDismiss) {
    Column {
      ActionRow(Strings.budgetingRename) { onOpen(PayeeDialog.Rename(payee.id)) }
      SwitchRow(
        text = Strings.payeesFavorite,
        hint = Strings.payeesFavoriteHint,
        checked = payee.isFavorite,
        onCheckedChange = { onAction(PayeeAction.SetFavorite(payee.id, it)) },
      )
      SwitchRow(
        text = Strings.payeesLearn,
        hint = Strings.payeesLearnHint,
        checked = payee.learnsCategories,
        onCheckedChange = { onAction(PayeeAction.SetLearn(payee.id, it)) },
      )
      ActionRow(Strings.payeesMerge) { onOpen(PayeeDialog.Merge(payee.id)) }
      ActionRow(Strings.payeesDelete, destructive = true) { onOpen(PayeeDialog.Delete(payee.id)) }
    }
  }
}

@Composable
private fun NameSheet(
  title: String,
  initial: String,
  confirm: String,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val field = rememberTextFieldState(initialText = initial)
  val canSave = field.text.isNotBlank() && field.text.toString() != initial
  SheetFrame(title = title, onDismiss = onDismiss) {
    AktualTextField(
      modifier = Modifier.fillMaxWidth(),
      state = field,
      placeholderText = Strings.payeesName,
      singleLine = true,
      keyboardOptions =
        KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
      onKeyboardAction = { if (canSave) onSave(field.text.toString()) },
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      NormalTextButton(
        modifier = Modifier.weight(1f),
        text = Strings.payeesCancel,
        onClick = onDismiss,
      )
      PrimaryTextButton(
        modifier = Modifier.weight(1f),
        text = confirm,
        isEnabled = canSave,
        onClick = { onSave(field.text.toString()) },
      )
    }
  }
}

@Composable
private fun MergeSheet(
  payee: ManagedPayee,
  all: ImmutableList<ManagedPayee>,
  onPick: (PayeeId) -> Unit,
  onDismiss: () -> Unit,
) {
  val search = rememberTextFieldState()
  val query = search.text.toString().trim()
  SheetFrame(title = Strings.payeesMergeTitle(payee.name), onDismiss = onDismiss) {
    AktualTextField(
      modifier = Modifier.fillMaxWidth(),
      state = search,
      placeholderText = Strings.payeesSearch,
      singleLine = true,
      clearable = true,
    )
    Column {
      all
        .filter { it.id != payee.id && (query.isEmpty() || it.name.contains(query, true)) }
        .take(MAX_MERGE_ROWS)
        .forEach { target -> ActionRow(target.name) { onPick(target.id) } }
    }
  }
}

@Composable
private fun SheetFrame(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val sheetState =
    rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded))
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.modalBackground,
    contentColor = colors.pageText,
  ) {
    Column(
      modifier =
        Modifier.verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        modifier = Modifier.semantics { heading() },
        text = title,
        style = typography.headlineSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      content()
    }
  }
}

@Composable
private fun ActionRow(text: String, destructive: Boolean = false, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 8.dp),
    verticalAlignment = CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = text,
      style = typography.bodyLarge,
      color = if (destructive) colors.errorText else colors.pageText,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
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
  var current by remember(checked) { mutableStateOf(checked) }
  val toggle = { value: Boolean ->
    current = value
    onCheckedChange(value)
  }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(role = Role.Switch) { toggle(!current) }
        .padding(vertical = 8.dp),
    verticalAlignment = CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = text, style = typography.bodyLarge)
      Text(text = hint, style = typography.bodySmall, color = colors.pageTextSubdued)
    }
    Switch(checked = current, onCheckedChange = toggle, colors = colors.switch())
  }
}

@Composable
private fun Confirm(
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
      TextButton(onClick = onDismiss) { Text(Strings.payeesCancel) }
      TextButton(onClick = onConfirm) { Text(confirm, color = colors.errorText) }
    },
    content = { Text(message) },
  )
}

private const val MAX_MERGE_ROWS = 50
