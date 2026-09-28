package com.goldenboat.keyvault

import android.content.Context

/**
 * Điểm truy cập DUY NHẤT tới KeyVault cho MỌI app — copy chung cặp với [KeyVault].
 * Thay Firebase Remote Config làm nơi lấy API key.
 *
 * Key mã hoá trên CDN (R2 tĩnh), content-key nhúng app. Đổi key = sửa ở admin, client tự nhận —
 * KHÔNG cần update app. KHÔNG server-compute, KHÔNG GCP: chỉ CDN tĩnh + content-key nhúng.
 *
 * Cách dùng (kiến trúc "1 base"):
 *   - Base Application (lớp chung mọi app kế thừa) gọi [init] 1 lần trong onCreate, bằng config app override.
 *   - Nơi cần key gọi [getKey] — trả NGAY từ RAM, không block, offline-first (defaults nhúng).
 *   - Khi API trả 401/403 gọi [onKeyRejected] để nhận key vừa rotate rồi retry.
 * Xem INTEGRATION_LOG.md cho snippet base/app đầy đủ.
 */
object KeyVaultProvider {
    @Volatile private var vault: KeyVault? = null

    /**
     * Gọi 1 lần trong Application.onCreate. Thiếu bất kỳ cấu hình nào -> bỏ qua lặng lẽ
     * (app đó chưa bật KeyVault, giữ nguyên hành vi cũ). runCatching: cấu hình sai KHÔNG crash app start.
     */
    fun init(context: Context, appId: String, cdnBase: String, contentKeyB64: String, defaultsJson: String) {
        if (vault != null) return
        if (appId.isBlank() || cdnBase.isBlank() || contentKeyB64.isBlank() || defaultsJson.isBlank()) return
        vault = runCatching {
            KeyVault.create(context.applicationContext, appId, cdnBase, contentKeyB64, defaultsJson)
        }.getOrNull()
    }

    fun isReady(): Boolean = vault != null

    /** Key hiện tại (RAM, không I/O). null nếu chưa init hoặc thiếu key. */
    fun getKey(name: String = "default"): String? = vault?.getKey(name)

    /**
     * Gọi khi API trả 401/403 (key bị từ chối) -> fetch blob mới từ CDN.
     * true nếu đã nhận config mới hơn (nên retry request). false nếu chưa init / không có gì mới.
     */
    suspend fun onKeyRejected(): Boolean = vault?.onKeyRejected() ?: false
}
