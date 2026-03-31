package uk.gov.nationalarchives.filechecks

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfig {
  private val configFactory: Config = ConfigFactory.load
  val containerSignatureName: String = configFactory.getString("containers.signature.name")
  val containerSignatureVersion: String = configFactory.getString("containers.version")
  val droidSignatureName: String = configFactory.getString("droid.signature.name")
  val droidSignatureVersion: String = configFactory.getString("droid.version")
  val pollMalwareScanCompleteAwaitSecs: Long = configFactory.getLong("poll_malware_scan_complete.await.secs")
  val version: String = sys.env.getOrElse("AWS_LAMBDA_FUNCTION_VERSION", "$LATEST")
  val s3Endpoint: String = configFactory.getString("s3.endpoint")
}
