package aktual.budget.budgeting.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

// Sizes shared by the budget page's rows, so the header lines up with the columns below it
internal object BudgetDS {
  val pagePadding = 12.dp
  val cardPadding = 16.dp
  val rowHorizontalPadding = 12.dp
  val rowVerticalPadding = 10.dp
  val groupVerticalPadding = 12.dp
  val columnWidth = 78.dp
  val balanceWidth = 84.dp
  val columnSpacing = 4.dp
  val sectionSpacing = 12.dp
  val pillHorizontalPadding = 8.dp
  val pillVerticalPadding = 3.dp
  val sheetPadding = 20.dp
  val sheetSpacing = 16.dp
  val chevronSize = 20.dp
}

/** Which figures a section shows. Envelope income only has what was received. */
internal enum class Columns {
  Expenses,
  TrackingIncome,
  EnvelopeIncome,
}

internal fun TextStyle.tabularFigures() = copy(fontFeatureSettings = "tnum")
