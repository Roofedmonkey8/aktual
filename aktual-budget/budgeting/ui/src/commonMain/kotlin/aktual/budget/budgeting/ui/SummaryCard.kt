package aktual.budget.budgeting.ui

import aktual.budget.budgeting.vm.BudgetSummary
import aktual.budget.model.Amount
import aktual.core.l10n.Strings
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import aktual.core.ui.CardShape
import aktual.core.ui.RounderCardShape
import aktual.core.ui.formattedString
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp.Companion.Hairline
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

@Composable
internal fun SummaryCard(summary: BudgetSummary, modifier: Modifier = Modifier) {
  SummaryCardFrame(modifier = modifier) {
    when (summary) {
      is BudgetSummary.Envelope -> EnvelopeSummary(summary)
      is BudgetSummary.Tracking -> TrackingSummary(summary)
    }
  }
}

@Composable
private fun SummaryCardFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RounderCardShape)
        .background(colors.tableBackground, RounderCardShape)
        .border(Hairline, colors.tableBorder, RounderCardShape)
        .padding(BudgetDS.cardPadding),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    content()
  }
}

@Composable
private fun EnvelopeSummary(summary: BudgetSummary.Envelope) {
  val isOver = summary.toBudget < Amount.Zero
  val headlineColor by
    animateColorAsState(
      when {
        isOver -> colors.toBudgetNegative
        summary.toBudget == Amount.Zero -> colors.toBudgetZero
        else -> colors.toBudgetPositive
      }
    )

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Headline(
      label = if (isOver) Strings.budgetingOverbudgeted else Strings.budgetingToBudget,
      amount = summary.toBudget.absolute(),
      color = headlineColor,
    )

    Breakdown {
      BreakdownRow(Strings.budgetingAvailableFunds, summary.availableFunds)
      BreakdownRow(Strings.budgetingOverspentLastMonth, -summary.overspentLastMonth)
      BreakdownRow(Strings.budgetingBudgeted, -summary.budgeted)
      BreakdownRow(Strings.budgetingForNextMonth, -summary.forNextMonth)
    }
  }
}

@Composable
private fun TrackingSummary(summary: BudgetSummary.Tracking) {
  val isOver = summary.saved < Amount.Zero
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Headline(
      label = if (isOver) Strings.budgetingOverspent else Strings.budgetingSaved,
      amount = summary.saved.absolute(),
      color = if (isOver) colors.numberNegative else colors.numberPositive,
    )

    Breakdown {
      BreakdownRow(Strings.budgetingExpectedIncome, summary.expectedIncome)
      BreakdownRow(Strings.budgetingReceived, summary.received)
      BreakdownRow(Strings.budgetingBudgeted, summary.budgeted)
      BreakdownRow(Strings.budgetingSpent, summary.spent)
    }
  }
}

@Composable
private fun Headline(label: String, amount: Amount, color: Color) {
  Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
    Text(
      text = label,
      style = typography.labelLarge,
      color = colors.pageTextSubdued,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = amount.formattedString(),
      style = typography.headlineMedium.tabularFigures(),
      fontWeight = FontWeight.SemiBold,
      color = color,
      maxLines = 1,
    )
  }
}

@Composable
private fun Breakdown(content: @Composable () -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(colors.tableRowBackgroundAlternate, CardShape)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    content()
  }
}

@Composable
private fun BreakdownRow(label: String, amount: Amount) {
  Row(
    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      modifier = Modifier.weight(1f),
      text = label,
      style = typography.bodySmall,
      color = colors.pageTextSubdued,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = amount.formattedString(includeSign = amount != Amount.Zero),
      style = typography.bodySmall.tabularFigures(),
      color = colors.pageText,
      maxLines = 1,
    )
  }
}

// Keep in step with SummaryCard's layout
@Composable
internal fun ShimmerSummaryCard(modifier: Modifier = Modifier) {
  val bar = Modifier.background(colors.tableText, CardShape)
  SummaryCardFrame(modifier = modifier.shimmer(rememberShimmer(ShimmerBounds.Window))) {
    Box(modifier = bar.width(80.dp).height(14.dp))
    Box(modifier = bar.width(160.dp).height(30.dp))
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = CenterHorizontally,
    ) {
      repeat(times = 4) { Box(modifier = bar.fillMaxWidth().height(12.dp)) }
    }
  }
}

internal fun Amount.absolute(): Amount = if (this < Amount.Zero) -this else this
