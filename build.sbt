lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.shaddysignal.mtproto",
      scalaVersion := "2.12.7",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "mtproto-simple",
    resolvers ++= Seq(
      "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.17",
      "org.scodec"        %% "scodec-bits" % "1.1.6",
      "org.scodec"        %% "scodec-core" % "1.10.3"
    )
  )
