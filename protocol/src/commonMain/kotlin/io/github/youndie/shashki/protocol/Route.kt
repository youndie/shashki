package io.github.youndie.shashki.protocol

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/**
 * A road route between two points: what to draw, how far it is and how long it takes.
 *
 * **Nothing from `com.graphhopper` is in this file, and that is the point.** The router is a Java
 * library embedded in the server; the client is told a list of points. Replacing the router — or
 * putting it behind an HTTP call, or answering from a cache — changes nothing here, which is what
 * makes it replaceable rather than merely wrapped.
 *
 * The geometry is points rather than an encoded polyline. Encoding would save perhaps two thirds of
 * the bytes and costs a codec on both sides that has to agree about precision; for a route inside
 * one city the saving is not worth a second place for a rounding rule to differ.
 */
@Serializable
public data class RouteView(
    val geometry: List<GeoPoint>,
    val distanceMetres: Int,
    val durationSeconds: Int,
)

/** Two points, and the class only because a future profile might route a business car differently. */
@Serializable
public data class RouteRequest(
    val from: GeoPoint,
    val to: GeoPoint,
    val rideClass: RideClass = RideClass.ECONOMY,
)

/**
 * `/api/routes`, under `/api` like everything else this server speaks.
 *
 * B-23 writes the endpoint as `POST /routes`; the prefix is this repository's convention and the
 * item's shorthand was not meant to overrule it.
 */
@Resource("/api/routes")
public class Routes
