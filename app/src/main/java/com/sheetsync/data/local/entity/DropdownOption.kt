package com.sheetsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dropdown_options")
data class DropdownOption(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val optionType: String,
    val name: String,
    val displayOrder: Int
)
