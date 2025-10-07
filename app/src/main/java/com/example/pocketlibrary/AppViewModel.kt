package com.example.pocketlibrary

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(val context: Context) : ViewModel() {

    private val repository = BookRepository(context)
    private val firestore = FirestoreRepository()

    private val _searchResults = MutableStateFlow<List<Book>>(emptyList())
    val searchResults: StateFlow<List<Book>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _myLibrary = MutableStateFlow<List<Book>>(emptyList())
    val myLibrary: StateFlow<List<Book>> = _myLibrary

    private val userId = "default_user"

    init {
        viewModelScope.launch {
            val remoteBooks = try { firestore.fetchFavourites(userId) } catch (e: Exception) { emptyList() }
            val localBooks = repository.getAllLocalBooks()
            _myLibrary.value = mergeLocalAndRemote(remoteBooks, localBooks)
        }
    }


    /**
     * Fetch Firestore favourites and merge with local DB without creating duplicates.
     */
    private suspend fun syncLibrary() {
        val localBooks = repository.getAllLocalBooks()
        val remoteBooks = try { firestore.fetchFavourites(userId) } catch (e: Exception) { emptyList() }

        // Build a set of existing local IDs to prevent duplicates
        val existingIds = localBooks.map { it.id }.toSet()

        // Only insert remote books if their ID is not in local DB
        val newBooks = remoteBooks.filter { it.id !in existingIds }

        newBooks.forEach { repository.insertBook(it) }

        // Update UI state with merged list
        _myLibrary.value = localBooks + newBooks
    }
    private suspend fun mergeLocalAndRemote(remote: List<Book>, local: List<Book>): List<Book> {
        val localIds = local.map { it.id }.toSet()
        val remoteToInsert = remote.filter { it.id !in localIds }

        // Only insert if book is not already in local DB
        remoteToInsert.forEach { repository.insertBook(it) }

        return (local + remoteToInsert).distinctBy { it.id }
    }


    // --- Online search ---
    fun searchOnline(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val results = repository.searchOnline(query)
                if (results.isEmpty()) _error.value = "No results found."
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = "Failed to fetch online results."
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Local library ---
    fun loadMyLibrary() {
        viewModelScope.launch {
            _myLibrary.value = repository.getAllLocalBooks()
        }
    }

    fun searchLocal(query: String) {
        viewModelScope.launch {
            _myLibrary.value = if (query.isBlank()) repository.getAllLocalBooks()
            else repository.searchLocal(query)
        }
    }

    fun addToLibrary(book: Book) {
        viewModelScope.launch {
            // Generate a consistent ID if missing
            val stableId = if (book.id != 0) book.id else generateStableId(book)
            val newBook = book.copy(id = stableId)

            repository.insertBook(newBook)
            _myLibrary.value = repository.getAllLocalBooks()

            try { firestore.uploadBook(userId, newBook) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book)
            _myLibrary.value = repository.getAllLocalBooks()
            try { firestore.uploadBook(userId, book) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
            try {
                firestore.deleteBook(userId, book.id) // delete from Firestore
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _myLibrary.value = repository.getAllLocalBooks()
        }
    }


    // --- Generate stable ID ---
    private fun generateStableId(book: Book): Int {
        return (book.title + book.author + (book.year ?: 0)).hashCode()
    }

    // --- Image saving ---
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val filename = "book_${System.currentTimeMillis()}.jpg"
            val file = java.io.File(context.filesDir, filename)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}


class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(context) as T
    }
}
