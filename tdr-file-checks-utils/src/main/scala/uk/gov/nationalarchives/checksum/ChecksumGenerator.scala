package uk.gov.nationalarchives.checksum

import cats.effect.{IO, Resource}

import java.io.{File, FileInputStream}
import java.security.MessageDigest

object ChecksumGenerator {

  def generate(filePath: String, chunkSize: Int): IO[String] = {
    val chunkSizeInMB = chunkSize
    val chunkSizeInBytes: Int = chunkSizeInMB * 1024 * 1024
    val messageDigester: MessageDigest = MessageDigest.getInstance("SHA-256")
    for {
      _ <- {
        Resource.fromAutoCloseable(IO(new FileInputStream(new File(filePath))))
          .use(inStream => {
            val bytes = new Array[Byte](chunkSizeInBytes)
            IO(Iterator.continually(inStream.read(bytes)).takeWhile(_ != -1).foreach(messageDigester.update(bytes, 0, _)))
          })
      }
      checksum <- IO(messageDigester.digest)
      mapped <- IO(checksum.map(byte => f"$byte%02x").mkString)
    } yield mapped
  }
}
