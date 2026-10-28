package br.com.joaolds.dnslocal.blocklist

import java.io.StringReader

/** Synthetic test data, not a copy of the third-party HaGeZi list. */
fun normalizationSmoke() {
    val names = listOf(
        "ads.example.com", "ADS.EXAMPLE.COM", "www.ads.example.com.",
        "bücher.example", "xn--bcher-kva.example", "WWW.BÜCHER.EXAMPLE.",
        "foo..example", "invalid_domain.example", "-bad.example", "bad-.example",
        "xn--.example", "ads.example.com.evil", "notads.example.com", "example.com"
    )
    val list = DomainBlocklist.parse(StringReader("ads.example.com\nBÜCHER.example\n"))
    val expected = listOf(true, true, true, true, true, true,
        false, false, false, false, false, false, false, false)
    check(names.map(list::blocks) == expected) { names.map(list::blocks).toString() }

    // Large enough to exercise a realistically sized sorted domain index.
    val fixture = buildString {
        for (n in 0 until 50000) append("example-$n.ads.test\n")
    }
    val index = DomainBlocklist.parse(StringReader(fixture))
    check(index.size == 50000)
    val probes = (0 until 10000).map { "sub.example-${it % 50000}.ads.test" }
    check(probes.all { index.blocks(it) })
    check(!index.blocks("example-10000.ads.test.evil"))
    check(!index.blocks("example-50000.ads.test"))
    val cancellation = java.util.concurrent.atomic.AtomicReference<Throwable?>()
    val parserThread = Thread {
        Thread.currentThread().interrupt()
        try {
            DomainBlocklist.parse(StringReader(fixture))
        } catch (failure: Throwable) {
            cancellation.set(failure)
        }
    }
    parserThread.start()
    parserThread.join(5000)
    check(!parserThread.isAlive)
    check(cancellation.get() is java.util.concurrent.CancellationException)
    println("OK: ASCII, IDN, invalid-domain, cancellation and 50k-entry index checks")
}
