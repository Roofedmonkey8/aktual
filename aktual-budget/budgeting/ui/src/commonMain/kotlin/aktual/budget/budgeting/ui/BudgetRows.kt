package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.CategoryState
import aktual.budget.budgeting.vm.GroupState
import aktual.budget.model.Amount
import aktual.core.icons.material.ExpandMore
import aktual.core.icons.material.MaterialIcons
import aktual.core.icons.material.Sync
import aktual.core.l10n.Strings
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.RounderCardShape
import aktual.core.ui.formattedString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Dp.Companion.Hairline
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnHeader(title: String, columns: Columns, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = BudgetDS.rowHorizontalPadding + BudgetDS.cardPadding / 4),
    horizontalArrangement = Arrangement.spacedBy(BudgetDS.columnSpacing),
    verticalAlignment = CenterVertically,
  ) {
    Text(
      modifier = Modifier.weight(1f).semantics { heading() },
      text = title.uppercase(),
      style = typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = colors.pageTextSubdued,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    when (columns) {
      Columns.Expenses -> {
        HeaderCell(Strings.budgetingBudgeted, BudgetDS.columnWidth)
        HeaderCell(Strings.budgetingSpent, BudgetDS.columnWidth)
        HeaderCell(Strings.budgetingBalance, BudgetDS.balanceWidth)
      }
      Columns.TrackingIncome -> {
        HeaderCell(Strings.budgetingBudgeted, BudgetDS.columnWidth)
        HeaderCell(Strings.budgetingReceived, BudgetDS.columnWidth)
        HeaderCell(Strings.budgetingBalance, BudgetDS.balanceWidth)
      }
      Columns.EnvelopeIncome -> {
        HeaderCell(Strings.budgetingReceived, BudgetDS.balanceWidth)
      }
    }
  }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
  Text(
    modifier = Modifier.width(width),
    text = text,
    style = typography.labelSmall,
    color = colors.pageTextSubdued,
    textAlign = TextAlign.End,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
internal fun GroupCard(
  group: GroupState,
  columns: Columns,
  onToggle: () -> Unit,
  onEdit: (CategoryState) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RounderCardShape)
        .background(colors.tableBackground, RounderCardShape)
        .border(Hairline, colors.tableBorder, RounderCardShape)
  ) {
    GroupHeader(group = group, columns = columns, onToggle = onToggle)

    AnimatedVisibility(
      visible = !group.isCollapsed,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column {
        group.categories.forEach { category ->
          Divider()
          CategoryRow(category = category, columns = columns, onEdit = { onEdit(category) })
        }
      }
    }
  }
}

@Composable
private fun GroupHeader(group: GroupState, columns: Columns, onToggle: () -> Unit) {
  val rotation by animateFloatAsState(if (group.isCollapsed) -90f else 0f)
  val toggleLabel =
    if (group.isCollapsed) {
      Strings.budgetingExpandGroup(group.name)
    } else {
      Strings.budgetingCollapseGroup(group.name)
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(colors.tableRowHeaderBackground)
        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
        .padding(
          horizontal = BudgetDS.rowHorizontalPadding,
          vertical = BudgetDS.groupVerticalPadding,
        ),
    horizontalArrangement = Arrangement.spacedBy(BudgetDS.columnSpacing),
    verticalAlignment = CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        modifier = Modifier.size(BudgetDS.chevronSize).rotate(rotation),
        imageVector = MaterialIcons.ExpandMore,
        contentDescription = null,
        tint = colors.tableRowHeaderText,
      )
      Text(
        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        text = group.name,
        style = typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.tableRowHeaderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (group.isHidden) HiddenBadge()
    }

    Figures(
      columns = columns,
      budgeted = group.budgeted,
      spent = group.spent,
      balance = group.balance,
      emphasised = true,
    )
  }
}

@Composable
private fun CategoryRow(category: CategoryState, columns: Columns, onEdit: () -> Unit) {
  val isEditable = columns != Columns.EnvelopeIncome
  val editLabel = Strings.budgetingEditTitle(category.name)
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .then(
          if (isEditable) {
            Modifier.clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit)
          } else {
            Modifier
          }
        )
        .padding(
          horizontal = BudgetDS.rowHorizontalPadding,
          vertical = BudgetDS.rowVerticalPadding,
        ),
    horizontalArrangement = Arrangement.spacedBy(BudgetDS.columnSpacing),
    verticalAlignment = CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f).padding(start = BudgetDS.chevronSize + 4.dp),
      verticalAlignment = CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
        modifier = Modifier.weight(1f, fill = false),
        text = category.name,
        style = typography.bodyMedium,
        color = if (category.isHidden) colors.tableTextSubdued else colors.tableText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (category.isHidden) HiddenBadge()
      if (category.rollover) {
        val rollover = Strings.budgetingRolloverOn
        Icon(
          modifier = Modifier.size(14.dp).semantics { contentDescription = rollover },
          imageVector = MaterialIcons.Sync,
          contentDescription = null,
          tint = colors.tableTextSubdued,
        )
      }
    }

    Figures(
      columns = columns,
      budgeted = category.budgeted,
      spent = category.spent,
      balance = category.balance,
      emphasised = false,
      budgetedIsEditable = isEditable,
    )
  }
}

@Composable
private fun RowScope.Figures(
  columns: Columns,
  budgeted: Amount,
  spent: Amount,
  balance: Amount,
  emphasised: Boolean,
  budgetedIsEditable: Boolean = false,
) {
  when (columns) {
    Columns.Expenses -> {
      BudgetedCell(budgeted, emphasised, budgetedIsEditable)
      AmountCell(spent, BudgetDS.columnWidth, emphasised, colors.tableTextSubdued)
      BalancePill(balance, emphasised)
    }
    Columns.TrackingIncome -> {
      BudgetedCell(budgeted, emphasised, budgetedIsEditable)
      AmountCell(spent, BudgetDS.columnWidth, emphasised, colors.tableTextSubdued)
      AmountCell(balance, BudgetDS.balanceWidth, emphasised, colors.tableText)
    }
    Columns.EnvelopeIncome -> {
      AmountCell(spent, BudgetDS.balanceWidth, emphasised, colors.tableText)
    }
  }
}

// Underlined like a field, so it reads as the thing to tap
@Composable
private fun BudgetedCell(amount: Amount, emphasised: Boolean, isEditable: Boolean) {
  Box(modifier = Modifier.width(BudgetDS.columnWidth), contentAlignment = Alignment.CenterEnd) {
    Column(horizontalAlignment = Alignment.End) {
      AmountText(
        amount = amount,
        emphasised = emphasised,
        color = if (amount == Amount.Zero) colors.tableTextSubdued else colors.tableText,
      )
      if (isEditable) {
        Box(
          modifier =
            Modifier.padding(top = 2.dp)
              .width(BudgetDS.columnWidth / 2)
              .height(1.dp)
              .background(colors.tableBorder, CircleShape)
        )
      }
    }
  }
}

@Composable
private fun AmountCell(amount: Amount, width: Dp, emphasised: Boolean, color: Color) {
  Box(modifier = Modifier.width(width), contentAlignment = Alignment.CenterEnd) {
    AmountText(amount = amount, emphasised = emphasised, color = color)
  }
}

@Composable
private fun AmountText(amount: Amount, emphasised: Boolean, color: Color) {
  Text(
    text = amount.formattedString(),
    style = (if (emphasised) typography.labelLarge else typography.bodyMedium).tabularFigures(),
    fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
    color = color,
    maxLines = 1,
    softWrap = false,
  )
}

// Upstream colours a balance by whether there's money left, none, or the category is overspent
@Composable
internal fun BalancePill(amount: Amount, emphasised: Boolean, modifier: Modifier = Modifier) {
  val (text, background) =
    when {
      amount < Amount.Zero ->
        colors.budgetNumberNegative to colors.budgetNumberNegative.copy(alpha = 0.14f)
      amount > Amount.Zero ->
        colors.budgetNumberPositive to colors.budgetNumberPositive.copy(alpha = 0.14f)
      else -> colors.budgetNumberZero to Color.Transparent
    }
  val state = if (amount < Amount.Zero) Strings.budgetingOverspent else Strings.budgetingBalance

  Box(modifier = modifier.width(BudgetDS.balanceWidth), contentAlignment = Alignment.CenterEnd) {
    Text(
      modifier =
        Modifier.semantics { stateDescription = state }
          .background(background, PillShape)
          .padding(
            horizontal = BudgetDS.pillHorizontalPadding,
            vertical = BudgetDS.pillVerticalPadding,
          ),
      text = amount.formattedString(),
      style = (if (emphasised) typography.labelLarge else typography.bodyMedium).tabularFigures(),
      fontWeight = FontWeight.SemiBold,
      color = text,
      maxLines = 1,
      softWrap = false,
    )
  }
}

@Composable
private fun HiddenBadge() {
  val label = Strings.budgetingHidden
  Text(
    modifier =
      Modifier.semantics { contentDescription = label }
        .border(Hairline, colors.tableBorder, PillShape)
        .padding(horizontal = 6.dp, vertical = 1.dp),
    text = label,
    style = typography.labelSmall,
    color = colors.tableTextSubdued,
    maxLines = 1,
  )
}

@Composable
private fun Divider() {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(start = BudgetDS.rowHorizontalPadding + BudgetDS.chevronSize + 4.dp)
        .height(1.dp)
        .background(colors.tableBorderSeparator)
  )
}

private val PillShape = RoundedCornerShape(percent = 50)
