package aktual.budget.budgeting.domain

import aktual.budget.BudgetSyncController
import aktual.budget.db.LiveCategoryOrder
import aktual.budget.db.dao.BudgetStructureDao
import aktual.budget.db.dao.DatabaseTables.CATEGORIES
import aktual.budget.db.dao.DatabaseTables.CATEGORY_GROUPS
import aktual.budget.db.dao.DatabaseTables.CATEGORY_MAPPING
import aktual.budget.db.dao.DatabaseTables.REFLECT_BUDGETS
import aktual.budget.db.dao.DatabaseTables.ZERO_BUDGETS
import aktual.budget.db.dao.MonthBudget
import aktual.budget.db.dao.PreferencesDao
import aktual.budget.model.Amount
import aktual.budget.model.BudgetType
import aktual.budget.model.CategoryGroupId
import aktual.budget.model.CategoryId
import aktual.budget.model.LocalChange
import aktual.budget.model.MessageValue
import aktual.budget.model.SyncedPrefKey
import aktual.budget.model.tombstone
import aktual.core.UuidGenerator
import aktual.di.BudgetScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Why a change to the budget's categories was refused */
sealed class CategoryChangeException(message: String) : IllegalArgumentException(message) {
  class DuplicateName(val name: String) : CategoryChangeException("'$name' already exists")

  class BlankName : CategoryChangeException("A name is needed")

  class IncomeMismatch :
    CategoryChangeException("Can't move money between income and expense categories")
}

/**
 * Adds, renames, hides, moves and deletes categories and groups, as upstream's db/index.ts and
 * budget/app.ts do. Each call goes out as one sync.
 */
@Inject
@SingleIn(BudgetScope::class)
class CategoryManager(
  private val dao: BudgetStructureDao,
  private val preferencesDao: PreferencesDao,
  private val syncController: BudgetSyncController,
  private val uuidGenerator: UuidGenerator,
) {
  /** insertCategoryGroup(): added at the end, with a name no other group has */
  suspend fun createGroup(name: String): CategoryGroupId {
    val trimmed = validName(name)
    val groups = dao.groups()
    if (groups.any { it.name.equals(trimmed, ignoreCase = true) }) {
      throw CategoryChangeException.DuplicateName(trimmed)
    }
    val id = uuidGenerator(::CategoryGroupId)
    val order = (groups.maxOfOrNull { it.sort_order ?: 0.0 } ?: 0.0) + SORT_INCREMENT
    sync(
      groupChange(id, NAME, MessageValue.String(trimmed)),
      groupChange(id, IS_INCOME, MessageValue.Number(0)),
      groupChange(id, SORT_ORDER, order.messageValue()),
      groupChange(id, HIDDEN, MessageValue.Number(0)),
    )
    return id
  }

  /** updateCategoryGroup() */
  suspend fun renameGroup(id: CategoryGroupId, name: String) {
    val trimmed = validName(name)
    if (dao.groups().any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
      throw CategoryChangeException.DuplicateName(trimmed)
    }
    sync(groupChange(id, NAME, MessageValue.String(trimmed)))
  }

  suspend fun setGroupHidden(id: CategoryGroupId, hidden: Boolean) =
    sync(groupChange(id, HIDDEN, hidden.messageValue()))

  /** moveCategoryGroup(): one place up or down among the groups of the same kind */
  suspend fun moveGroup(id: CategoryGroupId, direction: Direction) {
    val all = dao.groups()
    val group = all.firstOrNull { it.id == id } ?: return
    val siblings = all.filter { it.is_income == group.is_income }.map { it.id }
    val target = targetFor(siblings, id, direction) ?: return
    val shove =
      shoveSortOrders(all.map { Sortable(it.id.value, it.sort_order ?: 0.0) }, target.value?.value)
    sync(
      shove.updates.map {
        groupChange(CategoryGroupId(it.id), SORT_ORDER, it.sortOrder.messageValue())
      } + groupChange(id, SORT_ORDER, shove.sortOrder.messageValue())
    )
  }

  /**
   * deleteCategoryGroup(): deletes the group and every category in it, moving their budgets and
   * transactions to [transferTo] if given
   */
  suspend fun deleteGroup(id: CategoryGroupId, transferTo: CategoryId?) {
    val changes = dao.categoryIdsOf(id).flatMap { deleteCategoryChanges(it, transferTo) }
    sync(changes + tombstone(CATEGORY_GROUPS, id.value))
  }

  /**
   * insertCategory(): added at the top of [group], as upstream adds them, with a name nothing else
   * in the group has. It's an income category when the group is.
   */
  suspend fun createCategory(name: String, group: CategoryGroupId): CategoryId {
    val trimmed = validName(name)
    val existing = dao.categoriesOf(group)
    if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
      throw CategoryChangeException.DuplicateName(trimmed)
    }
    val isIncome = dao.groups().firstOrNull { it.id == group }?.is_income == true
    val id = uuidGenerator(::CategoryId)
    val shove = shoveSortOrders(existing.map { it.sortable() }, existing.firstOrNull()?.id?.value)
    sync(
      shove.updates.map {
        categoryChange(CategoryId(it.id), SORT_ORDER, it.sortOrder.messageValue())
      } +
        listOf(
          categoryChange(id, NAME, MessageValue.String(trimmed)),
          categoryChange(id, CAT_GROUP, MessageValue.String(group.value)),
          categoryChange(id, IS_INCOME, isIncome.messageValue()),
          categoryChange(id, SORT_ORDER, shove.sortOrder.messageValue()),
          categoryChange(id, HIDDEN, MessageValue.Number(0)),
          // A mapping that points the category at itself, as every live category has
          LocalChange(CATEGORY_MAPPING, id.value, TRANSFER_ID, MessageValue.String(id.value)),
        )
    )
    return id
  }

  /** updateCategory(), with the same check insertCategory() makes on names */
  suspend fun renameCategory(id: CategoryId, name: String) {
    val trimmed = validName(name)
    val group = dao.category(id)?.cat_group ?: return
    if (dao.categoriesOf(group).any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) {
      throw CategoryChangeException.DuplicateName(trimmed)
    }
    sync(categoryChange(id, NAME, MessageValue.String(trimmed)))
  }

  suspend fun setCategoryHidden(id: CategoryId, hidden: Boolean) =
    sync(categoryChange(id, HIDDEN, hidden.messageValue()))

  /** moveCategory(): one place up or down within its group */
  suspend fun moveCategory(id: CategoryId, direction: Direction) {
    val group = dao.category(id)?.cat_group ?: return
    val siblings = dao.categoriesOf(group)
    val target = targetFor(siblings.map { it.id }, id, direction) ?: return
    moveCategoryTo(id, group, siblings.map { it.sortable() }, target.value?.value)
  }

  /** moveCategory() into another group, at the end of it */
  suspend fun moveCategoryToGroup(id: CategoryId, group: CategoryGroupId) {
    val siblings = dao.categoriesOf(group).filter { it.id != id }
    moveCategoryTo(id, group, siblings.map { it.sortable() }, targetId = null)
  }

  /** Whether deleting [id] needs somewhere to move its transactions or budgets to */
  suspend fun needsTransfer(id: CategoryId): Boolean =
    dao.isUsed(id) || budgets(id).any { it.amount != Amount.Zero }

  /** Whether any category in [group] needs somewhere to go when it's deleted */
  suspend fun groupNeedsTransfer(group: CategoryGroupId): Boolean =
    dao.categoryIdsOf(group).any { needsTransfer(it) }

  /** budget/app.ts deleteCategory() */
  suspend fun deleteCategory(id: CategoryId, transferTo: CategoryId?) =
    sync(deleteCategoryChanges(id, transferTo))

  private suspend fun moveCategoryTo(
    id: CategoryId,
    group: CategoryGroupId,
    siblings: List<Sortable>,
    targetId: String?,
  ) {
    val shove = shoveSortOrders(siblings, targetId)
    sync(
      shove.updates.map {
        categoryChange(CategoryId(it.id), SORT_ORDER, it.sortOrder.messageValue())
      } +
        listOf(
          categoryChange(id, SORT_ORDER, shove.sortOrder.messageValue()),
          categoryChange(id, CAT_GROUP, MessageValue.String(group.value)),
        )
    )
  }

  private suspend fun deleteCategoryChanges(
    id: CategoryId,
    transferTo: CategoryId?,
  ): List<LocalChange> {
    val category = dao.category(id) ?: return emptyList()
    val changes = mutableListOf<LocalChange>()
    if (transferTo != null) {
      val transfer = dao.category(transferTo) ?: return emptyList()
      if (category.is_income != transfer.is_income) throw CategoryChangeException.IncomeMismatch()

      // doTransfer(): an expense category's budgets are added to the one taking its place
      if (category.is_income != true) changes += transferBudgets(id, transferTo)

      // Categories already folded into this one follow it to the new one, then this one does
      dao.mappedTo(id).forEach { mapped ->
        changes +=
          LocalChange(
            CATEGORY_MAPPING,
            mapped.value,
            TRANSFER_ID,
            MessageValue.String(transferTo.value),
          )
      }
      changes +=
        LocalChange(CATEGORY_MAPPING, id.value, TRANSFER_ID, MessageValue.String(transferTo.value))
    }
    changes += tombstone(CATEGORIES, id.value)
    return changes
  }

  private suspend fun transferBudgets(from: CategoryId, to: CategoryId): List<LocalChange> {
    val type =
      BudgetType.from(preferencesDao[SyncedPrefKey.Global.BudgetType]) ?: BudgetType.Envelope
    val table = if (type == Tracking) REFLECT_BUDGETS else ZERO_BUDGETS
    val existing = budgets(to, type).associateBy { it.month }
    return budgets(from, type)
      .filter { it.amount != Amount.Zero }
      .flatMap { moved ->
        val current = existing[moved.month]
        budgetChanges(
          table,
          current?.row,
          moved.month,
          to,
          (current?.amount ?: Amount.Zero) + moved.amount,
        )
      }
  }

  private suspend fun budgets(id: CategoryId): List<MonthBudget> {
    val type =
      BudgetType.from(preferencesDao[SyncedPrefKey.Global.BudgetType]) ?: BudgetType.Envelope
    return budgets(id, type)
  }

  private suspend fun budgets(id: CategoryId, type: BudgetType): List<MonthBudget> =
    when (type) {
      Envelope -> dao.envelopeBudgets(id)
      Tracking -> dao.trackingBudgets(id)
    }

  private suspend fun sync(vararg changes: LocalChange) = sync(changes.toList())

  private suspend fun sync(changes: List<LocalChange>) {
    if (changes.isNotEmpty()) syncController.syncChanges(changes)
  }
}

enum class Direction {
  Up,
  Down,
}

/**
 * Which item a move up or down goes before, as moveCategory() and moveCategoryGroup() take it: the
 * one above for up, the one two below for down (null meaning the end). Null when it can't move.
 */
internal fun <T> targetFor(siblings: List<T>, id: T, direction: Direction): Target<T>? {
  val index = siblings.indexOf(id)
  if (index == -1) return null
  return when (direction) {
    Direction.Up -> siblings.getOrNull(index - 1)?.let { Target(it) }
    Direction.Down ->
      if (index == siblings.lastIndex) null else Target(siblings.getOrNull(index + 2))
  }
}

/** Where to put a moved item: before [value], or at the end when it's null */
internal data class Target<T>(val value: T?)

private fun validName(name: String): String =
  name.trim().ifEmpty { throw CategoryChangeException.BlankName() }

private fun groupChange(id: CategoryGroupId, column: String, value: MessageValue) =
  LocalChange(CATEGORY_GROUPS, id.value, column, value)

private fun categoryChange(id: CategoryId, column: String, value: MessageValue) =
  LocalChange(CATEGORIES, id.value, column, value)

private fun LiveCategoryOrder.sortable() = Sortable(id.value, sort_order ?: 0.0)

// Booleans are synced as 1 or 0, as upstream's convertForInsert() writes them
private fun Boolean.messageValue() = MessageValue.Number(if (this) 1 else 0)

// Sort orders go out as whole numbers. Upstream can send halves, but this app only reads whole
// numbers back from sync messages, and rounding down still lands between the neighbours.
private fun Double.messageValue(): MessageValue =
  MessageValue.Number(kotlin.math.floor(this).toLong())

// Columns of the categories, category_groups and category_mapping tables
private const val NAME = "name"
private const val SORT_ORDER = "sort_order"
private const val HIDDEN = "hidden"
private const val IS_INCOME = "is_income"
private const val CAT_GROUP = "cat_group"
private const val TRANSFER_ID = "transferId"
