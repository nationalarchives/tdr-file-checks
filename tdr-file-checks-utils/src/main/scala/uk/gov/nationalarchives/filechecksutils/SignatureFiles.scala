package uk.gov.nationalarchives.filechecksutils

import com.typesafe.scalalogging.Logger

import java.io._
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.util.Using

class SignatureFiles(existingFiles: List[Path]) {

  def findSignatureFile(fileType: String): Path = {
    existingFiles
      .find(_.getFileName.toString.startsWith(fileType))
      .getOrElse(throw new FileNotFoundException(s"$fileType signature file not found locally."))
  }
}

object SignatureFiles {
  val logger: Logger = Logger[SignatureFiles]

  def apply(containerSignature: SignatureFile, droidSignature: SignatureFile): SignatureFiles = {

    val containerSignatureFile: String = containerSignature.name + containerSignature.version
    val droidSignatureFile: String = droidSignature.name + droidSignature.version

    def signatureFilePath(fileName: String): Path = {
      val resource = Option(getClass.getResource(s"/$fileName.xml"))
        .getOrElse(throw new FileNotFoundException(s"$fileName signature file not found locally."))

      resource.toURI.getScheme match {
        case "file" => Paths.get(resource.toURI)
        case _ =>
          val tempDirectory = Files.createTempDirectory("signature-files")
          tempDirectory.toFile.deleteOnExit()
          val tempFile = tempDirectory.resolve(s"$fileName.xml")
          tempFile.toFile.deleteOnExit()
          Using.resource(resource.openStream()) { inputStream =>
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
          }
          tempFile
      }
    }

    val existingFiles = List(containerSignatureFile, droidSignatureFile).map(signatureFilePath)
    new SignatureFiles(existingFiles)
  }
}
