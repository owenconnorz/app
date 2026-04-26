package com.aioweb.app.data.library

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnail: String?,
    @ColumnInfo(name = "liked_at") val likedAt: Long? = null,
    @ColumnInfo(name = "last_played") val lastPlayed: Long? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
)

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("UPDATE tracks SET last_played = :ts, play_count = play_count + 1 WHERE url = :url")
    suspend fun bumpPlayed(url: String, ts: Long)

    @Query("UPDATE tracks SET liked_at = :ts WHERE url = :url")
    suspend fun setLikedAt(url: String, ts: Long?)

    @Query("SELECT * FROM tracks WHERE last_played IS NOT NULL ORDER BY last_played DESC LIMIT 100")
    fun recent(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE liked_at IS NOT NULL ORDER BY liked_at DESC")
    fun liked(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY play_count DESC LIMIT 30")
    fun mostPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT liked_at IS NOT NULL FROM tracks WHERE url = :url")
    fun isLiked(url: String): Flow<Boolean?>
}

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class LibraryDb : RoomDatabase() {
    abstract fun tracks(): TrackDao
    companion object {
        @Volatile private var INSTANCE: LibraryDb? = null
        fun get(context: Context): LibraryDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext, LibraryDb::class.java, "streamcloud-library.db",
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
