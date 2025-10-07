package com.example.pocketlibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pocketlibrary.ui.theme.PocketLibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase first
        com.google.firebase.FirebaseApp.initializeApp(this)

        setContent {
            PocketLibraryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val appViewModel: AppViewModel = viewModel(
                        factory = AppViewModelFactory(applicationContext)
                    )

                    LibraryApp(viewModel = appViewModel)
                }
            }
        }
    }

}
