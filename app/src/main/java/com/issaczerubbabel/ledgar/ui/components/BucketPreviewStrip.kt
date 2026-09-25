package com.issaczerubbabel.ledgar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.issaczerubbabel.ledgar.data.bucket.BucketPreview
import com.issaczerubbabel.ledgar.ui.theme.bucketColor
import com.issaczerubbabel.ledgar.util.formatRupees

/**
 * Shows [preview] under the date row and collapses when it is null. The last preview is kept
 * while collapsing so the strip fades out with its content instead of going blank first.
 */
@Composable
fun BucketPreviewSlot(preview: BucketPreview?, modifier: Modifier = Modifier) {
    var lastPreview by remember { mutableStateOf(preview) }
    if (preview != null) lastPreview = preview

    AnimatedVisibility(
        visible = preview != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        lastPreview?.let { BucketPreviewStrip(it) }
    }
}

/** The bucket the transaction lands in, and how much of it is left once this amount is spent. */
@Composable
fun BucketPreviewStrip(preview: BucketPreview, modifier: Modifier = Modifier) {
    val summary = if (preview.isOver) {
        "${preview.bucket.name}, over by ${formatRupees(-preview.remaining)}"
    } else {
        "${preview.bucket.name}, ${formatRupees(preview.remaining)} left of ${formatRupees(preview.allocated)}"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BucketColorDot(preview.bucket.colorIndex)
            Spacer(Modifier.width(8.dp))
            Text(
                text = preview.bucket.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (preview.isOver) {
                OverBudgetFlag(overBy = -preview.remaining)
            } else {
                Text(
                    text = "${formatRupees(preview.remaining)} left of ${formatRupees(preview.allocated)}",
                    style = tabularNumbers(MaterialTheme.typography.labelMedium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        PaceBar(
            spentFraction = preview.spentFraction,
            paceFraction = preview.paceFraction,
            color = bucketColor(preview.bucket.colorIndex),
            isOver = preview.isOver
        )
    }
}
