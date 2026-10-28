package br.com.joaolds.dnslocal.blocklist

import java.io.Reader
import java.net.IDN
import java.util.Locale
import java.util.concurrent.CancellationException

/** Immutable sorted domain index. No polling, query logging or network access. */
internal class DomainBlocklist private constructor(private val domains: Array<String>) {
    val size: Int get() = domains.size

    fun blocks(domain: String): Boolean {
        val canonical = normalize(domain) ?: return false
        var suffix = canonical
        while (true) {
            if (domains.binarySearch(suffix) >= 0) return true
            val dot = suffix.indexOf('.')
            if (dot < 0) return false
            suffix = suffix.substring(dot + 1)
        }
    }

    companion object {
        private const val MAX_LINES = 250_000
        private const val MAX_CHARACTERS = 16_000_000

        /** HaGeZi onlydomains format: one domain per line and # comments. */
        fun parse(reader: Reader): DomainBlocklist {
            val entries = ArrayList<String>()
            var characters = 0
            var lineNumber = 0
            reader.buffered().use { input ->
                input.forEachLine { line ->
                    // Stop one-time list parsing after the VPN session is cancelled.
                    if ((lineNumber++ and 255) == 0 && Thread.currentThread().isInterrupted) {
                        throw CancellationException("Blocklist parsing cancelled")
                    }
                    characters += line.length + 1
                    require(characters <= MAX_CHARACTERS) { "Blocklist exceeds size limit" }
                    val value = line.trim()
                    if (value.isEmpty() || value.startsWith('#')) return@forEachLine
                    val domain = normalize(value)
                        ?: throw IllegalArgumentException("Invalid blocklist domain")
                    entries.add(domain)
                    require(entries.size <= MAX_LINES) { "Too many blocklist domains" }
                }
            }
            require(entries.isNotEmpty()) { "Empty blocklist" }
            entries.sort()
            var unique = 0
            for (entry in entries) {
                if (unique == 0 || entry != entries[unique - 1]) {
                    entries[unique++] = entry
                }
            }
            return DomainBlocklist(entries.subList(0, unique).toTypedArray())
        }

        private fun normalize(raw: String): String? {
            val input = raw.trim().removeSuffix(".")
            if (input.isEmpty() || input.length > 253 || !input.contains('.')) return null
            // The common case for HaGeZi and DNS questions is ASCII.
            // Avoid the cost of IDN conversion for ASCII names, while
            // retaining strict IDN checks for Unicode and punycode labels.
            val ascii = if (input.all { it.code < 128 } &&
                !input.contains("xn--", ignoreCase = true)) {
                input.lowercase(Locale.ROOT)
            } else try {
                IDN.toASCII(input, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (ascii.length > 253) return null
            val labels = ascii.split('.')
            if (labels.size < 2 || labels.last().all { it.isDigit() }) return null
            return ascii.takeIf {
                labels.all { label -> label.length in 1..63 &&
                    label.first() != '-' && label.last() != '-' &&
                    label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } }
            }
        }
    }
}
