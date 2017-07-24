name := "aug"

lazy val commonSettings = Seq(
  organization := "aug",
  version := "2017.1",
  scalaVersion := "2.11.8"
)

unmanagedSourceDirectories in Compile += baseDirectory.value / "darcula/src"

unmanagedJars in Compile += file("darcula/lib/iconloader.jar")
unmanagedJars in Compile += file("darcula/lib/annotations.jar")

val springVersion = "4.2.6.RELEASE"
val jacksonVersion = "2.7.4"

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"

libraryDependencies += "org.springframework" % "spring-core" % springVersion
libraryDependencies += "org.springframework" % "spring-context" % springVersion
libraryDependencies += "org.springframework" % "spring-test" % springVersion
libraryDependencies += "org.springframework" % "spring-jdbc" % springVersion

libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.1"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion

libraryDependencies += "commons-io" % "commons-io" % "2.5"
libraryDependencies += "commons-lang" % "commons-lang" % "2.6"


libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

libraryDependencies += "org.reflections" % "reflections" % "0.9.11"

// for apple toolkit and ui
libraryDependencies += "mrj" % "MRJToolkitStubs" % "1.0"

lazy val root = (project in file(".")).dependsOn(shared).settings(commonSettings)
lazy val shared = (project in file("shared")).settings(commonSettings)

