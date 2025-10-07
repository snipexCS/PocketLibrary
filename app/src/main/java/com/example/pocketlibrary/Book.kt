package com.example.pocketlibrary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "title")
    val title: String = "",
    @ColumnInfo(name = "author")
    val author:String = "",
    @ColumnInfo(name = "year")
    val year: Int?= null,
    val coverUrl: String?= null,
    val personalPhotoPath: String? = null


)
