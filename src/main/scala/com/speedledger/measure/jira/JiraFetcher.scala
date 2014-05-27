package com.speedledger.measure.jira

import akka.actor.{Props, ActorSystem}
import akka.event.Logging

/**
 * Application that fetches data (issues and changelogs) from Jira and stores the data in elasticsearch
 */
object JiraFetcher extends App {
  val system = ActorSystem()
  val log = Logging.getLogger(system, this)
  log.info("Starting jira fetcher actor system")
  val restartActor = system.actorOf(Props[JiraSupervisor], "restart")
  val elasticsearch = system.actorOf(Props[ElasticsearchActor], "elasticsearch")
}
