package com.example.clu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_records")
data class VaultRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float,
    val ruleIndex: Float
)
