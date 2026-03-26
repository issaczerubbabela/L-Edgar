package com.sheetsync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sheetsync.viewmodel.BudgetSettingItemUi
import com.sheetsync.viewmodel.BudgetSettingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    vm: BudgetSettingViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Budget", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = vm::openAddDialog) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding)
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2F262B))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFFFF6A6A))
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

            items(state.items.size) { index ->
                BudgetItemRow(
                    item = state.items[index],
                    onEdit = vm::openEditDialog,
                    onDelete = vm::deleteBudget
                )
            }
        }
    }

    if (state.showEditorDialog) {
        AlertDialog(
            onDismissRequest = vm::closeEditor,
            title = { Text(if (state.editingId == null) "Add Budget" else "Edit Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryPicker(
                        selected = state.selectedCategory,
                        categories = vm.categories,
                        onSelect = vm::updateCategory
                    )
                    OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = vm::updateAmount,
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = vm::closeEditor) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = vm::saveBudget) { Text("Save") }
            }
        )
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
        Text(item.category, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text("₹ ${"%,.2f".format(item.amount)}", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onEdit(item) }
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onDelete(item) }
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
private fun CategoryPicker(
    selected: String,
    categories: List<String>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { category ->
            Text(
                text = category,
                color = if (selected == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(category) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
