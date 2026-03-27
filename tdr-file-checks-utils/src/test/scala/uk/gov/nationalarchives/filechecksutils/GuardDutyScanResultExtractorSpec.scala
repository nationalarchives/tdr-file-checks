package uk.gov.nationalarchives.filechecksutils

import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, equal, be}
import org.scalatest.prop.TableDrivenPropertyChecks.{Table, forAll}
import uk.gov.nationalarchives.aws.utils.s3.S3Utils

class GuardDutyScanResultExtractorSpec extends AnyFlatSpec with MockitoSugar {

  "The getMalwareScanResult method" should "return correct result for all MalwareScanStatus values" in {
    val statusTable = Table(
      ("statusValue", "expectedValue"),
      ("NO_THREATS_FOUND", NO_THREATS_FOUND),
      ("THREATS_FOUND", THREATS_FOUND),
      ("UNSUPPORTED", UNSUPPORTED),
      ("ACCESS_DENIED", ACCESS_DENIED),
      ("FAILED", FAILED)
    )

    forAll(statusTable) { (statusValue, expectedValue) =>
      val mockS3Utils = mock[S3Utils]

      when(mockS3Utils.getObjectTags("testBucket", "objectKey")).thenReturn(Map("GuardDutyMalwareScanStatus" -> statusValue))

      val result = new GuardDutyScanResultExtractor(mockS3Utils).getMalwareScanResult("testBucket", "objectKey")

      result.result.get should equal(expectedValue)
      result.software should be("awsGuardDutyMalwareScan")
      result.softwareVersion should be("AWSGuardDuty")

      verify(mockS3Utils, times(1)).getObjectTags("testBucket", "objectKey")
    }
  }

  "The getMalwareScanResult method" should "poll multiple times before GuardDutyMalwareScanStatus tag is found" in {
    val mockS3Utils = mock[S3Utils]

    when(mockS3Utils.getObjectTags("testBucket", "objectKey"))
      .thenReturn(
        Map.empty,
        Map.empty,
        Map.empty,
        Map("GuardDutyMalwareScanStatus" -> "NO_THREATS_FOUND")
      )

    val result = new GuardDutyScanResultExtractor(mockS3Utils).getMalwareScanResult("testBucket", "objectKey", 1)

    result.result.get should equal(NO_THREATS_FOUND)
    result.software should be("awsGuardDutyMalwareScan")

    verify(mockS3Utils, times(4)).getObjectTags("testBucket", "objectKey")
  }

  "The getMalwareScanResult method" should "correctly set datetime to current time when scan completes" in {
    val mockS3Utils = mock[S3Utils]
    val beforeTime = System.currentTimeMillis()

    when(mockS3Utils.getObjectTags("testBucket", "objectKey")).thenReturn(Map("GuardDutyMalwareScanStatus" -> "NO_THREATS_FOUND"))

    val result = new GuardDutyScanResultExtractor(mockS3Utils).getMalwareScanResult("testBucket", "objectKey")
    val afterTime = System.currentTimeMillis()

    result.datetime should be >= beforeTime
    result.datetime should be <= afterTime
  }

  "The MalwareScanStatus.fromString method" should "convert all valid status strings to their corresponding enums" in {
    val statusTable = Table(
      ("statusString", "expectedEnum"),
      ("NO_THREATS_FOUND", Some(NO_THREATS_FOUND)),
      ("THREATS_FOUND", Some(THREATS_FOUND)),
      ("UNSUPPORTED", Some(UNSUPPORTED)),
      ("ACCESS_DENIED", Some(ACCESS_DENIED)),
      ("FAILED", Some(FAILED))
    )

    forAll(statusTable) { (statusString, expectedEnum) =>
      val result = MalwareScanStatus.fromString(statusString)

      result should equal(expectedEnum)
    }
  }

  "The MalwareScanStatus.fromString method" should "return None for invalid status" in {
    MalwareScanStatus.fromString("invalidStatus") should equal(None)
  }
}
