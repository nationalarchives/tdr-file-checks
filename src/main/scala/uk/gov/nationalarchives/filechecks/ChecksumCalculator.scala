package uk.gov.nationalarchives.filechecks

import cats.effect.{IO, Resource}

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.security.MessageDigest

object ChecksumCalculator {

  def calculateChecksum(path: Path, bufferSizeBytes: Int = ApplicationConfig.checksumBufferSizeBytes): IO[String] = {
    val messageDigester = MessageDigest.getInstance("SHA-256")
    readFileIntoDigest(path, messageDigester, bufferSizeBytes) *> formatDigest(messageDigester)
  }

  private def formatDigest(messageDigester: MessageDigest): IO[String] =
    IO(messageDigester.digest().toSeq.map(byte => f"${byte & 0xff}%02x").mkString)

  private def readFileIntoDigest(path: Path, messageDigester: MessageDigest, bufferSizeBytes: Int): IO[Unit] =
    Resource
      .fromAutoCloseable(IO.blocking(FileChannel.open(path, StandardOpenOption.READ)))
      .use { channel =>
        IO.blocking {
          val allocationSizeBytes = math.min(bufferSizeBytes.toLong, math.max(channel.size(), 1L)).toInt
          val buffer = ByteBuffer.allocateDirect(allocationSizeBytes)
          while (channel.read(buffer) != -1) {
            buffer.flip()
            messageDigester.update(buffer)
            buffer.clear()
          }
        }
      }
}
