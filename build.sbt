import com.github.retronym.SbtOneJar._
import Dependencies._
import sbt.Keys._

lazy val macroSettings = Seq(
//  libraryDependencies += scalameta,
//  addCompilerPlugin(paradise),
//  scalacOptions += "-Xplugin-require:macroparadise"
)

lazy val commonSettings = Seq(
  version := "2022.1",
  scalaVersion := scala213,
  exportJars := true,
  libraryDependencies ++= Seq(
    "org.apache.logging.log4j" % "log4j-core" % "2.19.0",
    "org.apache.logging.log4j" % "log4j-api" % "2.19.0",
    "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.19.0",
    "org.slf4j" % "slf4j-api" % "1.7.36",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    "commons-io" % "commons-io" % "2.11.0",
    "commons-lang" % "commons-lang" % "2.6",
    "org.scalatest" %% "scalatest" % "3.2.14" % "test",
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
