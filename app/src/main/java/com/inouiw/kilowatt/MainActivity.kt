package com.inouiw.kilowatt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.inouiw.kilowatt.ui.KiloWattApp
import com.inouiw.kilowatt.ui.theme.KiloWattTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KiloWattTheme {
                KiloWattApp()
            }
        }
    }
}
