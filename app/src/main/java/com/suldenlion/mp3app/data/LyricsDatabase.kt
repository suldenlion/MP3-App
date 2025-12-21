package com.suldenlion.mp3app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// 가사 데이터를 저장할 엔티티 (테이블)
@Entity(tableName = "lyrics")
data class Lyric(
    @PrimaryKey val musicId: Long, // MediaStore ID를 기본 키로 사용
    val lyrics: String,
    val timestamp: Long = System.currentTimeMillis() // 마지막 업데이트 시간
)

// 즐겨찾기 데이터를 저장할 엔티티
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val musicId: Long // MediaStore ID를 기본 키로 사용
)

// 데이터베이스 접근 객체 (DAO)
@Dao
interface LyricDao {
    @Query("SELECT * FROM lyrics WHERE musicId = :musicId")
    suspend fun getLyricsByMusicId(musicId: Long): Lyric?

    @Insert(onConflict = OnConflictStrategy.REPLACE) // 충돌 발생 시 (같은 musicId) 기존 데이터 교체
    suspend fun insertLyric(lyric: Lyric)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE musicId = :musicId")
    suspend fun removeFavorite(musicId: Long)

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavorites(): List<Favorite>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE musicId = :musicId)")
    suspend fun isFavorite(musicId: Long): Boolean
}

// Room 데이터베이스 정의
@Database(entities = [Lyric::class, Favorite::class], version = 2, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun lyricDao(): LyricDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: LyricsDatabase? = null

        fun getDatabase(context: Context): LyricsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LyricsDatabase::class.java,
                    "lyrics_database"
                ).fallbackToDestructiveMigration() // 스키마 변경 시 기존 데이터베이스를 파괴하고 새로 만듦
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
