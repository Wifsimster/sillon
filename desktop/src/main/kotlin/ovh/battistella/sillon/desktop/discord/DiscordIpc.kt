package ovh.battistella.sillon.desktop.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.UUID

class DiscordException(message: String, val code: Int? = null) : IOException(message)

interface IpcPipe : Closeable {
    val input: InputStream
    val output: OutputStream
}

object IpcFrames {
    const val HANDSHAKE = 0
    const val FRAME = 1
    const val CLOSE = 2
    const val PING = 3
    const val PONG = 4
    const val MAX_SIZE = 64 * 1024

    fun encode(op: Int, payload: ByteArray): ByteArray {
        require(payload.size <= MAX_SIZE) { "Discord IPC payload too large (${payload.size} bytes)" }
        return ByteBuffer.allocate(HEADER + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(op).putInt(payload.size).put(payload).array()
    }

    fun read(input: InputStream): Pair<Int, ByteArray> {
        val header = ByteBuffer.wrap(input.readExactly(HEADER)).order(ByteOrder.LITTLE_ENDIAN)
        val op = header.getInt()
        val length = header.getInt()
        if (length < 0 || length > MAX_SIZE) throw DiscordException("Discord IPC frame too large ($length bytes)")
        return op to input.readExactly(length)
    }

    private fun InputStream.readExactly(count: Int): ByteArray {
        val out = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(out, read, count - read)
            if (n < 0) throw EOFException("Discord closed the connection")
            read += n
        }
        return out
    }

    private const val HEADER = 8
}

class DiscordClient(private val pipe: IpcPipe) : Closeable {
    private val json = Json { ignoreUnknownKeys = true }

    fun handshake(clientId: String) {
        send(IpcFrames.HANDSHAKE, buildJsonObject {
            put("v", 1)
            put("client_id", clientId)
        })
        awaitReply()
    }

    fun setActivity(activity: JsonObject?) {
        send(IpcFrames.FRAME, buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", buildJsonObject {
                put("pid", ProcessHandle.current().pid())
                put("activity", activity ?: JsonNull)
            })
            put("nonce", UUID.randomUUID().toString())
        })
        awaitReply()
    }

    override fun close() {
        runCatching { pipe.close() }
    }

    private fun send(op: Int, body: JsonElement) {
        pipe.output.write(IpcFrames.encode(op, body.toString().toByteArray(Charsets.UTF_8)))
        pipe.output.flush()
    }

    private fun awaitReply() {
        while (true) {
            val (op, payload) = IpcFrames.read(pipe.input)
            val body = runCatching { json.parseToJsonElement(payload.decodeToString()).jsonObject }.getOrNull()
            when (op) {
                IpcFrames.PING -> {
                    pipe.output.write(IpcFrames.encode(IpcFrames.PONG, payload))
                    pipe.output.flush()
                }
                IpcFrames.CLOSE -> throw DiscordException(
                    body?.get("message")?.jsonPrimitive?.content ?: "Discord closed the connection",
                    body?.get("code")?.jsonPrimitive?.intOrNull,
                )
                IpcFrames.FRAME -> {
                    if (body?.get("evt")?.takeIf { it !is JsonNull }?.jsonPrimitive?.content == "ERROR") {
                        val data = body["data"]?.jsonObject
                        throw DiscordException(
                            data?.get("message")?.jsonPrimitive?.content ?: "Discord rejected the request",
                            data?.get("code")?.jsonPrimitive?.int,
                        )
                    }
                    return
                }
                else -> return
            }
        }
    }
}

object DiscordSockets {
    private const val SLOTS = 10

    fun open(env: Map<String, String> = System.getenv(), windows: Boolean = isWindows()): IpcPipe {
        var last: Exception? = null
        for (path in candidates(env, windows)) {
            try {
                return if (windows) openPipe(path) else openSocket(path)
            } catch (e: Exception) {
                last = e
            }
        }
        throw DiscordException("Discord isn't running").apply { last?.let(::addSuppressed) }
    }

    fun candidates(env: Map<String, String>, windows: Boolean): List<String> {
        if (windows) return (0 until SLOTS).map { "\\\\.\\pipe\\discord-ipc-$it" }
        val bases = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNull { env[it]?.takeIf(String::isNotBlank) } + "/tmp"
        val dirs = bases.distinct().flatMap { base ->
            listOf(
                base,
                "$base/snap.discord",
                "$base/app/com.discordapp.Discord",
                "$base/.flatpak/com.discordapp.Discord/xdg-run",
            )
        }
        return dirs.flatMap { dir -> (0 until SLOTS).map { "$dir/discord-ipc-$it" } }
    }

    private fun openPipe(path: String): IpcPipe {
        val file = RandomAccessFile(path, "rw")
        return object : IpcPipe {
            override val input = object : InputStream() {
                override fun read(): Int = file.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = file.read(b, off, len)
            }
            override val output = object : OutputStream() {
                override fun write(b: Int) = file.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = file.write(b, off, len)
            }

            override fun close() = file.close()
        }
    }

    private fun openSocket(path: String): IpcPipe {
        if (!File(path).exists()) throw DiscordException("No socket at $path")
        val channel = SocketChannel.open(UnixDomainSocketAddress.of(path))
        return object : IpcPipe {
            override val input: InputStream = Channels.newInputStream(channel)
            override val output: OutputStream = Channels.newOutputStream(channel)

            override fun close() = channel.close()
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")
}
