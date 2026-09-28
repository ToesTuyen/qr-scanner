package com.goldenboat.keyvault

import android.content.Context
import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * KeyVault client — lấy API key của app từ kho mã hoá tập trung.
 * Đổi key được KHÔNG cần update app (rotate ở server, client tự nhận qua CDN).
 *
 * LAZY: bình thường dùng key nhúng/cache, KHÔNG chạm mạng. Chỉ fetch CDN khi provider
 * trả 401/403 (tức đúng lúc key vừa bị đổi). => traffic ≈ 0 khi key không đổi.
 *
 * Bất biến (đừng phá khi sửa):
 *   - getKey() không bao giờ block, luôn trả ngay từ RAM.
 *   - refresh() không bao giờ ném; CDN chết -> chạy bằng cache, im lặng.
 *   - Giải mã fail -> giữ config cũ, KHÔNG dùng dữ liệu chưa xác thực.
 *   - Chọn blob có version cao nhất giữa (defaults nhúng, cache đĩa, CDN).
 */
class KeyVault private constructor(
    appContext: Context,
    private val appId: String,
    private val cdnBase: String,
    private val contentKey: ByteArray,   // 32B, nhúng lúc build (BuildConfig.KV_CONTENT_KEY)
    embeddedDefaultsJson: String,        // res/raw/keyvault_defaults.json
) {
    private val sodium = LazySodiumAndroid(SodiumAndroid())
    private val prefs = appContext.getSharedPreferences("keyvault_$appId", Context.MODE_PRIVATE)
    private val current = AtomicReference<Config>()

    init {
        val defaults = parse(embeddedDefaultsJson)
            ?: error("KeyVault: defaults không hợp lệ — sai content key hoặc file res/raw?")
        val cached = prefs.getString(KEY_BLOB, null)?.let { parse(it) }
        current.set(if (cached != null && cached.version > defaults.version) cached else defaults)
    }

    data class Config(val version: Int, val keys: Map<String, String>)

    /**
     * Lấy key. Trả ngay từ RAM, không I/O. Tên mặc định là "default".
     * null nếu chưa có key (hiếm — chỉ khi defaults rỗng và chưa fetch được).
     */
    fun getKey(name: String = "default"): String? = current.get().keys[name]

    fun version(): Int = current.get().version

    /**
     * Gọi khi provider trả 401/403 (key bị từ chối) -> xoá cache + fetch key mới.
     * Trả true nếu đã lấy được config mới hơn (nên retry request).
     */
    suspend fun onKeyRejected(): Boolean {
        prefs.edit().remove(KEY_BLOB).apply()
        return refresh()
    }

    /** Fetch blob mới từ CDN. Chỉ nhận nếu version cao hơn. KHÔNG bao giờ ném. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = httpGet("$cdnBase/config/$appId.json") ?: return@withContext false
            val incoming = parse(body) ?: return@withContext false
            if (incoming.version > current.get().version) {
                current.set(incoming)
                prefs.edit().putString(KEY_BLOB, body).apply()
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /** Refresh nếu lần cuối đã quá [maxAgeMillis]. Tuỳ chọn — thường không cần gọi. */
    suspend fun refreshIfStale(maxAgeMillis: Long): Boolean {
        val last = prefs.getLong(KEY_LAST, 0L)
        val now = System.currentTimeMillis()
        if (now - last < maxAgeMillis) return false
        prefs.edit().putLong(KEY_LAST, now).apply()
        return refresh()
    }

    /** Giải mã secretbox (XSalsa20-Poly1305) -> parse. null nếu fail (giữ config cũ). */
    private fun parse(json: String): Config? = try {
        val e = JSONObject(json)
        val nonce = b64d(e.getString("nonce"))
        val ct = b64d(e.getString("ct"))                 // MAC(16) || ciphertext
        if (ct.size <= SecretBox.MACBYTES) null
        else {
            val out = ByteArray(ct.size - SecretBox.MACBYTES)
            if (!sodium.cryptoSecretBoxOpenEasy(out, ct, ct.size.toLong(), nonce, contentKey)) null
            else {
                val k = JSONObject(String(out, Charsets.UTF_8)).getJSONObject("keys")
                Config(e.getInt("v"), k.keys().asSequence().associateWith { k.getString(it) })
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            // Cloudflare chặn request thiếu User-Agent (403). UA prefix Mozilla/5.0 để qua bot-check.
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) KeyVault/1.0")
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun b64d(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

    companion object {
        private const val KEY_BLOB = "blob"
        private const val KEY_LAST = "last_fetch"

        /**
         * @param appId       lấy từ ai-key.txt (vd "fossil-identifier")
         * @param cdnBase     lấy từ ai-key.txt (vd "https://media-keyvault-config.goldenboat.us")
         * @param contentKeyB64  BuildConfig.KV_CONTENT_KEY (nhúng qua local.properties, KHÔNG commit)
         * @param embeddedDefaultsJson  nội dung res/raw/keyvault_defaults.json
         */
        fun create(
            context: Context,
            appId: String,
            cdnBase: String,
            contentKeyB64: String,
            embeddedDefaultsJson: String,
        ) = KeyVault(
            context.applicationContext, appId, cdnBase,
            Base64.decode(contentKeyB64, Base64.DEFAULT), embeddedDefaultsJson)
    }
}
