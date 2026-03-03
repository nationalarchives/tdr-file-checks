package uk.gov.nationalarchives.fileformat

import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import net.logstash.logback.argument.StructuredArguments.value
import software.amazon.awssdk.services.s3.S3Client
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.APIResult
import uk.gov.nationalarchives.fileformat.FFIDExtractor._

import java.net.URI
import java.nio.file.Paths
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

class FFIDExtractor(api: DroidAPI) {

  private def fileExtension(filePath: String): String = {
    Paths.get(filePath).getFileName.toString.split("\\.").last
  }

  def ffidFile(consignmentId: UUID, fileId: UUID, originalPath: String, s3Bucket: String, s3ObjectKey: String): Either[Throwable, FFIDMetadataInputValues] = {
    Try {
      val extension = fileExtension(originalPath)
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results: List[APIResult] = api.submit(URI.create(s"s3://$s3Bucket/$s3ObjectKey"), extension).asScala.toList

      val matches = results.flatMap(_.identificationResults().asScala) match {
        case Nil => List(FFIDMetadataInputMatches(None, "", None, None, None))
        case results => results.map(res => {
          FFIDMetadataInputMatches(Option(res.extension()), res.method().getMethod, Option(res.puid()), Option(res.fileExtensionMismatch()), Option(res.name()))
        })
      }

      logger.info(
        "File metadata with {} matches found for file ID {} in consignment ID {}",
        value("matchCount", matches.length),
        value("fileId", fileId),
        value("consignmentId", consignmentId)
      )
      FFIDMetadataInputValues(fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
    }
      .toEither.left.map(err => {
        logger.error(
          "Error processing file ID {}' in consignment ID {}", value("fileId", fileId),
          value("consignmentId", consignmentId)
        )
        new RuntimeException(s"Error processing file id $fileId with original path $originalPath", err)
      })
  }
}

object FFIDExtractor {

  val logger: Logger = Logger[FFIDExtractor]

  def apply(containerSignature: SignatureFile, droidSignature: SignatureFile, s3Client: S3Client): FFIDExtractor = {
    val signatureFiles = SignatureFiles(containerSignature, droidSignature)
    val containerSignatureFile: String = containerSignature.name + containerSignature.version
    val droidSignatureFile: String = droidSignature.name + droidSignature.version
    val api: DroidAPI = DroidAPI.builder()
      .containerSignature(signatureFiles.findSignatureFile(containerSignatureFile))
      .binarySignature(signatureFiles.findSignatureFile(droidSignatureFile))
      .s3Client(s3Client)
      .build()
    new FFIDExtractor(api)
  }
}

case class SignatureFile(name: String, version: String)
