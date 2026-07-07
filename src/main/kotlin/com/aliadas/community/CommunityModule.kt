package com.aliadas.community

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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus

// ── Tables ─────────────────────────────────────────────────────────────────
object Posts : Table("posts") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(com.aliadas.auth.Users.id)
    val content = text("content")
    val category = varchar("category", 30).default("general")
    val likesCount = integer("likes_count").default(0)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PostLikes : Table("post_likes") {
    val postId = integer("post_id").references(Posts.id)
    val userId = integer("user_id").references(com.aliadas.auth.Users.id)
    override val primaryKey = PrimaryKey(postId, userId)
}

object Comments : Table("comments") {
    val id = integer("id").autoIncrement()
    val postId = integer("post_id").references(Posts.id)
    val userId = integer("user_id").references(com.aliadas.auth.Users.id)
    val content = text("content")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── DTOs ───────────────────────────────────────────────────────────────────
@Serializable data class PostRequest(val content: String, val category: String = "general")
@Serializable data class CommentRequest(val content: String)

@Serializable data class CommentResponse(
    val id: Int,
    val content: String,
    val created_at: Long
)

@Serializable data class PostResponse(
    val id: Int,
    val content: String,
    val category: String,
    val likesCount: Int,
    val commentsCount: Int,
    val hasLiked: Boolean,
    val created_at: Long
)

// ── Routes ─────────────────────────────────────────────────────────────────
fun Routing.communityRoutes() {
    authenticate("auth-jwt") {
        route("/api/community") {

            get("/posts") {
                val currentUserId = call.principal<JWTPrincipal>()!!.userId()
                val filter = call.request.queryParameters["filter"] ?: "recent"

                val posts = transaction {
                    val query = Posts.selectAll()
                    when (filter) {
                        "popular" -> query.orderBy(Posts.likesCount to SortOrder.DESC)
                        "apoyo" -> query.andWhere { Posts.category eq "apoyo" }.orderBy(Posts.createdAt to SortOrder.DESC)
                        else -> query.orderBy(Posts.createdAt to SortOrder.DESC)
                    }
                    query.limit(50).map { row ->
                        val postId = row[Posts.id]
                        val commentsCount = Comments.select { Comments.postId eq postId }.count().toInt()
                        val hasLiked = PostLikes.select {
                            (PostLikes.postId eq postId) and (PostLikes.userId eq currentUserId)
                        }.count() > 0
                        PostResponse(
                            postId,
                            row[Posts.content],
                            row[Posts.category],
                            row[Posts.likesCount],
                            commentsCount,
                            hasLiked,
                            row[Posts.createdAt]
                        )
                    }
                }
                call.respond(posts)
            }

            post("/posts") {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val req = call.receive<PostRequest>()

                if (req.content.isBlank() || req.content.length > 500) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Contenido inválido (máx 500 chars)"))
                    return@post
                }

                val postId = transaction {
                    Posts.insert {
                        it[Posts.userId] = userId
                        it[content] = req.content.trim()
                        it[category] = req.category
                        it[createdAt] = System.currentTimeMillis() // ✅
                    } get Posts.id
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to postId))
            }

            post("/posts/{id}/like") {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID inválido"))

                val alreadyLiked = transaction {
                    PostLikes.select {
                        (PostLikes.postId eq postId) and (PostLikes.userId eq userId)
                    }.count() > 0
                }

                transaction {
                    if (alreadyLiked) {
                        PostLikes.deleteWhere { (PostLikes.postId eq postId) and (PostLikes.userId eq userId) }
                        Posts.update({ Posts.id eq postId }) { it[likesCount] = likesCount - 1 }
                    } else {
                        PostLikes.insert { it[PostLikes.postId] = postId; it[PostLikes.userId] = userId }
                        Posts.update({ Posts.id eq postId }) { it[likesCount] = likesCount + 1 }
                    }
                }
                call.respond(mapOf("liked" to !alreadyLiked))
            }

            get("/posts/{id}/comments") {
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID inválido"))

                val comments = transaction {
                    Comments.select { Comments.postId eq postId }
                        .orderBy(Comments.createdAt to SortOrder.ASC)
                        .map { CommentResponse(it[Comments.id], it[Comments.content], it[Comments.createdAt]) }
                }
                call.respond(comments)
            }

            post("/posts/{id}/comments") {
                val userId = call.principal<JWTPrincipal>()!!.userId()
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID inválido"))
                val req = call.receive<CommentRequest>()

                if (req.content.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Comentario vacío"))
                    return@post
                }

                val commentId = transaction {
                    Comments.insert {
                        it[Comments.postId] = postId
                        it[Comments.userId] = userId
                        it[content] = req.content.trim()
                        it[createdAt] = System.currentTimeMillis() // ✅
                    } get Comments.id
                }
                call.respond(HttpStatusCode.Created, mapOf("id" to commentId))
            }
        }
    }
}
