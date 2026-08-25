package uk.gov.nationalarchives.filechecks

import cats.effect.{IO, Resource}

import java.io.OutputStream
import java.nio.file.{Files, Path}
import java.security.{DigestInputStream, MessageDigest}

object ChecksumCalculator {

  def calculateChecksum(path: Path): IO[String] = {
    val messageDigester = MessageDigest.getInstance("SHA-256")
    readFileIntoDigest(path, messageDigester) *> formatDigest(messageDigester)
  }

  private def formatDigest(messageDigester: MessageDigest): IO[String] =
    IO(messageDigester.digest().toSeq.map(byte => f"${byte & 0xff}%02x").mkString)

  private def readFileIntoDigest(path: Path, messageDigester: MessageDigest): IO[Unit] =
    Resource
      .fromAutoCloseable(IO.blocking(new DigestInputStream(Files.newInputStream(path), messageDigester)))
      .use(dis => IO.blocking(dis.transferTo(OutputStream.nullOutputStream())).void)
}
