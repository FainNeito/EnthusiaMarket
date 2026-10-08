package net.badgersmc.em.application

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/** Bounded reader for the serialized NBT used by Bukkit item stacks. */
internal object ItemNbtReader {
    fun read(bytes: ByteArray): Any {
        val raw = ByteArrayInputStream(bytes)
        val stream = if (isGzip(bytes)) GZIPInputStream(raw) else raw
        return DataInputStream(stream).use { readRoot(it) }
    }

    private fun isGzip(bytes: ByteArray): Boolean =
        bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    private fun readRoot(input: DataInputStream): Any {
        val type = input.readUnsignedByte()
        require(type == 10)
        readString(input)
        return payload(input, type, 0)
    }

    private fun payload(input: DataInputStream, type: Int, depth: Int): Any {
        require(depth < 64)
        return if (type in 1..6) scalar(input, type) else collection(input, type, depth)
    }

    private fun scalar(input: DataInputStream, type: Int): Any = when (type) {
        1 -> input.readByte()
        2 -> input.readShort()
        3 -> input.readInt()
        4 -> input.readLong()
        5 -> input.readFloat()
        6 -> input.readDouble()
        else -> error("Unsupported scalar NBT type $type")
    }

    private fun collection(input: DataInputStream, type: Int, depth: Int): Any = when (type) {
        7 -> List(size(input)) { input.readByte() }
        8 -> readString(input)
        9 -> readList(input, depth)
        10 -> readCompound(input, depth)
        11 -> List(size(input)) { input.readInt() }
        12 -> List(size(input)) { input.readLong() }
        else -> error("Unsupported NBT type $type")
    }

    private fun readList(input: DataInputStream, depth: Int): List<Any> {
        val child = input.readUnsignedByte()
        return List(size(input)) { payload(input, child, depth + 1) }
    }

    private fun readCompound(input: DataInputStream, depth: Int): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        while (true) {
            val child = input.readUnsignedByte()
            if (child == 0) return result
            val name = readString(input)
            result[name] = payload(input, child, depth + 1)
        }
    }

    private fun size(input: DataInputStream): Int = input.readInt().also { require(it in 0..1_000_000) }
    private fun readString(input: DataInputStream): String = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }.toString(Charsets.UTF_8)

}
