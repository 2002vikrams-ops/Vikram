package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.DataRepository
import com.example.ui.SocialViewModel
import com.example.ui.SocialViewModelFactory
import com.example.ui.screens.MainSocialApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SQLite Offline Room flow
        val database = AppDatabase.getDatabase(this)
        val repository = DataRepository(database.socialDao())
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Instantiate our social dashboard state machine
                val viewModel: SocialViewModel = viewModel(
                    factory = SocialViewModelFactory(repository)
                )
                MainSocialApp(viewModel = viewModel)
            }
        }
    }
}
