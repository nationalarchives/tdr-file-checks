package uk.gov.nationalarchives.filechecks

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.Logger
import graphql.codegen.types.FFIDMetadataInputValues
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.filechecks.ApplicationConfig._
import uk.gov.nationalarchives.filechecksutils._
import software.amazon.awssdk.services.s3.S3AsyncClient

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.io.Source
import scala.language.postfixOps

class Lambda {

  private val logger: Logger = Logger[Lambda]

  private val downloadedFileName: String = "source"
  private val downloadMaxAttempts: Int = 3
  private val downloadRetryDelay: FiniteDuration = 1.second

  private val containerSignature: SignatureFile = SignatureFile(containerSignatureName, containerSignatureVersion)
  private val droidSignature: SignatureFile = SignatureFile(droidSignatureName, droidSignatureVersion)

  private lazy val s3Client: S3AsyncClient = S3ClientProvider.s3AsyncClient
  private lazy val s3Utils: S3Utils = S3Utils(s3Client)
  private lazy val s3CopyUtils: S3Utils = S3Utils(S3ClientProvider.s3CopyAsyncClient)
  private val droidFileChecksResultExtractor: DroidFileChecksResultExtractor = DroidFileChecksResultExtractor(containerSignature, droidSignature)
  private lazy val guardDutyScanResultExtractor: GuardDutyScanResultExtractor = GuardDutyScanResultExtractor(s3Client)

  def process(inputBody: InputStream, output: OutputStream): Unit = (for {
    body <- IO(Source.fromInputStream(inputBody).getLines().mkString)
    fileChecksParameters <- IO.fromEither(decode[FileChecksParameters](body))
    fileChecksResult <- processFileChecks(fileChecksParameters)
    _ <- IO(output.write(fileChecksResult.asJson.printWith(Printer.noSpaces).getBytes(UTF_8)))
  } yield ())
    .handleErrorWith { throwable =>
      IO(logger.error("Error processing file checks", throwable)) *>
        IO(output.write(Map("error" -> throwable.getMessage).asJson.printWith(Printer.noSpaces).getBytes(UTF_8)))
    }
    .unsafeRunSync()

  private def processFileChecks(fileChecksParameters: FileChecksParameters): IO[FileChecksResult] =
    getObjectSize(fileChecksParameters.s3SourceBucket, fileChecksParameters.s3SourceBucketKey).flatMap { size =>
      if (size > largeFileThresholdBytes) {
        val s3FilePath = Paths.get(s3FilesMountPoint, fileChecksParameters.s3SourceBucketKey)
        IO(logger.info("File {} exceeds threshold ({}B > {}B), using S3 Files mount at {}", fileChecksParameters.s3SourceBucketKey, size, largeFileThresholdBytes, s3FilePath)) *>
          runFileChecks(fileChecksParameters, s3FilePath)
      } else {
        IO(logger.info("File {} under threshold ({}B <= {}B), downloading to local", fileChecksParameters.s3SourceBucketKey, size, largeFileThresholdBytes)) *>
          withDownloadedLocalFile(fileChecksParameters.s3SourceBucket, fileChecksParameters.s3SourceBucketKey) { localFile =>
            runFileChecks(fileChecksParameters, localFile)
          }
      }
    }

  private def withDownloadedLocalFile[A](bucket: String, key: String)(fn: Path => IO[A]): IO[A] =
    Resource
      .make(IO.blocking(Files.createTempDirectory("tdr-file-checks-")))(directory =>
        IO.blocking {
          Files.deleteIfExists(directory.resolve(downloadedFileName))
          Files.deleteIfExists(directory)
        }.void
      )
      .use { directory =>
        val path = directory.resolve(downloadedFileName)
        downloadWithRetry(bucket, key, path, downloadMaxAttempts).flatMap(_ => fn(path))
      }

  private def downloadWithRetry(bucket: String, key: String, path: Path, attemptsRemaining: Int): IO[Unit] =
    downloadToFile(bucket, key, path).handleErrorWith { throwable =>
      if (attemptsRemaining > 1) {
        IO(logger.warn(s"Download of s3://$bucket/$key failed, retrying (${attemptsRemaining - 1} attempts remaining)", throwable)) *>
          IO.sleep(downloadRetryDelay) *>
          downloadWithRetry(bucket, key, path, attemptsRemaining - 1)
      } else IO.raiseError(throwable)
    }

  // Streaming the object and writing it ourselves keeps the SDK away from the filesystem. Letting the SDK write the
  // file makes its internal retries fail with FileAlreadyExistsException, which masks the error that caused the retry.
  private def downloadToFile(bucket: String, key: String, path: Path): IO[Unit] =
    Resource
      .fromAutoCloseable(IO.blocking(s3Utils.getObjectAsStreamingInputStream(bucket, key)))
      .use(inputStream => IO.blocking(Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING)).void)

  private def getObjectSize(bucket: String, key: String): IO[Long] =
    IO.fromFuture(IO {
      import scala.jdk.FutureConverters._
      s3Client
        .headObject(
          software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder().bucket(bucket).key(key).build()
        )
        .asScala
    }).map(_.contentLength())

  private def runFileChecks(fileChecksParameters: FileChecksParameters, filePath: Path): IO[FileChecksResult] =
    for {
      parallelResults <- IO.both(
        runDroidAndChecksumInParallel(fileChecksParameters, filePath),
        timed(s"Malware scan poll for ${fileChecksParameters.fileId}")(
          IO.blocking(
            guardDutyScanResultExtractor.getMalwareScanResult(
              fileChecksParameters.s3SourceBucket,
              fileChecksParameters.s3SourceBucketKey,
              pollMalwareScanCompleteAwaitSecs
            )
          )
        ).flatMap { malwareScanResult =>
          timed(s"Copy of ${fileChecksParameters.fileId} to destination bucket")(
            copyToCleanDestinationOrQuarantineBucket(fileChecksParameters, malwareScanResult)
          ).as(malwareScanResult)
        }
      )
      ((droidResult, checksumResult), malwareScanResult) = parallelResults
      result = buildFileChecksResult(fileChecksParameters, droidResult, checksumResult, malwareScanResult)
    } yield result

  private def runDroidAndChecksumInParallel(fileChecksParameters: FileChecksParameters, filePath: Path): IO[(DroidFileChecksResult, String)] =
    for {
      result <- IO.both(
        timed(s"DROID identification of ${fileChecksParameters.fileId}")(
          IO.blocking {
            droidFileChecksResultExtractor.fileChecksResult(
              fileChecksParameters.fileId,
              fileChecksParameters.originalPath,
              filePath.toUri
            )
          }.flatMap(IO.fromEither)
        ),
        timed(s"Checksum of ${fileChecksParameters.fileId}")(ChecksumCalculator.calculateChecksum(filePath))
      )
      (droidResult, checksumResult) = result
    } yield (droidResult, checksumResult)

  private def timed[A](description: String)(io: IO[A]): IO[A] =
    for {
      start <- IO.monotonic
      result <- io
      end <- IO.monotonic
      _ <- IO(logger.info("{} took {}ms", description, (end - start).toMillis))
    } yield result

  private def buildFileChecksResult(
      fileChecksParameters: FileChecksParameters,
      droidFileChecksResult: DroidFileChecksResult,
      checksumValue: String,
      malwareScanResult: MalwareScanResult
  ) = {
    val checksum = Checksum(fileChecksParameters.fileId, checksumValue)
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
        s3CopyUtils
          .copyObject(fileChecksParameters.s3SourceBucket, fileChecksParameters.s3SourceBucketKey, bucket, key)
          .map(_ => logger.info("File {} copied to {} bucket: s3://{}/{}", fileChecksParameters.fileId, bucket, bucket, key))
      case _ => IO.unit
    }
  }

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
