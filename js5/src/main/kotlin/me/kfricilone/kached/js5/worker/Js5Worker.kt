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

package me.kfricilone.kached.js5.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import me.kfricilone.kached.js5.Js5Connection
import me.kfricilone.kached.js5.file.FileRequest
import me.kfricilone.kached.js5.file.FileResponse
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Kyle Fricilone on May 03, 2020.
 */
public class Js5Worker(
    private val connection: Js5Connection,
    private val requestChannel: Channel<FileRequest>,
    private val responseChannel: Channel<FileResponse>,
) : Closeable by connection {
    private val suspendChannel = Channel<Unit>()
    private val inFlightRequests = AtomicInteger(0)

    public suspend fun launch(scope: CoroutineScope): List<Job> {
        val requestJob =
            scope.launch {
                while (true) {
                    if (inFlightRequests.get() >= MAX_IN_FLIGHT) {
                        suspendChannel.receive()
                    }

                    val request = requestChannel.receive()
                    connection.request(request.archive, request.group)
                    inFlightRequests.incrementAndGet()
                }
            }

        val responseJob =
            scope.launch {
                while (true) {
                    val response = connection.response()
                    responseChannel.send(response)
                    inFlightRequests.decrementAndGet()
                    suspendChannel.trySend(Unit)
                }
            }

        return listOf(requestJob, responseJob)
    }

    public companion object {
        private const val MAX_IN_FLIGHT = 20

        public fun open(
            connection: Js5Connection,
            requestChannel: Channel<FileRequest>,
            responseChannel: Channel<FileResponse>,
        ): Js5Worker = Js5Worker(connection, requestChannel, responseChannel)
    }
}
