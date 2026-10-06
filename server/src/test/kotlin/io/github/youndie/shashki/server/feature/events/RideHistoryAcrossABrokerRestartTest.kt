package io.github.youndie.shashki.server.feature.events

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.github.youndie.booblik.TopicName
import io.github.youndie.booblik.net.client.BooblikConnection
import io.github.youndie.booblik.net.client.Producer
import io.github.youndie.shashki.server.feature.events.data.BooblikRideHistory
import io.github.youndie.shashki.server.feature.events.domain.InMemoryRideHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * B-103: the projection outlives its broker. The broker is restarted under a running history — the
 * container restarted, not replaced, so the log it holds survives the way a pod's volume does — and an
 * event published afterwards must reach the history with nobody restarting the server.
 *
 * On a fixed host port, because a restarted container is otherwise given a new one and the reader is
 * pointed at an address, like the server's configuration is.
 */
class RideHistoryAcrossABrokerRestartTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val port = ServerSocket(0).use { it.localPort }
    private val broker: GenericContainer<*> =
        GenericContainer(DockerImageName.parse(IMAGE))
            .withEnv("BOOBLIK_TOPICS", "${EventsConfig.TOPIC}:3")
            .withExposedPorts(BROKER)
            .withCreateContainerCmdModifier { command ->
                val hostConfig = command.hostConfig ?: HostConfig.newHostConfig()
                command.withHostConfig(
                    hostConfig.withPortBindings(PortBinding(Ports.Binding.bindPort(port), ExposedPort(BROKER))),
                )
            }.waitingFor(Wait.forListeningPort())
            .apply { start() }

    @AfterTest
    fun stop() {
        scope.cancel()
        broker.stop()
    }

    private val address = InetSocketAddress("127.0.0.1", port)

    // A publisher of its own per call: the broker restart closes every connection it had.
    private suspend fun publish(
        rideId: String,
        type: String,
    ) {
        val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val connection = BooblikConnection(address, connectionScope)
        try {
            val producer = Producer(connection, connectionScope)
            val envelope = """{"id":"$rideId:$type","type":"$type"}"""
            producer
                .topic(
                    TopicName(EventsConfig.TOPIC),
                ).send(envelope.toByteArray(), key = rideId.toByteArray())
                .await()
            producer.flush()
        } finally {
            connection.close()
            connectionScope.cancel()
        }
    }

    private suspend fun InMemoryRideHistory.awaitTypes(
        rideId: String,
        expected: List<String>,
    ): List<String>? =
        withTimeoutOrNull(20.seconds) {
            while (of(rideId).events.map { it.type } != expected) delay(100.milliseconds)
            expected
        }

    @Test
    fun `the history keeps reading after the broker restarts under it`() =
        runBlocking {
            val history = InMemoryRideHistory()
            BooblikRideHistory(address, history, retryAfter = 200.milliseconds).start(scope)

            publish("ride-1", "ride.assigned")
            assertNotNull(
                history.awaitTypes("ride-1", listOf("ride.assigned")),
                "the history never read the first event",
            )

            // THE RESTART: every connection the reader holds is closed under it, and for a few seconds
            // nothing listens on the port at all.
            broker.dockerClient.restartContainerCmd(broker.containerId).exec()

            // Published as soon as the broker takes it. The port answers before booblik does — docker's
            // proxy accepts the connection and the broker behind it is still starting — so the first
            // attempts are closed under the publisher; that is the test's own wait, not the subject.
            val published =
                withTimeoutOrNull(60.seconds) {
                    while (true) {
                        try {
                            publish("ride-1", "ride.settled")
                            return@withTimeoutOrNull true
                        } catch (cancellation: kotlinx.coroutines.CancellationException) {
                            throw cancellation
                        } catch (notYet: Exception) {
                            // Refused (`IOException`) or accepted and closed (`ConnectionClosedException`).
                            println("the broker is not taking publishes yet: ${notYet.message}")
                            delay(500.milliseconds)
                        }
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                }
            assertNotNull(published, "the restarted broker never took a publish")
            assertNotNull(
                history.awaitTypes("ride-1", listOf("ride.assigned", "ride.settled")),
                "an event published after the broker restarted never reached the history: ${history.of(
                    "ride-1",
                ).events}",
            )
            // Recorded once: the reader carried on from the next offset rather than replaying the log —
            // and the projection would have absorbed a replay anyway, so this asserts the end state only.
            assertEquals(2, history.of("ride-1").events.size)
        }

    private companion object {
        // The stand's broker (docker/compose.yaml), so the restart is the one a deployment would see.
        const val IMAGE = "ghcr.io/youndie/booblik:0.3.1"
        const val BROKER = 9092
    }
}
