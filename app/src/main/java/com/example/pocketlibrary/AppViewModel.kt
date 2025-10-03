package com.example.pocketlibrary
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class AppViewModel(val context: Context) : ViewModel() {

    private val repository = BookRepository(context)

    private val _searchResults = MutableStateFlow<List<Book>>(emptyList())
    val searchResults: StateFlow<List<Book>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Local library
    private val _myLibrary = MutableStateFlow<List<Book>>(emptyList())
    val myLibrary: StateFlow<List<Book>> = _myLibrary




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

    fun loadMyLibrary() {
        viewModelScope.launch {
            _myLibrary.value = repository.getAllLocalBooks()
        }
    }

    fun addToLibrary(book: Book) {
        viewModelScope.launch {
            repository.insertBook(book)
            loadMyLibrary()
        }
    }


    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val filename = "book_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
            loadMyLibrary() // refresh the list
        }
    }
    fun updateBook(book: Book) {
        viewModelScope.launch {
            repository.updateBook(book)
            loadMyLibrary() // refresh
        }
    }


    fun searchLocal(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                // If search query is empty, load all books
                _myLibrary.value = repository.getAllLocalBooks()
            } else {
                _myLibrary.value = repository.searchLocal(query)
            }
        }
    }

}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(context) as T
    }
}
