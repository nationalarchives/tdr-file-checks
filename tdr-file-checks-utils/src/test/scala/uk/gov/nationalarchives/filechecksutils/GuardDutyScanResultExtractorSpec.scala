package uk.gov.nationalarchives.filechecksutils

import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{be, not}
import org.scalatest.matchers.should.Matchers.{convertToAnyShouldWrapper, equal}
import uk.gov.nationalarchives.aws.utils.s3.S3Utils

class GuardDutyScanResultExtractorSpec extends AnyFlatSpec with MockitoSugar {

  "The getMalwareScanResult method" should "return result as 'NO_THREATS_FOUND' when GuardDutyMalwareScanStatus tag value is 'NO_THREATS_FOUND'" in {
    val mockS3Utils = mock[S3Utils]

    when(mockS3Utils.getObjectTags("testBucket", "objectKey")).thenReturn(Map.empty, Map("GuardDutyMalwareScanStatus" -> "NO_THREATS_FOUND"))

    val result = new GuardDutyScanResultExtractor(mockS3Utils).getMalwareScanResult("testBucket", "objectKey")

    result.result should equal(NO_THREATS_FOUND)
    result.software should be("awsGuardDutyMalwareScan")
    result.datetime should not equal 0L
    result.softwareVersion should be("AWSGuardDuty")

    verify(mockS3Utils, times(2)).getObjectTags("testBucket", "objectKey")
  }

  "The getMalwareScanResult method" should "return result as 'THREATS_FOUND' when GuardDutyMalwareScanStatus tag value is 'THREATS_FOUND'" in {
    val mockS3Utils = mock[S3Utils]

    when(mockS3Utils.getObjectTags("testBucket", "objectKey")).thenReturn(Map("GuardDutyMalwareScanStatus" -> "THREATS_FOUND", "OtherTag" -> "Value"))

    val result = new GuardDutyScanResultExtractor(mockS3Utils).getMalwareScanResult("testBucket", "objectKey")

    result.result should equal(THREATS_FOUND)
    result.software should be("awsGuardDutyMalwareScan")
    result.softwareVersion should be("AWSGuardDuty")

    verify(mockS3Utils, times(1)).getObjectTags("testBucket", "objectKey")
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

    result.result should equal(NO_THREATS_FOUND)
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

  "The getMalwareScanResult method" should "return NO_THREATS_FOUND for any status other than 'THREATS_FOUND'" in {
    val mockS3Utils = mock[S3Utils]

    when(mockS3Utils.getObjectTags("testBucket", "objectKey")).thenReturn(Map("GuardDutyMalwareScanStatus" -> "SCAN_IN_PROGRESS"))

    val result = new GuardDutyScanResultExtractor(mockS3Utils).getMalwareScanResult("testBucket", "objectKey")

    result.result should equal(NO_THREATS_FOUND)
    result.software should be("awsGuardDutyMalwareScan")
  }
}
