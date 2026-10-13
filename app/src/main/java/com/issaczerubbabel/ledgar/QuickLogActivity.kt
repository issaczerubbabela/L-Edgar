package com.issaczerubbabel.ledgar

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.ui.components.AmountDisplay
import com.issaczerubbabel.ledgar.ui.components.BucketPreviewSlot
import com.issaczerubbabel.ledgar.ui.components.ChipRow
import com.issaczerubbabel.ledgar.ui.components.ChipSpec
import com.issaczerubbabel.ledgar.ui.components.NumericKeypad
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.components.PickerOption
import com.issaczerubbabel.ledgar.ui.theme.SheetSyncTheme
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.KeypadAction
import com.issaczerubbabel.ledgar.util.amountValue
import com.issaczerubbabel.ledgar.util.applyKeypadAction
import com.issaczerubbabel.ledgar.util.isAmountExpression
import com.issaczerubbabel.ledgar.viewmodel.QuickLogViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Fraction of the screen the sheet fills, matching the full-screen Add Transaction layout.
private const val QUICK_LOG_SHEET_HEIGHT_FRACTION = 0.92f

@AndroidEntryPoint
class QuickLogActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemePreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark by themeRepository.isDarkTheme.collectAsStateWithLifecycle(initialValue = true)
            SheetSyncTheme(isDarkTheme = isDark) {
                Surface(color = Color.Transparent) {
                    QuickLogSheet()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogSheet(
    vm: QuickLogViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val bucketContext by vm.bucketContext.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.saveSuccess.collect {
            closeQuickLog(context)
        }
    }

    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { closeQuickLog(context) },
        sheetState = sheetState
    ) {
        // Same vertical structure as Add Transaction: the amount takes whatever height the strip,
        // chip row and keypad leave over, so the keypad stays pinned to the bottom of the sheet.
        Box(modifier = Modifier.fillMaxHeight(QUICK_LOG_SHEET_HEIGHT_FRACTION)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Quick Add",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                BucketPreviewSlot(preview = vm.bucketPreview(bucketContext))

                var evaluatePulse by remember { mutableIntStateOf(0) }
                AmountDisplay(
                    amount = vm.amount,
                    type = TransactionType.EXPENSE,
                    pulseSignal = evaluatePulse,
                    modifier = Modifier.weight(1f)
                )

                val accountName = accounts.firstOrNull { it.id == vm.selectedAccountId }?.accountName
                ChipRow(
                    primaryChips = listOf(
                        ChipSpec(vm.selectedCategory.ifBlank { "Category" }, vm.selectedCategory.isNotBlank()) {
                            showCategoryPicker = true
                        },
                        ChipSpec(accountName ?: "Account", accountName != null) {
                            showAccountPicker = true
                        }
                    ),
                    swapKey = TransactionType.EXPENSE
                )

                val hasExpression = isAmountExpression(vm.amount)
                NumericKeypad(
                    onAction = { action -> vm.amount = applyKeypadAction(vm.amount, action) },
                    onCommit = {
                        // "=" works the sum out first; the next tap saves.
                        if (hasExpression) {
                            vm.amount = applyKeypadAction(vm.amount, KeypadAction.Evaluate)
                            evaluatePulse++
                        } else {
                            vm.save()
                        }
                    },
                    commitEnabled = if (hasExpression) amountValue(vm.amount) != null else vm.amount.isNotBlank(),
                    hasExpression = hasExpression
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showCategoryPicker) {
        OptionPickerSheet(
            title = "Category",
            options = categories.map { PickerOption(it) },
            selectedKey = vm.selectedCategory,
            onSelect = {
                vm.selectedCategory = it
                showCategoryPicker = false
            },
            onCreate = {
                vm.addCategoryInline(it)
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
            manageHint = "Reorder or delete in More → Dropdowns"
        )
    }

    if (showAccountPicker) {
        OptionPickerSheet(
            title = "Account",
            options = accounts.map { PickerOption(key = it.id.toString(), label = it.accountName) },
            selectedKey = vm.selectedAccountId?.toString(),
            onSelect = { key ->
                key.toLongOrNull()?.let { vm.selectAccount(it) }
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
            manageHint = "Add accounts from the Accounts tab"
        )
    }
}

private fun closeQuickLog(context: Context) {
    val activity = context as? Activity ?: return
    activity.finishAndRemoveTask()
}
