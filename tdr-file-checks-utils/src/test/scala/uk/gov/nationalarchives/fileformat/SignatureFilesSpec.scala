package uk.gov.nationalarchives.fileformat

import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks

class SignatureFilesSpec extends AnyFlatSpec with MockitoSugar with TableDrivenPropertyChecks {
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
