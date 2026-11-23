package com.swara.play

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swara.play.ui.navigation.SwaraPlayNavHost
import com.swara.play.ui.theme.SwaraPlayTheme
import com.swara.play.viewmodel.SwaraPlayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0A0A0C")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0A0A0C"))
        )
        setContent {
            SwaraPlayTheme {
                val vm: SwaraPlayViewModel = viewModel()
                SwaraPlayNavHost(viewModel = vm)
            }
        }
    }
}
