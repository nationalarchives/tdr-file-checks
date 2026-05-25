package uk.gov.nationalarchives.filechecks

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.FFIDMetadataInputValues
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import uk.gov.nationalarchives.aws.utils.s3.S3Clients.{s3, s3Async}
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.filechecks.ApplicationConfig._
import uk.gov.nationalarchives.filechecksutils._

import java.io.{InputStream, OutputStream}
import java.util.UUID
import scala.io.Source
import scala.language.postfixOps

class Lambda {

  private val logger: Logger = Logger[Lambda]

  private val containerSignature: SignatureFile = SignatureFile(containerSignatureName, containerSignatureVersion)
  private val droidSignature: SignatureFile = SignatureFile(droidSignatureName, droidSignatureVersion)

  private val s3AsyncClient = s3Async(s3Endpoint)
  private val s3Client = s3(s3Endpoint)
  private val s3Utils = S3Utils(s3AsyncClient)
  private val droidFileChecksResultExtractor: DroidFileChecksResultExtractor = DroidFileChecksResultExtractor(containerSignature, droidSignature, s3Client)
  private val guardDutyScanResultExtractor: GuardDutyScanResultExtractor = GuardDutyScanResultExtractor(s3AsyncClient)

  def process(inputBody: InputStream, output: OutputStream): Unit = {
    val body = Source.fromInputStream(inputBody).getLines().mkString
    for {
      fileChecksParameters <- IO.fromEither(decode[FileChecksParameters](body))
      droidFileChecksResult <- IO.fromEither(extractDroidFileChecksResults(fileChecksParameters))
      malwareScanResult <- IO(
        guardDutyScanResultExtractor.getMalwareScanResult(
          fileChecksParameters.s3SourceBucket,
          fileChecksParameters.s3SourceBucketKey,
          pollMalwareScanCompleteAwaitSecs
        )
      )
      _ <- copyToCleanDestinationOrQuarantineBucket(fileChecksParameters, malwareScanResult)
      result: FileChecksResult = buildFileChecksResult(fileChecksParameters, droidFileChecksResult, malwareScanResult)
      output <- Resource
        .fromAutoCloseable(IO(output))
        .use(outputStream => {
          outputStream.write(result.asJson.printWith(Printer.noSpaces).getBytes())
          IO.unit
        })
    } yield output
  }.unsafeRunSync()

  private def buildFileChecksResult(fileChecksParameters: FileChecksParameters, droidFileChecksResult: DroidFileChecksResult, malwareScanResult: MalwareScanResult) = {
    val checksum = Checksum(fileChecksParameters.fileId, droidFileChecksResult.checksum)
    val ffidMetadataInputValues = droidFileChecksResult.ffidMetadataInputValues
    val antivirus = Antivirus(
      software = malwareScanResult.software,
      softwareVersion = malwareScanResult.softwareVersion,
      databaseVersion = version,
      result = malwareScanResult.result.map(_.value).getOrElse(""),
      datetime = malwareScanResult.datetime,
      fileId = fileChecksParameters.fileId
    )
    FileChecksResult(checksum, ffidMetadataInputValues, antivirus)
  }

  private def copyToCleanDestinationOrQuarantineBucket(
      fileChecksParameters: FileChecksParameters,
      malwareScanResult: MalwareScanResult
  ): IO[Any] = {

    val (destinationBucket, destinationBucketKey) = malwareScanResult.result match {
      case Some(NO_THREATS_FOUND) => (fileChecksParameters.s3CleanDestinationBucket, fileChecksParameters.s3CleanDestinationBucketKey)
      case _                      => (fileChecksParameters.s3QuarantineBucket, fileChecksParameters.s3QuarantineBucketKey)
    }
    (destinationBucket, destinationBucketKey) match {
      case (Some(bucket), Some(key)) =>
        logger.info("Copying file {} to {} bucket: s3://{}/{}", fileChecksParameters.fileId, bucket, bucket, key)
        s3Utils
          .copyObject(fileChecksParameters.s3SourceBucket, fileChecksParameters.s3SourceBucketKey, bucket, key)
          .map(_ => logger.info("File {} copied to {} bucket: s3://{}/{}", fileChecksParameters.fileId, bucket, bucket, key))
      case _ => IO.unit
    }
  }

  private def extractDroidFileChecksResults(fileChecksParameters: FileChecksParameters): Either[Throwable, DroidFileChecksResult] = for {
    metadata <- droidFileChecksResultExtractor.fileChecksResult(
      fileChecksParameters.consignmentId,
      fileChecksParameters.fileId,
      fileChecksParameters.originalPath,
      fileChecksParameters.s3SourceBucket,
      fileChecksParameters.s3SourceBucketKey
    )
  } yield metadata
}

case class FileChecksParameters(
    consignmentId: UUID,
    fileId: UUID,
    originalPath: String,
    userId: UUID,
    s3SourceBucket: String,
    s3SourceBucketKey: String,
    s3QuarantineBucket: Option[String],
    s3QuarantineBucketKey: Option[String],
    s3CleanDestinationBucket: Option[String],
    s3CleanDestinationBucketKey: Option[String]
)

case class Checksum(fileId: UUID, sha256Checksum: String)
case class Antivirus(software: String, softwareVersion: String, databaseVersion: String, result: String, datetime: Long, fileId: UUID)
case class FileChecksResult(checksum: Checksum, fileFormat: FFIDMetadataInputValues, antivirus: Antivirus)
