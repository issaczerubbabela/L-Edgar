package com.issaczerubbabel.ledgar

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.ui.components.NumericKeypad
import com.issaczerubbabel.ledgar.ui.components.OptionPickerSheet
import com.issaczerubbabel.ledgar.ui.theme.SheetSyncTheme
import com.issaczerubbabel.ledgar.util.applyKeypadAction
import com.issaczerubbabel.ledgar.viewmodel.QuickLogViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCategoryPicker by remember { mutableStateOf(false) }

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
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Quick Add", style = MaterialTheme.typography.titleLarge)

                    Text(
                        text = "₹ ${vm.amount.ifEmpty { "0" }}",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.align(Alignment.End)
                    )

                    AssistChip(
                        onClick = { showCategoryPicker = true },
                        label = { Text(vm.selectedCategory.ifBlank { "Category" }) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (vm.selectedCategory.isNotBlank())
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                NumericKeypad(
                    onAction = { action -> vm.amount = applyKeypadAction(vm.amount, action) },
                    onCommit = vm::save,
                    commitEnabled = vm.amount.isNotBlank()
                )
            }
        }
    }

    if (showCategoryPicker) {
        OptionPickerSheet(
            title = "Category",
            options = categories,
            selected = vm.selectedCategory,
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
}

private fun closeQuickLog(context: Context) {
    val activity = context as? Activity ?: return
    activity.finishAndRemoveTask()
}
