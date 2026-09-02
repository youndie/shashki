@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.crash

/**
 * A band across the top of the page when something escaped (B-56).
 *
 * **The screen owes a person a sentence, and the reporter owes an operator one** — they are two
 * jobs and this is the first. Twice on a running stand a single unhandled failure took a Compose
 * bundle down to a blank rectangle: a 404 on the tile archive painted the rider black before any
 * panel appeared, and a refused token exchange painted the driver white. `installCrashReporting`
 * sent both to katcher; the person looking at the page got nothing at all.
 *
 * **In the DOM and not in Compose, because by then Compose is what died.** A composition that has
 * thrown cannot draw its own apology — the canvas is frozen or gone — so this is plain markup over
 * it, installed before the application starts and independent of it.
 *
 * **A band and not a page**, which is the kit's own rule for a message that arrives while something
 * else is on screen (R7·a: "message box is a full-width band, never a floating card"). If the
 * application survived, the band sits over a working screen and says what failed; if it did not, the
 * band is the only thing there is, which is still more than a black rectangle.
 *
 * Installed *beside* the reporter's handlers rather than inside them: neither can stop the other,
 * and the order they fire in does not matter.
 */
public fun installFatalBand() {
    installFatalBandJs()
}

@JsFun(
    """() => {
        const show = (text) => {
            let band = document.getElementById('shashki-fatal');
            if (!band) {
                band = document.createElement('div');
                band.id = 'shashki-fatal';
                band.style.cssText = [
                    'position:fixed', 'top:0', 'left:0', 'right:0', 'z-index:2147483647',
                    'background:#1F1F1F', 'color:#FFFFFF', 'padding:12px 16px',
                    "font-family:'Source Sans 3','Segoe UI',sans-serif", 'font-size:15px',
                    'line-height:1.35', 'font-weight:400'
                ].join(';');
                document.body.appendChild(band);
            }
            band.innerHTML = '';
            const head = document.createElement('div');
            head.style.cssText = 'color:#E51400;font-size:24px;font-weight:300;margin-bottom:4px';
            head.textContent = 'something broke';
            const line = document.createElement('div');
            line.style.cssText = 'color:#99FFFFFF';
            line.textContent = text;
            const again = document.createElement('a');
            again.href = location.href;
            again.textContent = 'try again';
            again.style.cssText = 'color:#1BA1E2;text-decoration:none;display:inline-block;margin-top:8px';
            band.appendChild(head);
            band.appendChild(line);
            band.appendChild(again);
        };
        window.addEventListener('error', (event) => {
            show(String((event.error && event.error.message) || event.message || 'an error with no message'));
        });
        window.addEventListener('unhandledrejection', (event) => {
            const reason = event.reason;
            show(String((reason && reason.message) || reason || 'a failure with no message'));
        });
    }""",
)
private external fun installFatalBandJs()
