name := "aug"

version := "1.0"

scalaVersion := "2.11.8"

val springVersion = "4.2.6.RELEASE"

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"

libraryDependencies += "org.springframework" % "spring-core" % springVersion
libraryDependencies += "org.springframework" % "spring-context" % springVersion
libraryDependencies += "org.springframework" % "spring-test" % springVersion
libraryDependencies += "org.springframework" % "spring-jdbc" % springVersion


