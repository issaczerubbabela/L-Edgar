package com.issaczerubbabel.ledgar.viewmodel

enum class ExportInterval(val label: String) {
    CURRENT_MONTH("This month"),
    LAST_3_MONTHS("Last 3 months"),
    CURRENT_YEAR("This year"),
    LAST_YEAR("Last year"),
    CUSTOM("Custom date range")
}

data class ExportUiState(
    val showDialog: Boolean = false,
    val selectedInterval: ExportInterval = ExportInterval.CURRENT_MONTH,
    val customStartDateInput: String = "",
    val customEndDateInput: String = "",
    val pendingFileName: String? = null,
    val statusMessage: String? = null
)
