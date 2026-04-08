package com.issaczerubbabel.ledgar.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.FabRed
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.viewmodel.BudgetProgressUi
import com.issaczerubbabel.ledgar.viewmodel.ExportInterval
import com.issaczerubbabel.ledgar.viewmodel.TotalTabUiState

@Composable
fun TotalTabScreen(
    state: TotalTabUiState,
    onToggleBudget: () -> Unit,
    onToggleAccounts: () -> Unit,
    onNavigateBudgetSetting: () -> Unit,
    onExportClick: () -> Unit,
    onExportDismiss: () -> Unit,
    onSelectExportInterval: (ExportInterval) -> Unit,
    onCustomStartChanged: (String) -> Unit,
    onCustomEndChanged: (String) -> Unit,
    onExportConfirm: () -> Unit,
    pendingExportFileName: String?,
    onConsumeExportRequest: () -> Unit,
    onExportUriPicked: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportUriPicked(uri)
        }
    }

    LaunchedEffect(pendingExportFileName) {
        val fileName = pendingExportFileName ?: return@LaunchedEffect
        exportLauncher.launch(fileName)
        onConsumeExportRequest()
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            item {
                SectionHeader(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Budget",
                    trailingText = "Budget Setting >",
                    onTrailingClick = onNavigateBudgetSetting,
                    isExpanded = state.isBudgetExpanded,
                    onToggle = onToggleBudget
                )
            }

            if (state.isBudgetExpanded) {
                items(state.budgetItems.size) { index ->
                    BudgetProgressRow(item = state.budgetItems[index])
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    icon = Icons.Filled.Paid,
                    title = "Accounts",
                    trailingText = state.accountsSummary.dateRangeLabel,
                    onTrailingClick = {},
                    isExpanded = state.isAccountsExpanded,
                    onToggle = onToggleAccounts
                )
            }

            if (state.isAccountsExpanded) {
                item {
                    AccountsCard(
                        comparedPercent = state.accountsSummary.comparedExpensesPercent,
                        cashAccounts = state.accountsSummary.cashAccountsExpense,
                        card = state.accountsSummary.cardExpense,
                        transfer = state.accountsSummary.transferExpense
                    )
                }
                item {
                    ExportButton(onClick = onExportClick)
                }
                state.exportStatusMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (state.showExportDialog) {
            ExportDialog(
                selected = state.selectedExportInterval,
                customStart = state.customStartDateInput,
                customEnd = state.customEndDateInput,
                onSelect = onSelectExportInterval,
                onStartChanged = onCustomStartChanged,
                onEndChanged = onCustomEndChanged,
                onDismiss = onExportDismiss,
                onConfirm = onExportConfirm
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailingText: String,
    onTrailingClick: () -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = trailingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onTrailingClick),
            fontSize = 14.sp
        )

        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
private fun BudgetProgressRow(item: BudgetProgressUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.icon.isNotEmpty()) {
                Text(item.icon, fontSize = 22.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(item.title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text("${item.progressPercent}%", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
        }

        Spacer(Modifier.height(8.dp))

        IdealBudgetProgressBar(
            spentAmount = item.spentAmount,
            budgetAmount = item.budgetAmount,
            idealFraction = item.todayMarkerFraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("₹ ${money(item.budgetAmount)}", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("₹ ${money(item.spentAmount)}", color = IncomeBlue, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("₹ ${money(item.remainingAmount)}", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
private fun IdealBudgetProgressBar(
    spentAmount: Double,
    budgetAmount: Double,
    idealFraction: Float,
    modifier: Modifier = Modifier
) {
    val spentFraction = if (budgetAmount <= 0.0) 0f else (spentAmount / budgetAmount).toFloat()
    val fillFraction = spentFraction.coerceIn(0f, 1f)
    val isOverBudget = spentFraction > 1f

    Canvas(modifier = modifier) {
        val trackColor = Color(0xFF2D323C)
        val fillColor = if (isOverBudget) FabRed else IncomeBlue
        val barHeight = size.height * 0.72f
        val barTop = (size.height - barHeight) / 2f
        val corner = CornerRadius(x = barHeight / 2f, y = barHeight / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barTop),
            size = androidx.compose.ui.geometry.Size(width = size.width, height = barHeight),
            cornerRadius = corner
        )

        if (fillFraction > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, barTop),
                size = androidx.compose.ui.geometry.Size(width = size.width * fillFraction, height = barHeight),
                cornerRadius = corner
            )
        }

        val markerX = idealFraction.coerceIn(0f, 1f) * size.width
        drawLine(
            color = Color.White,
            start = Offset(markerX, barTop - 3.dp.toPx()),
            end = Offset(markerX, barTop + barHeight + 3.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun AccountsCard(
    comparedPercent: Int,
    cashAccounts: Double,
    card: Double,
    transfer: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(Color(0xFF232832), shape = MaterialTheme.shapes.small)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AccountsLine("Compared Expenses (Last month)", "${comparedPercent}%")
        AccountsLine("Expenses (Cash, Accounts)", "₹ ${money(cashAccounts)}")
        AccountsLine("Expenses (Card)", "₹ ${money(card)}")
        AccountsLine("Transfer (Cash, Accounts -> )", "₹ ${money(transfer)}")
    }
}

@Composable
private fun AccountsLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp)
    }
}

@Composable
private fun ExportButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color(0xFF232832), shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.TableChart, contentDescription = null, tint = Color(0xFF1FCE6D), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("Export data to Excel", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp)
    }
}

@Composable
private fun ExportDialog(
    selected: ExportInterval,
    customStart: String,
    customEnd: String,
    onSelect: (ExportInterval) -> Unit,
    onStartChanged: (String) -> Unit,
    onEndChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Money Manager - Excel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportInterval.values().forEach { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(interval) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected == interval) Icons.Filled.Description else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = if (selected == interval) FabRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(interval.label)
                    }
                }

                if (selected == ExportInterval.CUSTOM) {
                    OutlinedTextField(
                        value = customStart,
                        onValueChange = onStartChanged,
                        label = { Text("Start (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customEnd,
                        onValueChange = onEndChanged,
                        label = { Text("End (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    )
}

private fun money(value: Double): String = "%,.2f".format(kotlin.math.abs(value))
