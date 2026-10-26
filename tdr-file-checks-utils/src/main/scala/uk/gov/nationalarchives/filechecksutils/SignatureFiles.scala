package uk.gov.nationalarchives.filechecksutils

import com.typesafe.scalalogging.Logger

import java.io.FileNotFoundException
import java.nio.file.{Path, Paths}

class SignatureFiles(existingFiles: List[Path]) {

  def findSignatureFile(fileType: String): Path = {
    existingFiles
      .find(_.getFileName.toString.startsWith(fileType))
      .getOrElse(throw new FileNotFoundException(s"$fileType signature file not found locally."))
  }
}

object SignatureFiles {
  val logger: Logger = Logger[SignatureFiles]

  private val signatureFileExtension = ".xml"

  def apply(containerSignature: SignatureFile, droidSignature: SignatureFile): SignatureFiles = {

    val containerSignatureFile: String = containerSignature.name + containerSignature.version
    val droidSignatureFile: String = droidSignature.name + droidSignature.version

    val existingFiles = List(containerSignatureFile, droidSignatureFile).flatMap { fileName =>
      Option(getClass.getResource(s"/$fileName$signatureFileExtension")).map(url => Paths.get(url.toURI))
    }
    new SignatureFiles(existingFiles)
  }
}
