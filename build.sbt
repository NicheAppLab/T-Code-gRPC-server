name := "tcodeserver"

version := "1.0"

scalaVersion := "3.3.6"

scalacOptions ++= Seq("-deprecation")

lazy val pekkoVersion = "1.4.0"
lazy val pekkoHttpVersion = "1.3.0"
lazy val pekkoGrpcVersion = "1.2.0"

enablePlugins(PekkoGrpcPlugin)

enablePlugins(JavaAppPackaging)

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

libraryDependencies ++= Seq(
  "io.github.nicheapplab" %% "tcodeengine" % "0.7.4" cross CrossVersion.full,
  "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,
  "org.apache.pekko" %% "pekko-pki" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-cors" % pekkoHttpVersion,

  "ch.qos.logback" % "logback-classic" % "1.3.15",

  "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
  "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalameta" %% "munit" % "1.0.4" % Test
)
