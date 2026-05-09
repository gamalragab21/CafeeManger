package net.marllex.waselak.manager

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import net.marllex.waselak.manager.di.managerIosKoinModules
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(managerIosKoinModules())
    }
}

// `OnFocusBehavior.DoNothing` disables CMP's default
// `FocusableAboveKeyboard` mode, which translates the entire
// ComposeUIViewController upward when a TextField gains focus.
// Without this, our Compose layout's `Modifier.imePadding()` +
// `verticalScroll(...)` ALSO reacts to the keyboard, double-shifting
// the content and forcing the focused field to the top of the
// screen with the rest of the UI cropped off the top edge.
// Letting Compose's IME insets do the work alone keeps the layout
// natural — keyboard pushes the field up only as far as needed,
// hero/logo stay visible.
fun MainViewController() = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) { ManagerApp() }
