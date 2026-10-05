package aktual.budget.transactions.ui.edit

import aktual.budget.model.AccountId
import aktual.budget.model.CategoryId
import aktual.budget.model.PayeeId
import aktual.budget.transactions.domain.PickerAccount
import aktual.budget.transactions.domain.PickerCategory
import aktual.budget.transactions.domain.PickerPayee
import aktual.core.l10n.Strings
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun PayeePicker(
  payees: ImmutableList<PickerPayee>,
  accounts: ImmutableList<PickerAccount>,
  selected: PayeeId?,
  currentAccount: AccountId?,
  onPick: (PickerPayee) -> Unit,
  onCreate: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val accountNames = accounts.associate { it.id to it.name }
  val payeesTitle = Strings.editTransactionPayees
  val transfersTitle = Strings.editTransactionTransfers
  val createLabel = Strings.editTransactionCreatePayee("%s")
  val transferLabels =
    payees
      .filter { it.transferAccount != null }
      .associate {
        it.id to Strings.editTransactionTransferTo(accountNames[it.transferAccount].orEmpty())
      }

  PickerSheet(
    title = Strings.editTransactionPayee,
    onDismiss = onDismiss,
    onSearchSubmit = { query -> if (query.isNotEmpty()) onCreate(query) },
  ) { query, scope ->
    val matches = payees.filter {
      query.isEmpty() || it.label(transferLabels).contains(query, true)
    }
    val exact = payees.any {
      it.transferAccount == null && it.name.equals(query, ignoreCase = true)
    }

    if (query.isNotEmpty() && !exact) {
      pickerRow(
        key = "create",
        text = createLabel.replace("%s", query),
        isSelected = false,
        emphasised = true,
        onClick = { scope.close { onCreate(query) } },
      )
    }

    val regular = matches.filter { it.transferAccount == null }
    if (regular.isNotEmpty()) pickerHeader("payees", payeesTitle)
    regular.forEach { payee ->
      pickerRow(
        key = payee.id.value,
        text = payee.name,
        isSelected = payee.id == selected,
        onClick = { scope.close { onPick(payee) } },
      )
    }

    // A transfer to the account the transaction's already in makes no sense
    val transfers = matches.filter {
      it.transferAccount != null && it.transferAccount != currentAccount
    }
    if (transfers.isNotEmpty()) pickerHeader("transfers", transfersTitle)
    transfers.forEach { payee ->
      pickerRow(
        key = payee.id.value,
        text = payee.label(transferLabels),
        isSelected = payee.id == selected,
        onClick = { scope.close { onPick(payee) } },
      )
    }
  }
}

private fun PickerPayee.label(transferLabels: Map<PayeeId, String>) = transferLabels[id] ?: name

@Composable
internal fun CategoryPicker(
  categories: ImmutableList<PickerCategory>,
  selected: CategoryId?,
  onPick: (CategoryId?) -> Unit,
  onDismiss: () -> Unit,
) {
  val none = Strings.editTransactionNoCategory
  PickerSheet(title = Strings.editTransactionCategory, onDismiss = onDismiss) { query, scope ->
    if (query.isEmpty()) {
      pickerRow(
        key = "none",
        text = none,
        isSelected = selected == null,
        onClick = { scope.close { onPick(null) } },
      )
    }
    categories
      .filter {
        query.isEmpty() || it.name.contains(query, true) || it.groupName.contains(query, true)
      }
      .groupBy { it.group }
      .forEach { (group, rows) ->
        pickerHeader(group.value, rows.first().groupName)
        rows.forEach { category ->
          pickerRow(
            key = category.id.value,
            text = category.name,
            isSelected = category.id == selected,
            onClick = { scope.close { onPick(category.id) } },
          )
        }
      }
  }
}

@Composable
internal fun AccountPicker(
  accounts: ImmutableList<PickerAccount>,
  selected: AccountId?,
  onPick: (AccountId) -> Unit,
  onDismiss: () -> Unit,
) {
  val onBudget = Strings.editTransactionBudgetAccounts
  val offBudget = Strings.editTransactionOffBudgetAccounts
  PickerSheet(
    title = Strings.editTransactionAccount,
    onDismiss = onDismiss,
    searchable = accounts.size > SEARCH_THRESHOLD,
  ) { query, scope ->
    accounts
      .filter { query.isEmpty() || it.name.contains(query, true) }
      .groupBy { it.isOffBudget }
      .forEach { (isOffBudget, rows) ->
        pickerHeader(isOffBudget.toString(), if (isOffBudget) offBudget else onBudget)
        rows.forEach { account ->
          pickerRow(
            key = account.id.value,
            text = account.name,
            isSelected = account.id == selected,
            onClick = { scope.close { onPick(account.id) } },
          )
        }
      }
  }
}

private const val SEARCH_THRESHOLD = 8
