package uk.gov.nationalarchives.filechecks

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

import java.nio.file.{Files, Path, Paths}

class ChecksumCalculatorSpec extends AnyFlatSpec {

  private def testFile(name: String): Path = Paths.get(s"./src/test/resources/testfiles/$name")

  "calculateChecksum" should "return the correct checksum for a file smaller than one chunk" in {
    val checksum = ChecksumCalculator.calculateChecksum(testFile("ten_bytes")).unsafeRunSync()
    checksum should equal("be776ad8d02e9fa4c35484877b2d96753a847e8bfc59c917c2442f3746850fb5")
  }

  "calculateChecksum" should "return the correct checksum for a file spanning more than one chunk" in {
    val checksum = ChecksumCalculator.calculateChecksum(testFile("more_than_one_meg")).unsafeRunSync()
    checksum should equal("c08c59a10f61526ae02808f761d2fd75c09cb2d77d608dc01fdbc35e3fdaf11d")
  }

  "calculateChecksum" should "return the correct checksum for a binary file" in {
    val checksum = ChecksumCalculator.calculateChecksum(testFile("Test.docx")).unsafeRunSync()
    checksum should equal("252c2811bd57fc3bcc7683bd6d9515aeeab0758bf1c3e71718851c7831ca848e")
  }

  "calculateChecksum" should "return the checksum of an empty input for an empty file" in {
    val emptyFile = Files.createTempFile("checksum-calculator-spec-", ".tmp")
    try {
      val checksum = ChecksumCalculator.calculateChecksum(emptyFile).unsafeRunSync()
      checksum should equal("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    } finally {
      Files.deleteIfExists(emptyFile)
    }
  }

  "calculateChecksum" should "return the same checksum whatever the buffer size" in {
    val file = testFile("more_than_one_meg")
    val expected = "c08c59a10f61526ae02808f761d2fd75c09cb2d77d608dc01fdbc35e3fdaf11d"
    List(1, 7, 8192, 1024 * 1024, 64 * 1024 * 1024).foreach { bufferSizeBytes =>
      ChecksumCalculator.calculateChecksum(file, bufferSizeBytes).unsafeRunSync() should equal(expected)
    }
  }

  "calculateChecksum" should "fail if the file does not exist" in {
    val exception = intercept[java.nio.file.NoSuchFileException] {
      ChecksumCalculator.calculateChecksum(testFile("does_not_exist")).unsafeRunSync()
    }
    exception.getMessage should include("does_not_exist")
  }
}
