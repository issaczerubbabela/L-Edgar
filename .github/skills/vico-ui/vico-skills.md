# Vico Charting Library: Skills & Standards (v2.x API)

## Overview

Vico is a light, extensible, and Compose-first charting library for Android.
**CRITICAL AI INSTRUCTION:** Vico underwent a major API overhaul in version 2.0. You MUST use the 2.x nomenclature. Do NOT use Vico 1.x classes like `ChartEntryModel`, `ChartEntryModelProducer`, `lineChart()`, or `columnChart()`.

## 1. Core Nomenclature (Vico 2.x)

Always use the Cartesian prefix for standard 2D charts.

- **WRONG (v1.x):** `ChartEntryModel`, `ChartEntryModelProducer`, `Chart`
- **CORRECT (v2.x):** `CartesianChartModel`, `CartesianChartModelProducer`, `CartesianChartHost`

## 2. Dependencies

Ensure the following dependencies are used (or inferred):
`implementation("com.patrykandpatrick.vico:compose:2.0.0-alpha.x")`
`implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.x")`
`implementation("com.patrykandpatrick.vico:core:2.0.0-alpha.x")`

## 3. Data Supplying (The Producer)

Data must be decoupled from the UI. Use `CartesianChartModelProducer` hoisted in a ViewModel or high-level state, NOT instantiated inside the drawing Composable.

````kotlin
// In ViewModel
val modelProducer = CartesianChartModelProducer()

// Updating data (run inside a coroutine/viewModelScope)
modelProducer.runTransaction {
    lineSeries { series(xValues, yValues) }
    // OR
    columnSeries { series(xValues, yValues) }
}


## 4. Drawing the Chart (The UI)
Charts are constructed using `CartesianChartHost`, wrapping a `CartesianChart` with specific layers.
### Line Chart Example

```kotlin
@Composable
fun MyLineChart(modelProducer: CartesianChartModelProducer) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = modelProducer,
    )
}
````

### Column (Bar) Chart Example

```kotlin
@Composable
fun MyColumnChart(modelProducer: CartesianChartModelProducer) {
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = modelProducer,
    )
}
```

## 5. Styling & Best Practices

- **Material 3:** Always use Vico's Material 3 extensions to ensure the charts adapt to light/dark themes automatically. Do not hardcode Hex colors unless specifically requested.
- **Markers:** Interactive tooltips (Markers) are not built-in by default. Create a custom Marker component (implementing `CartesianMarker`) and pass it to the `marker` parameter of `rememberCartesianChart()`.
- **Value Formatters:** Use `CartesianValueFormatter` on the axes to format raw Double values into strings.

```kotlin
val bottomAxisFormatter = CartesianValueFormatter { value, _ ->
    myDateList.getOrNull(value.toInt()) ?: ""
}
HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter)
```

## 6. Official Resources for AI Context

- **Docs:** https://www.google.com/search?q=https://patrykandpatrick.com/vico
- **GitHub:** https://github.com/patrykandpatrick/vico

## 7. App-Specific Implementation Rules

1. Never block the main thread when updating the `CartesianChartModelProducer`.
2. Vico primarily specializes in Cartesian (X/Y) charts. For Donut charts, fallback to native Jetpack Compose Canvas with `drawArc` as defined in previous app standards, unless Vico's current alpha explicitly supports circular charts well.
3. Use `Modifier.fillMaxWidth().height(250.dp)` as the standard sizing constraint for `CartesianChartHost` unless otherwise specified.
