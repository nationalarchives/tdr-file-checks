package uk.gov.nationalarchives.filechecksutils

import com.typesafe.scalalogging.Logger
import graphql.codegen.types.{FFIDMetadataInputMatches, FFIDMetadataInputValues}
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.APIResult
import uk.gov.nationalarchives.droid.internal.api.DroidAPI
import uk.gov.nationalarchives.filechecksutils.DroidFileChecksResultExtractor._

import java.net.URI
import java.nio.file.Paths
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

class DroidFileChecksResultExtractor(api: DroidAPI) {

  private def fileExtension(filePath: String): Option[String] = {
    val name = Paths.get(filePath).getFileName.toString
    if (name.contains(".")) { Some(name.split("\\.").last) } else { None }
  }

  def fileChecksResult(fileId: UUID, originalPath: String, fileUri: URI): Either[Throwable, DroidFileChecksResult] = {
    Try {
      val extension = fileExtension(originalPath)
      val droidVersion = api.getDroidVersion
      val containerSignatureVersion = api.getContainerSignatureVersion
      val droidSignatureVersion = api.getBinarySignatureVersion
      val results: List[APIResult] = extension match {
        case Some(ext) => api.submit(fileUri, ext).asScala.toList
        case None => api.submit(fileUri).asScala.toList
      }

      val matches = results.flatMap(_.identificationResults().asScala) match {
        case Nil     => List(FFIDMetadataInputMatches(None, "", None, None, None))
        case results =>
          results.map(res => {
            FFIDMetadataInputMatches(Option(res.extension()), res.method().getMethod, Option(res.puid()), Option(res.fileExtensionMismatch()), Option(res.name()))
          })
      }

      val ffidMetadataInputValues = FFIDMetadataInputValues(fileId, "Droid", droidVersion, droidSignatureVersion, containerSignatureVersion, "pronom", matches)
      DroidFileChecksResult(ffidMetadataInputValues)
    }.toEither.left.map(err => new RuntimeException(s"Error processing file id $fileId with original path $originalPath", err))
  }
}

object DroidFileChecksResultExtractor {

  val logger: Logger = Logger[DroidFileChecksResultExtractor]

  def apply(containerSignature: SignatureFile, droidSignature: SignatureFile): DroidFileChecksResultExtractor = {
    val signatureFiles = SignatureFiles(containerSignature, droidSignature)
    val containerSignatureFile: String = containerSignature.name + containerSignature.version
    val droidSignatureFile: String = droidSignature.name + droidSignature.version
    val api: DroidAPI = DroidAPI
      .builder()
      .containerSignature(signatureFiles.findSignatureFile(containerSignatureFile))
      .binarySignature(signatureFiles.findSignatureFile(droidSignatureFile))
      .build()
    new DroidFileChecksResultExtractor(api)
  }
}

case class SignatureFile(name: String, version: String)
case class DroidFileChecksResult(ffidMetadataInputValues: FFIDMetadataInputValues)
