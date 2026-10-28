package br.com.joaolds.dnslocal.blocklist

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val dir = Files.createTempDirectory("dns-local-test-").toFile()
    try {
        val store = HaGeZiListStore(dir, minimumEntries = 2)
        check(store.load() == null)
        val first = "ads.example.com\ntracker.example.com\n".toByteArray()
        check(store.update { ByteArrayInputStream(first) }.blocks("sub.ads.example.com"))
        check(store.load()?.blocks("ads.example.com") == true)
        check(runCatching { store.update { ByteArrayInputStream("invalid list".toByteArray()) } }.isFailure)
        check(store.load()?.blocks("ads.example.com") == true) // Fail-safe retains last good list.
        check(runCatching { store.update { ByteArrayInputStream("just.example\n".toByteArray()) } }.isFailure)
        check(store.load()?.size == 2)
        val oversized = ByteArray(HaGeZiListStore.MAX_BYTES + 1) { 'a'.code.toByte() }
        check(runCatching { store.update { ByteArrayInputStream(oversized) } }.isFailure)
        check(store.load()?.size == 2)
        // An InputStream that produces zero bytes without EOF must not spin forever.
        val zeroRead = runCatching {
            store.update {
                object : ByteArrayInputStream(first) {
                    private var firstRead = true
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (firstRead) { firstRead = false; return 0 }
                        return super.read(buffer, offset, length)
                    }
                }
            }
        }
        check(zeroRead.isFailure)
        check(store.load()?.size == 2) // Keep the previously saved valid copy.
        // When the user closes the screen, stop the transfer before a second read.
        // The stored list and its atomic-update safety must remain intact.
        val readCalls = AtomicInteger()
        val cancelled = AtomicBoolean(false)
        val worker = Thread {
            cancelled.set(runCatching {
                store.update {
                    object : ByteArrayInputStream("replacement.example.net\nother.example.org\n".toByteArray()) {
                        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                            readCalls.incrementAndGet()
                            val count = super.read(bytes, offset, length)
                            if (count > 0) Thread.currentThread().interrupt()
                            return count
                        }
                    }
                }
            }.isFailure)
        }
        worker.start()
        worker.join(5000)
        check(!worker.isAlive)
        check(cancelled.get())
        check(readCalls.get() == 1)
        check(store.load()?.blocks("ads.example.com") == true)
        check(dir.listFiles()?.none { it.name.endsWith(".part") } == true)
        val second = "new.example.net\nother.example.org\n".toByteArray()
        store.update { ByteArrayInputStream(second) }
        check(store.load()?.blocks("ads.example.com") == false)
        check(store.load()?.blocks("sub.new.example.net") == true)
        check(dir.listFiles()?.none { it.name.endsWith(".part") } == true)
        println("OK: atomic HaGeZi persistence smoke checks passed")
    } finally {
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }
}
