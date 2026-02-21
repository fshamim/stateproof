package io.stateproof.sample

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        val viewModel = remember { createTaskAppViewModel() }

        DisposableEffect(Unit) {
            onDispose {
                viewModel.close()
            }
        }

        App(viewModel)
    }
}
