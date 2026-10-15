package app.aaps.plugins.wtconnect.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WTConnectDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOperation(operation: WTConnectOperation): Long

    @Query("SELECT * FROM wtconnect_operation WHERE operationNo = :operationNo LIMIT 1")
    fun operation(operationNo: String): WTConnectOperation?

    @Query("SELECT * FROM wtconnect_operation WHERE treatmentId = :treatmentId LIMIT 1")
    fun operationByTreatmentId(treatmentId: Long): WTConnectOperation?

    @Query("UPDATE wtconnect_operation SET finishAmount = :finishAmount, state = :state, finishedAt = :finishedAt WHERE operationNo = :operationNo")
    fun updateOperation(operationNo: String, finishAmount: Double, state: Int, finishedAt: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putOutbox(entry: WTConnectOutboxEntry): Long

    @Query("SELECT * FROM wtconnect_outbox WHERE nextAttemptAt <= :now ORDER BY createdAt LIMIT :limit")
    fun pendingOutbox(now: Long, limit: Int = 25): List<WTConnectOutboxEntry>

    @Query("DELETE FROM wtconnect_outbox WHERE id = :id")
    fun deleteOutbox(id: Long)

    @Query("UPDATE wtconnect_outbox SET attempts = :attempts, nextAttemptAt = :nextAttemptAt WHERE id = :id")
    fun retryOutbox(id: Long, attempts: Int, nextAttemptAt: Long)

    @Query("SELECT COUNT(*) FROM wtconnect_outbox")
    fun outboxCount(): Int
}
