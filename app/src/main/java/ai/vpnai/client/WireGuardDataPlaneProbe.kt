package ai.vpnai.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/**
 * End-to-end data-plane check bound to Android's active VPN Network.
 * It refuses to fall back to the physical/default network.
 */
internal class WireGuardDataPlaneProbe(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun verify(): Boolean {
        val network: Network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false

        val connection = network.openConnection(URL(PROBE_URL)) as? HttpsURLConnection ?: return false
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache, no-store")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.responseCode == EXPECTED_STATUS
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        internal const val EXPECTED_STATUS = 204
        internal fun acceptsStatus(statusCode: Int): Boolean = statusCode == EXPECTED_STATUS
        private const val PROBE_URL = "https://connectivitycheck.gstatic.com/generate_204"
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val READ_TIMEOUT_MS = 2500
    }
}
