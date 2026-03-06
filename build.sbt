import Dependencies.*

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "uk.gov.nationalarchives"

ThisBuild / javacOptions ++= Seq(
  "--release", "21"
)

val commonMergeStrategy: String => sbtassembly.MergeStrategy = {
  case PathList("META-INF", "MANIFEST.MF")           => MergeStrategy.discard
  case PathList("META-INF", "services", xs @ _*)     => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)                 => MergeStrategy.discard
  case PathList(xs @ _*) if xs.last.endsWith(".nir") => MergeStrategy.discard
  case "utf8.json"                                   => MergeStrategy.discard
  case PathList("java", _ @_*)                       => MergeStrategy.discard
  case PathList("javax", _ @_*)                      => MergeStrategy.discard
  case PathList("scala", "scalanative", _ @_*)       => MergeStrategy.discard
  case PathList("scala-native", _ @_*)               => MergeStrategy.discard
  case PathList("assets", "swagger", "ui", _ @_*)    => MergeStrategy.discard
  case PathList("wiremock", _ @_*)                   => MergeStrategy.discard
  case "module-info.class"                           => MergeStrategy.discard
  case "reference.conf"                              => MergeStrategy.concat
  case x                                             => MergeStrategy.first
}

// Common assembly merge strategy
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF")       => MergeStrategy.discard
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
  case "module-info.class"                       => MergeStrategy.discard
  case "reference.conf"                          => MergeStrategy.concat
  case _                                         => MergeStrategy.first
}

// Common test settings
ThisBuild / Test / fork := true
ThisBuild / Test / envVars := Map(
  "AWS_REGION" -> "eu-west-2",
  "AWS_ACCESS_KEY_ID" -> "test",
  "AWS_SECRET_ACCESS_KEY" -> "test",
  "AWS_REQUEST_CHECKSUM_CALCULATION" -> "when_required",
  "AWS_RESPONSE_CHECKSUM_CALCULATION" -> "when_required"
)

lazy val tdrFileChecksUtils = (project in file("tdr-file-checks-utils"))
  .enablePlugins(AssemblyPlugin)
  .settings(
    name := "tdr-file-checks-utils",
    libraryDependencies ++= Seq(
      typesafe,
      generatedGraphql,
      scalaLogging,
      logback,
      logstashLogbackEncoder,
      droidApi,
      catsEffect,
      s3Utils,
      scalaTest % Test,
      mockito % Test,
      wiremock % Test
    ),
    assembly / skip := true
  )

lazy val root = (project in file("."))
  .disablePlugins(AssemblyPlugin)
  .dependsOn(tdrFileChecksUtils % "test->test;compile->compile")
  .aggregate(tdrFileChecksUtils)
  .settings(
    name := "tdr-file-checks",
    libraryDependencies ++= Seq(
      typesafe,
      generatedGraphql,
      circeCore,
      circeGeneric,
      circeParser,
      scalaLogging,
      logback,
      logstashLogbackEncoder,
      droidApi,
      scalaTest % Test,
      wiremock % Test
    ),
    assembly / skip := false,
    assembly / assemblyJarName := "tdr-file-checks.jar",
  )
