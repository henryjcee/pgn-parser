package com.henrycourse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.*

@kotlinx.coroutines.ExperimentalCoroutinesApi
fun main() = runBlocking {

    val logger = LoggerFactory.getLogger("AppKt")

    val yearAndMonth = System.getenv("FILE_YEAR_AND_MONTH")
    val bucketName = System.getenv("BUCKET_NAME")
    val fileAddress = "https://database.lichess.org/standard/lichess_db_standard_rated_$yearAndMonth.pgn.bz2"

    val s3 = S3AsyncClient.builder().credentialsProvider(
        AwsCredentialsProviderChain.builder()
            .addCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
            .addCredentialsProvider(ProfileCredentialsProvider.create("prod"))
            .addCredentialsProvider(InstanceProfileCredentialsProvider.create()).build()
    ).region(Region.EU_WEST_2).build()

    HttpClient().get<HttpStatement>(fileAddress).execute {

        val decompressedLines = produce {

            BZip2CompressorInputStream(it.receive<ByteReadChannel>().toInputStream(), true)
                .bufferedReader()
                .useLines {
                    it.forEach { send(it) }
                }
        }

        val gameText = produce {

            val buffer = LinkedList<String>()

            for (line in decompressedLines) {

                if (line.startsWith("[Event")) {
                    send(buffer.joinToString("\n"))
                    buffer.clear()
                }

                buffer.add(line)
            }
        }

        val games = produce {

            for (lines in gameText) {

                val props = HashMap<String, String>()
                lines.split("\n").forEach {
                    if (it.startsWith("[")) {
                        props += parseProp(it)
                    } else if (it.startsWith("1.")) {
                        if (it.contains("%eval")) { // Only want games with eval
                            println(it)
                            send(Game(props, it))
                        }
                    }
                }
            }
        }

        val batches = produce {

            val buffer = LinkedList<Game>()
            var count = 0L

            for (game in games) {

                if (buffer.size > 999) {
                    send(count++ to buffer)
                    buffer.clear()
                } else {
                    buffer.add(game)
                }
            }
        }

        val uploaded = produce {

            val om = jacksonObjectMapper()

//        Change here if you want to do something other than put things in S3
            for ((count, batch) in batches) {

                val fileKey = "games_with_eval/${yearAndMonth}_${count.toString().padStart(6, '0')}"

                batch.joinToString("\n") { om.writeValueAsString(it) }.let {
                    s3.putObject(
                        PutObjectRequest.builder().acl("public-read").bucket(bucketName).key(fileKey).build(),
                        AsyncRequestBody.fromString(it)
                    ).await()
                }

                send(fileKey)
            }
        }

        uploaded.consumeEach {
            logger.info("Uploaded file $it")
        }
    }
}

tailrec fun parseProp(input: String, key: String = ""): Pair<String, String> {

    return when (val currentChar = input[0]) {
        '[' -> parseProp(input.drop(1))
        ' ' -> return key to input.drop(2).dropLast(2)
        else -> parseProp(input.drop(1), key + currentChar)
    }
}

data class Game(val props: Map<String, String>, val pgnString: String)
