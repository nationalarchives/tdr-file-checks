package uk.gov.nationalarchives.filechecksutils

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

class SignatureFilesSpec extends AnyFlatSpec {
  private val containerSignatureName = "container-signature-"
  private val containerSignatureVersion = "20240715"
  private val droidSignatureName = "DROID_SignatureFile_V"
  private val droidSignatureVersion = "120"

  val containerSignature: SignatureFile = SignatureFile(containerSignatureName, containerSignatureVersion)
  val droidSignature: SignatureFile = SignatureFile(droidSignatureName, droidSignatureVersion)

  val signatureFiles: SignatureFiles = SignatureFiles(containerSignature, droidSignature)

  "findSignatureFile" should s"find and return file path of container signature" in {
    val fileResponse = signatureFiles.findSignatureFile(containerSignature.name + containerSignature.version)
    fileResponse.getFileName.toString should be(containerSignature.name + containerSignature.version + ".xml")
  }

  "findSignatureFile" should s"find and return file path of droid signature" in {
    val fileResponse = signatureFiles.findSignatureFile(droidSignature.name + droidSignature.version)
    fileResponse.getFileName.toString should be(droidSignature.name + droidSignature.version + ".xml")
  }
}
