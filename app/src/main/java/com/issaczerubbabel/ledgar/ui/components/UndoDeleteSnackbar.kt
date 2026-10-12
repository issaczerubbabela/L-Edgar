package com.issaczerubbabel.ledgar.ui.components

import android.app.Activity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.issaczerubbabel.ledgar.data.repository.UndoableDelete

/**
 * Shows the Undo snackbar for [pending] on [snackbarHostState], and makes the delete final when the
 * screen is left or the app goes to the background. Rotating the phone isn't leaving: the delete
 * stays open to Undo.
 */
@Composable
fun UndoDeleteSnackbar(
    pending: UndoableDelete?,
    snackbarHostState: SnackbarHostState,
    onUndo: (UndoableDelete) -> Unit,
    onCommit: () -> Unit
) {
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnCommit by rememberUpdatedState(onCommit)

    // One snackbar per pending delete. A newer delete, or the delete becoming final, restarts this
    // effect, which cancels the snackbar showing; Undo brings the row back.
    LaunchedEffect(pending) {
        val delete = pending ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(delete.message, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) currentOnUndo(delete)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) currentOnCommit()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) currentOnCommit()
        }
    }
}
