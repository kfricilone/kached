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

package me.kfricilone.kached.js5.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import java.nio.ByteBuffer
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Created by Kyle Fricilone on Aug 26, 2021.
 */
public fun buildBytes(
    alloc: ByteBufAllocator = ByteBufAllocator.DEFAULT,
    capacity: Int = 256,
    builder: ByteBuf.() -> Unit,
): ByteBuf {
    contract {
        callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
    }

    return alloc.buffer(capacity).apply(builder)
}

public suspend fun ByteWriteChannel.writeAndFlush(buf: ByteBuf) {
    val nioCount = buf.nioBufferCount()

    when {
        buf.hasArray() -> writeFully(buf.array(), buf.arrayOffset(), buf.readableBytes())
        nioCount > 1 -> buf.nioBuffers().forEach { writeFully(it) }
        nioCount == 1 -> writeFully(buf.nioBuffer())
        else -> writeFully(ByteBuffer.wrap(ByteBufUtil.getBytes(buf)))
    }

    flush()
}

public suspend fun ByteReadChannel.readUnsignedByte(): Int = readByte().toUByte().toInt()

public suspend fun ByteReadChannel.readUnsignedShort(): Int = readShort().toUShort().toInt()

public suspend fun ByteReadChannel.readBytes(length: Int): ByteArray =
    ByteArray(length).apply {
        readFully(this)
    }
