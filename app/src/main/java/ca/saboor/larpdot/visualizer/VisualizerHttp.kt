package ca.saboor.larpdot.visualizer

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal object VisualizerHttp {
    private const val DEFAULT_USER_AGENT = "LarpDot/1.0 (Android)"

    fun fetchText(
        urlString: String,
        connectTimeoutMs: Int = 5_000,
        readTimeoutMs: Int = 5_000,
        userAgent: String = DEFAULT_USER_AGENT,
    ): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.instanceFollowRedirects = true
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadToFile(
        urlString: String,
        destination: File,
        connectTimeoutMs: Int = 6_000,
        readTimeoutMs: Int = 10_000,
        userAgent: String = DEFAULT_USER_AGENT,
    ) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.instanceFollowRedirects = true

        val partial = File(destination.parentFile, "${destination.name}.partial")
        try {
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(destination)) error("Could not save downloaded file")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}
