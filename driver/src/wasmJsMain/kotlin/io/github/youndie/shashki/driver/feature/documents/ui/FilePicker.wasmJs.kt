@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.shashki.driver.feature.documents.ui

import kotlinx.browser.document
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * The browser's own file dialog: an `<input type="file">` clicked from code.
 *
 * **A page cannot open a file dialog on its own** — it has to be a real click on a real input, which
 * is why the element is created, clicked and thrown away rather than kept somewhere tidy. The bytes
 * come back through `FileReader` as an `ArrayBuffer`; `Int8Array` is the one view that maps onto
 * Kotlin's `ByteArray` without a second copy per element.
 */
public actual fun pickDocument(onPicked: (bytes: ByteArray, contentType: String) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    // The browser's own filter, so a person is not offered their music library. It is a hint and not
    // a guarantee: a file's real type is decided by whoever reads it.
    input.accept = "image/*,application/pdf"
    input.onchange = { _ ->
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            // The handlers return nothing: in Kotlin/Wasm's DOM these are `(Event) -> Unit`, and a
            // trailing `null` — which the JS-target idiom wants — is an unused expression here.
            reader.onload = { _ ->
                val buffer = reader.result as? org.khronos.webgl.ArrayBuffer
                buffer?.let { arrayBuffer ->
                    val view = Int8Array(arrayBuffer)
                    onPicked(ByteArray(view.length) { view[it] }, file.type.ifBlank { "application/octet-stream" })
                }
            }
            reader.readAsArrayBuffer(file)
        }
    }
    input.click()
}
