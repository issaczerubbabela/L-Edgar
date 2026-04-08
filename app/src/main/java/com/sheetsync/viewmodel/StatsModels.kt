package com.sheetsync.viewmodel

import androidx.compose.ui.graphics.Color

enum class StatsTimeframe {
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    YTD,
    ALL_TIME
}

enum class StatsTransactionType {
    EXPENSE,
    INCOME,
    BOTH
}

data class StatsFilterState(
    val timeframe: StatsTimeframe = StatsTimeframe.THIS_MONTH,
    val accountGroupId: String? = null,
    val transactionType: StatsTransactionType = StatsTransactionType.BOTH
)

data class CategoryTotal(
    val categoryName: String,
    val totalAmount: Double,
    val assignedColor: Color
)
