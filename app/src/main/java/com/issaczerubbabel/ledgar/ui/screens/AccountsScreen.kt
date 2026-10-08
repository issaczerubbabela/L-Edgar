package com.issaczerubbabel.ledgar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.viewmodel.AccountGroupUi
import com.issaczerubbabel.ledgar.viewmodel.AccountRowUi
import com.issaczerubbabel.ledgar.viewmodel.AccountsTabUiState
import com.issaczerubbabel.ledgar.viewmodel.AccountsViewModel
import kotlin.math.roundToInt

/**
 * The Accounts tab: a Net worth card that opens Net worth, then each Account group with its
 * subtotal, asset groups before Liability groups. Edit order replaces the list with drag handles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    innerPadding: PaddingValues,
    onOpenAccountDetail: (Long) -> Unit,
    onOpenOverallStats: () -> Unit,
    onAddAccount: () -> Unit,
    vm: AccountsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editingOrder by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    if (editingOrder) {
                        TextButton(onClick = { editingOrder = false }) { Text("Done") }
                    } else {
                        IconButton(onClick = { editingOrder = true }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "Edit order")
                        }
                        FilledTonalButton(
                            onClick = onAddAccount,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { topPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "net-worth") {
                NetWorthCard(state = state, onClick = onOpenOverallStats)
            }

            if (state.isLoaded && state.groups.isEmpty() && state.hiddenAccounts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No accounts yet. Tap Add to create your first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }

            if (editingOrder) {
                item(key = "edit-order") {
                    EditOrder(
                        groups = state.groups,
                        onMoveGroup = vm::moveGroup,
                        onMoveAccount = vm::moveAccount
                    )
                }
            } else {
                items(state.groups, key = { "group-${it.name}" }) { group ->
                    val expanded = group.name !in collapsedGroups
                    GroupCard(
                        group = group,
                        expanded = expanded,
                        onToggle = {
                            collapsedGroups = if (expanded) collapsedGroups + group.name else collapsedGroups - group.name
                        },
                        onOpenAccount = onOpenAccountDetail
                    )
                }

                if (state.hiddenAccounts.isNotEmpty()) {
                    item(key = "hidden-toggle") {
                        OutlinedButton(
                            onClick = { showHidden = !showHidden },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                if (showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (showHidden) "Hide hidden accounts" else "Show hidden (${state.hiddenAccounts.size})")
                        }
                    }
                    if (showHidden) {
                        item(key = "hidden-accounts") {
                            AccountsCard {
                                state.hiddenAccounts.forEachIndexed { index, row ->
                                    if (index > 0) RowDivider()
                                    AccountRow(row = row, subtitle = "${row.groupName} · Hidden", onClick = { onOpenAccountDetail(row.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(state: AccountsTabUiState, onClick: () -> Unit) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Open Net worth", onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Net worth", style = MaterialTheme.typography.titleSmall, color = onContainer, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = onContainer)
        }
        Text(
            text = state.netWorth,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
            color = onContainer
        )
        if (state.monthChange.isNotBlank()) {
            Text(text = state.monthChange, style = MaterialTheme.typography.bodyMedium, color = onContainer)
        }
        HorizontalDivider(color = onContainer.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))
        Row {
            HeaderFigure("Assets", state.assets, Modifier.weight(1f))
            HeaderFigure("Liabilities", state.liabilities, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderFigure(label: String, value: String, modifier: Modifier) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = onContainer.copy(alpha = 0.85f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = onContainer)
    }
}

@Composable
private fun AccountsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun GroupCard(group: AccountGroupUi, expanded: Boolean, onToggle: () -> Unit, onOpenAccount: (Long) -> Unit) {
    AccountsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() }
            )
            if (group.isLiability) AccountTag("Liability")
            Spacer(Modifier.weight(1f))
            Text(
                text = group.subtotal,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (group.isSubtotalNegative) ExpenseOrange else MaterialTheme.colorScheme.onSurface
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            group.rows.forEach { row ->
                RowDivider()
                AccountRow(row = row, subtitle = null, onClick = { onOpenAccount(row.id) })
            }
        }
    }
}

@Composable
private fun AccountRow(row: AccountRowUi, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!row.isIncludedInTotals) AccountTag("Not in totals")
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = row.balance,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (row.isNegative) ExpenseOrange else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun AccountTag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun EditOrder(
    groups: List<AccountGroupUi>,
    onMoveGroup: (from: Int, to: Int) -> Unit,
    onMoveAccount: (groupName: String, from: Int, to: Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Drag the handles to reorder. Asset groups always come before Liability groups.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        EditOrderTitle("Groups")
        AccountsCard {
            DraggableRows(items = groups, keyOf = { it.name }, onMove = onMoveGroup) { group, handle ->
                EditOrderRow(label = group.name, detail = if (group.isLiability) "Liability" else null, handle = handle)
            }
        }
        groups.forEach { group ->
            EditOrderTitle(group.name)
            AccountsCard {
                DraggableRows(
                    items = group.allRows,
                    keyOf = { it.id },
                    onMove = { from, to -> onMoveAccount(group.name, from, to) }
                ) { row, handle ->
                    EditOrderRow(label = row.name, detail = if (row.isHidden) "Hidden" else null, handle = handle)
                }
            }
        }
    }
}

@Composable
private fun EditOrderTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .semantics { heading() }
    )
}

@Composable
private fun EditOrderRow(label: String, detail: String?, handle: Modifier) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (detail != null) AccountTag(detail)
        Box(modifier = handle.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.DragHandle, contentDescription = "Drag $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Fixed-height rows reordered by dragging their handle; TalkBack gets Move up and Move down actions instead. */
@Composable
private fun <T> DraggableRows(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    row: @Composable (item: T, handle: Modifier) -> Unit
) {
    val rowHeight = 56.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingIndex by remember(items) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(items) { mutableFloatStateOf(0f) }

    fun targetOf(from: Int): Int = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, items.lastIndex)

    Column {
        items.forEachIndexed { index, item ->
            key(keyOf(item)) {
                val from = draggingIndex
                val target = from?.let(::targetOf)
                val translation = when {
                    from == null || target == null -> 0f
                    index == from -> dragOffset
                    index in (from + 1)..target -> -rowHeightPx
                    index in target until from -> rowHeightPx
                    else -> 0f
                }
                if (index > 0) RowDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (index == from) 1f else 0f)
                        .graphicsLayer { translationY = translation }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Move up") {
                                    if (index > 0) onMove(index, index - 1)
                                    index > 0
                                },
                                CustomAccessibilityAction("Move down") {
                                    if (index < items.lastIndex) onMove(index, index + 1)
                                    index < items.lastIndex
                                }
                            )
                        }
                ) {
                    row(
                        item,
                        Modifier.pointerInput(items, index) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                },
                                onDragEnd = {
                                    val start = draggingIndex
                                    if (start != null) onMove(start, targetOf(start))
                                    draggingIndex = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
