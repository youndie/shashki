package io.github.youndie.shashki.server.feature.driver.data

import io.github.youndie.shashki.protocol.RideClass
import io.github.youndie.shashki.server.feature.driver.domain.Driver
import io.github.youndie.shashki.server.feature.driver.domain.DriverRepository
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The `drivers` table, read only.
 *
 * **Nothing writes it here on purpose** (B-63). The rows are a migration's, because this product has
 * no registration and inventing one on first sight would be the server making up a driver's car.
 */
public class ExposedDriverRepository(
    private val database: Database,
) : DriverRepository {
    override fun find(id: String): Driver? =
        transaction(database) {
            Drivers
                .selectAll()
                .where { Drivers.id eq id }
                .singleOrNull()
                ?.let {
                    Driver(
                        id = it[Drivers.id],
                        name = it[Drivers.name],
                        car = it[Drivers.car],
                        plate = it[Drivers.plate],
                        rideClass = RideClass.valueOf(it[Drivers.rideClass]),
                    )
                }
        }

    private object Drivers : Table("drivers") {
        val id = varchar("id", 255)
        val name = varchar("name", 255)
        val car = varchar("car", 255)
        val plate = varchar("plate", 32)
        val rideClass = varchar("ride_class", 20)
    }
}
