import sbt.*

object Dependencies {

  private val circeVersion = "0.14.16"
  private lazy val bouncyCastleJdk18onVersion = "1.85"
  private lazy val bouncyCastleJdk15to18BcprovVersion = "1.85.2"
  private lazy val bouncyCastleJdk15to18BcpkixVersion = "1.85"
  private lazy val bouncyCastleJdk15to18BcutilVersion = "1.85.1"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.20"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.480"
  lazy val s3Utils = "uk.gov.nationalarchives" %% "s3-utils" % "0.1.338"
  lazy val typesafe = "com.typesafe" % "config" % "1.4.9"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
  lazy val logback = "ch.qos.logback" % "logback-classic" % "1.5.38"
  lazy val logstashLogbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "9.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "2.1.0"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "3.0.1"
  lazy val droidApi = "uk.gov.nationalarchives" % "droid-api" % "6.9.13"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.6.1"
  lazy val awsGuardDuty = "software.amazon.awssdk" % "guardduty" % "2.20.121"
  lazy val bcprov = "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleJdk18onVersion
  lazy val bcpkix = "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleJdk18onVersion
  lazy val bcutil = "org.bouncycastle" % "bcutil-jdk18on" % bouncyCastleJdk18onVersion
  lazy val bcprovJdk15to18 = "org.bouncycastle" % "bcprov-jdk15to18" % bouncyCastleJdk15to18BcprovVersion
  lazy val bcpkixJdk15to18 = "org.bouncycastle" % "bcpkix-jdk15to18" % bouncyCastleJdk15to18BcpkixVersion
  lazy val bcutilJdk15to18 = "org.bouncycastle" % "bcutil-jdk15to18" % bouncyCastleJdk15to18BcutilVersion
}
