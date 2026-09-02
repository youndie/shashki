package io.github.youndie.shashki.ui.map.tiles

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * The archive over ordinary HTTP, one `Range` at a time.
 *
 * **This is the transport B-07 measured**: 812 ranged reads into one 16.6 MiB object at p50 around
 * 1 ms. What that item also found is that the reads only work from a browser once two switches are
 * on — a public-read bucket policy, because a browser cannot sign a request, and a CORS
 * configuration, because `Range` is not on the safelist and every tile read is therefore
 * preflighted. A deployment with the policy and no CORS rule passes every command-line check and
 * fails in the product.
 *
 * **206 is required rather than hoped for.** A server that ignores `Range` answers 200 with the
 * whole 17 MB, and a reader that took the first bytes of that would parse a header, walk a
 * directory, and hand back geometry from the wrong offsets — a city that is subtly not the one
 * asked for. So anything but 206 is a failure with the status in the message.
 */
public class HttpRangeReader(
    private val client: HttpClient,
    private val url: String,
) : RangeReader {
    override suspend fun read(
        offset: Long,
        length: Int,
    ): ByteArray {
        val last = offset + length - 1
        val response =
            client.get(url) {
                header(HttpHeaders.Range, "bytes=$offset-$last")
            }
        check(response.status == HttpStatusCode.PartialContent) {
            "expected 206 for bytes=$offset-$last from $url, got ${response.status}"
        }
        val bytes = response.readRawBytes()
        check(bytes.size == length) { "asked for $length bytes at $offset and got ${bytes.size}" }
        return bytes
    }
}
