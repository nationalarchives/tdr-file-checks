package uk.gov.nationalarchives.filechecks

import com.typesafe.scalalogging.Logger
import org.apache.commons.io.output.ByteArrayOutputStream

import java.io.ByteArrayInputStream

object LambdaRunner extends App {
  private val logger: Logger = Logger[LambdaRunner.type]
  private val body = """
    |{
    |  "userId": "0473f03b-2f45-4fab-bc89-0b3a55eaaaaa",
    |  "consignmentId": "ad042102-a992-4549-a2f1-bdfbf08bbbbb",
    |  "fileId": "0ef34284-3027-48c6-b4e4-ee8ba92ccccc",
    |  "originalPath": "fullsupport/goodbye.txt",
    |  "s3SourceBucket": "tdr-upload-files-cloudfront-dirty-intg",
    |  "s3SourceBucketKey": "0473f03b-2f45-4fab-bc89-0b3a55eaaaaa/9b11bd20-14b5-4711-b9cc-e276a2c26f1a/0ef34284-3027-48c6-b4e4-ee8ba92ccccc",
    |  "s3QuarantineBucket": "tdr-upload-files-quarantine-intg",
    |  "s3QuarantineBucketKey": "9b11bd20-14b5-4711-b9cc-e276a2c26f1a/0ef34284-3027-48c6-b4e4-ee8ba92ccccc",
    |  "s3CleanDestinationBucket": "tdr-upload-files-intg",
    |  "s3CleanDestinationBucketKey": "9b11bd20-14b5-4711-b9cc-e276a2c26f1a/0ef34284-3027-48c6-b4e4-ee8ba92ccccc"
    |}
    |""".stripMargin

  private val inputStream = new ByteArrayInputStream(body.getBytes())
  private val output = new ByteArrayOutputStream()
  new Lambda().process(inputStream, output)
  logger.info(output.toByteArray.map(_.toChar).mkString)
}
