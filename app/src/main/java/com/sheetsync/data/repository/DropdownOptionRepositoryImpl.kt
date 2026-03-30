package com.sheetsync.data.repository

import com.sheetsync.data.local.dao.DropdownOptionDao
import com.sheetsync.data.local.entity.DropdownOption
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DropdownOptionRepositoryImpl @Inject constructor(
    private val dao: DropdownOptionDao
) : DropdownOptionRepository {

    override fun getOptionsByType(optionType: String): Flow<List<DropdownOption>> =
        dao.getOptionsByType(optionType)

    override suspend fun getAllOptionsSnapshot(): List<DropdownOption> = dao.getAllOptionsSnapshot()

    override suspend fun insert(option: DropdownOption): Long = dao.insert(option)

    override suspend fun update(option: DropdownOption) = dao.update(option)

    override suspend fun delete(option: DropdownOption) = dao.delete(option)

    override suspend fun updateOptions(options: List<DropdownOption>) = dao.updateOptions(options)

    override suspend fun overwriteAllOptions(options: List<DropdownOption>) = dao.overwriteAllOptions(options)
}
