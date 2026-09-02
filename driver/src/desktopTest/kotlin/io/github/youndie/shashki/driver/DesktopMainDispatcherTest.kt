package io.github.youndie.shashki.driver

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * **The window's Main dispatcher, checked as a file on the classpath.**
 *
 * `viewModelScope` is `Dispatchers.Main.immediate`, and on the JVM that dispatcher arrives through a
 * service loader rather than from Compose: without `kotlinx-coroutines-swing`, the first
 * `viewModel { }` the application resolves throws inside its own `init`, and what the user sees is
 * `Could not create instance for ShiftViewModel` — a message about Koin for a missing
 * dependency. That is how the desktop driver failed the first time anybody ran it against the stand.
 *
 * **`DriverGraphTest` cannot catch this and it was measured, not assumed:** removing the artefact and
 * running that test again leaves it green, because `kotlinx-coroutines-test` installs its own Main
 * dispatcher factory in every test JVM. The environment a test runs in differs from the application's
 * in exactly the thing being checked, so the check is on the artefact instead.
 */
class DesktopMainDispatcherTest {
    @Test
    fun `the swing dispatcher is on the desktop runtime classpath`() {
        assertNotNull(
            Class.forName("kotlinx.coroutines.swing.SwingDispatcherFactory"),
            "no Main dispatcher in a desktop window: every view model dies in its init",
        )
    }
}
