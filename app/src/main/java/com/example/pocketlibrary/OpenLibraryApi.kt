

package com.example.pocketlibrary

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


interface OpenLibraryApi {
    @GET("search.json")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i",
        @Query("limit") limit: Int = 20
    ): OpenLibraryResponse
}


data class OpenLibraryResponse(
    val docs: List<BookDoc>
)

data class BookDoc(
    val title: String?,
    @Json(name = "author_name") val author_name: List<String>?,
    @Json(name = "first_publish_year") val first_publish_year: Int?,
    @Json(name = "cover_i") val cover_i: Int?
)


fun BookDoc.toBook(): Book {
    return Book(
        title = this.title ?: "Unknown",
        author = this.author_name?.joinToString(", ") ?: "Unknown",
        year = this.first_publish_year,
        coverUrl = this.cover_i?.let { "https://covers.openlibrary.org/b/id/$it-S.jpg" }
    )
}


object OpenLibraryRetrofit {
    private const val BASE_URL = "https://openlibrary.org/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: OpenLibraryApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenLibraryApi::class.java)
    }
}
