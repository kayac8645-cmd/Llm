package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.AuraViewModel
import com.example.ui.ChatScreen
import com.example.ui.ToolsHubScreen
import com.example.ui.theme.LlmWorldTheme
import com.example.ui.theme.OledBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LlmWorldTheme {
                val viewModel: AuraViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

                // Intercept back button when in Chat screen to return to Tools Hub
                BackHandler(enabled = currentScreen == AppScreen.CHAT) {
                    viewModel.navigateToHub()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OledBackground
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        label = "ScreenTransition",
                        transitionSpec = {
                            if (targetState == AppScreen.CHAT) {
                                (slideInHorizontally { width -> width } + fadeIn())
                                    .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                            }
                        }
                    ) { screen ->
                        when (screen) {
                            AppScreen.HUB -> {
                                ToolsHubScreen(viewModel = viewModel)
                            }
                            AppScreen.CHAT -> {
                                ChatScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}
