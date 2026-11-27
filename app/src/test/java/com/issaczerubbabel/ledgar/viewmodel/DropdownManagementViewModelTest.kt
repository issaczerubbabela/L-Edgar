package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.dao.DropdownUsage
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DropdownManagementViewModelTest {

    private val groceries = DropdownOption(id = 1, optionType = EXPENSE, name = "Groceries", displayOrder = 0)
    private val food = DropdownOption(id = 2, optionType = EXPENSE, name = "Food & Snacks", displayOrder = 1)
    private val savings = DropdownOption(id = 3, optionType = EXPENSE, name = "Investments/Savings", displayOrder = 2, role = DropdownRole.SAVING)
    private val fuel = DropdownOption(id = 4, optionType = EXPENSE, name = "Fuel", displayOrder = 3)
    private val cards = DropdownOption(id = 5, optionType = GROUP, name = "Cards", displayOrder = 0, role = DropdownRole.LIABILITY)

    private lateinit var repository: FakeDropdownOptions

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeDropdownOptions(listOf(groceries, food, savings, fuel, cards))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): DropdownManagementViewModel {
        val vm = DropdownManagementViewModel(repository, UndoableActions(unusedExpenseDao(), backgroundScope))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun DropdownManagementViewModel.shownNames() = uiState.value.options.map { it.name }

    @Test
    fun `an unused option deletes at once and offers Undo, writing only when the window ends`() = runTest {
        val vm = viewModel()

        vm.requestDelete(fuel)

        assertNull(vm.uiState.value.deleteConfirm)
        assertEquals(listOf("Groceries", "Food & Snacks", "Investments/Savings"), vm.shownNames())
        assertEquals("Deleted “Fuel”", vm.pendingDelete.value?.message)
        assertEquals(5, repository.options.value.size)

        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)
        advanceUntilIdle()

        assertEquals(listOf(groceries, food, savings, cards), repository.options.value)
        assertEquals(listOf("Groceries", "Food & Snacks", "Investments/Savings"), vm.shownNames())
    }

    @Test
    fun `an option in use asks first and says what uses it`() = runTest {
        repository.usages[food.id] = DropdownUsage(transactions = 12, bucketRoutes = 1, merchantRules = 2)
        val vm = viewModel()

        vm.requestDelete(food)

        val confirm = vm.uiState.value.deleteConfirm!!
        assertEquals("Delete Food & Snacks?", confirm.title)
        assertEquals("12 transactions, 1 Bucket route and 2 Merchant rules use it. They'll keep the name.", confirm.message)
        assertEquals(4, vm.uiState.value.options.size)
        assertNull(vm.pendingDelete.value)
    }

    @Test
    fun `Cancel changes nothing`() = runTest {
        repository.usages[food.id] = DropdownUsage(transactions = 3)
        val vm = viewModel()

        vm.requestDelete(food)
        vm.cancelDelete()
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS * 2)
        advanceUntilIdle()

        assertNull(vm.uiState.value.deleteConfirm)
        assertNull(vm.pendingDelete.value)
        assertEquals(listOf("Groceries", "Food & Snacks", "Investments/Savings", "Fuel"), vm.shownNames())
        assertEquals(listOf(groceries, food, savings, fuel, cards), repository.options.value)
    }

    @Test
    fun `Undo brings a deleted option back in its place with its role`() = runTest {
        repository.usages[savings.id] = DropdownUsage(transactions = 4, repeatRules = 1)
        val vm = viewModel()

        vm.requestDelete(savings)
        vm.confirmDelete()
        assertNull(vm.uiState.value.deleteConfirm)
        assertEquals(listOf("Groceries", "Food & Snacks", "Fuel"), vm.shownNames())

        vm.undoDelete(vm.pendingDelete.value!!)
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS * 2)
        advanceUntilIdle()

        assertEquals(listOf(groceries, food, savings, fuel), vm.uiState.value.options)
        assertEquals(listOf(groceries, food, savings, fuel, cards), repository.options.value)
    }

    @Test
    fun `an Account group in use says how many Accounts are in it`() = runTest {
        repository.usages[cards.id] = DropdownUsage(accounts = 3)
        val vm = viewModel()
        vm.selectType(DropdownOptionType.AccountGroup)

        vm.requestDelete(cards)

        assertEquals("3 Accounts are in it. They'll keep the name.", vm.uiState.value.deleteConfirm?.message)
    }

    @Test
    fun `an option added while a delete offers Undo goes after the deleted one`() = runTest {
        val vm = viewModel()

        vm.requestDelete(fuel)
        vm.showAddDialog()
        vm.updateNewOptionName("Travel")
        vm.addOption()

        assertEquals(4, repository.options.value.single { it.name == "Travel" }.displayOrder)
    }

    @Test
    fun `the confirm words one use in the singular`() {
        assertEquals("1 transaction uses it. It'll keep the name.", OptionUsage.describe(DropdownUsage(transactions = 1)))
        assertEquals("1 Account is in it. It'll keep the name.", OptionUsage.describe(DropdownUsage(accounts = 1)))
        assertEquals(
            "1 transaction and 1 Repeat rule use it. They'll keep the name.",
            OptionUsage.describe(DropdownUsage(transactions = 1, repeatRules = 1))
        )
        assertEquals("2 Repeat rules use it. They'll keep the name.", OptionUsage.describe(DropdownUsage(repeatRules = 2)))
    }

    private companion object {
        const val EXPENSE = "EXPENSE_CATEGORY"
        const val GROUP = "ACCOUNT_GROUP"

        /** Deleting an option never deletes a Transaction, so nothing here may touch this. */
        fun unusedExpenseDao(): ExpenseDao = Proxy.newProxyInstance(
            ExpenseDao::class.java.classLoader, arrayOf(ExpenseDao::class.java)
        ) { _, method, _ -> error("ExpenseDao.${method.name} isn't used by Dropdown deletes") } as ExpenseDao
    }
}

/** The options in memory, with each option's use set by the test. */
private class FakeDropdownOptions(options: List<DropdownOption>) : DropdownOptionRepository {
    val options = MutableStateFlow(options)
    val usages = mutableMapOf<Long, DropdownUsage>()

    override fun getOptionsByType(optionType: String): Flow<List<DropdownOption>> =
        options.map { all -> all.filter { it.optionType == optionType }.sortedBy { it.displayOrder } }

    override suspend fun getAllOptionsSnapshot() = options.value

    override suspend fun insert(option: DropdownOption): Long {
        val id = (options.value.maxOfOrNull { it.id } ?: 0) + 1
        options.update { it + option.copy(id = id) }
        return id
    }

    override suspend fun addOptionIfAbsent(optionType: String, name: String): String? = error("not used")

    override suspend fun update(option: DropdownOption) =
        options.update { all -> all.map { if (it.id == option.id) option else it } }

    override suspend fun delete(option: DropdownOption) = options.update { all -> all.filterNot { it.id == option.id } }

    override suspend fun usage(option: DropdownOption) = usages[option.id] ?: DropdownUsage()

    override suspend fun updateOptions(options: List<DropdownOption>) = options.forEach { update(it) }

    override suspend fun overwriteAllOptions(options: List<DropdownOption>) {
        this.options.value = options
    }
}
