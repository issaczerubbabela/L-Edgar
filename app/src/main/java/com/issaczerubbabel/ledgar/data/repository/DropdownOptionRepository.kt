package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import kotlinx.coroutines.flow.Flow

interface DropdownOptionRepository {
    fun getOptionsByType(optionType: String): Flow<List<DropdownOption>>
    suspend fun getAllOptionsSnapshot(): List<DropdownOption>
    suspend fun insert(option: DropdownOption): Long
    suspend fun update(option: DropdownOption)
    suspend fun delete(option: DropdownOption)
    suspend fun updateOptions(options: List<DropdownOption>)
    suspend fun overwriteAllOptions(options: List<DropdownOption>)
}
