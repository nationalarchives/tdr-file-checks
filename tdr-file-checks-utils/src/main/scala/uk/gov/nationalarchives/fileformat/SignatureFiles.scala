package uk.gov.nationalarchives.fileformat

import com.typesafe.scalalogging.Logger

import java.io._
import java.nio.file.{Path, Paths}

class SignatureFiles(existingFiles: List[File]) {

  def findSignatureFile(fileType: String): Path = {
    existingFiles
      .find(_.getName.startsWith(fileType))
      .map(f => Paths.get(f.getPath)).getOrElse(throw new FileNotFoundException(s"$fileType signature file not found locally."))
  }
}

object SignatureFiles {
  val logger: Logger = Logger[SignatureFiles]

  def apply(containerSignature: SignatureFile, droidSignature: SignatureFile): SignatureFiles = {

    val containerSignatureFile: String = containerSignature.name + containerSignature.version
    val droidSignatureFile: String = droidSignature.name + droidSignature.version

    val filter: FilenameFilter = (_: File, name: String) =>
      name.startsWith(containerSignatureFile) || name.startsWith(droidSignatureFile)
    val existingFiles = new File(getClass.getResource("/").toURI).listFiles(filter).toList
    new SignatureFiles(existingFiles)
  }
}
