package com.aliadas.contacts

import com.aliadas.auth.userId
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

// ── Table ──────────────────────────────────────────────────────────────────
object Contacts : Table("contacts") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(com.aliadas.auth.Users.id)
    val name = varchar("name", 100)
    val phone = varchar("phone", 20)
    val relation = varchar("relation", 50).default("Contacto")
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

// ── DTOs ───────────────────────────────────────────────────────────────────
@Serializable data class ContactRequest(val name: String, val phone: String, val relation: String = "Contacto")
@Serializable data class ContactResponse(val id: Int, val name: String, val phone: String, val relation: String, val isActive: Boolean)

// ── Routes ─────────────────────────────────────────────────────────────────
fun Routing.contactRoutes() {
    authenticate("auth-jwt") {
        route("/api/contacts") {

            // GET all contacts for user
            get {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val contacts = transaction {
                    Contacts.select { (Contacts.userId eq userId) and (Contacts.isActive eq true) }
                        .map {
                            ContactResponse(
                                it[Contacts.id],
                                it[Contacts.name],
                                it[Contacts.phone],
                                it[Contacts.relation],
                                it[Contacts.isActive]
                            )
                        }
                }
                call.respond(contacts)
            }

            // POST add contact
            post {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<ContactRequest>()

                if (req.name.isBlank() || req.phone.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Nombre y teléfono requeridos"))
                    return@post
                }

                val count = transaction {
                    Contacts.select { (Contacts.userId eq userId) and (Contacts.isActive eq true) }.count()
                }
                if (count >= 5) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Máximo 5 contactos de confianza"))
                    return@post
                }

                val newId = transaction {
                    Contacts.insert {
                        it[Contacts.userId] = userId
                        it[name] = req.name.trim()
                        it[phone] = req.phone.trim()
                        it[relation] = req.relation
                    } get Contacts.id
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to newId, "message" to "Contacto agregado"))
            }

            // DELETE contact
            delete("/{id}") {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val contactId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID inválido"))

                transaction {
                    Contacts.update({ (Contacts.id eq contactId) and (Contacts.userId eq userId) }) {
                        it[isActive] = false
                    }
                }
                call.respond(mapOf("message" to "Contacto eliminado"))
            }

            // PUT update contact
            put("/{id}") {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val contactId = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID inválido"))
                val req = call.receive<ContactRequest>()

                transaction {
                    Contacts.update({ (Contacts.id eq contactId) and (Contacts.userId eq userId) }) {
                        it[name] = req.name.trim()
                        it[phone] = req.phone.trim()
                        it[relation] = req.relation
                    }
                }
                call.respond(mapOf("message" to "Contacto actualizado"))
            }
        }
    }
}
