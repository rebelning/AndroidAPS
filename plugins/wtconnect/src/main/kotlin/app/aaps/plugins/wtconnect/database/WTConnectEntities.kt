package app.aaps.plugins.wtconnect.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wtconnect_operation",
    indices = [Index("treatmentId"), Index("recordNo")]
)
data class WTConnectOperation(
    @PrimaryKey val operationNo: String,
    val msgId: String,
    val recordNo: String,
    val treatmentId: Long,
    val pumpType: String,
    val pumpCode: String,
    val bolusType: Int,
    val targetAmount: Double,
    val finishAmount: Double = 0.0,
    val state: Int,
    val startedAt: Long,
    val finishedAt: Long? = null
)

@Entity(
    tableName = "wtconnect_outbox",
    indices = [Index(value = ["endpoint", "businessKey"], unique = true)]
)
data class WTConnectOutboxEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endpoint: String,
    val businessKey: String,
    val payloadJson: String,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
