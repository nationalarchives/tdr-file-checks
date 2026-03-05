package uk.gov.nationalarchives.checksum

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.generic.auto._
import io.circe.parser.decode
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.fileformat.TestUtils

import java.io.{ByteArrayInputStream, File}
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.io.Source.fromResource

class LambdaSpec extends TestUtils {

  val fileId: UUID = UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f")
  val consignmentId: UUID = UUID.fromString("f0a73877-6057-4bbb-a1eb-7c7b73cab586")
  val userId: UUID = UUID.fromString("bd4cbe2e-b752-4432-8aec-a3234b9d4339")

  def mockS3Response(fileName: String): StubMapping = {
    val filePath = getClass.getResource(s"/testfiles/$fileName").getFile
    val bytes = Files.readAllBytes(Paths.get(filePath))
    wiremockS3.stubFor(
      get(urlEqualTo(s"/$fileName"))
        .willReturn(aResponse().withStatus(200).withBody(bytes))
    )
  }

  def createEvent(location: String): ByteArrayInputStream = {
    new ByteArrayInputStream(fromResource(s"json/$location.json").mkString.getBytes())
  }

  "The process method" should "download the file from S3 bucket and return the correct file ID, checksum and FFID" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "feb8c01fd4fd0d6e56d9a630ef82b244df25f141ac2d611115cda74fa0a2a2a7"
    val fileName = "Test.docx"
    mockS3Response(fileName)
    stubS3GetBytes(fileName, s"/$userId/$consignmentId/$fileId")
    stubS3GetObjectList(userId, consignmentId, List(fileId), fileName)

    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "succeed if the file already exists (do not download from the S3 bucket) and return the correct file ID, checksum and FFID" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "feb8c01fd4fd0d6e56d9a630ef82b244df25f141ac2d611115cda74fa0a2a2a7"
    val fileName = "Test.docx"
    stubS3GetBytes(fileName, s"/$userId/$consignmentId/$fileId")
    stubS3GetObjectList(userId, consignmentId, List(fileId), fileName)

    val basePath = s"./src/test/resources/testfiles/running-files/$consignmentId/"
    val filePath = s"$basePath/$fileName"
    new File(basePath).mkdirs()
    Files.copy(Paths.get(s"./src/test/resources/testfiles/$fileName"), Paths.get(filePath))

    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "throw an exception if the file is not found in S3" in {
    val event = createEvent("file_no_key")
    val exception = intercept[RuntimeException] {
      val fileName = "Test.docx"
      stubS3GetBytes(fileName, s"/$userId/$consignmentId/$fileId")
      stubS3GetObjectList(userId, consignmentId, List(fileId), fileName)

      new Lambda().process(event, null)
    }
    exception.getMessage should equal("software.amazon.awssdk.services.s3.model.S3Exception: (Service: S3, Status Code: 404, Request ID: null) (SDK Attempt Count: 1)")
  }

  "The process method" should "calculate the correct checksum for a file with one chunk" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "be776ad8d02e9fa4c35484877b2d96753a847e8bfc59c917c2442f3746850fb5"
    val fileName = "ten_bytes"
    mockS3Response(fileName)
    stubS3GetBytes(fileName, s"/$userId/$consignmentId/$fileId")
    stubS3GetObjectList(userId, consignmentId, List(fileId), fileName)

    new Lambda().process(createEvent("file_event_one_chunk"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "calculate the correct checksum for a file with two chunks" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "2d18255f3cae74523d18e372433aa78caca19001d400add39f46fafb1c60daf2"
    val fileName = "more_than_one_meg"
    mockS3Response(fileName)
    stubS3GetBytes(fileName, s"/$userId/$consignmentId/$fileId")
    stubS3GetObjectList(userId, consignmentId, List(fileId), fileName)

    new Lambda().process(createEvent("file_event_large_file"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  private def validateFileChecksResult(expectedChecksum: String, maybeResult: Option[FileChecksResult]): Unit = {
    maybeResult.isDefined should be(true)
    maybeResult.get.checksum.sha256Checksum should equal(expectedChecksum)
    maybeResult.get.checksum.fileId should equal(fileId)
    maybeResult.get.ffidMetadataInputValues.fileId should equal(fileId)
  }
}
