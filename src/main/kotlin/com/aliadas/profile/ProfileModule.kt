package com.aliadas.profile

import com.aliadas.auth.Users
import com.aliadas.auth.userId
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable data class ProfileResponse(val id: Int, val name: String, val email: String, val avatarIcon: String)
@Serializable data class UpdateProfileRequest(val name: String? = null, val avatarIcon: String? = null)

// Available avatar icons (no real photos - just icon identifiers)
val AVAILABLE_AVATARS = listOf(
    "avatar_flower", "avatar_star", "avatar_moon", "avatar_sun",
    "avatar_butterfly", "avatar_leaf", "avatar_heart", "avatar_diamond",
    "avatar_crown", "avatar_wave", "avatar_fire", "avatar_snowflake"
)

fun Routing.profileRoutes() {
    authenticate("auth-jwt") {
        route("/api/profile") {

            get {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val user = transaction {
                    Users.select { Users.id eq userId }.singleOrNull()
                } ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Usuario no encontrado"))

                call.respond(ProfileResponse(user[Users.id], user[Users.name], user[Users.email], user[Users.avatarIcon]))
            }

            put {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<UpdateProfileRequest>()

                if (req.avatarIcon != null && req.avatarIcon !in AVAILABLE_AVATARS) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Avatar no válido"))
                    return@put
                }

                transaction {
                    Users.update({ Users.id eq userId }) {
                        if (req.name != null && req.name.isNotBlank()) it[name] = req.name.trim()
                        if (req.avatarIcon != null) it[avatarIcon] = req.avatarIcon
                    }
                }
                call.respond(mapOf("message" to "Perfil actualizado"))
            }

            get("/avatars") {
                call.respond(AVAILABLE_AVATARS)
            }
        }
    }
}
