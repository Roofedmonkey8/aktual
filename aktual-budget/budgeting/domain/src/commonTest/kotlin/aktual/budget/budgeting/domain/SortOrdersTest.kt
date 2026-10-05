package aktual.budget.budgeting.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

internal class SortOrdersTest {
  private val items = listOf(Sortable("a", 16384.0), Sortable("b", 32768.0), Sortable("c", 49152.0))

  @Test
  fun `With no target an item goes at the end`() {
    val shove = shoveSortOrders(items, targetId = null)
    assertThat(shove.updates).isEmpty()
    assertThat(shove.sortOrder).isEqualTo(65536.0)
    assertThat(shoveSortOrders(emptyList(), null).sortOrder).isEqualTo(SORT_INCREMENT)
  }

  @Test
  fun `An item goes halfway between the target and the one before it`() {
    assertThat(shoveSortOrders(items, "b").sortOrder).isEqualTo(24576.0)
    assertThat(shoveSortOrders(items, "a").sortOrder).isEqualTo(8192.0)
  }

  @Test
  fun `Items are pushed down when there's no room`() {
    val crowded = listOf(Sortable("a", 10.0), Sortable("b", 11.0), Sortable("c", 50000.0))
    val shove = shoveSortOrders(crowded, "b")
    assertThat(shove.updates).containsExactly(SortUpdate("b", 16395.0))
    assertThat(shove.sortOrder).isEqualTo(8202.5)
  }

  @Test
  fun `Moving up goes before the one above, down before the one after next`() {
    val ids = listOf("a", "b", "c", "d")
    assertThat(targetFor(ids, "c", Direction.Up)).isEqualTo(Target("b"))
    assertThat(targetFor(ids, "a", Direction.Up)).isNull()
    assertThat(targetFor(ids, "b", Direction.Down)).isEqualTo(Target("d"))
    assertThat(targetFor(ids, "c", Direction.Down)).isEqualTo(Target(null))
    assertThat(targetFor(ids, "d", Direction.Down)).isNull()
    assertThat(targetFor(ids, "z", Direction.Down)).isNull()
  }
}
