package br.com.joaolds.dnslocal.dns

import br.com.joaolds.dnslocal.blocklist.DomainBlocklist
import java.io.StringReader

fun main() {
    val domains = DomainBlocklist.parse(StringReader("ads.example.com\ntracker.test.org\n"))
    fun query(domain: String, qtype: Int = 1, withOpt: Boolean = false): ByteArray {
        val labels = domain.split('.').flatMap { label ->
            listOf(label.length.toByte()) + label.map { it.code.toByte() }
        }.toByteArray()
        val header = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, if (withOpt) 1 else 0)
        val opt = if (withOpt) byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0) else byteArrayOf()
        return header + labels + byteArrayOf(0, 0, qtype.toByte(), 0, 1) + opt
    }
    var forwarded = 0
    val filter = DnsDomainFilter(domains) { forwarded++; it }
    val request = query("Sub.ADS.example.com", withOpt = true)
    val blocked = requireNotNull(filter.resolve(request))
    check(blocked.size == request.size - 11) // EDNS OPT is not mirrored.
    check((blocked[3].toInt() and 15) == 3)
    check((blocked[2].toInt() and 128) != 0)
    check(blocked[4] == 0.toByte() && blocked[5] == 1.toByte())
    check(blocked[11] == 0.toByte())
    check(blocked[0] == request[0] && blocked[1] == request[1])
    check(forwarded == 0) // Blocked traffic must never reach upstream/backup.
    check((requireNotNull(filter.resolve(query("ads.example.com", 28)))[3].toInt() and 15) == 3)
    check(forwarded == 0)
    check(filter.resolve(query("notads.example.com")) != null)
    check(forwarded == 1)
    val disabled = DnsDomainFilter(null) { forwarded++; it }
    check(disabled.resolve(query("ads.example.com")) != null)
    check(forwarded == 2)
    val malformed = query("ads.example.com").also { it[12] = 0xc0.toByte() }
    check(filter.resolve(malformed) != null)
    check(forwarded == 3)
    println("OK: blocklist DNS interception smoke checks passed")
}
