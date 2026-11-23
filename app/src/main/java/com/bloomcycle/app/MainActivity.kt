package com.bloomcycle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bloomcycle.app.data.BloomRepository
import com.bloomcycle.app.ui.MainAppScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = BloomRepository(this)
        setContent {
            MainAppScreen(repository = repository)
        }
    }
}
