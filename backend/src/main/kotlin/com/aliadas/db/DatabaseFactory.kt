package com.aliadas.db

import com.aliadas.auth.Users
import com.aliadas.contacts.Contacts
import com.aliadas.community.Posts
import com.aliadas.community.PostLikes
import com.aliadas.community.Comments
import com.aliadas.resources.Resources
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init() {
        val dbUrl = System.getenv("DATABASE_URL")
            ?: "jdbc:mysql://localhost:3306/aliadas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
        val dbUser = System.getenv("MYSQLUSER") ?: "root"
        val dbPassword = System.getenv("MYSQLPASSWORD") ?: "password"

        val config = HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPassword
            driverClassName = "com.mysql.cj.jdbc.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(
                Users,
                Contacts,
                Posts,
                PostLikes,
                Comments,
                Resources
            )
            seedResources()
        }
    }

    private fun seedResources() {
        if (Resources.selectAll().count() > 0) return

        val seeds = listOf(
            Triple("Línea Nacional de Emergencia",
                "Atención inmediata en situaciones de violencia o peligro.",
                mapOf("category" to "emergency", "actionUrl" to "tel:911",
                    "actionLabel" to "Llamar ahora", "icon" to "phone", "available24h" to true)),
            Triple("INMUJERES - Línea de Apoyo",
                "Instituto Nacional de las Mujeres. Orientación y apoyo gratuito.",
                mapOf("category" to "emergency", "actionUrl" to "tel:8009112511",
                    "actionLabel" to "Llamar ahora", "icon" to "phone", "available24h" to true)),
            Triple("Guía: ¿Qué hacer en caso de acoso?",
                "Protocolos de seguridad y pasos a seguir ante situaciones de acoso.",
                mapOf("category" to "guide", "actionUrl" to "https://www.gob.mx/inmujeres",
                    "actionLabel" to "Leer guía", "icon" to "book", "available24h" to false)),
            Triple("Centros de Apoyo Cercanos",
                "Refugios, estaciones de policía y centros comunitarios aliados.",
                mapOf("category" to "map", "actionUrl" to "map",
                    "actionLabel" to "Ver en el mapa", "icon" to "location", "available24h" to false)),
            Triple("Guía de Seguridad Digital",
                "Cómo proteger tus redes sociales, contraseñas y privacidad en línea.",
                mapOf("category" to "guide", "actionUrl" to "https://www.gob.mx/inmujeres",
                    "actionLabel" to "Leer guía", "icon" to "book", "available24h" to false)),
            Triple("Fiscalía Especializada en Delitos contra Mujeres",
                "Denuncia situaciones de violencia de género.",
                mapOf("category" to "emergency", "actionUrl" to "tel:8008008290",
                    "actionLabel" to "Llamar ahora", "icon" to "phone", "available24h" to true))
        )

        seeds.forEach { (title, description, meta) ->
            Resources.insert {
                it[Resources.title] = title
                it[Resources.description] = description
                it[Resources.category] = meta["category"] as String
                it[Resources.actionUrl] = meta["actionUrl"] as String
                it[Resources.actionLabel] = meta["actionLabel"] as String
                it[Resources.icon] = meta["icon"] as String
                it[Resources.isAvailable24h] = meta["available24h"] as Boolean
            }
        }
    }
}
