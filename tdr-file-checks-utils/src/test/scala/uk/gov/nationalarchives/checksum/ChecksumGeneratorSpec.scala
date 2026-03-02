package uk.gov.nationalarchives.checksum

import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.funsuite.AnyFunSuiteLike

class ChecksumGeneratorSpec extends AnyFlatSpec {

  "generate" should "generate should return correct checksum for a file" in {
    val filePath = "src/test/resources/testfiles/Test.docx"
    val expectedChecksum = "252c2811bd57fc3bcc7683bd6d9515aeeab0758bf1c3e71718851c7831ca848e"
    val chunkSize = 1 // 1 MB

    val result = ChecksumGenerator.generate(filePath, chunkSize).unsafeRunSync()

    assert(result == expectedChecksum)
  }
}
