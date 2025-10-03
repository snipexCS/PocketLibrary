package com.example.pocketlibrary

import androidx.room.*


@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book)

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Book>

    @Query("SELECT * FROM books")
    suspend fun getAllBooks(): List<Book>
}
