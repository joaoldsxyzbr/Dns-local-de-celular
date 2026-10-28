package br.com.joaolds.dnslocal.blocklist

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Bounded, fail-safe persistence: parse a temporary download before atomic replacement. */
internal class HaGeZiListStore(
    private val directory: File,
    private val minimumEntries: Int = 10_000
) {
    companion object {
        private const val FILE_NAME = "hagezi-pro-mini-onlydomains.txt"
        const val MAX_BYTES = 16 * 1024 * 1024
    }

    fun load(): DomainBlocklist? = try {
        val file = File(directory, FILE_NAME)
        if (!file.isFile || file.length() !in 1..MAX_BYTES.toLong()) null
        else file.bufferedReader(Charsets.UTF_8).use { validate(it) }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun update(openDownload: () -> InputStream): DomainBlocklist {
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create private list directory" }
        val temp = File.createTempFile("hagezi-pro-mini-", ".part", directory)
        try {
            openDownload().use { source ->
                temp.outputStream().buffered().use { target ->
                    val buffer = ByteArray(16 * 1024)
                    var count = 0L
                    while (true) {
                        val received = source.read(buffer)
                        if (received < 0) break
                        require(received > 0) { "Download stream returned no data" }
                        count += received
                        require(count <= MAX_BYTES) { "List download too large" }
                        target.write(buffer, 0, received)
                    }
                }
            }
            val index = temp.bufferedReader(Charsets.UTF_8).use { validate(it) }
            Files.move(
                temp.toPath(), File(directory, FILE_NAME).toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
            return index
        } finally {
            temp.delete() // Existing valid list is not deleted on failure.
        }
    }

    private fun validate(reader: java.io.Reader): DomainBlocklist {
        val index = DomainBlocklist.parse(reader)
        require(index.size >= minimumEntries) { "Incomplete HaGeZi list" }
        return index
    }
}
