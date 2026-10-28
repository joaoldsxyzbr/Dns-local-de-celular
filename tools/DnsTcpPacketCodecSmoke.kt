package br.com.joaolds.dnslocal.dns

fun main() {
    val client = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.2"))
    val server = requireNotNull(DnsPacketCodec.parseIpv4Address("198.18.0.1"))
    val question = byteArrayOf(0x23,0x42,1,0,0,1,0,0,0,0,0,0,1,'a'.code.toByte(),0,0,1,0,1)
    val wire = byteArrayOf(0,question.size.toByte()) + question
    val request = DnsTcpPacketCodec.Segment(client,server,51111,700,600,0x18,wire)
    val output = requireNotNull(DnsTcpPacketCodec.encodeReply(request,600,700,0x18,wire))
    check(output.size == 40 + wire.size)
    check(output.sliceArray(12..15).contentEquals(byteArrayOf(198.toByte(),18,0,1)))
    val incoming = output.copyOf()
    output.copyInto(incoming,12,16,20)
    output.copyInto(incoming,16,12,16)
    output.copyInto(incoming,20,22,24)
    output.copyInto(incoming,22,20,22)
    fixChecksums(incoming)
    val decoded = requireNotNull(DnsTcpPacketCodec.decode(incoming,incoming.size,server))
    check(decoded.sourceIp == client && decoded.sourcePort == 51111)
    check(decoded.sequence == 600L && decoded.acknowledgment == 700L)
    check(decoded.flags == 0x18 && decoded.payload.contentEquals(wire))
    check(DnsTcpPacketCodec.singleDnsQuery(decoded.payload)?.contentEquals(question) == true)
    check(DnsTcpPacketCodec.singleDnsQuery(wire + 0) == null)
    check(DnsTcpPacketCodec.singleDnsQuery(byteArrayOf(0,25) + question) == null)
    check(DnsTcpPacketCodec.decode(incoming,incoming.size,client) == null)
    check(DnsTcpPacketCodec.decode(incoming.copyOf().also { it[8] = 9 },incoming.size,server) == null)
    check(DnsTcpPacketCodec.decode(incoming.copyOf().also { it[40] = 9 },incoming.size,server) == null)
    check(DnsTcpPacketCodec.decode(incoming.copyOf().also { it[32] = 0x40 },incoming.size,server) == null)
    check(DnsTcpPacketCodec.encodeReply(request,0,0,0,ByteArray(1461)) == null)
    check(DnsTcpPacketCodec.encodeReply(request,0,0,0,ByteArray(1460))?.size == 1500)
    val maximalDns = ByteArray(1472).also { it[2] = 1 }
    check(DnsTcpPacketCodec.singleDnsQuery(
        byteArrayOf(0x05, 0xc0.toByte()) + maximalDns)?.contentEquals(maximalDns) == true)
    check(DnsTcpPacketCodec.singleDnsQuery(
        byteArrayOf(0x05, 0xc1.toByte()) + ByteArray(1473)) == null)
    println("OK: IPv4/TCP codec smoke checks passed")
}

private fun fixChecksums(p:ByteArray) {
    fun sum(b:ByteArray):Int {
        var s=0L
        for(i in b.indices step 2) {
            s += ((b[i].toInt() and 255) shl 8) or
                (if(i+1<b.size) b[i+1].toInt() and 255 else 0)
        }
        while(s>65535) s=(s and 65535)+(s ushr 16)
        return s.inv().toInt() and 65535
    }
    fun put(i:Int,v:Int) { p[i]=(v ushr 8).toByte();p[i+1]=v.toByte() }
    put(10,0);put(10,sum(p.copyOfRange(0,20)))
    put(36,0)
    val pseudo=p.copyOfRange(12,20)+byteArrayOf(0,6,0,(p.size-20).toByte())+p.copyOfRange(20,p.size)
    val c=sum(pseudo);put(36,if(c==0)65535 else c)
}
