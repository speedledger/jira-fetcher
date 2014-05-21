import AssemblyKeys._

organization  := "com.speedledger"

name          := "jira-fetcher"

version       := "0.1"

scalaVersion  := "2.10.4"

libraryDependencies ++= {
  val sprayVersion = "1.3.1"
  val akkaVersion = "2.3.2"
  Seq(
    "io.spray"            %  "spray-can"     % sprayVersion,
    "io.spray"            %  "spray-client"  % sprayVersion,
    "io.spray"            %  "spray-http"    % sprayVersion,
    "io.spray"            %  "spray-httpx"   % sprayVersion,
    "io.spray"            %  "spray-util"    % sprayVersion,
    "io.spray"            %  "spray-testkit" % sprayVersion % "test",
    "com.typesafe.akka"   %% "akka-actor"    % akkaVersion,
    "com.typesafe.akka"   %% "akka-slf4j"    % akkaVersion,
    "com.typesafe.akka"   %% "akka-testkit"  % akkaVersion % "test",
    "org.json4s"          %% "json4s-native" % "3.2.7",
    "org.scalatest"       %% "scalatest"     % "2.1.1" % "test",
    "com.typesafe"        %  "config"        % "1.2.0",
    "ch.qos.logback"      %  "logback-classic" % "1.1.1",
    "com.github.nscala-time" %% "nscala-time" % "0.8.0"
  )
}

assemblySettings
