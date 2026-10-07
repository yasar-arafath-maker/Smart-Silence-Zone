package com.smartsilentzone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.smartsilentzone.ui.MainAppScreen
import com.smartsilentzone.ui.theme.SmartSilentZoneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartSilentZoneTheme(darkTheme = true) {
                Surface {
                    MainAppScreen()
                }
            }
        }
    }
}
