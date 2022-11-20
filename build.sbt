import com.github.retronym.SbtOneJar._
import Dependencies._
import sbt.Keys._

lazy val macroSettings = Seq(
  libraryDependencies += scalameta,
  addCompilerPlugin(paradise),
  scalacOptions += "-Xplugin-require:macroparadise"
)

lazy val commonSettings = Seq(
  version := "2017.4",
  scalaVersion := scala212,
  exportJars := true,
  libraryDependencies ++= Seq(
    "ch.qos.logback" %  "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
    "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0",
    "commons-io" % "commons-io" % "2.5",
    "commons-lang" % "commons-lang" % "2.6",
    "org.scalatest" %% "scalatest" % "3.2.0-SNAP9" % "test",
    "mrj" % "MRJToolkitStubs" % "1.0",
    "javax.xml.bind" % "jaxb-api" % "2.3.1",
    "com.sun.xml.bind" % "jaxb-core" % "2.3.0",
    "com.sun.xml.bind" % "jaxb-impl" % "2.3.0",
    "javax.activation" % "activation" % "1.1.1"
  )
) ++ oneJarSettings

lazy val rootSettings = Seq(
  name := "augustmc",
  organization := "augustmc",
  mainClass in Compile := Some("aug.gui.Main")
)

lazy val macros = project
  .in(file("macro"))
  .settings(
    macroSettings,
    commonSettings
  )

lazy val framework = project
  .in(file("framework"))
  .dependsOn(macros)
  .settings(
    macroSettings,
    commonSettings
  )

lazy val examples = project
  .in(file("examples"))
  .dependsOn(framework)
  .settings(
    commonSettings
  )

lazy val root = project
  .in(file("."))
  .dependsOn(macros)
  .dependsOn(framework)
  .dependsOn(examples)
  .settings(
    macroSettings,
    commonSettings,
    rootSettings
  )
