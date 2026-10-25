package com.example.myapplication.domain.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 发布清单：由 CI 在每次构建时随 APK 一起上传到 GitHub Release。
 * 应用只读 latest 这一份，不需要 GitHub API（避免限流与 api.github.com 不可达）。
 */
@Serializable
data class ReleaseManifest(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val apkAsset: String = "app-release-signed.apk",
    val shaAsset: String = "SHA256SUMS.txt",
    val notes: String? = null
)

data class InstalledVersion(val versionCode: Long, val versionName: String)

sealed class UpdateCheckResult {
    data class UpToDate(val current: InstalledVersion) : UpdateCheckResult()
    data class Available(val current: InstalledVersion, val remote: ReleaseManifest) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

/**
 * 应用内更新：检查 → 下载 → 校验 → 交给系统安装。
 *
 * 校验分两道：
 *  1) SHA256 对版本清单里的 SHA256SUMS.txt，防传输损坏；
 *  2) 签名一致性，防被替换成他人签名包（不一致直接拒绝安装）。
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val json: Json
) {

    companion object {
        private const val REPO = "jimieguang/ComfyUI-GO-Mobile-app"

        /** GitHub Release 的 latest 资源直链：无需 API，自动跟随 302 到 CDN */
        private const val BASE_URL = "https://github.com/$REPO/releases/latest/download"

        const val MANIFEST_URL = "$BASE_URL/version.json"
    }

    /** 下载大文件时不能沿用小超时，单独派生一个不设读超时的客户端 */
    private val downloadClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun installedVersion(): InstalledVersion {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return InstalledVersion(pi.longVersionCode, pi.versionName ?: "")
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val text = httpGet(MANIFEST_URL)
            val remote = json.decodeFromString<ReleaseManifest>(text)
            val current = installedVersion()
            if (remote.versionCode > current.versionCode) {
                UpdateCheckResult.Available(current, remote)
            } else {
                UpdateCheckResult.UpToDate(current)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 下载并校验 APK。
     * @param onProgress 0f..1f，contentLength 不可知时不回调
     */
    suspend fun downloadAndVerify(
        manifest: ReleaseManifest,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var target: File? = null
        try {
            val expectedSha = runCatching { fetchExpectedSha(manifest.shaAsset) }.getOrNull()
            val url = "$BASE_URL/${manifest.apkAsset}"
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "app-update.apk")
            if (out.exists()) out.delete()
            target = out

            val response = downloadClient.newCall(Request.Builder().url(url).build()).execute()
            response.use { r ->
                if (!r.isSuccessful) throw IOException("下载失败：HTTP ${r.code}")
                val body = r.body ?: throw IOException("响应体为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(out).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var sum = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            fos.write(buf, 0, read)
                            sum += read
                            if (total > 0) onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            if (out.length() <= 0L) throw IOException("下载内容为空")

            val actualSha = sha256Of(out)
            if (expectedSha != null && !expectedSha.equals(actualSha, ignoreCase = true)) {
                out.delete()
                return@withContext Result.failure(
                    IOException("校验失败：期望 ${expectedSha.take(12)}… 实际 ${actualSha.take(12)}…")
                )
            }

            if (!sameSignatureAsInstalled(out)) {
                out.delete()
                return@withContext Result.failure(IOException("签名与已安装版本不一致，已拒绝安装"))
            }

            Result.success(out)
        } catch (e: Exception) {
            runCatching { target?.delete() }
            Result.failure(e)
        }
    }

    /** 从 SHA256SUMS.txt 里取目标 APK 的哈希 */
    private fun fetchExpectedSha(shaAsset: String): String? {
        val text = httpGet("$BASE_URL/$shaAsset")
        val fileName = "app-release-signed.apk"
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                // 格式：<hash>  <name>
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2 && parts.last().substringAfterLast('/') == fileName &&
                    parts.first().length == 64
                ) parts.first() else null
            }
            .firstOrNull()
    }

    private fun httpGet(url: String): String {
        val response = downloadClient.newCall(Request.Builder().url(url).build()).execute()
        response.use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("响应体为空")
        }
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun certDigests(info: PackageInfo?): Set<String> {
        val signingInfo = info?.signingInfo ?: return emptySet()
        @Suppress("DEPRECATION")
        val signers = signingInfo.apkContentsSigners ?: return emptySet()
        return signers.mapTo(mutableSetOf()) { sig ->
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    /** 下载包的证书必须与当前安装包完全一致，否则将来覆盖安装也会失败，直接拒绝更安全 */
    private fun sameSignatureAsInstalled(apk: File): Boolean {
        val pm = context.packageManager
        val installed = certDigests(
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        )
        val archive = certDigests(
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        )
        return installed.isNotEmpty() && installed == archive
    }
}
