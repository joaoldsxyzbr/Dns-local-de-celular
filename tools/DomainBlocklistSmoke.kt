package br.com.joaolds.dnslocal.blocklist

import java.io.StringReader

fun main() {
    val list = DomainBlocklist.parse(StringReader("""
        # HaGeZi test fixture, not downloaded
        ads.example.com
        trackers.example.org
        ads.example.com
        BÜCHER.example
        # ignore comment
    """.trimIndent()))
    check(list.size == 3)
    check(list.blocks("ads.example.com"))
    check(list.blocks("www.ads.example.com"))
    check(list.blocks("WWW.ADS.EXAMPLE.COM."))
    check(list.blocks("bücher.example"))
    check(list.blocks("xn--bcher-kva.example"))
    check(!list.blocks("notads.example.com"))
    check(!list.blocks("example.com"))
    check(!list.blocks("example.org"))
    check(!list.blocks("invalid_domain.example"))
    check(!list.blocks(""))
    val invalid = listOf("<html>oops</html>", "*.ads.example.com", "0.0.0.0 ads.example.com", "", "bad_domain.example.com")
    invalid.forEach {
        check(runCatching { DomainBlocklist.parse(StringReader(it)) }.isFailure) { it }
    }
    println("OK: HaGeZi domain index smoke checks passed")
}
