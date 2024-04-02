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

package me.kfricilone.kached

import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.retry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kfricilone.kached.js5.Js5Connection
import me.kfricilone.kached.js5.file.FileRequest
import me.kfricilone.kached.js5.file.FileResponse
import me.kfricilone.kached.js5.store.Js5Store
import me.kfricilone.kached.js5.worker.Js5Worker
import org.openrs2.buffer.crc32
import org.openrs2.buffer.use
import org.openrs2.cache.DiskStore
import org.openrs2.cache.Js5Compression
import org.openrs2.cache.Js5Index
import org.openrs2.cache.Js5MasterIndex
import org.openrs2.cache.MasterIndexFormat
import org.openrs2.cache.Store
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.Charsets.ISO_8859_1

/**
 * Created by Kyle Fricilone on Aug 22, 2021.
 */
public class Js5Downloader(
    private val cache: Path,
    private val versions: Boolean,
    private val workers: Int,
) {
    private val requestChannel = Channel<FileRequest>()
    private val responseChannel = Channel<FileResponse>()
    private val retryChannel = Channel<FileRequest>(capacity = Channel.UNLIMITED)

    public suspend fun download() {
        logger.info { "Opening disk cache from $cache" }
        val diskStore =
            runCatching {
                DiskStore.open(cache)
            }.getOrElse {
                DiskStore.create(cache)
            }

        logger.info { "Finding current revision" }
        val revision = AtomicInteger(fetchRevision())
        val connection =
            retry(constantDelay(RETRY_DELAY)) {
                val rev = revision.getAndIncrement()
                logger.info { "Opening connection with revision $rev" }
                Js5Connection.open(HOST, rev)
            }

        val js5Store = Js5Store(connection)
        val masterIndex = downloadMaster(js5Store)
        val indexes = downloadIndexes(js5Store, masterIndex)

        withContext(Dispatchers.IO) {
            js5Store.close()
        }

        val changes = findChanges(diskStore, masterIndex, indexes)
        logger.info { "Found ${changes.size} changes" }

        if (changes.isNotEmpty()) {
            downloadChanges(diskStore, revision.decrementAndGet(), changes, masterIndex, indexes)
        }

        withContext(Dispatchers.IO) {
            diskStore.close()
        }
    }

    private suspend fun downloadMaster(js5Store: Js5Store): Js5MasterIndex {
        logger.info { "Downloading master index" }
        return js5Store.read(Store.ARCHIVESET, Store.ARCHIVESET).use { buf ->
            Js5Compression.uncompress(buf).use {
                Js5MasterIndex.readUnverified(it, MasterIndexFormat.VERSIONED)
            }
        }
    }

    private suspend fun downloadIndexes(
        js5Store: Js5Store,
        masterIndex: Js5MasterIndex,
    ): Map<Int, Js5Index> {
        logger.info { "Downloading archive indexes" }
        return masterIndex.entries.withIndex()
            .associateBy({ it.index }, { it.value })
            .filterValues { it.version != 0 && it.checksum != 0 }
            .mapValues { (index, _) ->
                js5Store.read(Store.ARCHIVESET, index).use { buf ->
                    Js5Compression.uncompress(buf).use {
                        Js5Index.read(it)
                    }
                }
            }
    }

    private fun findChanges(
        diskStore: Store,
        masterIndex: Js5MasterIndex,
        indexes: Map<Int, Js5Index>,
    ): List<FileRequest> {
        val requests = mutableListOf<FileRequest>()

        logger.info { "Finding master index changes" }
        val diskIndexes =
            masterIndex.entries.withIndex()
                .associateBy({ it.index }, { it.value })
                .filterValues { it.version != 0 && it.checksum != 0 }
                .mapValues { (archive, js5index) ->
                    runCatching {
                        diskStore.read(Store.ARCHIVESET, archive).use { buf ->
                            if (buf.crc32() != js5index.checksum) {
                                requests.add(FileRequest(Store.ARCHIVESET, archive))
                            }

                            Js5Compression.uncompress(buf).use { Js5Index.read(it) }
                        }
                    }.onFailure {
                        requests.add(FileRequest(Store.ARCHIVESET, archive))
                    }.getOrNull()
                }

        logger.info { "Finding archive index changes" }
        indexes.forEach { (archive, js5index) ->
            val diskIndex = diskIndexes[archive]
            val groups =
                when {
                    diskIndex == null -> js5index
                    else -> js5index.filter { it != diskIndex[it.id] }
                }.map { FileRequest(archive, it.id) }

            logger.debug { "archive $archive, found ${groups.size} changes" }

            requests.addAll(groups)
        }

        return requests
    }

    private suspend fun downloadChanges(
        diskStore: Store,
        revision: Int,
        requests: List<FileRequest>,
        masterIndex: Js5MasterIndex,
        indexes: Map<Int, Js5Index>,
    ) = coroutineScope {
        val responseCounter = AtomicInteger(requests.size)

        logger.info { "Opening $workers js5 workers" }
        val js5Workers =
            (0 until workers).map {
                Js5Worker.open(
                    Js5Connection.open(HOST_TEMPLATE.format(it + 1), revision),
                    requestChannel,
                    responseChannel,
                )
            }

        logger.debug { "Creating worker request/response jobs" }
        val workerJobs = js5Workers.flatMap { it.launch(this) }

        logger.debug { "Creating request handler job" }
        val requestJob = createRequestJob(requests)

        logger.debug { "Creating response handler job" }
        val responseJob = createResponseJob(diskStore, masterIndex, indexes, responseCounter)

        logger.debug { "Waiting for all requests to be processed" }
        responseJob.join()

        logger.debug { "Closing request handler job" }
        requestJob.cancelAndJoin()

        logger.debug { "Closing worker request/response jobs" }
        workerJobs.forEach { it.cancelAndJoin() }

        logger.debug { "Closing workers" }
        js5Workers.forEach { it.close() }
    }

    private fun CoroutineScope.createRequestJob(requests: List<FileRequest>) =
        launch {
            requests.forEach {
                logger.info { "Requesting ${it.archive}, ${it.group}" }
                requestChannel.send(it)
            }

            while (true) {
                val request = retryChannel.receive()
                logger.info { "Retrying ${request.archive}, ${request.group}" }
                requestChannel.send(request)
            }
        }

    private fun CoroutineScope.createResponseJob(
        diskStore: Store,
        masterIndex: Js5MasterIndex,
        indexes: Map<Int, Js5Index>,
        responseCounter: AtomicInteger,
    ) = launch {
        while (responseCounter.get() > 0) {
            val response = responseChannel.receive()
            val buf = response.buf

            val checksum =
                when (response.archive) {
                    Store.ARCHIVESET -> masterIndex.entries[response.group].checksum
                    else -> indexes[response.archive]!![response.group]!!.checksum
                }

            if (buf.crc32() != checksum) {
                buf.release()
                retryChannel.send(FileRequest(response.archive, response.group))
                continue
            }

            if (versions && response.archive != Store.ARCHIVESET) {
                val version = indexes[response.archive]!![response.group]!!.version
                if (version != -1) {
                    buf.writeShort(version)
                }
            }

            buf.use {
                diskStore.write(response.archive, response.group, it)
            }

            responseCounter.decrementAndGet()
        }
    }

    private suspend fun fetchRevision(): Int =
        client.get(CONFIG).bodyAsText(ISO_8859_1)
            .lines().firstOrNull { it.startsWith(PARAM) }
            ?.replace(PARAM, "")?.toInt() ?: DEFAULT_REVISION

    private companion object {
        private val logger = InlineLogger()
        private const val HOST = "oldschool1.runescape.com"
        private const val HOST_TEMPLATE = "oldschool%d.runescape.com"
        private const val CONFIG = "http://oldschool.runescape.com/jav_config.ws"
        private const val PARAM = "param=25="
        private const val RETRY_DELAY = 500L
        private const val DEFAULT_REVISION = 217

        private val client = HttpClient(CIO)
    }
}
