package uk.gov.nationalarchives.checksum

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import graphql.codegen.types.FFIDMetadataInputValues
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import uk.gov.nationalarchives.aws.utils.s3.S3Clients.s3Async
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.fileformat.{FFIDExtractor, SignatureFile}

import java.io.{File, InputStream, OutputStream}
import java.nio.file.Paths
import java.util.UUID
import scala.io.Source
import scala.language.postfixOps

class Lambda {

  private val configFactory: Config = ConfigFactory.load
  private val containerSignatureName = configFactory.getString("containers.signature.name")
  private val containerSignatureVersion = configFactory.getString("containers.version")
  private val droidSignatureName = configFactory.getString("droid.signature.name")
  private val droidSignatureVersion = configFactory.getString("droid.version")

  private val containerSignature: SignatureFile = SignatureFile(containerSignatureName, containerSignatureVersion)
  private val droidSignature: SignatureFile = SignatureFile(droidSignatureName, droidSignatureVersion)

  private val ffidExtractor: FFIDExtractor = FFIDExtractor(containerSignature, droidSignature)

  def process(input: InputStream, output: OutputStream): Unit = {
    val body = Source.fromInputStream(input).getLines().mkString
    for {
      fileChecksParameters <- IO.fromEither(decode[FileChecksParameters](body))
      _ <- download(fileChecksParameters)
      checksum <- ChecksumGenerator.generate(getFilePath(fileChecksParameters), configFactory.getInt("chunk.size"))
      extractedFFID <- IO.fromEither(extractFFID(fileChecksParameters))
      output <- Resource.fromAutoCloseable(IO(output)).use(outputStream => {
        outputStream.write(FileChecksResult(Checksum(fileChecksParameters.fileId, checksum), extractedFFID).asJson.printWith(Printer.noSpaces).getBytes())
        IO.unit
      })
    } yield output
  }.unsafeRunSync()

  private def getFilePath(fileChecksParameters: FileChecksParameters) = s"""${configFactory.getString("root.directory")}/${fileChecksParameters.consignmentId}/${fileChecksParameters.originalPath}"""

  private def extractFFID(fileChecksParameters: FileChecksParameters): Either[Throwable, FFIDMetadataInputValues] = for {
    metadata <- ffidExtractor.ffidFile(
      fileChecksParameters.consignmentId,
      fileChecksParameters.fileId,
      fileChecksParameters.originalPath,
      fileChecksParameters.s3SourceBucket.name,
      fileChecksParameters.s3SourceBucket.objectKey
    )
  } yield metadata

  private def download(fileChecksParameters: FileChecksParameters): IO[Any] = {
    val s3Utils = S3Utils(s3Async(configFactory.getString("s3.endpoint")))
    val filePath = getFilePath(fileChecksParameters)
    if (new File(filePath).exists()) {
      IO.unit
    } else {
      IO(new File(filePath.split("/").dropRight(1).mkString("/")).mkdirs()).flatMap(_ => {
        s3Utils.downloadFiles(fileChecksParameters.s3SourceBucket.name, fileChecksParameters.s3SourceBucket.objectKey, Paths.get(filePath).some)
      })
    }
  }
}

case class S3Bucket(name: String, objectKey: String)

case class FileChecksParameters(consignmentId: UUID, fileId: UUID, originalPath: String, userId: UUID, s3SourceBucket: S3Bucket, s3CleanDestinationBucket: Option[S3Bucket], s3DirtyBucket: Option[S3Bucket])

case class Checksum(fileId: UUID, sha256Checksum: String)

case class FileChecksResult(checksum: Checksum, fileFormat: FFIDMetadataInputValues)
