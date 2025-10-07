package com.example.pocketlibrary

import com.google.firebase.firestore.FirebaseFirestoreSettings


import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    init {
        // Enable offline persistence
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
    }

    suspend fun uploadBook(userId: String, book: Book) {
        val docRef = db.collection("users")
            .document(userId)
            .collection("favourites")
            .document(book.id.toString()) // Use local Room ID
        docRef.set(book).await()
    }

    suspend fun fetchFavourites(userId: String): List<Book> {
        val snapshot = db.collection("users")
            .document(userId)
            .collection("favourites")
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toObject(Book::class.java) }
    }

    suspend fun deleteBook(userId: String, bookId: Int) {
        db.collection("users")
            .document(userId)
            .collection("favourites")
            .document(bookId.toString())
            .delete()
            .await()
    }
}
