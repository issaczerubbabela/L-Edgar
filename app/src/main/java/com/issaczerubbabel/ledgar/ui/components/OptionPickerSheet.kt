package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * One choice in an [OptionPickerSheet]. [key] is what comes back from a selection, so two options
 * that read the same (two accounts with one name) are still told apart; [label] is what is shown.
 */
data class PickerOption(val key: String, val label: String = key)

/**
 * Bottom sheet picker for a single option (category, account, ...): a search field, a chip grid
 * of [options], and a dashed "+ Add" chip that swaps to an inline text field so a brand-new
 * option can be created without leaving the screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OptionPickerSheet(
    title: String,
    options: List<PickerOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCreate: ((String) -> Unit)? = null,
    manageHint: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    var newOptionName by remember { mutableStateOf("") }

    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) options
        else options.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    fun confirmAdd() {
        val trimmed = newOptionName.trim()
        if (trimmed.isEmpty()) return
        onCreate?.invoke(trimmed)
        isAdding = false
        newOptionName = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            AnimatedContent(
                targetState = isAdding,
                transitionSpec = { slideSwap() },
                label = "picker-search-or-add"
            ) { adding ->
                if (adding) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newOptionName,
                            onValueChange = { newOptionName = it },
                            label = { Text("New $title") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = ::confirmAdd) {
                            Icon(Icons.Filled.Check, contentDescription = "Add $title")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredOptions.forEach { option ->
                    OptionChip(
                        label = option.label,
                        selected = option.key == selectedKey,
                        onClick = { onSelect(option.key) }
                    )
                }
                AnimatedVisibility(
                    visible = !isAdding && onCreate != null,
                    enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
                    exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f)
                ) {
                    AddOptionChip(onClick = { isAdding = true })
                }
            }

            if (manageHint != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = manageHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** The dashed "+ Add" chip: same footprint as an option chip, outlined rather than filled. */
@Composable
private fun AddOptionChip(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                )
                drawRoundRect(outline, cornerRadius = CornerRadius(12.dp.toPx()), style = stroke)
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Add",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
