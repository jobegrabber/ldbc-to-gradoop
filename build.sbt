organization := "de.jgrabber"
name := "ldbc-to-gradoop"

version := "0.1"

scalaVersion := "2.12.14"

semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
scalacOptions += "-Ywarn-unused"

val flinkVersion = "1.13.2"
lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.flink" %% "flink-clients" % flinkVersion % "provided",
      "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
      "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
      "org.gradoop" % "gradoop-flink" % "0.6.0",
      "com.github.pureconfig" %% "pureconfig" % "0.14.0",
      "org.slf4j" % "slf4j-api" % "1.7.30", // log API
      "ch.qos.logback" % "logback-classic" % "1.2.3", // log IMPL
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2", // lazy logging
      "com.github.bigwheel" %% "util-backports" % "2.1" // Using (loan pattern), tap, pipe
    ),
    assembly / test := {},
    assembly / assemblyMergeStrategy := {
      case "META-INF/io.netty.versions.properties" => MergeStrategy.first
      case PathList("META-INF", "native", _*) =>
        MergeStrategy.first // for io.netty
      case PathList("META-INF", "services", _*) =>
        MergeStrategy.filterDistinctLines // for IOChannelFactory
      case PathList("META-INF", _*) => MergeStrategy.discard
      case _                        => MergeStrategy.first
    }
  )

// make run command include the provided dependencies
Compile / run := Defaults
  .runTask(
    Compile / fullClasspath,
    Compile / run / mainClass,
    Compile / run / runner
  )
  .evaluated

// stays inside the sbt console when we press "ctrl-c" while a Flink programme executes with "run" or "runMain"
Compile / run / fork := true
Global / cancelable := true

// exclude Scala library from assembly
assembly / assemblyOption := (assembly / assemblyOption).value
  .copy(includeScala = false)
