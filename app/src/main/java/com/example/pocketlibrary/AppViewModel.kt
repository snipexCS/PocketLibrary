package com.example.pocketlibrary

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(val context: Context) : ViewModel() {

    private val repository = BookRepository(context)
    private val firestore = FirestoreRepository()

    private val _searchResults = MutableStateFlow<List<Book>>(emptyList())
    val searchResults: StateFlow<List<Book>> = _searchResults

    private val _myLibrary = MutableStateFlow<List<Book>>(emptyList())
    val myLibrary: StateFlow<List<Book>> = _myLibrary

    private val userId = "default_user"

    init {
        viewModelScope.launch {
            try {
                val remoteBooks = firestore.fetchFavourites(userId)
                val localBooks = repository.getAllLocalBooks()
                _myLibrary.value = mergeLocalAndRemote(remoteBooks, localBooks)
            } catch (e: Exception) {
                e.printStackTrace()
                _myLibrary.value = repository.getAllLocalBooks()
            }
        }
    }

    private suspend fun mergeLocalAndRemote(remote: List<Book>, local: List<Book>): List<Book> {
        val remoteIds = remote.map { it.id }.toSet()
        local.filter { it.id !in remoteIds }.forEach { repository.deleteBook(it) }

        val localIds = local.map { it.id }.toSet()
        remote.filter { it.id !in localIds }.forEach { repository.insertBook(it) }

        return (local.filter { it.id in remoteIds } + remote.filter { it.id !in localIds }).distinctBy { it.id }
    }

    fun searchOnline(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                _searchResults.value = repository.searchOnline(query)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadMyLibrary() {
        viewModelScope.launch {
            _myLibrary.value = repository.getAllLocalBooks()
        }
    }

    fun searchLocal(query: String, filter: String = "title", sort: String? = null) {
        viewModelScope.launch {
            val all = repository.getAllLocalBooks()
            val filtered = if (query.isBlank()) {
                all
            } else {
                when (filter.lowercase()) {
                    "title" -> all.filter { it.title.contains(query, ignoreCase = true) }
                    "author" -> all.filter { it.author.contains(query, ignoreCase = true) }
                    else -> all
                }
            }

            val sorted = when (sort?.lowercase()) {
                "title_asc" -> filtered.sortedBy { it.title.lowercase() }
                "author_asc" -> filtered.sortedBy { it.author.lowercase() }
                "year_asc" -> filtered.sortedWith(compareBy(nullsLast()) { it.year ?: Int.MIN_VALUE })
                else -> filtered
            }

            _myLibrary.value = sorted
        }
    }

    fun addToLibrary(book: Book, filter: String = "title", sort: String? = null) {
        viewModelScope.launch {
            val stableId = if (book.id != 0) book.id else generateStableId(book)
            val newBook = book.copy(id = stableId)

            repository.insertBook(newBook)
            searchLocal("", filter, sort)

            try { firestore.uploadBook(userId, newBook) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateBook(book: Book, filter: String = "title", sort: String? = null) {
        viewModelScope.launch {
            repository.updateBook(book)
            searchLocal("", filter, sort)
            try { firestore.uploadBook(userId, book) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteBook(book: Book, filter: String = "title", sort: String? = null) {
        viewModelScope.launch {
            repository.deleteBook(book)
            try { firestore.deleteBook(userId, book.id) } catch (e: Exception) { e.printStackTrace() }
            searchLocal("", filter, sort)
        }
    }

    private fun generateStableId(book: Book): Int =
        (book.title + book.author + (book.year ?: 0)).hashCode()

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val filename = "book_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(context.filesDir, filename)
            java.io.FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

