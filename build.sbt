name := "augustmc"

lazy val commonSettings = Seq(
  organization := "augustmc",
  version := "2017.1",
  scalaVersion := "2.11.8"
)

unmanagedSourceDirectories in Compile += baseDirectory.value / "darcula/src"

unmanagedJars in Compile += file("darcula/lib/iconloader.jar")
unmanagedJars in Compile += file("darcula/lib/annotations.jar")

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"

libraryDependencies += "org.mongodb.scala" % "mongo-scala-driver_2.11" % "2.1.0"

libraryDependencies += "commons-io" % "commons-io" % "2.5"
libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "org.reflections" % "reflections" % "0.9.11"

libraryDependencies += "mrj" % "MRJToolkitStubs" % "1.0"


lazy val root = (project in file(".")).dependsOn(shared).settings(commonSettings)
lazy val shared = (project in file("shared")).settings(commonSettings)

