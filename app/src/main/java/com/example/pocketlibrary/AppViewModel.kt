package com.example.pocketlibrary

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that exposes:
 * - searchResults (online)
 * - myLibrary (local + merged remote)
 *
 * It now supports:
 * - searchLocal(query, filter, sort)
 * - sorting and filter state flows for UI to observe (optional)
 */
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

    // Optional: expose current filter/sort state so UI can reflect them
    private val _currentFilter = MutableStateFlow(FilterType.TITLE)
    val currentFilter: StateFlow<FilterType> = _currentFilter

    private val _currentSort = MutableStateFlow(SortOption.NONE)
    val currentSort: StateFlow<SortOption> = _currentSort

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

        val existingIds = localBooks.map { it.id }.toSet()
        val newBooks = remoteBooks.filter { it.id !in existingIds }

        newBooks.forEach { repository.insertBook(it) }

        _myLibrary.value = localBooks + newBooks
    }

    private suspend fun mergeLocalAndRemote(remote: List<Book>, local: List<Book>): List<Book> {
        val localIds = local.map { it.id }.toSet()
        val remoteToInsert = remote.filter { it.id !in localIds }

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

    // --- Local library load ---
    fun loadMyLibrary() {
        viewModelScope.launch {
            _myLibrary.value = repository.getAllLocalBooks()
        }
    }

    /**
     * Search local books with filter and sort.
     * - query: text to match (blank => return all)
     * - filter: FilterType.TITLE or FilterType.AUTHOR
     * - sort: SortOption (NONE means preserve repository order or natural order)
     *
     * This implementation pulls all local books and applies filter+sort in-memory.
     * That guarantees consistent behaviour independent of repository implementation.
     */
    fun searchLocal(query: String, filter: FilterType = FilterType.TITLE, sort: SortOption = SortOption.NONE) {
        viewModelScope.launch {
            // remember chosen filter/sort so UI can display it
            _currentFilter.value = filter
            _currentSort.value = sort

            val all = repository.getAllLocalBooks()
            val filtered = if (query.isBlank()) {
                all
            } else {
                when (filter) {
                    FilterType.AUTHOR -> all.filter { it.author.contains(query, ignoreCase = true) }
                    FilterType.TITLE -> all.filter { it.title.contains(query, ignoreCase = true) }
                }
            }

            val sorted = applySort(filtered, sort)
            _myLibrary.value = sorted
        }
    }

    // helper to apply sorting
    private fun applySort(list: List<Book>, sort: SortOption): List<Book> {
        return when (sort) {
            SortOption.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOption.AUTHOR_ASC -> list.sortedBy { it.author.lowercase() }
            SortOption.AUTHOR_DESC -> list.sortedByDescending { it.author.lowercase() }
            SortOption.YEAR_ASC -> list.sortedWith(compareBy(nullsLast()) { it.year ?: Int.MIN_VALUE })
            SortOption.YEAR_DESC -> list.sortedWith(compareByDescending(nullsLast<Int>()) { it.year ?: Int.MIN_VALUE })
            SortOption.NONE -> list
        }
    }

    // --- CRUD operations that keep Firestore in sync ---
    fun addToLibrary(book: Book) {
        viewModelScope.launch {
            val stableId = if (book.id != 0) book.id else generateStableId(book)
            val newBook = book.copy(id = stableId)

            repository.insertBook(newBook)

            // refresh local state (respect current filter/sort if present)
            val currentFilter = _currentFilter.value
            val currentSort = _currentSort.value
            searchLocal("", currentFilter, currentSort)

            try { firestore.uploadBook(userId, newBook) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun updateBook(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book)
            val currentFilter = _currentFilter.value
            val currentSort = _currentSort.value
            searchLocal("", currentFilter, currentSort)
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
            val currentFilter = _currentFilter.value
            val currentSort = _currentSort.value
            searchLocal("", currentFilter, currentSort)
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

/**
 * Filter types and Sorting options exposed by the ViewModel
 */
enum class FilterType { TITLE, AUTHOR }

enum class SortOption {
    NONE,
    TITLE_ASC, TITLE_DESC,
    AUTHOR_ASC, AUTHOR_DESC,
    YEAR_ASC, YEAR_DESC
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(context) as T
    }
}
