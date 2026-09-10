package com.md4a.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Result of loading a README document. */
internal data class LoadedDoc(val markdown: String, val imageBaseUrl: String?)

/**
 * Fetches README markdown for the demo app.
 *
 * Accepted inputs:
 *  - `owner/repo` or any github.com repo URL → GitHub API readme (raw)
 *  - direct markdown URL (raw.githubusercontent.com, gist raw, …) → raw text
 *  - `null`/blank → a random popular repo's readme
 */
internal object ReadmeFetcher {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private const val UA = "MD4a-demo"

    sealed interface Result {
        data class Ok(val doc: LoadedDoc) : Result
        data class Failed(val message: String) : Result
    }

    suspend fun fetch(input: String?): Result = withContext(Dispatchers.IO) {
        try {
            val trimmed = input?.trim().orEmpty()
            when {
                trimmed.isEmpty() -> fetchRandom()
                Regex("^[\\w.-]+/[\\w.-]+$").matches(trimmed) -> fetchRepo(trimmed)
                trimmed.contains("github.com") && !trimmed.contains("/blob/") && !trimmed.contains("/raw/") ->
                    fetchRepo(repoSlugOf(trimmed) ?: return@withContext Result.Failed("无法识别仓库地址：$trimmed"))
                trimmed.endsWith(".md") || trimmed.contains("raw.githubusercontent") ->
                    fetchRawUrl(trimmed)
                else -> fetchRawUrl(trimmed)
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun repoSlugOf(url: String): String? {
        val cleaned = url.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
        val parts = cleaned.split('/', '?', '#')
        val slug = parts.take(2).joinToString("/")
        return slug.takeIf { parts.size >= 2 && parts.take(2).all { p -> p.isNotEmpty() } }
    }

    private fun get(url: String, accept: String? = null): String {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        accept?.let { builder.header("Accept", it) }
        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}：${body.take(160)}")
            return body
        }
    }

    private fun fetchRepo(slug: String): Result {
        val repoJson = json.parseToJsonElement(get("https://api.github.com/repos/$slug", "application/json")).jsonObject
        val branch = repoJson["default_branch"]?.jsonPrimitive?.content ?: "main"
        val markdown = get(
            "https://api.github.com/repos/$slug/readme",
            "application/vnd.github.raw+json",
        )
        val base = "https://raw.githubusercontent.com/$slug/$branch/"
        return Result.Ok(LoadedDoc(markdown, base))
    }

    private fun fetchRawUrl(url: String): Result =
        Result.Ok(LoadedDoc(get(url), baseUrlOf(url)))

    private fun baseUrlOf(url: String): String? {
        // raw file URL → base directory for relative images
        val rawPrefix = "https://raw.githubusercontent.com/"
        if (url.startsWith(rawPrefix)) {
            val rest = url.removePrefix(rawPrefix)            // owner/repo/branch/path/to.md
            val parts = rest.split('/')
            return if (parts.size >= 3) {
                rawPrefix + parts.take(3).joinToString("/") + "/"
            } else null
        }
        return null
    }

    private fun fetchRandom(): Result {
        val page = (1..8).random()
        val body = get(
            "https://api.github.com/search/repositories?q=stars%3A%3E8000&sort=stars&order=desc&per_page=100&page=$page",
            "application/vnd.github+json",
        )
        val items = json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
            ?: return Result.Failed("搜索结果为空")
        if (items.isEmpty()) return Result.Failed("搜索结果为空")
        val repo = items.random().jsonObject
        val slug = repo["full_name"]?.jsonPrimitive?.content
            ?: return Result.Failed("结果缺少 full_name")
        return fetchRepo(slug)
    }
}
