package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import kotlinx.coroutines.flow.Flow

interface DropdownOptionRepository {
    fun getOptionsByType(optionType: String): Flow<List<DropdownOption>>
    suspend fun getAllOptionsSnapshot(): List<DropdownOption>
    suspend fun insert(option: DropdownOption): Long

    /**
     * Returns the existing option of [optionType] whose name matches [name] ignoring case, or
     * appends a new one at the end of the list. There is no unique index on (optionType, name),
     * so the check lives here rather than in each caller. Returns the canonical name to select,
     * or null if [name] is blank.
     */
    suspend fun addOptionIfAbsent(optionType: String, name: String): String?
    suspend fun update(option: DropdownOption)
    suspend fun delete(option: DropdownOption)
    suspend fun updateOptions(options: List<DropdownOption>)
    suspend fun overwriteAllOptions(options: List<DropdownOption>)
}
