package io.stateproof.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel = remember { createTaskAppViewModel() }

            DisposableEffect(Unit) {
                onDispose {
                    viewModel.close()
                }
            }

            AndroidNavApp(viewModel)
        }
    }
}
