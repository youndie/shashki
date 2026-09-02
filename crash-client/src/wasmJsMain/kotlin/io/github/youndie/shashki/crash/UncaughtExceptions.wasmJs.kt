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
 * **Run, since B-34.** This was compiled and never executed — a `@JsFun` is a string the compiler
 * cannot check, so a typo in `event.reason` would have shipped as a crash reporter that reports
 * nothing. `BrowserCrashHookTest` now fires both events in a real Chrome and requires the ingest
 * request to come out the other side.
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
