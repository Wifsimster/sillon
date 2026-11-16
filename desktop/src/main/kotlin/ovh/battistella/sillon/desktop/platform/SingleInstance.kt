package ovh.battistella.sillon.desktop.platform

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption

class SingleInstance private constructor(private val channel: FileChannel, private val lock: FileLock) {
    fun release() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        fun acquire(file: File): SingleInstance? {
            file.parentFile?.mkdirs()
            val channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                return null
            }
            return SingleInstance(channel, lock)
        }
    }
}
