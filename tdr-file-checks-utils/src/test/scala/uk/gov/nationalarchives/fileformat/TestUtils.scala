package uk.gov.nationalarchives.fileformat

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.{HttpHeader, HttpHeaders}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import uk.gov.nationalarchives.droid.internal.api.DroidAPI

import java.io.{File, RandomAccessFile}
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.UUID
import scala.io.Source.fromFile
import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}
import scala.reflect.io.Directory
import scala.util.{Failure, Success, Using}

class TestUtils extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with TableDrivenPropertyChecks {
  val testBinarySignatureVersion = "./src/test/resources/containers/droid_signatures.xml"
  val testContainerSignatureVersion = "./src/test/resources/containers/container_signatures.xml"

  val s3Client: S3Client = S3Client.builder
    .region(Region.EU_WEST_2)
    .endpointOverride(URI.create("http://localhost:8003/"))
    .build()

  val api: DroidAPI = DroidAPI.builder()
    .containerSignature(Path.of(testContainerSignatureVersion))
    .binarySignature(Path.of(testBinarySignatureVersion))
    .s3Client(s3Client)
    .build()

  val wiremockS3 = new WireMockServer(8003)

  def getFile(filePath: String): String = {
    Using(fromFile(filePath)) { file => file.mkString } match {
      case Failure(exception) => throw exception
      case Success(value) => value
    }
  }

  override def beforeEach(): Unit = {
    wiremockS3.start()
  }

  override def afterEach(): Unit = {
    wiremockS3.resetAll()
    wiremockS3.stop()
    val runningFiles = new File(s"./src/test/resources/testfiles/running-files/")
    if (runningFiles.exists()) {
      new Directory(runningFiles).deleteRecursively()
    }
  }

  def getBytesForRange(filePath: String, range: String): Array[Byte] = {
    val rangeArr: Array[String] = range.split("=")(1).split("-")
    val rangeStart = rangeArr(0).toInt
    val rangeEnd = rangeArr(1).toInt
    val length = rangeEnd - rangeStart + 1
    val raf = new RandomAccessFile(filePath, "r")
    try {
      raf.seek(rangeStart)
      val buffer: Array[Byte] = new Array[Byte](length)
      raf.read(buffer) match {
        case br: Int if br == length => buffer
        case br: Int => util.Arrays.copyOf(buffer, br)
      }
    }
  }

  def stubS3HeadObject(fileName: String, urlStub: String): StubMapping = {
    val filePath = s"./src/test/resources/testfiles/$fileName"
    val bytes = Files.readAllBytes(Paths.get(filePath))
    wiremockS3.stubFor(head(urlEqualTo(urlStub))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeaders(new HttpHeaders(List(new HttpHeader("Content-Length", bytes.size.toString), new HttpHeader("Last-Modified", "Mon, 03 Mar 2025 17:29:48 GMT")).asJava))
        .withBody("".getBytes)
      )
    )
  }

  def stubS3GetBytes(fileName: String, urlStub: String): Unit = {
    val filePath = s"./src/test/resources/testfiles/$fileName"
    val bytes = Files.readAllBytes(Paths.get(filePath))

    wiremockS3.stubFor(get(urlEqualTo(urlStub)).withHeader("range", equalTo("bytes=0-4194303"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(bytes.slice(0, math.min(bytes.size, 4096))),
      )
    )

    wiremockS3.stubFor(get(urlEqualTo(urlStub)).withHeader("range", equalTo("bytes=4096-8191"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(bytes.slice(4096, math.min(bytes.size, 8192))),
      )
    )
  }

  def stubS3GetBytesNoHeader(fileName: String, urlStub: String): Unit = {
    val filePath = s"./src/test/resources/testfiles/$fileName"
    val bytes = Files.readAllBytes(Paths.get(filePath))

    wiremockS3.stubFor(get(urlEqualTo(urlStub))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(bytes.slice(0, math.min(bytes.size, 4096))),
      )
    )
    wiremockS3.stubFor(get(urlEqualTo(urlStub))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(bytes.slice(4096, math.min(bytes.size, 8192))),
      )
    )
  }

  def stubS3GetObjectList(userId: UUID, consignmentId: UUID, fileIds: List[UUID], urlStub: String): StubMapping = {
    val params = Map("list-type" -> equalTo("2"), "prefix" -> equalTo(urlStub)).asJava
    val response = <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      {fileIds.map(fileId =>
        <Contents>
          <Key>{userId}/{consignmentId}/{fileId}</Key>
          <LastModified>2009-10-12T17:50:30.000Z</LastModified>
          <ETag>"fba9dede5f27731c9771645a39863328"</ETag>
          <Size>1</Size>
        </Contents>
      )}
    </ListBucketResult>
    wiremockS3.stubFor(
      get(anyUrl())
        .withQueryParams(params)
        .willReturn(okXml(response.toString))
    )
  }

  def mockS3Error(): StubMapping = {
    wiremockS3.stubFor(get(anyUrl())
      .willReturn(aResponse().withStatus(404))
    )
  }
}
