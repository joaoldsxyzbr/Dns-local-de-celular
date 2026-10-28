package br.com.joaolds.dnslocal.blocklist

import java.io.ByteArrayInputStream
import java.nio.file.Files

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
