package com.sh7411usa.shliachtzibbur.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sh7411usa.shliachtzibbur.data.local.dao.GroupDao
import com.sh7411usa.shliachtzibbur.data.local.dao.MemberDao
import com.sh7411usa.shliachtzibbur.data.local.dao.MessageDao
import com.sh7411usa.shliachtzibbur.data.local.dao.OutboxDao
import com.sh7411usa.shliachtzibbur.data.local.entity.GroupEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.MemberEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.MessageEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.OutboxEntity

@Database(
    entities = [GroupEntity::class, MessageEntity::class, MemberEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun messageDao(): MessageDao
    abstract fun memberDao(): MemberDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tzibbur.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
