package com.aliadas.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
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
import org.mindrot.jbcrypt.BCrypt
import java.util.*

// ── Table ──────────────────────────────────────────────────────────────────
object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 150).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val avatarIcon = varchar("avatar_icon", 50).default("avatar_1")
    val createdAt = long("created_at").default(System.currentTimeMillis())
    override val primaryKey = PrimaryKey(id)
}

// ── DTOs ───────────────────────────────────────────────────────────────────
@Serializable data class RegisterRequest(val name: String, val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String, val userId: Int, val name: String, val avatarIcon: String)
@Serializable data class ApiError(val error: String)

// ── JWT helpers ────────────────────────────────────────────────────────────
private val jwtSecret = System.getenv("JWT_SECRET") ?: "aliadas_super_secret_dev"
private const val ISSUER = "aliadas-app"
private const val AUDIENCE = "aliadas-users"

fun generateToken(userId: Int): String = JWT.create()
    .withAudience(AUDIENCE)
    .withIssuer(ISSUER)
    .withClaim("userId", userId)
    .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30 days
    .sign(Algorithm.HMAC256(jwtSecret))

fun Application.configureAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "aliadas"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(AUDIENCE)
                    .withIssuer(ISSUER)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null)
                    JWTPrincipal(credential.payload) else null
            }
        }
    }
}

fun JWTPrincipal.userId(): Int = payload.getClaim("userId").asInt()

// ── Routes ─────────────────────────────────────────────────────────────────
fun Routing.authRoutes() {
    route("/api/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()

            if (req.name.isBlank() || req.email.isBlank() || req.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Datos inválidos"))
                return@post
            }

            val exists = transaction { Users.select { Users.email eq req.email }.count() > 0 }
            if (exists) {
                call.respond(HttpStatusCode.Conflict, ApiError("El correo ya está registrado"))
                return@post
            }

            val hash = BCrypt.hashpw(req.password, BCrypt.gensalt())
            val userId = transaction {
                Users.insert {
                    it[name] = req.name.trim()
                    it[email] = req.email.trim().lowercase()
                    it[passwordHash] = hash
                } get Users.id
            }

            val token = generateToken(userId)
            call.respond(HttpStatusCode.Created, AuthResponse(token, userId, req.name.trim(), "avatar_1"))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()

            val user = transaction {
                Users.select { Users.email eq req.email.trim().lowercase() }.singleOrNull()
            }

            if (user == null || !BCrypt.checkpw(req.password, user[Users.passwordHash])) {
                call.respond(HttpStatusCode.Unauthorized, ApiError("Credenciales inválidas"))
                return@post
            }

            val token = generateToken(user[Users.id])
            call.respond(AuthResponse(token, user[Users.id], user[Users.name], user[Users.avatarIcon]))
        }
    }
}
