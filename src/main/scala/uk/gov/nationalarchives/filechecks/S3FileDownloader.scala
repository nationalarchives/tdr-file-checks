package uk.gov.nationalarchives.filechecks

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.Logger
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import uk.gov.nationalarchives.aws.utils.s3.S3Utils

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class S3FileDownloader(s3Client: S3AsyncClient, maxAttempts: Int = 3, retryDelay: FiniteDuration = 1.second) {

  private val logger: Logger = Logger[S3FileDownloader]
  private val downloadedFileName: String = "source"
  private lazy val s3Utils: S3Utils = S3Utils(s3Client)

  def objectSize(bucket: String, key: String): IO[Long] =
    IO.fromFuture(IO {
      import scala.jdk.FutureConverters._
      s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).asScala
    }).map(_.contentLength())

  def withDownloadedFile[A](bucket: String, key: String)(fn: Path => IO[A]): IO[A] =
    Resource
      .make(IO.blocking(Files.createTempDirectory("tdr-file-checks-")))(directory =>
        IO.blocking {
          Files.deleteIfExists(directory.resolve(downloadedFileName))
          Files.deleteIfExists(directory)
        }.void
      )
      .use { directory =>
        val path = directory.resolve(downloadedFileName)
        downloadWithRetry(bucket, key, path, maxAttempts).flatMap(_ => fn(path))
      }

  private def downloadWithRetry(bucket: String, key: String, path: Path, attemptsRemaining: Int): IO[Unit] =
    downloadToFile(bucket, key, path).handleErrorWith { throwable =>
      if (attemptsRemaining > 1) {
        IO(logger.warn(s"Download of s3://$bucket/$key failed, retrying (${attemptsRemaining - 1} attempts remaining)", throwable)) *>
          IO.sleep(retryDelay) *>
          downloadWithRetry(bucket, key, path, attemptsRemaining - 1)
      } else IO.raiseError(throwable)
    }

  private def downloadToFile(bucket: String, key: String, path: Path): IO[Unit] =
    Resource
      .fromAutoCloseable(IO.blocking(s3Utils.getObjectAsStreamingInputStream(bucket, key)))
      .use(inputStream => IO.blocking(Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING)).void)
}

object S3FileDownloader {
  def apply(s3Client: S3AsyncClient): S3FileDownloader = new S3FileDownloader(s3Client)
}
