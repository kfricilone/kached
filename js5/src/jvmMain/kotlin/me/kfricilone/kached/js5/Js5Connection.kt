/*
 * Copyright (c) 2022 Kyle Fricilone (https://kfricilone.me)
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package me.kfricilone.kached.js5

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.readInt
import kotlinx.coroutines.Dispatchers
import me.kfricilone.kached.js5.file.FileResponse
import me.kfricilone.kached.js5.prot.Js5Prot
import me.kfricilone.kached.js5.prot.LoginProt
import me.kfricilone.kached.js5.util.buildBytes
import me.kfricilone.kached.js5.util.readBytes
import me.kfricilone.kached.js5.util.readUnsignedByte
import me.kfricilone.kached.js5.util.readUnsignedShort
import me.kfricilone.kached.js5.util.writeAndFlush
import org.openrs2.buffer.use
import java.io.Closeable

/**
 * Created by Kyle Fricilone on May 03, 2020.
 */
public open class Js5Connection(
    private val socket: Socket,
) : Closeable by socket {
    private val connection = socket.connection()

    private suspend fun connect(revision: Int) {
        val buf =
            buildBytes(capacity = 21) {
                writeByte(LoginProt.InitJs5RemoteConnection.opcode)
                writeInt(revision)
                repeat(4) { writeInt(0) }
            }

        buf.use { connection.output.writeAndFlush(it) }

        val status = connection.input.readUnsignedByte()
        logger.debug { "js5 init response: $status" }

        check(status == 0) { "JS5 connection error with code: $status." }
    }

    public suspend fun state() {
        val buf =
            buildBytes(capacity = PACKET_LEN) {
                writeByte(Js5Prot.LoggedOutState.opcode)
                writeByte(0)
                writeShort(0)
            }

        logger.debug { "js5prot state: logged_out" }

        buf.use { connection.output.writeAndFlush(it) }
    }

    public suspend fun encryption(key: Int) {
        val buf =
            buildBytes(capacity = PACKET_LEN) {
                writeByte(Js5Prot.EncryptionKeyUpdate.opcode)
                writeByte(key)
                writeShort(0)
            }

        logger.debug { "js5prot encryption: $key" }

        buf.use { connection.output.writeAndFlush(it) }
    }

    public suspend fun request(
        archive: Int,
        group: Int,
    ) {
        val js5Prot = if (archive == ARCHIVESET) Js5Prot.PriorityFileRequest else Js5Prot.FileRequest
        val buf =
            buildBytes(capacity = PACKET_LEN) {
                writeByte(js5Prot.opcode)
                writeByte(archive)
                writeShort(group)
            }

        logger.debug { "js5prot request: $archive, $group" }

        buf.use { connection.output.writeAndFlush(it) }
    }

    public suspend fun response(): FileResponse {
        val archive = connection.input.readUnsignedByte()
        val group = connection.input.readUnsignedShort()
        val compression = connection.input.readUnsignedByte()
        val length = connection.input.readInt()

        val header = if (compression == 0) HEADER_UNCOMPRESSED else HEADER_COMPRESSED
        val size = header + length

        val buf =
            buildBytes(capacity = size) {
                writeByte(compression)
                writeInt(length)
            }

        var remaining = size - buf.readableBytes()
        var read = remaining.coerceAtMost(INITIAL_BLOCK)
        buf.writeBytes(connection.input.readBytes(read))
        remaining -= read

        while (remaining > 0) {
            val trailer = connection.input.readUnsignedByte()
            check(trailer == BLOCK_TRAILER) { "Unexpected block trailer $trailer" }

            read = remaining.coerceAtMost(SUPPLEMENT_BLOCK)
            buf.writeBytes(connection.input.readBytes(read))
            remaining -= read
        }

        logger.debug { "js5prot response: $archive, $group" }

        return FileResponse(archive, group, buf)
    }

    public companion object {
        private val logger = KotlinLogging.logger {}

        private const val ARCHIVESET = 255

        private const val PACKET_LEN = 4

        private const val HEADER_UNCOMPRESSED = 5
        private const val HEADER_COMPRESSED = 9

        private const val BLOCK = 512
        private const val INITIAL_BLOCK = BLOCK - 8
        private const val SUPPLEMENT_BLOCK = BLOCK - 1

        private const val BLOCK_TRAILER = 0xFF

        private const val PORT: Int = 43594

        public suspend fun open(
            host: String,
            revision: Int,
        ): Js5Connection {
            val socket =
                aSocket(SelectorManager(Dispatchers.IO))
                    .tcp()
                    .connect(InetSocketAddress(host, PORT))

            return Js5Connection(socket).apply {
                connect(revision)
                state()
                encryption(0)
            }
        }
    }
}
