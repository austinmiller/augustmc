name := "aug"

version := "1.0"

scalaVersion := "2.11.8"

val springVersion = "4.2.6.RELEASE"
val jacksonVersion = "2.7.4"

libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.1.7"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"

libraryDependencies += "org.springframework" % "spring-core" % springVersion
libraryDependencies += "org.springframework" % "spring-context" % springVersion
libraryDependencies += "org.springframework" % "spring-test" % springVersion
libraryDependencies += "org.springframework" % "spring-jdbc" % springVersion

libraryDependencies += "com.google.guava" % "guava" % "18.0"

libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.1"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion

libraryDependencies += "commons-io" % "commons-io" % "2.5"
