package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.ledger.LedgerRow
import com.issaczerubbabel.ledgar.ui.theme.ExpenseOrange
import com.issaczerubbabel.ledgar.ui.theme.IncomeBlue
import com.issaczerubbabel.ledgar.ui.theme.TransferGray
import com.issaczerubbabel.ledgar.util.TransactionType

/** The colour a Transaction's amount takes: money in, money out, a Transfer, or a Balance adjustment. */
@Composable
fun transactionAmountColor(type: String): Color = when (type) {
    TransactionType.INCOME -> IncomeBlue
    TransactionType.EXPENSE -> ExpenseOrange
    // A Balance adjustment only corrects a balance, so it reads apart from money in and out.
    TransactionType.ADJUSTMENT -> MaterialTheme.colorScheme.tertiary
    else -> TransferGray
}

/**
 * One Transaction, drawn the same on every screen that lists them: the Category, the description
 * over its Account, and the amount in full.
 *
 * @param trailingLine a line under the amount, such as an Account page's running balance.
 * @param trailing a slot after the amount, such as Bookmarks' quick un-bookmark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    row: LedgerRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailingLine: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val amountColor = transactionAmountColor(row.type)
    val italic = if (row.isAdjustment) FontStyle.Italic else null
    val selectedBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) selectedBg else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.category,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = italic,
            color = if (row.isAdjustment) amountColor else MaterialTheme.colorScheme.onSurfaceVariant,
            // Wider on a tablet, where there's room for longer Category names.
            modifier = Modifier.width(if (LocalConfiguration.current.screenWidthDp >= 600) 128.dp else 96.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.accountLabel.isNotBlank()) {
                Text(
                    row.accountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 72.dp)) {
            // In full, never ellipsised: the description gives way instead.
            Text(
                text = row.amount,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                fontStyle = italic,
                color = amountColor,
                maxLines = 1,
                softWrap = false
            )
            trailingLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        trailing?.invoke()
    }
}
