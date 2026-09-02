package io.github.youndie.shashki.driver.feature.shift.data

import io.github.youndie.shashki.driver.feature.shift.domain.ShiftRepository
import io.github.youndie.shashki.protocol.DRIVER_POSITIONS_PATH
import io.github.youndie.shashki.protocol.DriverReport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json

/**
 * The socket the server's `driverPositionRoutes` is on the other end of.
 *
 * **The frame is written here rather than by content negotiation.** The server reads text frames and
 * decodes `DriverReport` itself (it keeps reading after a bad one, which is why it does its own
 * decoding); sending through Ktor's WebSocket serialization would put a second opinion about the
 * frame's shape in the pipeline for no gain.
 *
 * The URL is built from the base the client already carries, with `http` swapped for `ws` — the one
 * place in this bundle where a scheme is edited as a string, because a WebSocket address is not a
 * `@Resource` and Ktor's `webSocket(urlString)` wants the whole thing.
 */
public class WebSocketShiftRepository(
    private val client: HttpClient,
    private val serverUrl: String,
    private val json: Json = Json,
) : ShiftRepository {
    override fun stream(reports: Flow<DriverReport>): Flow<DriverReport> =
        channelFlow {
            client.webSocket(socketUrl()) {
                reports.collect { report ->
                    send(Frame.Text(json.encodeToString(DriverReport.serializer(), report)))
                    // Emitted *after* the frame went out, so a screen that says "online" is saying
                    // something the socket did rather than something the application intended.
                    this@channelFlow.send(report)
                }
            }
        }

    private fun socketUrl(): String = serverUrl.trimEnd('/').replaceFirst(Regex("^http"), "ws") + DRIVER_POSITIONS_PATH
}
