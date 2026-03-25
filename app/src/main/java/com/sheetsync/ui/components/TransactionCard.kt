package com.sheetsync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.ui.theme.ExpenseRed
import com.sheetsync.ui.theme.IncomeGreen

fun categoryIcon(category: String) = when (category.lowercase()) {
    "food & dining", "food" -> Icons.Filled.Restaurant
    "transport" -> Icons.Filled.DirectionsCar
    "shopping" -> Icons.Filled.ShoppingCart
    "entertainment" -> Icons.Filled.Movie
    "health" -> Icons.Filled.LocalHospital
    "education" -> Icons.Filled.School
    "utilities" -> Icons.Filled.Bolt
    "rent" -> Icons.Filled.Home
    "salary" -> Icons.Filled.Work
    "investment" -> Icons.Filled.TrendingUp
    "freelance", "business" -> Icons.Filled.Laptop
    else -> Icons.Filled.Category
}

@Composable
fun TransactionCard(record: ExpenseRecord, modifier: Modifier = Modifier) {
    val isExpense = record.type == "Expense"
    val amountColor by animateColorAsState(
        targetValue = if (isExpense) ExpenseRed else IncomeGreen,
        label = "amountColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = amountColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon(record.category),
                        contentDescription = record.category,
                        tint = amountColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.category,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (record.description.isNotBlank()) {
                    Text(
                        text = record.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(record.date, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(22.dp)
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(record.paymentMode, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(22.dp)
                    )
                }
            }
            Text(
                text = "${if (isExpense) "-" else "+"}₹${"%.2f".format(record.amount)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = amountColor
            )
        }
    }
}
