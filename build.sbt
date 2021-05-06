val AkkaVersion = "2.6.13"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "io.socket" % "socket.io-client" % "2.0.0",
  "commons-net" % "commons-net" % "3.8.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.12.1",
  "com.typesafe" % "config" % "1.4.0",
  "junit" % "junit" % "4.9" % Test,
  "com.novocode" % "junit-interface" % "0.11" % Test,
  "org.slf4j" %"slf4j-api" %"1.7.30" % "test"
)

lazy val thisProject = (project in file("."))
  .settings(
    crossPaths := false
  )