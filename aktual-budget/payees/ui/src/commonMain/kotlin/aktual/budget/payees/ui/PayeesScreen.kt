package aktual.budget.payees.ui

import aktual.budget.payees.vm.PayeesEvent
import aktual.budget.payees.vm.PayeesState
import aktual.budget.payees.vm.PayeesViewModel
import aktual.budget.transactions.domain.ManagedPayee
import aktual.core.icons.material.Add
import aktual.core.icons.material.MaterialIcons
import aktual.core.l10n.Plurals
import aktual.core.l10n.Strings
import aktual.core.ui.AktualTextField
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.BottomSpacing
import aktual.core.ui.LoadingScreen
import aktual.core.ui.LocalBottomSpacing
import aktual.core.ui.NavDrawerIconButton
import aktual.core.ui.PageBackground
import aktual.core.ui.RounderCardShape
import aktual.core.ui.bottomNavBarPadding
import aktual.core.ui.scrollbar
import aktual.core.ui.transparentTopAppBarColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp.Companion.Hairline
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
internal fun PayeesScreen(
  modifier: Modifier = Modifier,
  viewModel: PayeesViewModel = metroViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var dialog by remember { mutableStateOf<PayeeDialog?>(null) }
  val blank = Strings.payeesBlank
  val failed = Strings.payeesFailed
  val duplicate = Strings.payeesDuplicate("%s")

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        PayeesEvent.Saved -> dialog = null
        is PayeesEvent.DuplicateName -> snackbar.showSnackbar(duplicate.replace("%s", event.name))
        PayeesEvent.BlankName -> snackbar.showSnackbar(blank)
        PayeesEvent.Failed -> snackbar.showSnackbar(failed)
      }
    }
  }

  val onAction = PayeeActionHandler { action ->
    when (action) {
      is PayeeAction.Search -> viewModel.search(action.query)
      is PayeeAction.Open -> dialog = action.dialog
      is PayeeAction.Create -> viewModel.create(action.name)
      is PayeeAction.Rename -> viewModel.rename(action.id, action.name)
      is PayeeAction.SetFavorite -> viewModel.setFavorite(action.id, action.favorite)
      is PayeeAction.SetLearn -> viewModel.setLearnCategories(action.id, action.learn)
      is PayeeAction.Merge -> viewModel.merge(action.id, action.into)
      is PayeeAction.Delete -> viewModel.delete(action.id)
    }
  }

  PayeesScaffold(
    modifier = modifier,
    state = state,
    snackbarHostState = snackbar,
    onAction = onAction,
  )

  (state as? PayeesState.Loaded)?.let { loaded ->
    PayeeDialogs(dialog = dialog, all = loaded.all, onAction = onAction)
  }
}

@Composable
private fun PayeesScaffold(
  state: PayeesState,
  snackbarHostState: SnackbarHostState,
  onAction: PayeeActionHandler,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize().imePadding(),
    topBar = {
      TopAppBar(
        colors = colors.transparentTopAppBarColors(),
        navigationIcon = { NavDrawerIconButton() },
        title = { Text(Strings.payeesTitle) },
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        modifier = Modifier.padding(bottom = LocalBottomSpacing.current),
        onClick = { onAction(PayeeAction.Open(PayeeDialog.New)) },
        containerColor = colors.buttonPrimaryBackground,
        contentColor = colors.buttonPrimaryText,
      ) {
        Icon(imageVector = MaterialIcons.Add, contentDescription = Strings.payeesAdd)
      }
    },
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(bottom = LocalBottomSpacing.current + bottomNavBarPadding()),
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
      PageBackground()
      when (state) {
        PayeesState.Loading -> LoadingScreen(modifier = Modifier.padding(innerPadding))
        is PayeesState.Loaded ->
          PayeesList(state = state, contentPadding = innerPadding, onAction = onAction)
      }
    }
  }
}

@Composable
private fun PayeesList(
  state: PayeesState.Loaded,
  contentPadding: PaddingValues,
  onAction: PayeeActionHandler,
) {
  val search = rememberTextFieldState(initialText = state.query)
  LaunchedEffect(search) {
    snapshotFlow { search.text.toString() }.collect { onAction(PayeeAction.Search(it)) }
  }
  val listState = rememberLazyListState()

  Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 12.dp)) {
    AktualTextField(
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      state = search,
      placeholderText = Strings.payeesSearch,
      singleLine = true,
      clearable = true,
    )

    if (state.payees.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Center) {
        Text(
          text = if (state.all.isEmpty()) Strings.payeesEmpty else Strings.payeesNoMatches,
          color = colors.pageTextSubdued,
        )
      }
    } else {
      LazyColumn(
        modifier =
          Modifier.fillMaxWidth()
            .scrollbar(listState)
            .background(colors.tableBackground, RounderCardShape)
            .border(Hairline, colors.tableBorder, RounderCardShape),
        state = listState,
      ) {
        itemsIndexed(state.payees, key = { _, payee -> payee.id.value }) { index, payee ->
          if (index > 0) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(start = 16.dp)
                  .height(1.dp)
                  .background(colors.tableBorderSeparator)
            )
          }
          PayeeRow(
            modifier = Modifier.animateItem(),
            payee = payee,
            onClick = { onAction(PayeeAction.Open(PayeeDialog.Options(payee.id))) },
          )
        }
        item { BottomSpacing() }
      }
    }
  }
}

@Composable
private fun PayeeRow(payee: ManagedPayee, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = payee.name,
        style = typography.bodyLarge,
        fontWeight = if (payee.isFavorite) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text =
          if (payee.transactionCount == 0L) {
            Strings.payeesNoTransactions
          } else {
            val count = payee.transactionCount.toInt()
            Plurals.payeesTransactions(count, count)
          },
        style = typography.bodySmall,
        color = colors.pageTextSubdued,
      )
    }
    if (payee.isFavorite) {
      Text(text = "★", color = colors.pageTextPositive)
    }
  }
}
