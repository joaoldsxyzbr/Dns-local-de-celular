package br.com.joaolds.dnslocal.dns

/** Codec only; does NOT implement TCP state or enable TCP DNS in the VPN. */
internal object DnsTcpPacketCodec {
    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 255
    private fun word(b: ByteArray, i: Int) = (u(b,i) shl 8) or u(b,i+1)
    private fun num(b: ByteArray, i: Int) = (word(b,i).toLong() shl 16) or word(b,i+2).toLong()
    private fun set16(b: ByteArray,i:Int,v:Int) { b[i]=(v ushr 8).toByte(); b[i+1]=v.toByte() }
    private fun set32(b: ByteArray,i:Int,v:Long) { set16(b,i,(v ushr 16).toInt()); set16(b,i+2,v.toInt()) }

    private class Sum {
        var acc=0L
        fun add(v:Int) { acc += (v and 65535).toLong() }
        fun bytes(b:ByteArray,from:Int,len:Int) {
            var n=from
            while(n+1<from+len) { add((u(b,n) shl 8) or u(b,n+1)); n+=2 }
            if(n<from+len) add(u(b,n) shl 8)
        }
        fun result():Int {
            while(acc ushr 16 != 0L) acc = (acc and 65535) + (acc ushr 16)
            return acc.inv().toInt() and 65535
        }
    }
    data class Segment(val sourceIp:Int,val destinationIp:Int,val sourcePort:Int,
                       val sequence:Long,val acknowledgment:Long,val flags:Int,val payload:ByteArray)

    fun decode(p:ByteArray,length:Int,virtualDnsIp:Int):Segment? {
        if(length !in 40..1500 || length>p.size || u(p,0) ushr 4 != 4) return null
        val ipHeader=(u(p,0) and 15)*4
        if(ipHeader<20 || ipHeader+20>length || word(p,2)!=length) return null
        if(Sum().apply { bytes(p,0,ipHeader) }.result()!=0) return null
        if(word(p,6) and 0x3fff != 0 || u(p,9)!=6 || num(p,16)!=virtualDnsIp.toLong() and 0xffffffffL) return null
        if(word(p,ipHeader+2)!=53 || word(p,ipHeader)==0) return null
        val tcpHeader=(u(p,ipHeader+12) ushr 4)*4
        if(tcpHeader<20 || ipHeader+tcpHeader>length) return null
        val sum=Sum()
        sum.bytes(p,12,8); sum.add(6); sum.add(length-ipHeader); sum.bytes(p,ipHeader,length-ipHeader)
        if(sum.result()!=0) return null
        return Segment(num(p,12).toInt(),virtualDnsIp,word(p,ipHeader),
                       num(p,ipHeader+4),num(p,ipHeader+8),u(p,ipHeader+13),
                       p.copyOfRange(ipHeader+tcpHeader,length))
    }

    fun encodeReply(query:Segment,sequence:Long,acknowledgment:Long,flags:Int,payload:ByteArray):ByteArray? {
        if(payload.size>1460 || flags !in 0..255 || sequence !in 0..0xffffffffL ||
           acknowledgment !in 0..0xffffffffL) return null
        val p=ByteArray(40+payload.size)
        p[0]=0x45; set16(p,2,p.size); p[8]=64; p[9]=6
        set32(p,12,query.destinationIp.toLong() and 0xffffffffL)
        set32(p,16,query.sourceIp.toLong() and 0xffffffffL)
        set16(p,20,53); set16(p,22,query.sourcePort)
        set32(p,24,sequence); set32(p,28,acknowledgment)
        p[32]=0x50; p[33]=flags.toByte(); set16(p,34,65535)
        payload.copyInto(p,40)
        set16(p,10,Sum().apply { bytes(p,0,20) }.result())
        val sum=Sum();sum.bytes(p,12,8);sum.add(6);sum.add(p.size-20);sum.bytes(p,20,p.size-20)
        val cs=sum.result();set16(p,36,if(cs==0)65535 else cs)
        return p
    }

    /** Only a single complete DNS frame; segmented TCP requires a separate state machine. */
    fun singleDnsQuery(payload:ByteArray):ByteArray? {
        if(payload.size !in 14..1460) return null
        val size=word(payload,0)
        if(size !in 12..1458 || size!=payload.size-2) return null
        val query=payload.copyOfRange(2,payload.size)
        return query.takeIf { u(it,2) and 128 == 0 }
    }
}
