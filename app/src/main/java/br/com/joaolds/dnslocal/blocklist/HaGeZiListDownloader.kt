package br.com.joaolds.dnslocal.blocklist

import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** Manual HTTPS refresh from the official HaGeZi Multi PRO Mini distribution. */
internal class HaGeZiListDownloader {
    companion object {
        const val SOURCE_URL =
            "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard/pro.mini-onlydomains.txt"
    }

    fun refresh(store: HaGeZiListStore): DomainBlocklist {
        checkNotCancelled()
        val connection = URL(SOURCE_URL).openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false // Never switch to an unverified host.
        connection.setRequestProperty("Accept-Encoding", "identity")
        try {
            require(connection.responseCode == HttpsURLConnection.HTTP_OK) {
                "HaGeZi download failed"
            }
            require(connection.contentLengthLong <= HaGeZiListStore.MAX_BYTES) {
                "HaGeZi list exceeds maximum size"
            }
            checkNotCancelled()
            return store.update { connection.inputStream }
        } finally {
            connection.disconnect()
        }
    }

    private fun checkNotCancelled() {
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("HaGeZi download cancelled")
        }
    }
}
