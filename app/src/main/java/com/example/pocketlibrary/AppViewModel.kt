package com.example.pocketlibrary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(private val context: Context) : ViewModel() {

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

    fun searchLocal(query: String) {
        viewModelScope.launch {
            _myLibrary.value = repository.searchLocal(query)
        }
    }
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(context) as T
    }
}
