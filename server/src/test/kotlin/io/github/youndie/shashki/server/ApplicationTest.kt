package io.github.youndie.shashki.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun `the base module installs and answers its health probe without a database`() =
        testApplication {
            application { baseModule() }

            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }
}
