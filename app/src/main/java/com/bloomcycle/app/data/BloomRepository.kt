package com.bloomcycle.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bloomcycle.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BloomRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("bloomcycle_native", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val healthSyncMutex = Mutex()
    private val authRefreshMutex = Mutex()
    private var token: String? = prefs.getString("supabase_access_token", null)
    private var refreshToken: String? = prefs.getString("supabase_refresh_token", null)
    private var healthDirty: Boolean = prefs.getBoolean("health_dirty", false)
    private var healthDirtyEmail: String? = prefs.getString("health_dirty_email", null)

    var currentUser: User? by mutableStateOf(null)
        private set

    var isLoggedIn: Boolean by mutableStateOf(false)
        private set

    var hasSeenIntro: Boolean by mutableStateOf(false)
        private set

    var remind: Boolean by mutableStateOf(true)
        private set

    var theme: String by mutableStateOf("auto")
        private set

    var periods: java.util.ArrayList<PeriodRecord> by mutableStateOf(java.util.ArrayList<PeriodRecord>())
        private set

    var notes: java.util.ArrayList<DailyNote> by mutableStateOf(java.util.ArrayList<DailyNote>())
        private set

    var discussions: java.util.ArrayList<DiscussionPost> by mutableStateOf(java.util.ArrayList<DiscussionPost>())
        private set

    var articleComments: java.util.HashMap<Int, java.util.ArrayList<ArticleComment>> by mutableStateOf(java.util.HashMap<Int, java.util.ArrayList<ArticleComment>>())
        private set

    var reports: java.util.ArrayList<Report> by mutableStateOf(java.util.ArrayList<Report>())
        private set

    var settings: AppSettings by mutableStateOf(AppSettings())
        private set

    var syncError: String? by mutableStateOf(null)
        private set

    val articles: List<Article> = java.util.Arrays.asList(
        Article(
            id = 0,
            title = "Mengapa tubuh terasa tidak fit sebelum haid?",
            tag = "Kesehatan",
            body = java.util.Arrays.asList(
                "Beberapa hari sebelum haid, kadar hormon estrogen dan progesteron berubah. Perubahan ini bisa membuat badan terasa lelah, perut kembung, payudara nyeri, atau suasana hati naik turun.",
                "Kumpulan keluhan ini sering disebut sindrom pramenstruasi (PMS). Tidur cukup, bergerak ringan, dan mengurangi kafein bisa membantu meredakannya.",
                "Jika keluhan sangat mengganggu aktivitas, catat polanya di BloomCycle dan konsultasikan ke dokter."
            )
        ),
        Article(
            id = 1,
            title = "Mengenal fase-fase siklus menstruasi",
            tag = "Fakta",
            body = java.util.Arrays.asList(
                "Siklus dihitung dari hari pertama haid sampai sehari sebelum haid berikutnya. Panjangnya berbeda pada tiap orang, umumnya 21–35 hari.",
                "Ada empat fase: menstruasi, folikular, ovulasi, dan luteal. Tiap fase membawa perubahan hormon yang memengaruhi energi dan suasana hati."
            )
        ),
        Article(
            id = 2,
            title = "Tips meredakan kram perut saat haid",
            tag = "Tips",
            body = java.util.Arrays.asList(
                "Kompres hangat di perut bawah, minum air putih yang cukup, dan lakukan peregangan ringan.",
                "Catat tingkat nyerimu di menu Catatan. Bila nyeri sangat berat atau perdarahan mengkhawatirkan, segera periksa ke tenaga kesehatan."
            )
        )
    )

    init {
        loadData()
    }

    private fun loadData() {
        val userJson = prefs.getString("user", null)
        if (userJson != null) {
            currentUser = gson.fromJson(userJson, User::class.java)
        }

        isLoggedIn = prefs.getBoolean("is_logged_in", false) && !token.isNullOrBlank()
        hasSeenIntro = prefs.getBoolean("has_seen_intro", false)
        remind = prefs.getBoolean("remind", true)
        theme = prefs.getString("theme", "auto") ?: "auto"

        val periodsJson = prefs.getString("periods", null)
        if (periodsJson != null) {
            val type = object : TypeToken<java.util.ArrayList<PeriodRecord>>() {}.type
            periods = gson.fromJson(periodsJson, type) ?: java.util.ArrayList()
        }

        val notesJson = prefs.getString("notes", null)
        if (notesJson != null) {
            val type = object : TypeToken<java.util.ArrayList<DailyNote>>() {}.type
            notes = gson.fromJson(notesJson, type) ?: java.util.ArrayList()
        }

        val discussionsJson = prefs.getString("discussions", null)
        if (discussionsJson != null) {
            val type = object : TypeToken<java.util.ArrayList<DiscussionPost>>() {}.type
            discussions = gson.fromJson(discussionsJson, type) ?: java.util.ArrayList()
        } else {
            seedDiscussions()
        }

        val commentsJson = prefs.getString("article_comments", null)
        if (commentsJson != null) {
            val type = object : TypeToken<java.util.HashMap<Int, java.util.ArrayList<ArticleComment>>>() {}.type
            articleComments = gson.fromJson(commentsJson, type) ?: java.util.HashMap()
        } else {
            seedArticleComments()
        }

        val reportsJson = prefs.getString("reports", null)
        if (reportsJson != null) {
            val type = object : TypeToken<java.util.ArrayList<Report>>() {}.type
            reports = gson.fromJson(reportsJson, type) ?: java.util.ArrayList()
        }
    }

    private fun saveData() {
        periods = java.util.ArrayList(periods)
        notes = java.util.ArrayList(notes)
        discussions = java.util.ArrayList(discussions)
        articleComments = java.util.HashMap(articleComments)
        reports = java.util.ArrayList(reports)
        val editor = prefs.edit()
        editor.putString("user", gson.toJson(currentUser))
        editor.putBoolean("is_logged_in", isLoggedIn)
        editor.putBoolean("has_seen_intro", hasSeenIntro)
        editor.putBoolean("remind", remind)
        editor.putString("theme", theme)
        editor.putString("periods", gson.toJson(periods))
        editor.putString("notes", gson.toJson(notes))
        editor.putString("discussions", gson.toJson(discussions))
        editor.putString("article_comments", gson.toJson(articleComments))
        editor.putString("reports", gson.toJson(reports))
        editor.remove("api_token")
        if (token.isNullOrBlank()) {
            editor.remove("supabase_access_token")
            editor.remove("supabase_refresh_token")
        } else {
            editor.putString("supabase_access_token", token)
            if (refreshToken.isNullOrBlank()) {
                editor.remove("supabase_refresh_token")
            } else {
                editor.putString("supabase_refresh_token", refreshToken)
            }
        }
        editor.putBoolean("health_dirty", healthDirty)
        if (healthDirtyEmail.isNullOrBlank()) {
            editor.remove("health_dirty_email")
        } else {
            editor.putString("health_dirty_email", healthDirtyEmail)
        }
        editor.apply()
    }

    suspend fun restoreSession() {
        if (token.isNullOrBlank()) return
        try {
            if (healthDirty && currentUser?.email.equals(healthDirtyEmail, ignoreCase = true)) {
                apiRequest("health", "PUT", healthPayload())
                clearHealthDirty()
            }
            val bootstrap = apiRequest("bootstrap")
            applyBootstrap(bootstrap)
            isLoggedIn = true
            saveData()
            syncError = null
        } catch (error: IOException) {
            if (error is ApiException && error.statusCode in 401..403) {
                token = null
                refreshToken = null
                isLoggedIn = false
                saveData()
            }
            syncError = error.message ?: "Tidak dapat menghubungi server BloomCycle."
        }
    }

    private suspend fun apiRequest(
        route: String,
        method: String = "GET",
        body: JsonObject? = null,
        authToken: String? = token
    ): JsonObject = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_KEY.trim()
        if (!base.startsWith("https://", ignoreCase = true) || anonKey.isBlank()) {
            throw IOException("Supabase belum dikonfigurasi. Build dengan -PsupabaseUrl dan -PsupabaseKey.")
        }
        val requestBody = JsonObject().apply {
            addProperty("p_route", route)
            addProperty("p_method", method)
            add("p_body", body ?: JsonObject())
        }

        val connection = (URL("$base/rest/v1/rpc/bloom_api").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            if (!authToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $authToken")
            }
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        connection.setRequestProperty("apikey", anonKey)
        connection.setRequestProperty("Authorization", "Bearer ${authToken ?: anonKey}")

        try {
            connection.outputStream.use { stream ->
                stream.write(gson.toJson(requestBody).toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val responseText = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val response = if (responseText.isBlank()) {
                JsonObject()
            } else {
                try {
                    JsonParser.parseString(responseText).asJsonObject
                } catch (_: RuntimeException) {
                    throw IOException("Supabase mengirim respons yang tidak valid.")
                }
            }
            if (statusCode == 401 && !authToken.isNullOrBlank() && authToken != token && !token.isNullOrBlank()) {
                return@withContext apiRequest(route, method, body, token)
            }
            if (statusCode == 401 && !authToken.isNullOrBlank() && authToken == token && !refreshToken.isNullOrBlank()) {
                refreshSupabaseSession(authToken)
                return@withContext apiRequest(route, method, body, token)
            }
            if (statusCode !in 200..299) {
                val message = response.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: response.get("msg")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: response.get("error_description")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: response.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: response.get("details")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: "Supabase tidak dapat menyelesaikan permintaan."
                throw ApiException(message, statusCode)
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun supabaseAuthRequest(
        path: String,
        body: JsonObject,
        authToken: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_KEY.trim()
        if (!base.startsWith("https://", ignoreCase = true) || anonKey.isBlank()) {
            throw IOException("Supabase belum dikonfigurasi. Build dengan -PsupabaseUrl dan -PsupabaseKey.")
        }

        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${authToken ?: anonKey}")
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(gson.toJson(body).toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val text = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val result = if (text.isBlank()) JsonObject() else try {
                JsonParser.parseString(text).asJsonObject
            } catch (_: RuntimeException) {
                throw IOException("Supabase mengirim respons yang tidak valid.")
            }
            if (statusCode !in 200..299) {
                val message = result.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: result.get("msg")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: result.get("error_description")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: result.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: "Supabase tidak dapat menyelesaikan permintaan."
                throw ApiException(message, statusCode)
            }
            result
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun refreshSupabaseSession(failedToken: String) {
        authRefreshMutex.withLock {
            if (token != failedToken) return@withLock
            val savedRefreshToken = refreshToken ?: throw IOException("Sesi berakhir. Silakan masuk kembali.")
            try {
                val refreshed = supabaseAuthRequest(
                    "/auth/v1/token?grant_type=refresh_token",
                    JsonObject().apply { addProperty("refresh_token", savedRefreshToken) }
                )
                saveSupabaseTokens(refreshed)
            } catch (error: ApiException) {
                if (error.statusCode in 400..403) {
                    token = null
                    refreshToken = null
                    isLoggedIn = false
                    saveData()
                    throw IOException("Sesi berakhir. Silakan masuk kembali.")
                }
                throw error
            }
        }
    }

    private fun saveSupabaseTokens(response: JsonObject) {
        token = response.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
        refreshToken = response.get("refresh_token")?.takeIf { it.isJsonPrimitive }?.asString
        if (token.isNullOrBlank()) throw IOException("Supabase tidak mengembalikan access token.")
        prefs.edit()
            .putString("supabase_access_token", token)
            .putString("supabase_refresh_token", refreshToken)
            .remove("api_token")
            .apply()
    }

    private suspend fun signIn(response: JsonObject, legacyUser: User?): String? {
        val issuedToken = response.get("token")?.takeIf { it.isJsonPrimitive }?.asString
        if (issuedToken.isNullOrBlank()) return "Respons login server tidak lengkap."
        token = issuedToken

        return try {
            var bootstrap = apiRequest("bootstrap")
            val serverHealth = bootstrap.get("health")
            val serverUser = parseUser(bootstrap.getAsJsonObject("user"))
            val hasPendingLocalChanges = healthDirty &&
                healthDirtyEmail.equals(serverUser.email, ignoreCase = true)
            if (matchesLegacyProfile(legacyUser, serverUser) &&
                ((serverHealth == null || serverHealth.isJsonNull) || hasPendingLocalChanges)
            ) {
                apiRequest("health", "PUT", healthPayload())
                bootstrap = apiRequest("bootstrap")
            }
            applyBootstrap(bootstrap)
            clearHealthDirty()
            isLoggedIn = true
            hasSeenIntro = prefs.getBoolean("has_seen_intro", false)
            saveData()
            syncError = null
            null
        } catch (error: IOException) {
            isLoggedIn = false
            syncError = error.message ?: "Tidak dapat menghubungi server BloomCycle."
            error.message ?: "Tidak dapat menghubungi server BloomCycle."
        }
    }

    private fun applyBootstrap(data: JsonObject) {
        currentUser = parseUser(data.getAsJsonObject("user"))
        val health = data.get("health")
        if (health != null && health.isJsonObject) {
            val payload = health.asJsonObject
            periods = parsePeriods(payload.getAsJsonArray("periods"))
            notes = parseNotes(payload.getAsJsonArray("notes"))
            remind = payload.get("remind")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
            theme = payload.get("theme")?.takeIf { it.isJsonPrimitive }?.asString ?: "auto"
        } else {
            periods = java.util.ArrayList()
            notes = java.util.ArrayList()
            remind = true
            theme = "auto"
        }
        discussions = parseDiscussions(data.getAsJsonArray("discussions"))
        articleComments = parseArticleComments(data.getAsJsonArray("articleComments"))
        val settingsJson = data.getAsJsonObject("settings")
        settings = AppSettings(
            announcement = settingsJson?.get("announcement")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            communityEnabled = settingsJson?.get("communityEnabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        )
    }

    private fun healthPayload(): JsonObject {
        val payload = JsonObject()
        payload.add("periods", JsonArray().apply {
            periods.forEach { period ->
                add(JsonObject().apply {
                    addProperty("s", period.startDate)
                    if (period.endDate == null) add("e", com.google.gson.JsonNull.INSTANCE)
                    else addProperty("e", period.endDate)
                })
            }
        })
        payload.add("notes", gson.toJsonTree(notes))
        payload.addProperty("remind", remind)
        payload.addProperty("theme", theme)
        return payload
    }

    private fun parseUser(user: JsonObject): User = User(
        id = user.get("id")?.asString.orEmpty(),
        name = user.get("name")?.asString.orEmpty(),
        age = user.get("age")?.asInt ?: 0,
        email = user.get("email")?.asString.orEmpty(),
        role = user.get("role")?.asString ?: "user"
    )

    private fun parsePeriods(items: JsonArray?): java.util.ArrayList<PeriodRecord> =
        java.util.ArrayList<PeriodRecord>().apply {
            items?.forEach { element ->
                if (!element.isJsonObject) return@forEach
                val item = element.asJsonObject
                add(
                    PeriodRecord(
                        id = item.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: java.util.UUID.randomUUID().toString(),
                        startDate = item.get("s")?.asString ?: item.get("startDate")?.asString.orEmpty(),
                        endDate = item.get("e")?.takeUnless { it.isJsonNull }?.asString
                    )
                )
            }
        }

    private fun parseNotes(items: JsonArray?): java.util.ArrayList<DailyNote> =
        java.util.ArrayList<DailyNote>().apply {
            items?.forEach { element ->
                if (!element.isJsonObject) return@forEach
                val item = element.asJsonObject
                val symptoms = item.getAsJsonArray("symptoms")?.mapNotNull { symptom ->
                    symptom.takeIf { it.isJsonPrimitive }?.asString
                } ?: emptyList()
                add(
                    DailyNote(
                        id = item.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: java.util.UUID.randomUUID().toString(),
                        date = item.get("date")?.asString.orEmpty(),
                        flow = item.get("flow")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                        pain = item.get("pain")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                        mood = item.get("mood")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                        symptoms = symptoms,
                        text = item.get("text")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    )
                )
            }
        }

    private fun parseDiscussions(items: JsonArray?): java.util.ArrayList<DiscussionPost> =
        java.util.ArrayList<DiscussionPost>().apply {
            items?.forEach { element ->
                if (!element.isJsonObject) return@forEach
                val item = element.asJsonObject
                val answers = java.util.ArrayList<DiscussionAnswer>()
                item.getAsJsonArray("answers")?.forEach answerLoop@{ answerElement ->
                    if (!answerElement.isJsonObject) return@answerLoop
                    val answer = answerElement.asJsonObject
                    answers.add(
                        DiscussionAnswer(
                            id = answer.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                            authorName = answer.get("authorName")?.asString.orEmpty(),
                            authorEmail = answer.get("authorEmail")?.asString.orEmpty(),
                            date = answer.get("date")?.asString.orEmpty(),
                            text = answer.get("text")?.asString.orEmpty()
                        )
                    )
                }
                add(
                    DiscussionPost(
                        id = item.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        title = item.get("title")?.asString.orEmpty(),
                        category = item.get("cat")?.asString ?: item.get("category")?.asString.orEmpty(),
                        authorName = item.get("authorName")?.asString.orEmpty(),
                        authorEmail = item.get("authorEmail")?.asString.orEmpty(),
                        date = item.get("date")?.asString.orEmpty(),
                        text = item.get("text")?.asString.orEmpty(),
                        answers = answers
                    )
                )
            }
        }

    private fun parseArticleComments(items: JsonArray?): java.util.HashMap<Int, java.util.ArrayList<ArticleComment>> {
        val result = java.util.HashMap<Int, java.util.ArrayList<ArticleComment>>()
        items?.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val item = element.asJsonObject
            val articleId = item.get("articleId")?.asInt ?: return@forEach
            result.getOrPut(articleId) { java.util.ArrayList() }.add(
                ArticleComment(
                    id = item.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                    articleId = articleId,
                    name = item.get("name")?.asString.orEmpty(),
                    email = item.get("email")?.asString.orEmpty(),
                    date = item.get("date")?.asString.orEmpty(),
                    text = item.get("text")?.asString.orEmpty()
                )
            )
        }
        return result
    }

    private fun matchesLegacyProfile(legacyUser: User?, serverUser: User): Boolean =
        legacyUser != null &&
            legacyUser.age == serverUser.age &&
            legacyUser.name.trim().equals(serverUser.name.trim(), ignoreCase = true) &&
            legacyUser.email.trim().equals(serverUser.email.trim(), ignoreCase = true)

    private fun queueHealthSync() {
        if (!isLoggedIn || token.isNullOrBlank()) return
        healthDirty = true
        healthDirtyEmail = currentUser?.email
        saveData()
        repositoryScope.launch {
            healthSyncMutex.withLock {
                if (!isLoggedIn || token.isNullOrBlank()) return@withLock
                try {
                    apiRequest("health", "PUT", healthPayload())
                    clearHealthDirty()
                    syncError = null
                } catch (error: IOException) {
                    syncError = error.message ?: "Sinkronisasi data gagal."
                }
            }
        }
    }

    private fun clearHealthDirty() {
        healthDirty = false
        healthDirtyEmail = null
        prefs.edit().remove("health_dirty").remove("health_dirty_email").apply()
    }

    private class ApiException(message: String, val statusCode: Int) : IOException(message)

    private fun seedDiscussions() {
        val todayStr = dateFormat.format(Date())
        discussions = java.util.ArrayList(
            java.util.Arrays.asList(
                DiscussionPost(
                    id = "d101",
                    title = "Apakah wajar siklus haid berubah saat sedang stres atau banyak ujian?",
                    category = "Kesehatan",
                    authorName = "Siti Rahma",
                    authorEmail = "siti.rahma@example.com",
                    date = todayStr,
                    text = "Halo teman-teman, belakangan ini aku lagi banyak tugas dan ujian sekolah. Siklus haidku jadi mundur 5 hari dari biasanya. Apakah ada teman-teman yang pernah mengalami hal sama?",
                    answers = java.util.Arrays.asList(
                        DiscussionAnswer(
                            id = "a1",
                            authorName = "Dr. Anita (Konsultan)",
                            authorEmail = "anita.med@example.com",
                            date = todayStr,
                            text = "Halo Siti! Hal tersebut sangat wajar terjadi. Stres fisik maupun emosional memengaruhi hormon kortisol yang dapat menunda proses ovulasi. Usahakan tidur cukup dan kelola stres ya!"
                        ),
                        DiscussionAnswer(
                            id = "a2",
                            authorName = "Nadia P.",
                            authorEmail = "nadia.p@example.com",
                            date = todayStr,
                            text = "Aku juga pernah mengalami hal sama saat minggu ujian semester. Setelah rileks biasanya siklus akan kembali normal di bulan berikutnya."
                        )
                    )
                ),
                DiscussionPost(
                    id = "d102",
                    title = "Tips paling ampuh meredakan kram perut hari pertama tanpa obat",
                    category = "Tips",
                    authorName = "Clara V.",
                    authorEmail = "clara.v@example.com",
                    date = todayStr,
                    text = "Hai sahabat BloomCycle! Perutku selalu kram lumayan hebat di hari pertama haid. Metode alami apa yang paling ampuh buat teman-teman?",
                    answers = java.util.Arrays.asList(
                        DiscussionAnswer(
                            id = "a3",
                            authorName = "Fitri R.",
                            authorEmail = "fitri.r@example.com",
                            date = todayStr,
                            text = "Kompres botol berisi air hangat di bagian perut bawah sambil minum teh jahe hangat! Rasanya otot langsung rileks dan nyeri berkurang banyak."
                        )
                    )
                ),
                DiscussionPost(
                    id = "d103",
                    title = "Pengalaman pertama kali pakai pembalut kain vs disposable",
                    category = "Pengalaman",
                    authorName = "Dina Permata",
                    authorEmail = "dina.p@example.com",
                    date = todayStr,
                    text = "Ada yang pernah mencoba beralih ke pembalut kain yang ramah lingkungan? Boleh bagikan suka dukanya untuk pemula?",
                    answers = java.util.Collections.emptyList()
                )
            )
        )
        saveData()
    }

    private fun seedArticleComments() {
        val todayStr = dateFormat.format(Date())
        val map = java.util.HashMap<Int, java.util.ArrayList<ArticleComment>>()
        map[0] = java.util.ArrayList(
            java.util.Arrays.asList(
                ArticleComment(
                    id = "ac1",
                    articleId = 0,
                    name = "Dr. Anita (Tim Medis)",
                    email = "admin@bloomcycle.com",
                    role = "admin",
                    date = todayStr,
                    text = "Artikel ini telah ditinjau oleh tim konsultan medis BloomCycle. Selalu konsultasikan keluhan fisik yang mengganggu aktivitas ke dokter."
                ),
                ArticleComment(
                    id = "ac2",
                    articleId = 0,
                    name = "Maya S.",
                    email = "maya.s@example.com",
                    role = "user",
                    date = todayStr,
                    text = "Artikelnya sangat informatif! Sekarang aku paham kenapa 3 hari sebelum haid suka gampang capek."
                )
            )
        )
        map[1] = java.util.ArrayList(
            java.util.Arrays.asList(
                ArticleComment(
                    id = "ac3",
                    articleId = 1,
                    name = "Ayu Kartika",
                    email = "ayu.k@example.com",
                    role = "user",
                    date = todayStr,
                    text = "Penjelasan 4 fase menstruasinya ringkas dan mudah dipahami."
                ),
                ArticleComment(
                    id = "ac4",
                    articleId = 1,
                    name = "Admin BloomCycle",
                    email = "admin.support@bloomcycle.com",
                    role = "admin",
                    date = todayStr,
                    text = "Catat siklusmu secara rutin di menu Kalender untuk mendapatkan analisis perkiraan fase yang akurat."
                )
            )
        )
        map[2] = java.util.ArrayList(
            java.util.Arrays.asList(
                ArticleComment(
                    id = "ac5",
                    articleId = 2,
                    name = "Lia Rahma",
                    email = "lia.r@example.com",
                    role = "user",
                    date = todayStr,
                    text = "Kompres air hangat memang terbukti paling ampuh buat kram perut!"
                )
            )
        )
        articleComments = map
        saveData()
    }

    suspend fun register(name: String, age: Int, email: String, password: String): String? {
        if (name.trim().isEmpty()) return "Nama wajib diisi."
        if (name.trim().length > 120) return "Nama tidak boleh lebih dari 120 karakter."
        if (age !in 9..120) return "Masukkan umur antara 9 dan 120 tahun."
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Masukkan email yang valid."
        if (password.toByteArray(Charsets.UTF_8).size !in 10..72) {
            return "Kata sandi harus terdiri dari 10 sampai 72 byte."
        }
        val legacyUser = currentUser
        return try {
            val registration = JsonObject().apply {
                addProperty("email", email.trim().lowercase(Locale.ROOT))
                addProperty("password", password)
                add("data", JsonObject().apply {
                    addProperty("name", name.trim())
                    addProperty("age", age)
                })
            }
            val authResponse = supabaseAuthRequest("/auth/v1/signup", registration)
            val issuedToken = authResponse.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
            if (issuedToken.isNullOrBlank()) {
                return "Akun dibuat. Periksa email untuk konfirmasi, lalu masuk."
            }
            saveSupabaseTokens(authResponse)
            signIn(JsonObject().apply { addProperty("token", issuedToken) }, legacyUser)
        } catch (error: IOException) {
            error.message ?: "Pendaftaran gagal. Coba lagi."
        }
    }

    suspend fun login(email: String, password: String): String? {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return "Masukkan email yang valid."
        }
        if (password.isBlank()) return "Masukkan kata sandi."
        val legacyUser = currentUser
        return try {
            val authResponse = supabaseAuthRequest(
                "/auth/v1/token?grant_type=password",
                JsonObject().apply {
                    addProperty("email", email.trim().lowercase(Locale.ROOT))
                    addProperty("password", password)
                }
            )
            val issuedToken = authResponse.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
                ?: throw IOException("Respons login Supabase tidak menyertakan sesi.")
            saveSupabaseTokens(authResponse)
            signIn(JsonObject().apply { addProperty("token", issuedToken) }, legacyUser)
        } catch (error: IOException) {
            error.message ?: "Login gagal. Coba lagi."
        }
    }

    fun setSeenIntro() {
        hasSeenIntro = true
        saveData()
    }

    fun logout() {
        val activeToken = token
        if (!activeToken.isNullOrBlank()) {
            repositoryScope.launch {
                try {
                    supabaseAuthRequest("/auth/v1/logout", JsonObject(), activeToken)
                } catch (error: IOException) {
                    syncError = error.message ?: "Logout server gagal."
                }
            }
        }
        token = null
        refreshToken = null
        isLoggedIn = false
        saveData()
    }

    fun toggleRemind() {
        remind = !remind
        saveData()
        queueHealthSync()
    }

    fun setThemePreference(newTheme: String) {
        theme = newTheme
        saveData()
        queueHealthSync()
    }

    fun addPeriodStart(dateStr: String) {
        var existingIdx = -1
        for (i in 0 until periods.size) {
            val p = periods[i]
            if (p.startDate <= dateStr && dateStr <= (p.endDate ?: p.startDate)) {
                existingIdx = i
                break
            }
        }
        if (existingIdx != -1) {
            val existing = periods[existingIdx]
            periods[existingIdx] = existing.copy(startDate = dateStr)
        } else {
            periods.add(PeriodRecord(startDate = dateStr))
        }
        java.util.Collections.sort(periods) { a, b -> a.startDate.compareTo(b.startDate) }
        saveData()
        queueHealthSync()
    }

    fun addPeriodEnd(dateStr: String) {
        var lastIdx = -1
        for (i in 0 until periods.size) {
            if (periods[i].startDate <= dateStr) {
                lastIdx = i
            }
        }
        if (lastIdx != -1) {
            periods[lastIdx] = periods[lastIdx].copy(endDate = dateStr)
            saveData()
            queueHealthSync()
        }
    }

    fun removePeriod(dateStr: String) {
        val toRemove = java.util.ArrayList<PeriodRecord>()
        for (p in periods) {
            if (p.startDate <= dateStr && dateStr <= (p.endDate ?: p.startDate)) {
                toRemove.add(p)
            }
        }
        periods.removeAll(toRemove)
        saveData()
        queueHealthSync()
    }

    fun saveNote(note: DailyNote) {
        val toRemove = java.util.ArrayList<DailyNote>()
        for (n in notes) {
            if (n.date == note.date) toRemove.add(n)
        }
        notes.removeAll(toRemove)
        notes.add(0, note)
        java.util.Collections.sort(notes) { a, b -> b.date.compareTo(a.date) }
        saveData()
        queueHealthSync()
    }

    fun deleteNote(id: String) {
        val toRemove = java.util.ArrayList<DailyNote>()
        for (n in notes) {
            if (n.id == id) toRemove.add(n)
        }
        notes.removeAll(toRemove)
        saveData()
        queueHealthSync()
    }

    fun addDiscussion(title: String, category: String, text: String) {
        val user = currentUser ?: return
        repositoryScope.launch {
            try {
                val response = apiRequest("discussions", "POST", JsonObject().apply {
                    addProperty("title", title.trim())
                    addProperty("category", category)
                    addProperty("body", text.trim())
                })
                discussions.add(
                    0,
                    DiscussionPost(
                        id = response.get("id")?.asString ?: throw IOException("Server tidak mengembalikan ID diskusi."),
                        title = title.trim(),
                        category = category,
                        authorName = user.name,
                        authorEmail = "",
                        date = dateFormat.format(Date()),
                        text = text.trim()
                    )
                )
                saveData()
                syncError = null
            } catch (error: IOException) {
                syncError = error.message ?: "Diskusi gagal dikirim."
            }
        }
    }

    fun addDiscussionAnswer(discussionId: String, text: String) {
        val user = currentUser ?: return
        val post = discussions.find { it.id == discussionId } ?: return
        repositoryScope.launch {
            try {
                val response = apiRequest(
                    "discussions/$discussionId/answers",
                    "POST",
                    JsonObject().apply { addProperty("body", text.trim()) }
                )
                val index = discussions.indexOfFirst { it.id == discussionId }
                if (index >= 0) {
                    val answers = java.util.ArrayList(discussions[index].answers)
                    answers.add(
                        DiscussionAnswer(
                            id = response.get("id")?.asString ?: throw IOException("Server tidak mengembalikan ID jawaban."),
                            authorName = user.name,
                            authorEmail = "",
                            date = dateFormat.format(Date()),
                            text = text.trim()
                        )
                    )
                    discussions[index] = post.copy(answers = answers)
                    saveData()
                }
                syncError = null
            } catch (error: IOException) {
                syncError = error.message ?: "Jawaban gagal dikirim."
            }
        }
    }

    fun addComment(articleId: Int, text: String) {
        val user = currentUser ?: return
        repositoryScope.launch {
            try {
                val response = apiRequest("article-comments", "POST", JsonObject().apply {
                    addProperty("articleId", articleId)
                    addProperty("body", text.trim())
                })
                articleComments.getOrPut(articleId) { java.util.ArrayList() }.add(
                    ArticleComment(
                        id = response.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        articleId = articleId,
                        name = user.name,
                        email = "",
                        role = user.role,
                        date = dateFormat.format(Date()),
                        text = text.trim()
                    )
                )
                saveData()
                syncError = null
            } catch (error: IOException) {
                syncError = error.message ?: "Komentar gagal dikirim."
            }
        }
    }

    fun addReport(targetType: String, targetTitle: String, targetId: String?, category: String, description: String) {
        repositoryScope.launch {
            try {
                apiRequest("reports", "POST", JsonObject().apply {
                    addProperty("targetType", if (targetType == "general") "other" else targetType)
                    targetId?.let { addProperty("targetId", it) }
                    addProperty("targetTitle", targetTitle)
                    addProperty("category", category)
                    addProperty("description", description)
                })
                if (currentUser?.role == "admin") refreshAdminData()
                syncError = null
            } catch (error: IOException) {
                syncError = error.message ?: "Laporan gagal dikirim."
            }
        }
    }

    fun refreshAdminData() {
        if (currentUser?.role != "admin") return
        repositoryScope.launch {
            try {
                val reportResponse = apiRequest("admin/reports")
                val discussionResponse = apiRequest("admin/discussions")
                reports = parseReports(reportResponse.getAsJsonArray("reports"))
                discussions = parseDiscussions(discussionResponse.getAsJsonArray("discussions"))
                saveData()
                syncError = null
            } catch (error: IOException) {
                syncError = error.message ?: "Data administrator gagal dimuat."
            }
        }
    }

    private fun parseReports(items: JsonArray?): java.util.ArrayList<Report> =
        java.util.ArrayList<Report>().apply {
            items?.forEach { element ->
                if (!element.isJsonObject) return@forEach
                val item = element.asJsonObject
                add(
                    Report(
                        id = item.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        targetType = item.get("targetType")?.asString.orEmpty(),
                        targetTitle = item.get("targetTitle")?.asString.orEmpty(),
                        targetId = item.get("targetId")?.takeUnless { it.isJsonNull }?.asString,
                        category = item.get("category")?.asString.orEmpty(),
                        description = item.get("description")?.asString.orEmpty(),
                        date = item.get("createdAt")?.asString.orEmpty(),
                        reporterEmail = item.get("reporterEmail")?.asString.orEmpty()
                    )
                )
            }
        }

    fun wipeData() {
        periods.clear()
        notes.clear()
        reports.clear()
        discussions.clear()
        articleComments.clear()
        seedDiscussions()
        seedArticleComments()
        saveData()
        queueHealthSync()
    }

    fun isPeriodDay(dateStr: String): Boolean {
        for (p in periods) {
            if (p.startDate <= dateStr && dateStr <= (p.endDate ?: p.startDate)) {
                return true
            }
        }
        return false
    }

    fun calculateStats(): CycleStats {
        val sorted = java.util.ArrayList(periods)
        java.util.Collections.sort(sorted) { a, b -> a.startDate.compareTo(b.startDate) }
        if (sorted.isEmpty()) {
            return CycleStats(null, null, null, true, null)
        }

        val cycleLengths = java.util.ArrayList<Int>()
        for (i in 1 until sorted.size) {
            val diff = diffDays(sorted[i - 1].startDate, sorted[i].startDate)
            if (diff > 0) cycleLengths.add(diff)
        }

        val durations = java.util.ArrayList<Int>()
        for (p in sorted) {
            val end = p.endDate
            if (end != null) {
                durations.add(diffDays(p.startDate, end) + 1)
            }
        }

        var sumCycle = 0
        for (c in cycleLengths) sumCycle += c
        val avgCycle = if (cycleLengths.isNotEmpty()) sumCycle / cycleLengths.size else null

        var sumDur = 0
        for (d in durations) sumDur += d
        val avgDuration = if (durations.isNotEmpty()) sumDur / durations.size else null

        val isEst = avgCycle == null
        val lastStart = sorted[sorted.size - 1].startDate
        val nextStart = addDays(lastStart, avgCycle ?: 28)

        var variation: Int? = null
        if (cycleLengths.size > 1) {
            var minVal = cycleLengths[0]
            var maxVal = cycleLengths[0]
            for (c in cycleLengths) {
                if (c < minVal) minVal = c
                if (c > maxVal) maxVal = c
            }
            variation = maxVal - minVal
        }

        return CycleStats(
            averageCycleLength = avgCycle,
            averagePeriodDuration = avgDuration,
            nextPeriodStart = nextStart,
            isEstimated = isEst,
            cycleVariation = variation
        )
    }

    fun diffDays(startStr: String, endStr: String): Int {
        val start = dateFormat.parse(startStr) ?: return 0
        val end = dateFormat.parse(endStr) ?: return 0
        val diff = end.time - start.time
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun addDays(dateStr: String, days: Int): String {
        val date = dateFormat.parse(dateStr) ?: return dateStr
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_MONTH, days)
        return dateFormat.format(cal.time)
    }
}
