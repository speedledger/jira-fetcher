jira-fetcher
============

Fetch data about issues and the issues's changelogs from Jira and store in elasticsearch.

Setup
-----
Recommended IDE is IntelliJ IDEA. Clone the repo. Open the project by either just opening build.sbt from IntelliJ or import project with "Import project" and selecting build.sbt.

Building
--------
Run `sbt assembly` and a fat JAR will be created in the `target/scala-2.10` folder.

Usage
-----
Configure the application in a copy of [application.conf](src/main/resources/application.conf).

Start the application using the fat JAR with command `java -Dconfig.file=<path to config file> -jar <path to JAR file>`.
