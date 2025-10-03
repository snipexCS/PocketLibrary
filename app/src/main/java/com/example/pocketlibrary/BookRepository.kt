package com.example.pocketlibrary

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository(context: Context) {

    private val bookDao = BookDatabase.getDatabase(context).bookDao()
    private val api = OpenLibraryRetrofit.api

    // --- Search online books ---
    suspend fun searchOnline(query: String): List<Book> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchBooks(query)
            println("OpenLibrary API docs size: ${response.docs.size}")
            response.docs.forEach { println("BookDoc: $it") }
            response.docs.map { it.toBook() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // --- Local DB CRUD ---
    suspend fun insertBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.insert(book)
    }

    suspend fun updateBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.update(book)
    }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.delete(book)
    }

    suspend fun searchLocal(query: String): List<Book> = withContext(Dispatchers.IO) {
        bookDao.search(query)
    }

    suspend fun getAllLocalBooks(): List<Book> = withContext(Dispatchers.IO) {
        bookDao.getAllBooks()
    }
}
