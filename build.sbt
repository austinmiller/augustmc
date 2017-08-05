import com.github.retronym.SbtOneJar._
import Dependencies._

name := "augustmc"
organization := "augustmc"

lazy val macroAnnotationSettings = Seq(
  addCompilerPlugin(paradise),
  scalacOptions += "-Xplugin-require:macroparadise"
)

lazy val commonSettings = Seq(
  version := "2017.1",
  scalaVersion := scala212,
  exportJars := true
) ++ oneJarSettings

mainClass in Compile := Some("aug.gui.Main")

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"

libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0"

libraryDependencies += "commons-io" % "commons-io" % "2.5"
libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.0-SNAP9" % "test"

libraryDependencies += "org.reflections" % "reflections" % "0.9.11"

libraryDependencies += "mrj" % "MRJToolkitStubs" % "1.0"

libraryDependencies += scalameta

lazy val macroProject = project
  .in(file("macro"))
  .settings(commonSettings)

lazy val root = project
  .in(file("."))
  .dependsOn(macroProject)
  .settings(
    commonSettings,
    macroAnnotationSettings
  )
