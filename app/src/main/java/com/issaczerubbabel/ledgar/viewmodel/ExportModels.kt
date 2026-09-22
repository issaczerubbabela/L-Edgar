package com.issaczerubbabel.ledgar.viewmodel

import java.time.YearMonth

enum class ExportInterval {
    CURRENT_MONTH,
    LAST_3_MONTHS,
    CURRENT_YEAR,
    LAST_YEAR,
    CUSTOM
}

data class ExportUiState(
    val showDialog: Boolean = false,
    val selectedInterval: ExportInterval = ExportInterval.CURRENT_MONTH,
    val anchorMonth: YearMonth = YearMonth.now(),
    val customStartDateInput: String = "",
    val customEndDateInput: String = "",
    val pendingFileName: String? = null,
    val statusMessage: String? = null
) {
    val canPickLaterMonth: Boolean get() = anchorMonth.isBefore(YearMonth.now())
}
