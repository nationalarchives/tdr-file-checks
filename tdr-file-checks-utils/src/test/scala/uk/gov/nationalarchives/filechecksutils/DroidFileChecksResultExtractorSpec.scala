package uk.gov.nationalarchives.filechecksutils

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableFor3
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.{APIIdentificationResult, APIResult}
import uk.gov.nationalarchives.droid.internal.api.{DroidAPI, HashAlgorithm}

import java.net.URI
import java.nio.file.Paths
import java.util.UUID
import scala.jdk.CollectionConverters._

class DroidFileChecksResultExtractorSpec extends TestUtils with MockitoSugar with EitherValues {
  val fileId: UUID = UUID.randomUUID()
  val mockUri: URI = URI.create("/some/uri")
  val localFileUri: URI = URI.create("file:///tmp/test-file")

  "The fileChecksResult method" should "return the correct droid and signature version" in {
    val mockApi = mock[DroidAPI]
    val testDroidVersion = "TEST_DROID_VERSION"
    val testBinarySignatureVersion = "TEST_BINARY_SIGNATURE_VERSION"
    val testContainerSignatureVersion = "TEST_CONTAINER_SIGNATURE_VERSION"

    when(mockApi.getDroidVersion).thenReturn(testDroidVersion)
    when(mockApi.getBinarySignatureVersion).thenReturn(testBinarySignatureVersion)
    when(mockApi.getContainerSignatureVersion).thenReturn(testContainerSignatureVersion)

    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath", localFileUri)

    val ffid = result.value.ffidMetadataInputValues
    ffid.softwareVersion should equal(testDroidVersion)
    ffid.containerSignatureFileVersion should equal(testContainerSignatureVersion)
    ffid.binarySignatureFileVersion should equal(testBinarySignatureVersion)
  }

  "The fileChecksResult method" should "return the correct value if the extension and puid are empty" in {
    val mockApi = mock[DroidAPI]
    val identificationResult = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", false, mockUri)
    val mockResult = new APIResult(List(identificationResult).asJava, Map.empty[HashAlgorithm, String].asJava)
    when(mockApi.submit(localFileUri, "txt")).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath.txt", localFileUri)
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.extension.isEmpty should be(true)
    m.puid.isEmpty should be(true)
  }

  "The fileChecksResult method" should "return a file extension mismatch if one exists" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", true, mockUri)
    val mockResult = new APIResult(List(apiIdentification).asJava, Map.empty[HashAlgorithm, String].asJava)
    when(mockApi.submit(localFileUri, "pdf")).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath.pdf", localFileUri)
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.fileExtensionMismatch should be(Some(true))
  }

  "The fileChecksResult method" should "call API without file extension if given file has no extension" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", true, mockUri)
    val mockResult = new APIResult(List(apiIdentification).asJava, Map.empty[HashAlgorithm, String].asJava)
    when(mockApi.submit(localFileUri)).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath", localFileUri)
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.fileExtensionMismatch should be(Some(true))
  }

  "The fileChecksResult method" should "return a file format name if one exists" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, ".formatName", true, mockUri)

    val mockResult = new APIResult(List(apiIdentification).asJava, Map.empty[HashAlgorithm, String].asJava)
    when(mockApi.submit(localFileUri, "txt")).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath.txt", localFileUri)
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.formatName should be(Some(".formatName"))
  }

  "The fileChecksResult method" should "return more than one result for multiple result rows" in {
    val mockApi = mock[DroidAPI]
    val apiResults = for {
      count <- List("1", "2", "3")
      res <- new APIResult(
        List(new APIIdentificationResult(s"extension$count", IdentificationMethod.EXTENSION, s"puid$count", s"testName$count", false, mockUri)).asJava,
        Map.empty[HashAlgorithm, String].asJava
      ) :: Nil
    } yield res

    when(mockApi.submit(localFileUri, "txt")).thenReturn(apiResults.asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath.txt", localFileUri)
    val ffid = result.value.ffidMetadataInputValues
    ffid.matches.size should equal(3)
  }

  "The fileChecksResult method" should "return an error if there is an error running the droid commands" in {
    val mockApi = mock[DroidAPI]
    when(mockApi.submit(any[URI], any[String])).thenThrow(new Exception("Droid error processing files"))
    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, "originalPath", localFileUri)
    result.left.value.getMessage should equal(s"Error processing file id $fileId with original path originalPath")
    result.left.value.getCause.getMessage should equal("Droid error processing files")
  }

  "The fileChecksResult method" should "return a correct value if there are quotes in the filename" in {
    val mockApi = mock[DroidAPI]
    when(mockApi.submit(any[URI], any[String])).thenReturn(List().asJava)
    val result = new DroidFileChecksResultExtractor(mockApi).fileChecksResult(fileId, """rootDirectory/originalPath"withQu'ote""", localFileUri)
    result.isRight should be(true)
  }

  val testFiles: TableFor3[String, List[String], Boolean] = Table(
    ("FileName", "ExpectedPuids", "FileExtensionMismatch"),
    ("Test.docx", List("fmt/412"), false),
    ("Test.xlsx", List("fmt/214"), false)
  )

  forAll(testFiles) { (fileName, expectedPuids, fileExtensionMismatch) =>
    "The fileChecksResult method" should s"return the correct file checks results for $fileName" in {
      testDroidFileChecksResult(fileName, "originalPath." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch)
    }

    "The fileChecksResult method" should s"return the correct file checks results for a nested directory for $fileName" in {
      testDroidFileChecksResult(fileName, "rootDirectory/subDirectory/originalPath." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch)
    }

    "The fileChecksResult method" should s"return the correct file checks results for a file with a backtick for $fileName" in {
      testDroidFileChecksResult(fileName, "pathwith`." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch)
    }

    "The fileChecksResult method" should s"return the correct file checks results for a file with a space for $fileName" in {
      testDroidFileChecksResult(fileName, "path with space." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch)
    }
  }

  def testDroidFileChecksResult(fileName: String, originalFilePath: String, expectedPuids: List[String], expectedFileExtensionMismatch: Boolean): Unit = {
    val testFilePath = Paths.get(s"./src/test/resources/testfiles/$fileName").toAbsolutePath
    val containerSignature: SignatureFile = SignatureFile("container-signature-", "20240715")
    val droidSignature: SignatureFile = SignatureFile("DROID_SignatureFile_V", "120")

    val result = DroidFileChecksResultExtractor(containerSignature, droidSignature).fileChecksResult(fileId, originalFilePath, testFilePath.toUri)
    result.isRight should be(true)
    result.foreach(v => {
      v.ffidMetadataInputValues.matches.size should equal(expectedPuids.size)
      v.ffidMetadataInputValues.matches.exists(_.fileExtensionMismatch == Option(expectedFileExtensionMismatch)) should equal(true)
      expectedPuids.foreach(puid => v.ffidMetadataInputValues.matches.exists(_.puid == Option(puid)) should equal(true))
    })
  }
}
