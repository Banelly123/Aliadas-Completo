package com.aliadas.network

import com.aliadas.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ── Data classes ───────────────────────────────────────────────────────────
data class RegisterRequest(val name: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val token: String, val userId: Int, val name: String, val avatarIcon: String)

data class ContactRequest(val name: String, val phone: String, val relation: String = "Contacto")
data class ContactResponse(val id: Int, val name: String, val phone: String, val relation: String, val isActive: Boolean)

data class PostRequest(val content: String, val category: String = "general")
data class PostResponse(
    val id: Int, val content: String, val category: String,
    val likesCount: Int, val commentsCount: Int, val hasLiked: Boolean, val createdAt: Long
)
data class CommentRequest(val content: String)
data class CommentResponse(val id: Int, val content: String, val createdAt: Long)

data class ResourceResponse(
    val id: Int, val title: String, val description: String,
    val category: String, val actionUrl: String, val actionLabel: String,
    val icon: String, val isAvailable24h: Boolean
)

data class ProfileResponse(val id: Int, val name: String, val email: String, val avatarIcon: String)
data class UpdateProfileRequest(val name: String? = null, val avatarIcon: String? = null)

// ── API Interface ──────────────────────────────────────────────────────────
interface AlidasApi {
    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    // Contacts
    @GET("api/contacts")
    suspend fun getContacts(@Header("Authorization") token: String): Response<List<ContactResponse>>

    @POST("api/contacts")
    suspend fun addContact(@Header("Authorization") token: String, @Body req: ContactRequest): Response<Map<String, Any>>

    @DELETE("api/contacts/{id}")
    suspend fun deleteContact(@Header("Authorization") token: String, @Path("id") id: Int): Response<Map<String, String>>

    // Community
    @GET("api/community/posts")
    suspend fun getPosts(@Header("Authorization") token: String, @Query("filter") filter: String = "recent"): Response<List<PostResponse>>

    @POST("api/community/posts")
    suspend fun createPost(@Header("Authorization") token: String, @Body req: PostRequest): Response<Map<String, Any>>

    @POST("api/community/posts/{id}/like")
    suspend fun likePost(@Header("Authorization") token: String, @Path("id") id: Int): Response<Map<String, Any>>

    @GET("api/community/posts/{id}/comments")
    suspend fun getComments(@Header("Authorization") token: String, @Path("id") id: Int): Response<List<CommentResponse>>

    @POST("api/community/posts/{id}/comments")
    suspend fun addComment(@Header("Authorization") token: String, @Path("id") id: Int, @Body req: CommentRequest): Response<Map<String, Any>>

    // Resources
    @GET("api/resources")
    suspend fun getResources(@Header("Authorization") token: String): Response<List<ResourceResponse>>

    // Profile
    @GET("api/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<ProfileResponse>

    @PUT("api/profile")
    suspend fun updateProfile(@Header("Authorization") token: String, @Body req: UpdateProfileRequest): Response<Map<String, String>>

    @GET("api/profile/avatars")
    suspend fun getAvatars(@Header("Authorization") token: String): Response<List<String>>
}

// ── Retrofit singleton ─────────────────────────────────────────────────────
object RetrofitClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: AlidasApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL + "/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AlidasApi::class.java)
}
