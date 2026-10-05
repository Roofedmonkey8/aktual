package aktual.budget.transactions.ui.edit

import aktual.budget.model.Amount
import aktual.budget.model.parseAmountInput
import aktual.budget.model.toInputText
import aktual.budget.transactions.ui.edit.EditTransactionAction.CloseSheet
import aktual.budget.transactions.ui.edit.EditTransactionAction.CreatePayee
import aktual.budget.transactions.ui.edit.EditTransactionAction.Delete
import aktual.budget.transactions.ui.edit.EditTransactionAction.Leave
import aktual.budget.transactions.ui.edit.EditTransactionAction.Open
import aktual.budget.transactions.ui.edit.EditTransactionAction.Save
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetAccount
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetAmount
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetCategory
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetCleared
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetDate
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetDeposit
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetNotes
import aktual.budget.transactions.ui.edit.EditTransactionAction.SetPayee
import aktual.budget.transactions.vm.edit.EditTransactionEvent
import aktual.budget.transactions.vm.edit.EditTransactionSpec
import aktual.budget.transactions.vm.edit.EditTransactionState
import aktual.budget.transactions.vm.edit.EditTransactionState.Editing
import aktual.budget.transactions.vm.edit.EditTransactionViewModel
import aktual.core.icons.material.CalendarToday
import aktual.core.icons.material.ChevronRight
import aktual.core.icons.material.Delete
import aktual.core.icons.material.MaterialIcons
import aktual.core.icons.material.Warning
import aktual.core.l10n.Strings
import aktual.core.nav.BackNavigator
import aktual.core.ui.AktualAlertDialog
import aktual.core.ui.AktualSlidingToggleButton
import aktual.core.ui.AktualTextField
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.BackHandler
import aktual.core.ui.BareIconButton
import aktual.core.ui.BottomSpacing
import aktual.core.ui.CardShape
import aktual.core.ui.FailureScreen
import aktual.core.ui.LoadingScreen
import aktual.core.ui.NavBackIconButton
import aktual.core.ui.PageBackground
import aktual.core.ui.PrimaryTextButton
import aktual.core.ui.RounderCardShape
import aktual.core.ui.formatted
import aktual.core.ui.switch
import aktual.core.ui.transparentTopAppBarColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.getSelectedDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp.Companion.Hairline
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

@Composable
internal fun EditTransactionScreen(
  spec: EditTransactionSpec,
  back: BackNavigator,
  modifier: Modifier = Modifier,
  viewModel: EditTransactionViewModel = editTransactionViewModel(spec),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val saveFailed = Strings.editTransactionSaveFailed
  var sheet by remember { mutableStateOf<Sheet?>(null) }

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        EditTransactionEvent.Saved,
        EditTransactionEvent.Deleted -> back()
        is EditTransactionEvent.Failed -> snackbar.showSnackbar(saveFailed)
      }
    }
  }

  val onAction = EditTransactionActionHandler { action ->
    when (action) {
      Leave -> if (viewModel.hasChanges) sheet = Sheet.DiscardConfirm else back()
      Save -> viewModel.save()
      Delete -> viewModel.delete()
      is Open -> sheet = action.sheet
      CloseSheet -> sheet = null
      is SetAmount -> viewModel.setAmount(action.amount)
      is SetDeposit -> viewModel.setDeposit(action.isDeposit)
      is SetPayee -> viewModel.setPayee(action.payee)
      is CreatePayee -> viewModel.setNewPayee(action.name)
      is SetCategory -> viewModel.setCategory(action.category)
      is SetAccount -> viewModel.setAccount(action.account)
      is SetDate -> viewModel.setDate(action.date)
      is SetNotes -> viewModel.setNotes(action.notes)
      is SetCleared -> viewModel.setCleared(action.cleared)
    }
  }

  BackHandler(enabled = state is Editing) { onAction(Leave) }

  EditTransactionScaffold(
    modifier = modifier,
    state = state,
    snackbarHostState = snackbar,
    onAction = onAction,
  )

  EditTransactionSheets(
    sheet = sheet,
    state = state as? Editing,
    onAction = onAction,
    onDiscard = {
      sheet = null
      back()
    },
  )
}

@Composable
private fun EditTransactionScaffold(
  state: EditTransactionState,
  snackbarHostState: SnackbarHostState,
  onAction: EditTransactionActionHandler,
  modifier: Modifier = Modifier,
) {
  val editing = state as? Editing
  Scaffold(
    modifier = modifier.fillMaxSize().imePadding(),
    topBar = {
      TopAppBar(
        colors = colors.transparentTopAppBarColors(),
        navigationIcon = { NavBackIconButton(onClick = { onAction(Leave) }) },
        title = {
          Text(
            text =
              if (editing?.isNew != false) {
                Strings.editTransactionNewTitle
              } else {
                Strings.editTransactionTitle
              }
          )
        },
        actions = {
          if (editing != null && !editing.isNew) {
            BareIconButton(
              imageVector = MaterialIcons.Delete,
              contentDescription = Strings.editTransactionDelete,
              enabled = !editing.isSaving,
              onClick = { onAction(EditTransactionAction.Open(Sheet.DeleteConfirm)) },
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (editing != null && !editing.isSplit) {
        PrimaryTextButton(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = EditTransactionDS.pagePadding, vertical = 12.dp),
          text = Strings.editTransactionSave,
          isEnabled = editing.canSave,
          onClick = { onAction(Save) },
        )
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
      PageBackground()

      when (state) {
        EditTransactionState.Loading -> {
          LoadingScreen(modifier = Modifier.padding(innerPadding))
        }
        EditTransactionState.NotFound -> {
          FailureScreen(
            modifier = Modifier.padding(innerPadding),
            title = Strings.editTransactionNotFound,
            reason = null,
            action = null,
          )
        }
        is Editing -> {
          EditTransactionForm(
            modifier = Modifier.padding(innerPadding),
            state = state,
            onAction = onAction,
          )
        }
      }
    }
  }
}

@Composable
private fun EditTransactionSheets(
  sheet: Sheet?,
  state: Editing?,
  onAction: EditTransactionActionHandler,
  onDiscard: () -> Unit,
) {
  val close = { onAction(CloseSheet) }
  if (state == null || sheet == null) return
  when (sheet) {
    Sheet.Payee -> {
      PayeePicker(
        payees = state.payees,
        accounts = state.accounts,
        selected = state.form.payee,
        currentAccount = state.form.account,
        onPick = { onAction(EditTransactionAction.SetPayee(it)) },
        onCreate = { onAction(EditTransactionAction.CreatePayee(it)) },
        onDismiss = close,
      )
    }
    Sheet.Category -> {
      CategoryPicker(
        categories = state.categories,
        selected = state.form.category,
        onPick = { onAction(EditTransactionAction.SetCategory(it)) },
        onDismiss = close,
      )
    }
    Sheet.Account -> {
      AccountPicker(
        accounts = state.accounts,
        selected = state.form.account,
        onPick = { onAction(EditTransactionAction.SetAccount(it)) },
        onDismiss = close,
      )
    }
    Sheet.Date -> {
      DateDialog(
        date = state.form.date,
        onPick = { onAction(EditTransactionAction.SetDate(it)) },
        onDismiss = close,
      )
    }
    Sheet.DeleteConfirm -> {
      ConfirmDialog(
        title = Strings.editTransactionDeleteTitle,
        message =
          if (state.transferAccount != null) {
            Strings.editTransactionDeleteTransferMessage
          } else {
            Strings.editTransactionDeleteMessage
          },
        confirm = Strings.editTransactionDelete,
        onConfirm = {
          close()
          onAction(Delete)
        },
        onCancel = close,
      )
    }
    Sheet.DiscardConfirm -> {
      ConfirmDialog(
        title = Strings.editTransactionDiscardTitle,
        message = Strings.editTransactionDiscardMessage,
        confirm = Strings.editTransactionDiscardConfirm,
        onConfirm = onDiscard,
        onCancel = close,
      )
    }
  }
}

@Composable
private fun editTransactionViewModel(spec: EditTransactionSpec) =
  assistedMetroViewModel<EditTransactionViewModel, EditTransactionViewModel.Factory>(
    key = spec.toString()
  ) {
    create(spec)
  }

@Composable
private fun EditTransactionForm(
  state: Editing,
  onAction: EditTransactionActionHandler,
  modifier: Modifier = Modifier,
) {
  val onOpen = { sheet: Sheet -> onAction(EditTransactionAction.Open(sheet)) }
  val enabled = !state.isSplit && !state.isSaving
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = EditTransactionDS.pagePadding),
    verticalArrangement = Arrangement.spacedBy(EditTransactionDS.fieldSpacing),
  ) {
    if (state.isSplit) {
      Notice(text = Strings.editTransactionSplitNotice)
    } else if (state.isReconciled) {
      Notice(text = Strings.editTransactionReconciledNotice)
    }

    AmountEntry(state = state, enabled = enabled, onAction = onAction)

    FieldCard {
      val payee = state.payee
      val accountNames = state.accounts.associate { it.id to it.name }
      FieldRow(
        label = Strings.editTransactionPayee,
        value =
          when {
            state.form.newPayeeName != null ->
              "${state.form.newPayeeName} · ${Strings.editTransactionNewPayee}"
            payee?.transferAccount != null ->
              Strings.editTransactionTransferTo(accountNames[payee.transferAccount].orEmpty())
            payee != null -> payee.name
            else -> null
          },
        enabled = enabled,
        onClick = { onOpen(Sheet.Payee) },
      )
      FieldDivider()
      FieldRow(
        label = Strings.editTransactionCategory,
        value =
          if (state.canHaveCategory) {
            state.category?.let { "${it.groupName} › ${it.name}" }
          } else {
            null
          },
        hint =
          when {
            state.canHaveCategory -> null
            state.transferAccount != null -> Strings.editTransactionCategoryNotNeeded
            else -> Strings.editTransactionCategoryOffBudget
          },
        enabled = enabled && state.canHaveCategory,
        onClick = { onOpen(Sheet.Category) },
      )
      FieldDivider()
      FieldRow(
        label = Strings.editTransactionAccount,
        value = state.account?.name,
        enabled = enabled,
        onClick = { onOpen(Sheet.Account) },
      )
      FieldDivider()
      FieldRow(
        label = Strings.editTransactionDate,
        value = state.form.date.formatted(),
        icon = MaterialIcons.CalendarToday,
        enabled = enabled,
        onClick = { onOpen(Sheet.Date) },
      )
    }

    FieldCard {
      NotesField(
        initial = state.form.notes,
        enabled = enabled,
        onChange = { onAction(EditTransactionAction.SetNotes(it)) },
      )
      FieldDivider()
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) {
              onAction(EditTransactionAction.SetCleared(!state.form.cleared))
            }
            .padding(horizontal = EditTransactionDS.rowPadding, vertical = 8.dp),
        verticalAlignment = CenterVertically,
      ) {
        Text(
          modifier = Modifier.weight(1f),
          text = Strings.editTransactionCleared,
          style = typography.bodyLarge,
        )
        Switch(
          checked = state.form.cleared,
          enabled = enabled,
          onCheckedChange = { onAction(EditTransactionAction.SetCleared(it)) },
          colors = colors.switch(),
        )
      }
    }

    BottomSpacing()
  }
}

@Composable
private fun AmountEntry(state: Editing, enabled: Boolean, onAction: EditTransactionActionHandler) {
  val field = rememberTextFieldState(initialText = state.form.amount.toInputText())

  // Typed amounts are unsigned and pick up their sign from the payment/deposit toggle
  LaunchedEffect(field) {
    snapshotFlow { field.text.toString() }
      .collect { text ->
        parseAmountInput(text)?.let { onAction(EditTransactionAction.SetAmount(it)) }
      }
  }

  val color: Color =
    when {
      state.form.amount == Amount.Zero -> colors.pageTextSubdued
      state.form.isDeposit -> colors.numberPositive
      else -> colors.pageText
    }

  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalAlignment = CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    AktualSlidingToggleButton(
      modifier = Modifier.fillMaxWidth(),
      selected = state.form.isDeposit,
      options = persistentListOf(false, true),
      onSelect = { if (enabled) onAction(EditTransactionAction.SetDeposit(it)) },
      string = { deposit ->
        if (deposit) Strings.editTransactionDeposit else Strings.editTransactionPayment
      },
    )

    AktualTextField(
      modifier = Modifier.fillMaxWidth(),
      state = field,
      placeholderText = ZERO_PLACEHOLDER,
      isEnabled = enabled,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      textStyle =
        typography.displaySmall.copy(
          color = color,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.SemiBold,
          fontFeatureSettings = "tnum",
        ),
    )
  }
}

@Composable
private fun NotesField(initial: String, enabled: Boolean, onChange: (String) -> Unit) {
  val field = rememberTextFieldState(initialText = initial)
  LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(onChange) }

  Column(
    modifier = Modifier.fillMaxWidth().padding(EditTransactionDS.rowPadding),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = Strings.editTransactionNotes,
      style = typography.labelMedium,
      color = colors.pageTextSubdued,
    )
    AktualTextField(
      modifier = Modifier.fillMaxWidth(),
      state = field,
      placeholderText = null,
      isEnabled = enabled,
      showBorder = false,
      textStyle = typography.bodyLarge.copy(color = colors.pageText),
    )
  }
}

@Composable
private fun FieldCard(content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(colors.tableBackground, RounderCardShape)
        .border(Hairline, colors.tableBorder, RounderCardShape),
    content = content,
  )
}

@Composable
private fun FieldRow(
  label: String,
  value: String?,
  enabled: Boolean,
  onClick: () -> Unit,
  hint: String? = null,
  icon: ImageVector = MaterialIcons.ChevronRight,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
        .padding(horizontal = EditTransactionDS.rowPadding, vertical = 14.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(LABEL_WEIGHT),
      text = label,
      style = typography.bodyMedium,
      color = colors.pageTextSubdued,
      maxLines = 1,
    )
    Text(
      modifier = Modifier.weight(VALUE_WEIGHT),
      text = value ?: hint ?: Strings.editTransactionChoose,
      style = if (value != null) typography.bodyLarge else typography.bodyMedium,
      color =
        when {
          value == null -> colors.pageTextSubdued
          enabled -> colors.pageText
          else -> colors.pageTextSubdued
        },
      textAlign = TextAlign.End,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (enabled) {
      Icon(
        modifier = Modifier.size(18.dp),
        imageVector = icon,
        contentDescription = null,
        tint = colors.pageTextSubdued,
      )
    }
  }
}

@Composable
private fun FieldDivider() {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(start = EditTransactionDS.rowPadding)
        .height(1.dp)
        .background(colors.tableBorderSeparator)
  )
}

@Composable
private fun Notice(text: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth().background(colors.warningBackground, CardShape).padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = CenterVertically,
  ) {
    Icon(imageVector = MaterialIcons.Warning, contentDescription = null, tint = colors.warningText)
    Text(text = text, style = typography.bodyMedium, color = colors.warningText)
  }
}

@Composable
private fun DateDialog(
  date: kotlinx.datetime.LocalDate,
  onPick: (kotlinx.datetime.LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  val state = rememberDatePickerState(date.toJavaLocalDate())
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        enabled = state.selectedDateMillis != null,
        onClick = {
          state.getSelectedDate()?.toKotlinLocalDate()?.let(onPick)
          onDismiss()
        },
        content = { Text(Strings.editTransactionOk) },
      )
    },
    dismissButton = {
      TextButton(onClick = onDismiss, content = { Text(Strings.editTransactionCancel) })
    },
    content = { DatePicker(state = state) },
  )
}

@Composable
private fun ConfirmDialog(
  title: String,
  message: String,
  confirm: String,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
) {
  AktualAlertDialog(
    title = title,
    onDismissRequest = onCancel,
    buttons = {
      TextButton(onClick = onCancel) { Text(Strings.editTransactionCancel) }
      TextButton(onClick = onConfirm) { Text(confirm, color = colors.errorText) }
    },
    content = { Text(message) },
  )
}

private const val ZERO_PLACEHOLDER = "0.00"
private const val LABEL_WEIGHT = 0.35f
private const val VALUE_WEIGHT = 0.65f
