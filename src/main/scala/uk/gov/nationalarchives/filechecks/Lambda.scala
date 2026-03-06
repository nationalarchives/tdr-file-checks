package uk.gov.nationalarchives.filechecks

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import com.typesafe.config.{Config, ConfigFactory}
import graphql.codegen.types.FFIDMetadataInputValues
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import uk.gov.nationalarchives.aws.utils.s3.S3Clients.s3
import uk.gov.nationalarchives.filechecksutils.{DroidFileChecksResult, DroidFileChecksResultExtractor, SignatureFile}

import java.io.{InputStream, OutputStream}
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

  private val s3Client = s3(configFactory.getString("s3.endpoint"))
  private val droidFileChecksResultExtractor: DroidFileChecksResultExtractor = DroidFileChecksResultExtractor(containerSignature, droidSignature, s3Client)

  def process(input: InputStream, output: OutputStream): Unit = {
    val body = Source.fromInputStream(input).getLines().mkString
    for {
      fileChecksParameters <- IO.fromEither(decode[FileChecksParameters](body))
      droidFileChecksResult <- IO.fromEither(extractDroidFileChecksResults(fileChecksParameters))
      ffidMetadataInputValues = droidFileChecksResult.ffidMetadataInputValues
      checksum = Checksum(fileChecksParameters.fileId, droidFileChecksResult.checksum)
      output <- Resource
        .fromAutoCloseable(IO(output))
        .use(outputStream => {
          outputStream.write(FileChecksResult(checksum, ffidMetadataInputValues).asJson.printWith(Printer.noSpaces).getBytes())
          IO.unit
        })
    } yield output
  }.unsafeRunSync()

  private def extractDroidFileChecksResults(fileChecksParameters: FileChecksParameters): Either[Throwable, DroidFileChecksResult] = for {
    metadata <- droidFileChecksResultExtractor.fileChecksResult(
      fileChecksParameters.consignmentId,
      fileChecksParameters.fileId,
      fileChecksParameters.originalPath,
      fileChecksParameters.s3SourceBucket.name,
      fileChecksParameters.s3SourceBucket.objectKey
    )
  } yield metadata
}

case class S3Bucket(name: String, objectKey: String)

case class FileChecksParameters(
    consignmentId: UUID,
    fileId: UUID,
    originalPath: String,
    userId: UUID,
    s3SourceBucket: S3Bucket,
    s3CleanDestinationBucket: Option[S3Bucket],
    s3DirtyBucket: Option[S3Bucket]
)

case class Checksum(fileId: UUID, sha256Checksum: String)

case class FileChecksResult(checksum: Checksum, ffidMetadataInputValues: FFIDMetadataInputValues)
