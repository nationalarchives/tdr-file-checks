
import Dependencies.*

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "uk.gov.nationalarchives"

libraryDependencies ++= Seq(
  circeCore,
  circeGeneric,
  circeParser,
  s3Utils,
  scalaTest % Test,
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
      circeCore,
      circeGeneric,
      circeParser,
      csvParser,
      generatedGraphql,
      scalaLogging,
      logback,
      logstashLogbackEncoder,
      droidApi,
      javaxXml,
      catsEffect,
      apacheCommons % Test,
      scalaTest % Test,
      mockito % Test,
      wiremock % Test,
      byteBuddy % Test
    ),
    assembly / skip := true
  )

// Root: disable assembly
lazy val root = (project in file("."))
  .disablePlugins(AssemblyPlugin)
  .dependsOn(tdrFileChecksUtils % "test->test;compile->compile")
  .aggregate(tdrFileChecksUtils)
  .settings(
    name := "tdr-file-checks",
    libraryDependencies ++= Seq(
      typesafe,
      circeCore,
      circeGeneric,
      circeParser,
      csvParser,
      generatedGraphql,
      scalaLogging,
      logback,
      logstashLogbackEncoder,
      droidApi,
      javaxXml,
      apacheCommons % Test,
      scalaTest % Test,
      mockito % Test,
      wiremock % Test,
      byteBuddy % Test
    ),
    assembly / skip := false,
    assembly / assemblyJarName := "tdr-file-checks.jar",
    assembly / assemblyMergeStrategy := commonMergeStrategy
  )
