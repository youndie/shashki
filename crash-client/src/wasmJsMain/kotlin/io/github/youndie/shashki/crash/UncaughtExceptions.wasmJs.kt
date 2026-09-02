@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.crash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `window.onerror` and `unhandledrejection`, which are the two ways a browser loses an exception.
 *
 * **Both, because either alone leaves half the failures unreported.** `onerror` catches what a
 * synchronous frame throws; a coroutine that fails after a suspension point surfaces as a rejected
 * promise and reaches `unhandledrejection` instead — which in a Compose/Wasm application is most of
 * them.
 *
 * The handler returns `false` so the browser still logs the error to the console. A reporter that
 * swallowed it would take away the thing a developer looks at first.
 *
 * **Compiled but not run.** There is no browser on the build box, so nothing here executes in
 * `check`; what the build guarantees is that it compiles for `wasmJs`, and B-26's end-to-end sign-in
 * is the first thing that will actually load a page. That limit is stated rather than left implied.
 */
public actual fun installCrashReporting(
    reporter: CrashReporter,
    scope: CoroutineScope,
) {
    installBrowserHandlers { message, stack ->
        scope.launch { reporter.report(message.toString(), stack.toString()) }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun installBrowserHandlers(handler: (JsString, JsString) -> Unit) {
    installBrowserHandlersJs(handler)
}

@JsFun(
    """(handler) => {
        window.onerror = (message, source, line, column, error) => {
            handler(String(message), String((error && error.stack) || (source + ':' + line + ':' + column)));
            return false;
        };
        window.addEventListener('unhandledrejection', (event) => {
            const reason = event.reason;
            handler(
                'unhandled rejection: ' + String((reason && reason.message) || reason),
                String((reason && reason.stack) || '')
            );
        });
    }""",
)
private external fun installBrowserHandlersJs(handler: (JsString, JsString) -> Unit)
