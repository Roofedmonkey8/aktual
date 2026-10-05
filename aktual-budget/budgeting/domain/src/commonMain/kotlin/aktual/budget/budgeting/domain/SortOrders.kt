package aktual.budget.budgeting.domain

/** An item ordered by [sortOrder], as categories, groups and the like are */
data class Sortable(val id: String, val sortOrder: Double)

data class SortUpdate(val id: String, val sortOrder: Double)

/** Where a moved item goes, and any items that had to shuffle along to make room for it */
data class Shove(val updates: List<SortUpdate>, val sortOrder: Double)

/**
 * packages/loot-core/src/server/db/sort.ts shoveSortOrders(). Places an item just before
 * [targetId], or at the end when it's null or missing. When there's no room between the target and
 * the item before it, the target and those after it are pushed down first.
 */
fun shoveSortOrders(items: List<Sortable>, targetId: String?): Shove {
  val to = items.indexOfFirst { it.id == targetId }
  if (targetId == null || to == -1) {
    val order = items.lastOrNull()?.let { it.sortOrder + SORT_INCREMENT } ?: SORT_INCREMENT
    return Shove(emptyList(), order)
  }

  val target = items[to]
  val before = items.getOrNull(to - 1)
  val updates = mutableListOf<SortUpdate>()
  if (target.sortOrder - (before?.sortOrder ?: 0.0) <= 2) {
    var order = kotlin.math.floor(target.sortOrder) + SORT_INCREMENT
    for (next in to until items.size) {
      // Big gaps further on can already leave room
      if (order <= items[next].sortOrder) break
      updates += SortUpdate(items[next].id, order)
      order += SORT_INCREMENT
    }
  }
  // Unlike upstream, the midpoint is taken after the shove, so there's always a whole number of
  // room either side and the order can be sent as one
  val shoved = updates.associate { it.id to it.sortOrder }
  val after = items.map { item -> shoved[item.id]?.let { item.copy(sortOrder = it) } ?: item }
  return Shove(updates, midpoint(after, to))
}

private fun midpoint(items: List<Sortable>, to: Int): Double {
  val below = items.getOrNull(to - 1)
  val above = items.getOrNull(to)
  return when {
    below == null -> requireNotNull(above).sortOrder / 2
    above == null -> below.sortOrder + SORT_INCREMENT
    else -> (below.sortOrder + above.sortOrder) / 2
  }
}

const val SORT_INCREMENT = 16384.0
