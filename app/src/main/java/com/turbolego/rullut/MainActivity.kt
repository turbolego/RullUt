package com.turbolego.rullut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.turbolego.rullut.ui.MapScreen
import com.turbolego.rullut.ui.RullUtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RullUtTheme {
                MapScreen()
            }
        }
    }
}