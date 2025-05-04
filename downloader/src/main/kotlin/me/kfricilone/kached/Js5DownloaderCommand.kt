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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Created by Kyle Fricilone on Aug 22, 2021.
 */
public fun main(args: Array<String>): Unit = Js5DownloaderCommand().main(args)

public class Js5DownloaderCommand : CliktCommand() {
    private val versions: Boolean by option(
        "-v",
        "--versions",
        help = "Add version trailer",
    ).flag()

    private val workers: Int by option(
        "-w",
        "--workers",
        help = "Number of workers",
    ).int().default(1)

    private val cache: Path by argument(
        help = "Cache directory",
    ).path(
        canBeDir = true,
        canBeFile = false,
    )

    override fun run(): Unit =
        runBlocking {
            Js5Downloader(cache, versions, workers).download()
        }
}
