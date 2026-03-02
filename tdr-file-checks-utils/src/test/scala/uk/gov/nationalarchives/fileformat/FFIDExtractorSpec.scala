package uk.gov.nationalarchives.fileformat

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationMethod
import uk.gov.nationalarchives.droid.internal.api.DroidAPI.{APIIdentificationResult, APIResult}
import uk.gov.nationalarchives.droid.internal.api.{DroidAPI, HashAlgorithm}

import java.net.URI
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters._

//noinspection ScalaDeprecation
class FFIDExtractorSpec extends AnyFlatSpec with MockitoSugar with EitherValues {
  val bucketName = "testbucket"
  val userId: UUID = UUID.randomUUID()
  val consignmentId: UUID = UUID.randomUUID()
  val fileId: UUID = UUID.randomUUID()
  val mockUri: URI = URI.create("/some/uri")
  val emptyChecksumMap: util.Map[HashAlgorithm, String] = Map.empty[HashAlgorithm, String].asJava

  "The ffid method" should "return the correct droid and signature version" in {
    val mockApi = mock[DroidAPI]
    val testDroidVersion = "TEST_DROID_VERSION"
    val testBinarySignatureVersion = "TEST_BINARY_SIGNATURE_VERSION"
    val testContainerSignatureVersion = "TEST_CONTAINER_SIGNATURE_VERSION"

    when(mockApi.getDroidVersion).thenReturn(testDroidVersion)
    when(mockApi.getBinarySignatureVersion).thenReturn(testBinarySignatureVersion)
    when(mockApi.getContainerSignatureVersion).thenReturn(testContainerSignatureVersion)

    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")

    val ffid = result.right.value
    ffid.softwareVersion should equal(testDroidVersion)
    ffid.containerSignatureFileVersion should equal(testContainerSignatureVersion)
    ffid.binarySignatureFileVersion should equal(testBinarySignatureVersion)
  }

  "The ffid method" should "return the correct value if the extension and puid are empty" in {
    val mockApi = mock[DroidAPI]
    val identificationResult = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", false, mockUri)
    val mockResult = new APIResult(List(identificationResult).asJava, emptyChecksumMap)
    when(mockApi.submit(any[URI])).thenReturn(List(mockResult).asJava)

    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")
    val ffid = result.right.value
    val m = ffid.matches.head
    m.extension.isEmpty should be(true)
    m.puid.isEmpty should be(true)
  }

  "the ffid method" should "return a file extension mismatch if one exists" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, "testName", true, mockUri)
    val mockResult = new APIResult(List(apiIdentification).asJava, emptyChecksumMap)
    when(mockApi.submit(any[URI])).thenReturn(List(mockResult).asJava)

    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")
    val ffid = result.right.value
    val m = ffid.matches.head
    m.fileExtensionMismatch.contains(true)
  }

  "the ffid method" should "return a file format name if one exists" in {
    val mockApi = mock[DroidAPI]
    val apiIdentification = new APIIdentificationResult(null, IdentificationMethod.EXTENSION, null, ".formatName", true, mockUri)

    val mockResult = new APIResult(List(apiIdentification).asJava, emptyChecksumMap)
    when(mockApi.submit(any[URI])).thenReturn(List(mockResult).asJava)

    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")
    val ffid = result.right.value
    val m = ffid.matches.head
    m.formatName.contains(".formatName")
  }

  "The ffid method" should "return more than one result for multiple result rows" in {
    val mockApi = mock[DroidAPI]
    val apiResults = for {
      count <- List("1", "2", "3")
      res <- new APIResult(List(new APIIdentificationResult(s"extension$count", IdentificationMethod.EXTENSION, s"puid$count", s"testName$count", false, mockUri)).asJava, emptyChecksumMap) :: Nil
    } yield res

    when(mockApi.submit(any[URI], any[String])).thenReturn(apiResults.asJava)

    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")
    val ffid = result.right.value
    ffid.matches.size should equal(3)
  }

  "The ffid method" should "return an error if there is an error running the droid commands" in {
    val mockApi = mock[DroidAPI]
    when(mockApi.submit(any[URI], any[String])).thenThrow(new Exception("Droid error processing files"))
    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, "originalPath", "testbucket", "bucketKey")
    result.left.value.getMessage should equal(s"Error processing file id $fileId with original path originalPath")
    result.left.value.getCause.getMessage should equal("Droid error processing files")
  }

  "The ffid method" should "return a correct value if there are quotes in the filename" in {
    val mockApi = mock[DroidAPI]
    when(mockApi.submit(any[URI])).thenReturn(List().asJava)

    val result = new FFIDExtractor(mockApi).ffidFile(consignmentId, fileId, """rootDirectory/originalPath"withQu'ote""", "testbucket", "bucketKey")
    result.isRight should be(true)
  }
}
