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

/**
 * What each class would cost for a journey, before anybody asks for a car.
 *
 * **The server answers this because the server owns pricing.** The alternative is the client holding
 * a copy of the fare formula so it can fill in R4's three tiles, and a copy of a rule is a rule that
 * drifts — the rider would be shown one number and charged another the first time a coefficient
 * moved.
 */
@Serializable
public data class ClassQuote(
    val rideClass: RideClass,
    val quote: Quote,
)

@Serializable
public data class QuotesView(
    val distanceMetres: Int,
    val durationSeconds: Int,
    val classes: List<ClassQuote>,
)

/** `/api/quotes` — the same two points as `/api/routes`, priced for every class. */
@Resource("/api/quotes")
public class Quotes
