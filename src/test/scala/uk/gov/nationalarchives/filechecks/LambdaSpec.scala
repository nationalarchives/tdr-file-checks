package uk.gov.nationalarchives.filechecks

import io.circe.generic.auto._
import io.circe.parser.decode
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.filechecksutils.TestUtils

import java.io.ByteArrayInputStream
import java.util.UUID
import scala.io.Source.fromResource

class LambdaSpec extends TestUtils {

  val fileId: UUID = UUID.fromString("acea5919-25a3-4c6b-8908-fa47cc77878f")

  private val noThreatsFound = "NO_THREATS_FOUND"
  private val threatsFound = "THREATS_FOUND"

  def createEvent(location: String): ByteArrayInputStream = {
    new ByteArrayInputStream(fromResource(s"json/$location.json").mkString.getBytes())
  }

  "The process method" should "return the correct file checks results for given parameters" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "252c2811bd57fc3bcc7683bd6d9515aeeab0758bf1c3e71718851c7831ca848e"
    val fileName = "Test.docx"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)

    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "calculate the correct checksum for a file with one chunk" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "be776ad8d02e9fa4c35484877b2d96753a847e8bfc59c917c2442f3746850fb5"
    val fileName = "ten_bytes"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3GetObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)

    new Lambda().process(createEvent("file_event_one_chunk"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "recover when the first download attempt fails" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "be776ad8d02e9fa4c35484877b2d96753a847e8bfc59c917c2442f3746850fb5"
    val fileName = "ten_bytes"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3GetObject(fileName, s"/testbucket/$fileName", failFirstAttempt = true)
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)

    new Lambda().process(createEvent("file_event_one_chunk"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "calculate the correct checksum for a file with two chunks" in {
    val outputStream = new ByteArrayOutputStream()
    val expectedChecksum = "c08c59a10f61526ae02808f761d2fd75c09cb2d77d608dc01fdbc35e3fdaf11d"
    val fileName = "more_than_one_meg"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)

    new Lambda().process(createEvent("file_event_large_file"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption
    validateFileChecksResult(expectedChecksum, decoded)
  }

  "The process method" should "throw when the file does not exist" in {
    val outputStream = new ByteArrayOutputStream()
    a[Throwable] should be thrownBy new Lambda().process(createEvent("file_event_missing_file"), outputStream)
    outputStream.toByteArray shouldBe empty
  }

  "The process method" should "populate antivirus field with NO_THREATS_FOUND result" in {
    val outputStream = new ByteArrayOutputStream()
    val fileName = "Test.docx"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)

    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption

    verifyAntivirus(decoded, noThreatsFound)
  }

  "The process method" should "not copy to quarantine bucket when s3QuarantineBucket is missing in the input for THREATS_FOUND result" in {
    val outputStream = new ByteArrayOutputStream()
    val fileName = "Test.docx"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", threatsFound)

    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption

    verifyAntivirus(decoded, threatsFound)
  }

  "The process method" should "not copy to clean bucket when s3CleanDestinationBucket is missing in the input for NO_THREATS_FOUND result" in {
    val outputStream = new ByteArrayOutputStream()
    val fileName = "Test.docx"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)

    new Lambda().process(createEvent("file_event"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption

    verifyAntivirus(decoded, noThreatsFound)
  }

  "The process method" should "copy to quarantine bucket when s3QuarantineBucket is provided in the input for THREATS_FOUND result" in {
    val outputStream = new ByteArrayOutputStream()
    val fileName = "Test.docx"
    val destinationBucket = "quarantineBucket"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", threatsFound)
    stubS3PutObject(s"/$destinationBucket/$fileName")

    new Lambda().process(createEvent("file_event_with_quarantine_bucket"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption

    verifyAntivirus(decoded, threatsFound)
  }

  "The process method" should "copy to clean bucket when s3CleanDestinationBucket is provided in the input for NO_THREATS_FOUND result" in {
    val outputStream = new ByteArrayOutputStream()
    val fileName = "Test.docx"
    val cleanDestinationBucket = "cleanBucket"
    stubS3HeadObject(fileName, s"/testbucket/$fileName")
    stubS3ObjectTagging(s"/testbucket/$fileName?tagging", "GuardDutyMalwareScanStatus", noThreatsFound)
    stubS3PutObject(s"/$cleanDestinationBucket/$fileName")

    new Lambda().process(createEvent("file_event_with_clean_bucket"), outputStream)
    val result = outputStream.toByteArray.map(_.toChar).mkString
    val decoded = decode[FileChecksResult](result).toOption

    verifyAntivirus(decoded, noThreatsFound)
  }

  private def verifyAntivirus(decoded: Option[FileChecksResult], expectedScanResult: String): Unit = {
    decoded.isDefined should be(true)
    decoded.get.antivirus.software should equal("awsGuardDutyMalwareScan")
    decoded.get.antivirus.softwareVersion should equal("AWSGuardDuty")
    decoded.get.antivirus.result should equal(expectedScanResult)
    decoded.get.antivirus.datetime should be > 0L
    decoded.get.antivirus.fileId should equal(fileId)
  }

  private def validateFileChecksResult(expectedChecksum: String, maybeResult: Option[FileChecksResult]): Unit = {
    maybeResult.isDefined should be(true)
    maybeResult.get.checksum.sha256Checksum should equal(expectedChecksum)
    maybeResult.get.checksum.fileId should equal(fileId)
    maybeResult.get.fileFormat.fileId should equal(fileId)
  }
}
