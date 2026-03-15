package com.example.clu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Insert
    suspend fun insert(record: VaultRecord)

    @Query("SELECT * FROM vault_records ORDER BY timestamp DESC LIMIT 100")
    fun getLatestRecords(): Flow<List<VaultRecord>>

    @Query("SELECT * FROM vault_records WHERE timestamp >= :startOfDay AND timestamp <= :endOfDay ORDER BY timestamp ASC")
    fun getRecordsForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<VaultRecord>>
}
