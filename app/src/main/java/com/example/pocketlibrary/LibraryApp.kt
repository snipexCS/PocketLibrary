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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction


@Composable
fun LibraryApp(viewModel: AppViewModel) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 600
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }

    val searchResults by viewModel.searchResults.collectAsState(initial = emptyList())
    val myLibrary by viewModel.myLibrary.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // 🔹 Observe filter and sort states from ViewModel
    val currentFilter by viewModel.currentFilter.collectAsState()
    val currentSort by viewModel.currentSort.collectAsState()

    val booksToShow = if (showLocal) myLibrary else searchResults

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)) {

        // 🔹 Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                if (showLocal) {
                    // 🔹 Call local search immediately on text change
                    viewModel.searchLocal(
                        query,
                        currentFilter,
                        currentSort
                    )
                } else {
                    // 🔹 Optional: live online search (may want debounce to avoid too many requests)
                    viewModel.searchOnline(query)
                }
            },
            label = { Text("Search by title or author") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (!showLocal) viewModel.searchOnline(searchQuery)
                }
            )
        )


        Spacer(modifier = Modifier.height(8.dp))

        // 🔹 Top Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                showLocal = false
                viewModel.searchOnline(searchQuery)
            }) { Text("Search Online") }

            Button(onClick = {
                showLocal = true
                viewModel.loadMyLibrary()
            }) { Text("My Library") }

            Button(onClick = { showManualEntry = true }) { Text("Add Book") }
        }

        // 🔹 Filter + Sort Controls (only for local view)
        if (showLocal) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DropdownSelector(
                    label = "Filter by",
                    options = listOf("Title", "Author"),
                    selected = currentFilter.name.lowercase().replaceFirstChar { it.uppercase() }
                ) { selected ->
                    val filter = if (selected == "Author") FilterType.AUTHOR else FilterType.TITLE
                    viewModel.searchLocal(searchQuery, filter, currentSort)
                }

                DropdownSelector(
                    label = "Sort by",
                    options = listOf(
                        "None", "Title (A–Z)", "Title (Z–A)",
                        "Author (A–Z)", "Author (Z–A)",
                        "Year (Oldest)", "Year (Newest)"
                    ),
                    selected = when (currentSort) {
                        SortOption.TITLE_ASC -> "Title (A–Z)"
                        SortOption.TITLE_DESC -> "Title (Z–A)"
                        SortOption.AUTHOR_ASC -> "Author (A–Z)"
                        SortOption.AUTHOR_DESC -> "Author (Z–A)"
                        SortOption.YEAR_ASC -> "Year (Oldest)"
                        SortOption.YEAR_DESC -> "Year (Newest)"
                        SortOption.NONE -> "None"
                    }
                ) { selected ->
                    val sort = when (selected) {
                        "Title (A–Z)" -> SortOption.TITLE_ASC
                        "Title (Z–A)" -> SortOption.TITLE_DESC
                        "Author (A–Z)" -> SortOption.AUTHOR_ASC
                        "Author (Z–A)" -> SortOption.AUTHOR_DESC
                        "Year (Oldest)" -> SortOption.YEAR_ASC
                        "Year (Newest)" -> SortOption.YEAR_DESC
                        else -> SortOption.NONE
                    }
                    viewModel.searchLocal(searchQuery, currentFilter, sort)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 🔹 Manual Entry Dialog
        if (showManualEntry) {
            ManualEntryDialog(viewModel) { showManualEntry = false }
        }

        // 🔹 Loading / Error / Book List
        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
            }
            booksToShow.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (showLocal) "Your library is empty" else "No books found")
            }
            else -> {
                val isGrid = isTablet || isLandscape
                BookList(
                    books = booksToShow,
                    viewModel = viewModel,
                    showAddButton = !showLocal,
                    modifier = Modifier.fillMaxSize(),
                    isGrid = isGrid
                )
            }
        }
    }
}
@Composable
fun DropdownSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("$label: $selected")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}




@Composable
fun BookList(
    books: List<Book>,
    viewModel: AppViewModel,
    showAddButton: Boolean,
    modifier: Modifier = Modifier,
    isGrid: Boolean = false
) {
    if (isGrid) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp

        // Determine number of columns: Tablet 2+, Phone landscape 1
        val columns = if (screenWidthDp >= 600) 2 else 1
        val spacing = if (columns > 1) 12.dp else 4.dp

        val gridState = rememberLazyGridState()

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            items(books, key = { it.id.takeIf { it != 0 } ?: "${it.title}-${it.author}-${it.year}".hashCode() }) { book ->
                BookItem(book, viewModel, showAddButton)
            }
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(books, key = { it.id.takeIf { it != 0 } ?: "${it.title}-${it.author}-${it.year}".hashCode() }) { book ->
                BookItem(book, viewModel, showAddButton)
            }
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

                Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.padding(top = 8.dp)) {
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
                viewModel.addToLibrary(book)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
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
            val bitmap = remember(book.personalPhotoPath, book.coverUrl) {
                book.personalPhotoPath?.let { BitmapFactory.decodeFile(it) }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Book Photo",
                    modifier = Modifier.size(80.dp).border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                )
            } else if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = "Book Cover",
                    modifier = Modifier.size(80.dp).border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                        .background(Color(0xFF90A4AE), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.title.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
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
                    ShareBookButton(book)
                } else {
                    Button(onClick = { showEditDialog = true }) { Text("Edit") }
                    Button(onClick = { viewModel.deleteBook(book) }) { Text("Delete") }
                    ShareBookButton(book)
                }
            }
        }
    }

    if (showEditDialog) {
        EditBookDialog(book = book, viewModel = viewModel) { showEditDialog = false }
    }

    @Composable
    fun DropdownSelector(
        label: String,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text("$label: $selected")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShareBookButton(book: Book) {
    val context = LocalContext.current

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            val (_, number) = loadNameAndNumber(context.contentResolver, contactUri)
            number?.let { phone ->
                val smsIntent = Intent(Intent.ACTION_SENDTO)
                smsIntent.data = Uri.parse("smsto:$phone")
                smsIntent.putExtra(
                    "sms_body",
                    "Check out this book!\nTitle: ${book.title}\nAuthor: ${book.author}\nPublished: ${book.year ?: "Unknown"}"
                )

                // Include image if available
                book.personalPhotoPath?.let {
                    val imageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(it)
                    )
                    smsIntent.type = "image/*"
                    smsIntent.putExtra(Intent.EXTRA_STREAM, imageUri)
                    smsIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(smsIntent, "Share via"))
            } ?: Toast.makeText(context, "No phone number found", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No contact selected", Toast.LENGTH_SHORT).show()
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickContactLauncher.launch(null)
        else Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
    }

    Button(onClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) pickContactLauncher.launch(null)
        else requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }) {
        Text("Share")
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

                capturedPhoto?.let { bitmap ->
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Book Photo", modifier = Modifier.height(150.dp))
                }

                Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.padding(top = 8.dp)) {
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
