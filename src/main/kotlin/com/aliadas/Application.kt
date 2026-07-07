package com.aliadas

import com.aliadas.auth.configureAuth
import com.aliadas.auth.authRoutes
import com.aliadas.contacts.contactRoutes
import com.aliadas.community.communityRoutes
import com.aliadas.resources.resourceRoutes
import com.aliadas.profile.profileRoutes
import com.aliadas.db.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.server.plugins.callloging.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

    // Content Negotiation (JSON)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // CORS - allow Android app to connect
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }

    // Error handling
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Unknown error"))
            )
        }
    }

    // Call logging
    install(CallLogging)

    // Authentication (JWT)
    configureAuth()

    // Routes
    routing {
        get("/health") {
            call.respond(mapOf("status" to "OK", "app" to "Aliadas Backend"))
        }
        authRoutes()
        contactRoutes()
        communityRoutes()
        resourceRoutes()
        profileRoutes()
    }
}
