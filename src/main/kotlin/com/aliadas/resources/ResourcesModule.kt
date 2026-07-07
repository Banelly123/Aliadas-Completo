package com.aliadas.resources

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

// ── Table ──────────────────────────────────────────────────────────────────
object Resources : Table("resources") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 200)
    val description = text("description")
    val category = varchar("category", 30) // emergency | guide | map
    val actionUrl = varchar("action_url", 500)
    val actionLabel = varchar("action_label", 100)
    val icon = varchar("icon", 50).default("info")
    val isAvailable24h = bool("is_available_24h").default(false)
    override val primaryKey = PrimaryKey(id)
}

// ── DTOs ───────────────────────────────────────────────────────────────────
@Serializable data class ResourceResponse(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val actionUrl: String,
    val actionLabel: String,
    val icon: String,
    val isAvailable24h: Boolean
)

// ── Routes ─────────────────────────────────────────────────────────────────
fun Routing.resourceRoutes() {
    authenticate("auth-jwt") {
        route("/api/resources") {
            get {
                val resources = transaction {
                    Resources.selectAll().map {
                        ResourceResponse(
                            it[Resources.id],
                            it[Resources.title],
                            it[Resources.description],
                            it[Resources.category],
                            it[Resources.actionUrl],
                            it[Resources.actionLabel],
                            it[Resources.icon],
                            it[Resources.isAvailable24h]
                        )
                    }
                }
                call.respond(resources)
            }
        }
    }
}
