package br.com.joaolds.dnslocal.dns

fun responseIntegritySmoke() {
    val query = byteArrayOf(
        0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0,
        0x03, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
        0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
        'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
        0x03, 'o'.code.toByte(), 'r'.code.toByte(), 'g'.code.toByte(), 0, 0, 1, 0, 1
    )
    val ok = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 2 }
    check(DnsResponseValidator.matches(query, ok))
    check(DnsResponseValidator.matches(query, ok.copyOf().also { it[13] = 'W'.code.toByte() }))
    check(!DnsResponseValidator.matches(query, query))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[1] = 0x12 }))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[13] = 'x'.code.toByte() }))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[30] = 28 }))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[32] = 3 }))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[2] = 0x89.toByte() }))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[5] = 0 }))
    check(!DnsResponseValidator.matches(query, ok.copyOf().also { it[12] = 0xc0.toByte() }))
    check(!DnsResponseValidator.matches(query.copyOf(12), ok))
    check(!DnsResponseValidator.matches(query, ok.copyOf(16)))
    val withEdns = query.copyOf().also { it[11] = 1 } +
        byteArrayOf(0, 0, 41, 16, 0, 0, 0, 0, 0, 0, 0)
    check(DnsResponseValidator.matches(withEdns, ok))
    println("OK: DNS upstream question integrity checks passed")
}
