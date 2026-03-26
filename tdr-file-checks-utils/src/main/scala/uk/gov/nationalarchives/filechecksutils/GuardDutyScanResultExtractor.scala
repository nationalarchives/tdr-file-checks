package uk.gov.nationalarchives.filechecksutils

import com.typesafe.scalalogging.Logger
import software.amazon.awssdk.services.s3.S3AsyncClient
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.filechecksutils.GuardDutyScanResultExtractor._

class GuardDutyScanResultExtractor(s3Utils: S3Utils) {
  // Poll for GuardDuty scan completion
  @annotation.tailrec
  private def pollGuardDutyScanComplete(bucket: String, objectKey: String, awaitDelaySecs: Long): String = {
    logger.info(s"Polling for GuardDuty scan result: $objectKey")

    val tagSet = s3Utils.getObjectTags(bucket, objectKey)

    val malwareScanResult = tagSet.get(scanCompleteTagKey)

    malwareScanResult match {
      case Some(tag) =>
        logger.info(s"Polling for GuardDuty scan result completed: $objectKey")
        tag
      case None =>
        Thread.sleep(awaitDelaySecs * 1000)
        pollGuardDutyScanComplete(bucket, objectKey, awaitDelaySecs)
    }
  }

  def getMalwareScanResult(bucketName: String, objectKey: String, pollMalwareScanCompleteAwaitSecs: Long = 5L): MalwareScanResult = {
    val scanResult = pollGuardDutyScanComplete(bucketName, objectKey, pollMalwareScanCompleteAwaitSecs)
    val result = if (scanResult == THREATS_FOUND.toString) {
      logger.info(s"GuardDuty scan result: $THREATS_FOUND for s3://$bucketName/$objectKey")
      THREATS_FOUND
    } else {
      logger.info(s"GuardDuty scan result: $NO_THREATS_FOUND for s3://$bucketName/$objectKey")
      NO_THREATS_FOUND
    }

    MalwareScanResult(
      software = awsGuardDutyMalwareScan,
      softwareVersion = awsGuardDuty,
      result = result,
      datetime = System.currentTimeMillis()
    )
  }
}

object GuardDutyScanResultExtractor {
  private val logger = Logger[GuardDutyScanResultExtractor]

  private val scanCompleteTagKey = "GuardDutyMalwareScanStatus"
  private val awsGuardDutyMalwareScan = "awsGuardDutyMalwareScan"
  private val awsGuardDuty = "AWSGuardDuty"

  def apply(s3AsyncClient: S3AsyncClient): GuardDutyScanResultExtractor =
    new GuardDutyScanResultExtractor(S3Utils(s3AsyncClient))
}

sealed trait MalwareScanStatus
case object NO_THREATS_FOUND extends MalwareScanStatus
case object THREATS_FOUND extends MalwareScanStatus
case class MalwareScanResult(software: String, softwareVersion: String, result: MalwareScanStatus, datetime: Long)
