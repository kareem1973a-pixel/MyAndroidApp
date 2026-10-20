package com.nova.ai.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memories")
data class MemoryEntity(
    @PrimaryKey val memoryKey: String,
    val memoryValue: String,
    val createdAt: Long = System.currentTimeMillis()
)
