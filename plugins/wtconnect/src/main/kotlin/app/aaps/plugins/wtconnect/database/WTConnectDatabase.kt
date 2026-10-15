package app.aaps.plugins.wtconnect.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WTConnectOperation::class, WTConnectOutboxEntry::class],
    version = 1,
    exportSchema = true
)
abstract class WTConnectDatabase : RoomDatabase() {
    abstract fun dao(): WTConnectDao

    companion object {
        fun build(context: Context): WTConnectDatabase =
            Room.databaseBuilder(context.applicationContext, WTConnectDatabase::class.java, "wtconnect.db")
                .build()
    }
}
