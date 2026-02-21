package io.stateproof.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    val viewModel = createTaskAppViewModel()

    application {
        Window(
            onCloseRequest = {
                viewModel.close()
                this.exitApplication()
            },
            title = "TaskProof",
        ) {
            App(viewModel)
        }
    }
}
