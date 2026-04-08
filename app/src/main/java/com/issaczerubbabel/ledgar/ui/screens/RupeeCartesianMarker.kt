package com.issaczerubbabel.ledgar.ui.screens

import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.HorizontalDimensions
import com.patrykandpatrick.vico.core.cartesian.HorizontalInsets
import com.patrykandpatrick.vico.core.cartesian.Insets
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.HorizontalPosition
import com.patrykandpatrick.vico.core.common.VerticalPosition
import com.patrykandpatrick.vico.core.common.component.TextComponent
import kotlin.math.max

class RupeeCartesianMarker(
    private val label: TextComponent,
    private val valueFormatter: CartesianMarkerValueFormatter,
    private val verticalOffsetPx: Float = 8f,
    private val topInsetSampleText: CharSequence = "₹99,99,999"
) : CartesianMarker {

    override fun draw(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        if (targets.isEmpty()) return

        val labelText = valueFormatter.format(context, targets)
        if (labelText.isEmpty()) return

        val bounds = context.layerBounds
        val maxWidth = bounds.width().toInt()
        val maxHeight = bounds.height().toInt()

        val labelWidth = label.getWidth(context, labelText, maxWidth, maxHeight, 0f, true)
        val labelHeight = label.getHeight(context, labelText, maxWidth, maxHeight, 0f, true)

        val centerX = targets.first().canvasX
        val minX = bounds.left + (labelWidth / 2f)
        val maxX = bounds.right - (labelWidth / 2f)
        val clampedX = centerX.coerceIn(minX, maxX)

        val markerTopY = targets.mapNotNull { it.topCanvasY() }.minOrNull() ?: bounds.top
        val desiredBottomY = markerTopY - verticalOffsetPx
        val minBottomY = bounds.top + labelHeight
        val labelBottomY = max(desiredBottomY, minBottomY)

        label.draw(
            context = context,
            text = labelText,
            x = clampedX,
            y = labelBottomY,
            horizontalPosition = HorizontalPosition.Center,
            verticalPosition = VerticalPosition.Bottom,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            rotationDegrees = 0f
        )
    }

    override fun updateInsets(
        context: CartesianMeasuringContext,
        horizontalDimensions: HorizontalDimensions,
        model: CartesianChartModel,
        insets: Insets
    ) {
        val topInset = label.getHeight(
            context,
            topInsetSampleText,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            0f,
            true
        ) + verticalOffsetPx
        insets.ensureValuesAtLeast(0f, topInset, 0f, 0f)
    }

    override fun updateHorizontalInsets(
        context: CartesianMeasuringContext,
        freeHeight: Float,
        model: CartesianChartModel,
        insets: HorizontalInsets
    ) {
        insets.ensureValuesAtLeast(0f, 0f)
    }
}

private fun CartesianMarker.Target.topCanvasY(): Float? {
    return when (this) {
        is ColumnCartesianLayerMarkerTarget -> columns.minOfOrNull { it.canvasY }
        is LineCartesianLayerMarkerTarget -> points.minOfOrNull { it.canvasY }
        else -> null
    }
}