package uk.gov.nationalarchives.filechecks

import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import uk.gov.nationalarchives.filechecks.ApplicationConfig._

import java.net.URI
import java.time.Duration

object S3ClientProvider {

  lazy val s3AsyncClient: S3AsyncClient = {
    val httpClient = NettyNioAsyncHttpClient
      .builder()
      .readTimeout(Duration.ofSeconds(60))
      .maxConcurrency(300)
      .connectionMaxIdleTime(Duration.ofSeconds(10))
      .build()

    S3AsyncClient
      .builder()
      .region(Region.EU_WEST_2)
      .endpointOverride(URI.create(s3Endpoint))
      .forcePathStyle(true)
      .httpClient(httpClient)
      .build()
  }
}
