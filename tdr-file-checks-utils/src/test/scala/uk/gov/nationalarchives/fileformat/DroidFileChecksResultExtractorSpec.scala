package uk.gov.nationalarchives.fileformat

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableFor4
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.{APIIdentificationResult, APIResult}
import uk.gov.nationalarchives.droid.internal.api.{DroidAPI, HashAlgorithm}

import java.net.URI
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters._

class DroidFileChecksResultExtractorSpec extends TestUtils with MockitoSugar with EitherValues {
  val bucketName = "testbucket"
  val userId: UUID = UUID.randomUUID()
  val consignmentId: UUID = UUID.randomUUID()
  val fileId: UUID = UUID.randomUUID()
  val mockUri: URI = URI.create("/some/uri")
  val emptyChecksumMap: util.Map[HashAlgorithm, String] = Map.empty[HashAlgorithm, String].asJava
  val expectedChecksum = "996902da9b9ce84840b9c835fed417f4381309df6da0603a6cf16c4ec46742d7"

  "The ffid method" should "return the correct droid and signature version" in {
    val mockApi = mock[DroidAPI]
    val testDroidVersion = "TEST_DROID_VERSION"
    val testBinarySignatureVersion = "TEST_BINARY_SIGNATURE_VERSION"
    val testContainerSignatureVersion = "TEST_CONTAINER_SIGNATURE_VERSION"

    when(mockApi.getDroidVersion).thenReturn(testDroidVersion)
    when(mockApi.getBinarySignatureVersion).thenReturn(testBinarySignatureVersion)
    when(mockApi.getContainerSignatureVersion).thenReturn(testContainerSignatureVersion)

    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")

    val ffid = result.value.ffidMetadataInputValues
    ffid.softwareVersion should equal(testDroidVersion)
    ffid.containerSignatureFileVersion should equal(testContainerSignatureVersion)
    ffid.binarySignatureFileVersion should equal(testBinarySignatureVersion)
  }

  "The ffid method" should "return checksum and the correct value if the extension and puid are empty" in {
    val mockApi = mock[DroidAPI]
    val identificationResult = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", false, mockUri)
    val expectedChecksum = "feb8c01fd4fd0d6e56d9a630ef82b244df25f141ac2d611115cda74fa0a2a2a7"
    val mockResult = new APIResult(List(identificationResult).asJava, Map(HashAlgorithm.SHA256 -> expectedChecksum).asJava)
    when(mockApi.submit(URI.create("s3://testbucket/bucketKey"), "txt")).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, "originalPath.txt", "testbucket", "bucketKey")
    result.value.checksum should equal(expectedChecksum)
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.extension.isEmpty should be(true)
    m.puid.isEmpty should be(true)
  }

  "the ffid method" should "return a file extension mismatch if one exists" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", true, mockUri)
    val mockResult = new APIResult(List(apiIdentification).asJava, emptyChecksumMap)
    when(mockApi.submit(URI.create("s3://testbucket/bucketKey"), "pdf")).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, "originalPath.pdf", "testbucket", "bucketKey")
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.fileExtensionMismatch should be(Some(true))
  }

  "the ffid method" should "return a file format name if one exists" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, ".formatName", true, mockUri)

    val mockResult = new APIResult(List(apiIdentification).asJava, emptyChecksumMap)
    when(mockApi.submit(URI.create("s3://testbucket/bucketKey"), "txt")).thenReturn(List(mockResult).asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, "originalPath.txt", "testbucket", "bucketKey")
    val ffid = result.value.ffidMetadataInputValues
    val m = ffid.matches.head
    m.formatName should be(Some(".formatName"))
  }

  "The ffid method" should "return more than one result for multiple result rows" in {
    val mockApi = mock[DroidAPI]
    val apiResults = for {
      count <- List("1", "2", "3")
      res <- new APIResult(
        List(new APIIdentificationResult(s"extension$count", IdentificationMethod.EXTENSION, s"puid$count", s"testName$count", false, mockUri)).asJava,
        emptyChecksumMap
      ) :: Nil
    } yield res

    when(mockApi.submit(URI.create("s3://testbucket/bucketKey"), "txt")).thenReturn(apiResults.asJava)

    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, "originalPath.txt", "testbucket", "bucketKey")
    val ffid = result.value.ffidMetadataInputValues
    ffid.matches.size should equal(3)
  }

  "The ffid method" should "return an error if there is an error running the droid commands" in {
    val mockApi = mock[DroidAPI]
    when(mockApi.submit(any[URI], any[String])).thenThrow(new Exception("Droid error processing files"))
    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")
    result.left.value.getMessage should equal(s"Error processing file id $fileId with original path originalPath")
    result.left.value.getCause.getMessage should equal("Droid error processing files")
  }

  "The ffid method" should "return a correct value if there are quotes in the filename" in {
    val mockApi = mock[DroidAPI]
    when(mockApi.submit(any[URI])).thenReturn(List().asJava)
    val result = new DroidFileChecksResultExtractor(mockApi).ffidAndChecksumResult(consignmentId, fileId, """rootDirectory/originalPath"withQu'ote""", "testbucket", "bucketKey")
    result.isRight should be(true)
  }

  val testFiles: TableFor4[String, List[String], Boolean, String] = Table(
    ("FileName", "ExpectedPuids", "FileExtensionMismatch", "Checksum"),
    ("Test.docx", List("fmt/412"), false, "feb8c01fd4fd0d6e56d9a630ef82b244df25f141ac2d611115cda74fa0a2a2a7"),
    ("Test.xlsx", List("fmt/214"), false, "996902da9b9ce84840b9c835fed417f4381309df6da0603a6cf16c4ec46742d7")
  )

  forAll(testFiles) { (fileName, expectedPuids, fileExtensionMismatch, checksum) =>
    "The ffid method" should s"put return the correct format for $fileName" in {
      testFFIDExtractResult(fileName, "originalPath." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch, checksum)
    }

    "The ffid method" should s"return the correct format for a nested directory for $fileName" in {
      testFFIDExtractResult(fileName, "rootDirectory/subDirectory/originalPath." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch, checksum)
    }

    "The ffid method" should s"return the correct format for a file with a backtick for $fileName" in {
      testFFIDExtractResult(fileName, "pathwith`." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch, checksum)
    }

    "The ffid method" should s"return the correct format for a file with a space for $fileName" in {
      testFFIDExtractResult(fileName, "path with space." + fileName.split("\\.").last, expectedPuids, fileExtensionMismatch, checksum)
    }
  }

  def testFFIDExtractResult(fileName: String, originalFilePath: String, expectedPuids: List[String], expectedFileExtensionMismatch: Boolean, expectedChecksum: String): Unit = {
    val objectKey = s"/$userId/$consignmentId/$fileId"
    stubS3GetBytes(fileName, objectKey)
    stubS3HeadObject(fileName, objectKey)
    stubS3GetObjectList(userId, consignmentId, List(fileId), objectKey)

    val containerSignature: SignatureFile = SignatureFile("container-signature-", "20240715")
    val droidSignature: SignatureFile = SignatureFile("DROID_SignatureFile_V", "120")

    val result = DroidFileChecksResultExtractor(containerSignature, droidSignature, s3Client).ffidAndChecksumResult(consignmentId, fileId, originalFilePath, bucketName, objectKey)
    result.isRight should be(true)
    result.foreach(v => {
      v.checksum should equal(expectedChecksum)
      v.ffidMetadataInputValues.matches.size should equal(expectedPuids.size)
      v.ffidMetadataInputValues.matches.exists(_.fileExtensionMismatch == Option(expectedFileExtensionMismatch)) should equal(true)
      expectedPuids.foreach(puid => v.ffidMetadataInputValues.matches.exists(_.puid == Option(puid)) should equal(true))
    })
  }
}
