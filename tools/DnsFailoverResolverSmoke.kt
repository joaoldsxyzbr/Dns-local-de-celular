package br.com.joaolds.dnslocal.dns

import java.net.Inet4Address
import java.net.InetAddress

fun failoverSmoke() {
    val a = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)) as Inet4Address
    val b = InetAddress.getByAddress(byteArrayOf(9, 9, 9, 9)) as Inet4Address
    val request = byteArrayOf(
        0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        0x01, 'a'.code.toByte(), 0, 0, 1, 0, 1
    )
    val valid = requireNotNull(DnsPacketCodec.servfail(request))
    val calls = mutableListOf<Inet4Address>()

    var resolver = DnsFailoverResolver { _, ip -> calls.add(ip); valid }
    check(resolver.resolve(request, a, b, true)?.contentEquals(valid) == true)
    check(calls == listOf(a))

    calls.clear()
    resolver = DnsFailoverResolver { _, ip -> calls.add(ip); if (ip == a) null else valid }
    check(resolver.resolve(request, a, b, false) == null)
    check(calls == listOf(a)) // Secondary OFF: zero backup queries.

    calls.clear()
    check(resolver.resolve(request, a, b, true)?.contentEquals(valid) == true)
    check(calls == listOf(a, b)) // Secondary ON: fallback only on failure.

    calls.clear()
    check(resolver.resolve(request, a, null, true) == null)
    check(calls == listOf(a))

    calls.clear()
    check(resolver.resolve(request, a, a, true) == null)
    check(calls == listOf(a))

    calls.clear()
    val wrongId = valid.copyOf().also { it[0] = 0x32 }
    resolver = DnsFailoverResolver { _, ip -> calls.add(ip); if (ip == a) wrongId else valid }
    check(resolver.resolve(request, a, b, true)?.contentEquals(valid) == true)
    check(calls == listOf(a, b))

    calls.clear()
    resolver = DnsFailoverResolver { _, ip -> calls.add(ip); if (ip == a) throw IllegalStateException("no network") else wrongId }
    check(resolver.resolve(request, a, b, true) == null)
    check(calls == listOf(a, b))

    calls.clear()
    resolver = DnsFailoverResolver { _, ip -> calls.add(ip); if (ip == a) ByteArray(1473) else valid }
    check(resolver.resolve(request, a, b, true)?.contentEquals(valid) == true)
    check(calls == listOf(a, b))
    println("OK: optional DNS secondary failover checks passed")
}
