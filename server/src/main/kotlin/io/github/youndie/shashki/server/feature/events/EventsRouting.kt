package io.github.youndie.shashki.server.feature.events

import io.github.youndie.shashki.protocol.Rides
import io.github.youndie.shashki.server.feature.events.domain.RideHistory
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

/**
 * `GET /api/rides/{id}/history` — what happened to a ride, from the topic alone.
 *
 * **Auth tier: public, and chosen.** It says which states a ride passed through and when, and
 * nothing about who. The rider's own routes are behind a token because they change something or name
 * a person; this names neither. When B-09 puts the rider in the token this becomes "the rider's own,
 * or a role", for the same reason `GET /api/rides/{id}` will.
 *
 * A ride the projection has never heard of answers with an empty list rather than 404: the topic is
 * the only thing this route reads, and "the broker has nothing about it" is a different fact from
 * "there is no such ride" — which the ride's own route already answers.
 */
public fun Route.eventRoutes() {
    val history by inject<RideHistory>()

    get<Rides.History> { route -> call.respond(history.of(route.id)) }
}
