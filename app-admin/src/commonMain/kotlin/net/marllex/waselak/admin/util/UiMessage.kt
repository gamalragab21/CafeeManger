package net.marllex.waselak.admin.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Represents a user-facing message that can be either:
 * - A localizable string resource (with optional format args)
 * - A plain text string (for dynamic server errors)
 *
 * ViewModels emit [UiMessage] instances; screens resolve them via [resolve].
 */
sealed class UiMessage {
    /** Whether this message indicates a successful operation. */
    abstract val isSuccess: Boolean

    data class Resource(
        val resId: StringResource,
        val args: List<Any> = emptyList(),
        override val isSuccess: Boolean = false,
    ) : UiMessage()

    data class Text(
        val text: String,
        override val isSuccess: Boolean = false,
    ) : UiMessage()
}

/** Resolve a [UiMessage] to a displayable string inside a @Composable scope. */
@Composable
fun UiMessage.resolve(): String = when (this) {
    is UiMessage.Resource -> if (args.isEmpty()) {
        stringResource(resId)
    } else {
        stringResource(resId, *args.toTypedArray())
    }
    is UiMessage.Text -> text
}
