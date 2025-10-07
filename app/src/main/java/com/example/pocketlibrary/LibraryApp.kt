package com.example.pocketlibrary

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage



@Composable
fun LibraryApp(viewModel: AppViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var showLocal by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsState()
    val myLibrary by viewModel.myLibrary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(6.dp).padding(top = 30.dp)) {

        // Top buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Row {
                Button(onClick = {
                    showLocal = false
                    viewModel.searchOnline(searchQuery)
                }) { Text("Search Online") }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    showLocal = true
                    viewModel.loadMyLibrary()
                }) { Text("My Library") }
            }

            Button(onClick = { showManualEntry = true }) { Text("Add Book") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (showLocal) viewModel.searchLocal(searchQuery)
            },
            label = { Text("Search by title or author") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    if (showLocal) viewModel.searchLocal(searchQuery)
                    else viewModel.searchOnline(searchQuery)
                }
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Manual entry dialog
        if (showManualEntry) {
            ManualEntryDialog(viewModel = viewModel) {
                showManualEntry = false
            }
        }

        // Loading / Error / Book list
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
            error != null -> Text(text = error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
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
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            capturedPhoto = it
            photoUri = viewModel.saveBitmapToInternalStorage(viewModel.context, it)
        }
    }

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

                Spacer(modifier = Modifier.height(8.dp))

                capturedPhoto?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }

                Button(onClick = { cameraLauncher.launch() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Capture Photo")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val yearInt = year.toIntOrNull()
                val book = Book(
                    title = title,
                    author = author,
                    year = yearInt,
                    coverUrl = null,
                    personalPhotoPath = photoUri?.path
                )
                viewModel.addToLibrary(book) // ✅ This now syncs to Firestore automatically
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
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {

            // Image
            if (book.personalPhotoPath != null) {
                val bitmap = BitmapFactory.decodeFile(book.personalPhotoPath)
                bitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "Book Photo",
                        modifier = Modifier.size(80.dp).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)))
                }
            } else if (book.coverUrl != null) {
                AsyncImage(model = book.coverUrl, contentDescription = "Book Cover",
                    modifier = Modifier.size(80.dp).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)))
            } else {
                Box(modifier = Modifier.size(80.dp).border(1.dp, Color.Gray, RoundedCornerShape(4.dp)))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(text = "by ${book.author}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(text = "Published: ${book.year ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
            }

            // Buttons
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showAddButton) {
                    Button(onClick = { viewModel.addToLibrary(book) }) { Text("Add") }
                } else {
                    Button(onClick = { showEditDialog = true }) { Text("Edit") }
                    Button(onClick = { viewModel.deleteBook(book) }) { Text("Delete") }
                }
            }
        }
    }

    if (showEditDialog) {
        EditBookDialog(book = book, viewModel = viewModel) { showEditDialog = false }
    }
}


@Composable
fun EditBookDialog(book: Book, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var year by remember { mutableStateOf(book.year?.toString() ?: "") }
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var photoUri by remember { mutableStateOf(book.personalPhotoPath?.let { Uri.parse(it) }) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            capturedPhoto = it
            photoUri = viewModel.saveBitmapToInternalStorage(viewModel.context, it)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Book") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author") })
                OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year") })

                Spacer(modifier = Modifier.height(8.dp))

                // Photo preview
                capturedPhoto?.let { bitmap ->
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Book Photo", modifier = Modifier.height(150.dp))
                }

                Button(onClick = { cameraLauncher.launch() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Capture Photo")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val yearInt = year.toIntOrNull()
                val updatedBook = book.copy(
                    title = title,
                    author = author,
                    year = yearInt,
                    personalPhotoPath = photoUri?.path
                )
                viewModel.updateBook(updatedBook)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    )
}



/**
 * Safe function to get display name and phone number from a contact Uri.
 */
private fun loadNameAndNumber(
    resolver: android.content.ContentResolver,
    contactUri: Uri
): Pair<String?, String?> {
    var id: String? = null
    var name: String? = null

    val contactsProjection = arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME
    )

    resolver.query(contactUri, contactsProjection, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
        }
    }

    var number: String? = null
    if (id != null) {
        val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phoneProjection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(id),
            null
        )?.use { pc ->
            if (pc.moveToFirst()) {
                number = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
    }

    return name to number
}










