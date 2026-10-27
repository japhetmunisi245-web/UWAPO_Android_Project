package com.uwapo.app.cloud

import com.uwapo.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    val configured: Boolean get() = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun signIn(email: String, password: String): JSONObject {
        requireConfigured()
        val body = JSONObject().put("email", email).put("password", password)
        return request("/auth/v1/token?grant_type=password", "POST", body, null, authEndpoint = true)
    }

    fun refresh(refreshToken: String): JSONObject {
        requireConfigured()
        return request("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject().put("refresh_token", refreshToken), null, authEndpoint = true)
    }

    fun get(path: String, token: String): JSONArray = JSONArray(requestRaw(path, "GET", null, token))

    fun post(path: String, body: JSONObject, token: String, prefer: String? = null): String =
        requestRaw(path, "POST", body, token, prefer = prefer)

    fun rpc(name: String, body: JSONObject, token: String): String =
        requestRaw("/rest/v1/rpc/$name", "POST", body, token)

    private fun request(path: String, method: String, body: JSONObject?, token: String?, authEndpoint: Boolean = false): JSONObject {
        return JSONObject(requestRaw(path, method, body, token, authEndpoint = authEndpoint))
    }

    private fun requestRaw(
        path: String,
        method: String,
        body: JSONObject?,
        token: String?,
        prefer: String? = null,
        authEndpoint: Boolean = false
    ): String {
        requireConfigured()
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val builder = Request.Builder().url(base + path)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Accept", "application/json")
        if (authEndpoint) builder.header("Content-Type", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        if (prefer != null) builder.header("Prefer", prefer)
        val requestBody = body?.toString()?.toRequestBody(jsonType)
        when (method) {
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(jsonType))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(jsonType))
            "DELETE" -> builder.delete(requestBody)
            else -> builder.get()
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { JSONObject(text).optString("message", text) } catch (_: Exception) { text }
                throw IOException("Supabase request failed (${response.code}): ${detail.take(300)}")
            }
            return text
        }
    }

    private fun requireConfigured() {
        if (!configured) throw IllegalStateException(
            "Supabase is not configured. Add uwapo.supabase.url and uwapo.supabase.anon_key to local.properties, then rebuild."
        )
    }
}
