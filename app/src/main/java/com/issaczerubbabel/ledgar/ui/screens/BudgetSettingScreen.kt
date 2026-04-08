package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.issaczerubbabel.ledgar.viewmodel.BudgetSettingItemUi
import com.issaczerubbabel.ledgar.viewmodel.BudgetSettingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: BudgetSettingViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)   // honour outer Scaffold's padding (nav bar height)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ────────────────────────────────────────────────────────
            CenterAlignedTopAppBar(
                title = { Text("Budget", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // ── Scrollable content ─────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 88.dp)  // clear FAB
            ) {
                // Info banner
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2F262B))
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFFFF6A6A)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "You can check out the budget status in Trans. tab > Total page.",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }

                // Total budget section
                item {
                    TotalBudgetSection(
                        totalBudgetInput = state.totalBudgetInput,
                        onTotalBudgetInputChange = vm::updateTotalBudgetInput,
                        onSaveTotalBudget = vm::saveTotalBudget,
                        allocatedAmount = state.allocatedAmount,
                        otherAmount = state.otherAmount
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                }

                // Category budget rows
                items(state.items.size) { index ->
                    BudgetItemRow(
                        item = state.items[index],
                        onEdit = vm::openEditDialog,
                        onDelete = vm::deleteBudget
                    )
                }

                // "Other" read-only row
                if (state.items.isNotEmpty() || state.totalBudgetAmount > 0.0) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📂", fontSize = 23.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Other",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "₹ ${"%,.2f".format(state.otherAmount)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    }
                }
            }
        }

        // ── FAB pinned to bottom-end of the Box ────────────────────────────────
        FloatingActionButton(
            onClick = vm::openAddDialog,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add budget")
        }
    }

    // ── Add / Edit Budget — ModalBottomSheet ────────────────────────────────────
    if (state.showEditorDialog) {
        val noCategoriesAvailable = state.categoryOptions.isEmpty()
        val canSave = !noCategoriesAvailable &&
                state.selectedCategory.isNotBlank() &&
                state.amountInput.toDoubleOrNull() != null

        ModalBottomSheet(
            onDismissRequest = vm::closeEditor,
            sheetState = sheetState,
            windowInsets = WindowInsets.navigationBars
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (state.editingId == null) "Add Category Budget" else "Edit Category Budget",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Warning when no expense categories are configured
                if (noCategoriesAvailable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "No expense categories found. Add options in Dropdown Management first.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Category dropdown
                CategoryDropdown(
                    selected = state.selectedCategory,
                    categories = state.categoryOptions,
                    onSelect = vm::updateCategory,
                    enabled = !noCategoriesAvailable
                )

                // Amount field
                OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = vm::updateAmount,
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = vm::closeEditor) { Text("Cancel") }
                    Button(onClick = vm::saveBudget, enabled = canSave) { Text("Save") }
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun TotalBudgetSection(
    totalBudgetInput: String,
    onTotalBudgetInputChange: (String) -> Unit,
    onSaveTotalBudget: () -> Unit,
    allocatedAmount: Double,
    otherAmount: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Total Budget",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        OutlinedTextField(
            value = totalBudgetInput,
            onValueChange = onTotalBudgetInputChange,
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onSaveTotalBudget, modifier = Modifier.align(Alignment.End)) {
            Text("Save Total")
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            thickness = 0.5.dp
        )

        AmountSummaryRow(label = "Allocated", value = allocatedAmount)
        AmountSummaryRow(label = "Other", value = otherAmount)
    }
}

@Composable
private fun AmountSummaryRow(
    label: String,
    value: Double,
    valueColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("₹ ${"%,.2f".format(value)}", color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BudgetItemRow(
    item: BudgetSettingItemUi,
    onEdit: (BudgetSettingItemUi) -> Unit,
    onDelete: (BudgetSettingItemUi) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.icon, fontSize = 23.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            item.category,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "₹ ${"%,.2f".format(item.amount)}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).clickable { onEdit(item) }
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).clickable { onDelete(item) }
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String,
    categories: List<String>,
    onSelect: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
