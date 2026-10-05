package aktual.budget.budgeting.ui

import aktual.budget.budgeting.ui.BudgetAction.EditBudget
import aktual.budget.budgeting.ui.BudgetAction.NextMonth
import aktual.budget.budgeting.ui.BudgetAction.PreviousMonth
import aktual.budget.budgeting.ui.BudgetAction.Refresh
import aktual.budget.budgeting.ui.BudgetAction.SetShowHidden
import aktual.budget.budgeting.ui.BudgetAction.ThisMonth
import aktual.budget.budgeting.ui.BudgetAction.ToggleGroup
import aktual.budget.budgeting.vm.BudgetContent
import aktual.budget.budgeting.vm.BudgetEvent
import aktual.budget.budgeting.vm.BudgetState
import aktual.budget.budgeting.vm.BudgetSummary
import aktual.budget.budgeting.vm.BudgetViewModel
import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.budgeting.vm.GroupState
import aktual.core.icons.material.CalendarToday
import aktual.core.icons.material.ChevronLeft
import aktual.core.icons.material.ChevronRight
import aktual.core.icons.material.MaterialIcons
import aktual.core.icons.material.MoreVert
import aktual.core.icons.material.Visibility
import aktual.core.icons.material.VisibilityOff
import aktual.core.l10n.Strings
import aktual.core.ui.AktualDropdownMenu
import aktual.core.ui.AktualDropdownMenuItem
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.BareIconButton
import aktual.core.ui.BottomSpacing
import aktual.core.ui.ColoredParameterProvider
import aktual.core.ui.ColoredParams
import aktual.core.ui.FailureScreen
import aktual.core.ui.HazedPullToRefreshBox
import aktual.core.ui.LocalBottomSpacing
import aktual.core.ui.NavDrawerIconButton
import aktual.core.ui.PageBackground
import aktual.core.ui.PortraitPreview
import aktual.core.ui.PreviewWithColoredParams
import aktual.core.ui.bottomNavBarPadding
import aktual.core.ui.hazedTopBar
import aktual.core.ui.rememberHazedTopBarState
import aktual.core.ui.scrollbar
import aktual.core.ui.stringLong
import aktual.core.ui.transparentTopAppBarColors
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.YearMonth

@Composable
internal fun BudgetScreen(
  modifier: Modifier = Modifier,
  viewModel: BudgetViewModel = metroViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val saveFailed = Strings.budgetingEditFailed

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        BudgetEvent.SaveFailed -> snackbar.showSnackbar(saveFailed)
      }
    }
  }

  // Held by ID, so the sheet shows fresh figures if a sync lands while it's open
  var editingId by rememberSaveable { mutableStateOf<String?>(null) }
  val editing =
    (state.content as? BudgetContent.Loaded)?.let { content ->
      (content.expenseGroups + content.incomeGroups)
        .flatMap { it.categories }
        .firstOrNull { it.id.value == editingId }
    }

  BudgetScaffold(
    modifier = modifier,
    state = state,
    snackbarHostState = snackbar,
    onAction = { action ->
      when (action) {
        PreviousMonth -> viewModel.previousMonth()
        NextMonth -> viewModel.nextMonth()
        ThisMonth -> viewModel.thisMonth()
        Refresh -> viewModel.refresh()
        is SetShowHidden -> viewModel.setShowHidden(action.show)
        is ToggleGroup -> viewModel.toggleGroup(action.id)
        is EditBudget -> editingId = action.category.id.value
      }
    },
  )

  if (editing != null) {
    EditBudgetSheet(
      category = editing,
      month = state.month,
      onDismiss = { editingId = null },
      onSave = { amount -> viewModel.setBudget(editing.id, amount) },
    )
  }
}

@Composable
private fun BudgetScaffold(
  state: BudgetState,
  onAction: BudgetActionHandler,
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  val hazeState = rememberHazedTopBarState()
  val listState = rememberLazyListState()

  Scaffold(
    modifier = modifier.fillMaxSize().imePadding(),
    topBar = {
      TopAppBar(
        modifier = Modifier.hazedTopBar(hazeState, listState),
        colors = colors.transparentTopAppBarColors(),
        navigationIcon = { NavDrawerIconButton() },
        title = { MonthSwitcher(month = state.month, onAction = onAction) },
        actions = { BudgetMenu(state = state, onAction = onAction) },
      )
    },
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(bottom = LocalBottomSpacing.current + bottomNavBarPadding()),
      )
    },
  ) { innerPadding ->
    Box {
      PageBackground()

      HazedPullToRefreshBox(
        modifier = Modifier.padding(horizontal = BudgetDS.pagePadding),
        onRefresh = { onAction(Refresh) },
        isRefreshing = state.isRefreshing,
        hazeState = hazeState,
        innerPadding = innerPadding,
      ) { padding ->
        // Slides the way the month moved, so it's clear which way you've gone
        AnimatedContent(
          targetState = state.month to state.content,
          contentKey = { (month, content) -> month to (content is BudgetContent.Loaded) },
          transitionSpec = {
            val forward = targetState.first > initialState.first
            val direction = if (forward) 1 else -1
            if (targetState.first == initialState.first) {
              fadeIn() togetherWith fadeOut()
            } else {
              slideInHorizontally { it / SLIDE_FRACTION * direction } + fadeIn() togetherWith
                slideOutHorizontally { -it / SLIDE_FRACTION * direction } + fadeOut()
            }
          },
        ) { (_, content) ->
          BudgetContentView(
            content = content,
            contentPadding = padding,
            listState = listState,
            onAction = onAction,
          )
        }
      }
    }
  }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onAction: BudgetActionHandler) {
  Row(verticalAlignment = CenterVertically) {
    BareIconButton(
      imageVector = MaterialIcons.ChevronLeft,
      contentDescription = Strings.budgetingPreviousMonth,
      onClick = { onAction(PreviousMonth) },
    )
    Text(
      modifier =
        Modifier.weight(1f, fill = false).semantics {
          heading()
          liveRegion = LiveRegionMode.Polite
        },
      text = month.stringLong(),
      style = typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    BareIconButton(
      imageVector = MaterialIcons.ChevronRight,
      contentDescription = Strings.budgetingNextMonth,
      onClick = { onAction(NextMonth) },
    )
  }
}

@Composable
private fun BudgetMenu(state: BudgetState, onAction: BudgetActionHandler) {
  var expanded by remember { mutableStateOf(false) }

  if (!state.isCurrentMonth) {
    BareIconButton(
      imageVector = MaterialIcons.CalendarToday,
      contentDescription = Strings.budgetingThisMonth,
      onClick = { onAction(ThisMonth) },
    )
  }

  Box {
    BareIconButton(
      imageVector = MaterialIcons.MoreVert,
      contentDescription = Strings.budgetingMenu,
      onClick = { expanded = true },
    )

    AktualDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      AktualDropdownMenuItem(
        text = if (state.showHidden) Strings.budgetingHideHidden else Strings.budgetingShowHidden,
        leadingIcon =
          if (state.showHidden) MaterialIcons.VisibilityOff else MaterialIcons.Visibility,
        onClick = {
          expanded = false
          onAction(SetShowHidden(!state.showHidden))
        },
      )
    }
  }
}

@Composable
private fun BudgetContentView(
  content: BudgetContent,
  contentPadding: PaddingValues,
  listState: LazyListState,
  onAction: BudgetActionHandler,
  modifier: Modifier = Modifier,
) {
  when (content) {
    BudgetContent.Loading -> {
      LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        userScrollEnabled = false,
      ) {
        item { ShimmerSummaryCard() }
      }
    }

    BudgetContent.Empty -> {
      Box(modifier = modifier.fillMaxSize().padding(contentPadding), contentAlignment = Center) {
        FailureScreen(
          title = Strings.budgetingEmpty,
          reason = Strings.budgetingEmptySecond,
          action = null,
          icon = null,
          background = colors.tableBackground,
        )
      }
    }

    is BudgetContent.Loaded -> {
      val expensesTitle = Strings.budgetingCategory
      val incomeTitle = Strings.budgetingIncome
      LazyColumn(
        modifier = modifier.fillMaxSize().scrollbar(listState),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(BudgetDS.sectionSpacing),
      ) {
        item(key = "summary") { SummaryCard(summary = content.summary) }

        val isTracking = content.summary is BudgetSummary.Tracking
        section(
          key = "expenses",
          title = expensesTitle,
          groups = content.expenseGroups,
          columns = Columns.Expenses,
          onAction = onAction,
        )
        section(
          key = "income",
          title = incomeTitle,
          groups = content.incomeGroups,
          columns = if (isTracking) Columns.TrackingIncome else Columns.EnvelopeIncome,
          onAction = onAction,
        )

        item(key = "bottom") { BottomSpacing() }
      }
    }
  }
}

private fun LazyListScope.section(
  key: String,
  title: String,
  groups: List<GroupState>,
  columns: Columns,
  onAction: BudgetActionHandler,
) {
  if (groups.isEmpty()) return
  item(key = "$key-header") {
    ColumnHeader(
      modifier = Modifier.padding(top = 4.dp).height(20.dp),
      title = title,
      columns = columns,
    )
  }
  items(groups, key = { "$key-${it.id.value}" }) { group ->
    GroupCard(
      modifier = Modifier.animateItem(),
      group = group,
      columns = columns,
      onToggle = { onAction(ToggleGroup(group.id)) },
      onEdit = { category: CategoryState -> onAction(EditBudget(category)) },
    )
  }
}

private const val SLIDE_FRACTION = 4

private class BudgetStateProvider :
  ColoredParameterProvider<BudgetState>(
    PREVIEW_ENVELOPE_STATE,
    PREVIEW_TRACKING_STATE,
    PREVIEW_ENVELOPE_STATE.copy(content = BudgetContent.Loading),
  )

@PortraitPreview
@Composable
private fun PreviewBudgetScaffold(
  @PreviewParameter(BudgetStateProvider::class) params: ColoredParams<BudgetState>
) =
  PreviewWithColoredParams(params) {
    BudgetScaffold(state = this, onAction = {})
  }
