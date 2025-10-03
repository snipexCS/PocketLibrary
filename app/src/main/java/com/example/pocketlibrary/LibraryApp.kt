package com.example.pocketlibrary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun LibraryApp(viewModel: AppViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var showLocal by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) } // State for manual entry dialog

    val searchResults by viewModel.searchResults.collectAsState()
    val myLibrary by viewModel.myLibrary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {

        // Top buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                showLocal = false
                viewModel.searchOnline(searchQuery)
            }) { Text("Search Online") }

            Button(onClick = {
                showLocal = true
                viewModel.loadMyLibrary()
            }) { Text("My Library") }

            Button(onClick = { showManualEntry = true }) { Text("Add Book Manually") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by title or author") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (showLocal) viewModel.searchLocal(searchQuery)
                    else viewModel.searchOnline(searchQuery)
                }
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Show manual entry dialog
        if (showManualEntry) {
            ManualEntryDialog(viewModel = viewModel) {
                showManualEntry = false
            }
        }

        // Loading / Error / List
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.align(alignment = androidx.compose.ui.Alignment.CenterHorizontally))
            error != null -> Text(text = error!!, color = MaterialTheme.colorScheme.error)
            showLocal -> BookList(books = myLibrary, viewModel = viewModel, showAddButton = false)
            else -> BookList(books = searchResults, viewModel = viewModel, showAddButton = true)
        }
    }
}

@Composable
fun BookList(books: List<Book>, viewModel: AppViewModel, showAddButton: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(books, key = { it.id.takeIf { it != 0 } ?: "${it.title}-${it.author}-${it.year}".hashCode() }) { book ->
            BookItem(book, viewModel, showAddButton)
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}
@Composable
fun ManualEntryDialog(viewModel: AppViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Book Manually") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val yearInt = year.toIntOrNull()
                val book = Book(title = title, author = author, year = yearInt, coverUrl = null)
                viewModel.addToLibrary(book)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun BookItem(book: Book, viewModel: AppViewModel, showAddButton: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = "Book Cover",
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = book.title, style = MaterialTheme.typography.bodyLarge)
                Text(text = "by ${book.author}", style = MaterialTheme.typography.bodyMedium)
                Text(text = book.year?.toString() ?: "Unknown Year", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (showAddButton) {
            Button(onClick = { viewModel.addToLibrary(book) }) { Text("Add") }
        }
    }
}
