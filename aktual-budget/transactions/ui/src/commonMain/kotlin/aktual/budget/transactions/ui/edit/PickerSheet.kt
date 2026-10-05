package aktual.budget.transactions.ui.edit

import aktual.core.icons.material.Check
import aktual.core.icons.material.MaterialIcons
import aktual.core.l10n.Strings
import aktual.core.ui.AktualTextField
import aktual.core.ui.AktualTheme.colors
import aktual.core.ui.AktualTheme.typography
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A searchable list in a bottom sheet. [content] gets the current query and adds its rows with
 * [pickerHeader] and [pickerRow]; it's given a [PickerScope] that closes the sheet before picking.
 */
@Composable
internal fun PickerSheet(
  title: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  searchable: Boolean = true,
  onSearchSubmit: ((String) -> Unit)? = null,
  content: LazyListScope.(query: String, scope: PickerScope) -> Unit,
) {
  // Fully open only, so the list has room above the keyboard
  val sheetState =
    rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded))
  val coroutineScope = rememberCoroutineScope()
  val search = rememberTextFieldState()
  val query = search.text.toString().trim()
  val scope = PickerScope { pick ->
    coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { pick() }
  }

  ModalBottomSheet(
    modifier = modifier,
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.modalBackground,
    contentColor = colors.pageText,
  ) {
    Column(
      modifier = Modifier.fillMaxHeight(SHEET_HEIGHT_FRACTION),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        modifier =
          Modifier.padding(horizontal = EditTransactionDS.sheetPadding).semantics { heading() },
        text = title,
        style = typography.titleLarge,
      )

      if (searchable) {
        AktualTextField(
          modifier = Modifier.fillMaxWidth().padding(horizontal = EditTransactionDS.sheetPadding),
          state = search,
          placeholderText = Strings.editTransactionSearch,
          singleLine = true,
          clearable = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          onKeyboardAction = { onSearchSubmit?.let { done -> scope.close { done(query) } } },
        )
      }

      LazyColumn(modifier = Modifier.fillMaxWidth()) { content(query, scope) }
    }
  }
}

internal fun interface PickerScope {
  /** Closes the sheet, then runs [then] */
  fun close(then: () -> Unit)
}

internal fun LazyListScope.pickerHeader(key: String, text: String) {
  item(key = "header-$key") {
    Text(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = EditTransactionDS.sheetPadding)
          .padding(top = 16.dp, bottom = 4.dp)
          .semantics { heading() },
      text = text.uppercase(),
      style = typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = colors.pageTextSubdued,
    )
  }
}

internal fun LazyListScope.pickerRow(
  key: String,
  text: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  subtitle: String? = null,
  emphasised: Boolean = false,
) {
  item(key = key) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .semantics { selected = isSelected }
          .clickable(role = Role.Button, onClick = onClick)
          .padding(horizontal = EditTransactionDS.sheetPadding, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = text,
          style = typography.bodyLarge,
          fontWeight = if (isSelected || emphasised) FontWeight.SemiBold else FontWeight.Normal,
          color = if (emphasised) colors.pageTextPositive else colors.pageText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            style = typography.bodySmall,
            color = colors.pageTextSubdued,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (isSelected) {
        Icon(
          modifier = Modifier.size(20.dp),
          imageVector = MaterialIcons.Check,
          contentDescription = null,
          tint = colors.pageTextPositive,
        )
      }
    }
  }
}

private const val SHEET_HEIGHT_FRACTION = 0.9f
