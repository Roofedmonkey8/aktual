package aktual.budget.db.dao

import aktual.budget.db.BudgetDatabase
import aktual.budget.db.withResult
import alakazam.kotlin.CoroutineContexts
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Notes are keyed by what they're about: a category or account's own ID, or "budget-YYYY-MM" for a
 * budget month.
 */
@Inject
class NotesDao(database: BudgetDatabase, private val contexts: CoroutineContexts) {
  private val queries = database.notesQueries

  suspend operator fun get(id: String): String? = queries.withResult {
    getNote(id).awaitAsOneOrNull()?.note
  }

  fun observe(ids: Collection<String>): Flow<Map<String, String>> =
    queries
      .observeNotes(ids)
      .asFlow()
      .mapToList(contexts.default)
      .map { rows -> rows.mapNotNull { row -> row.note?.let { row.id to it } }.toMap() }
      .distinctUntilChanged()
}
