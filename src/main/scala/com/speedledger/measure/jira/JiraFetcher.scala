package com.speedledger.measure.jira

import akka.actor.{Props, ActorSystem}
import scala.concurrent.duration._
import spray.httpx.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import com.typesafe.config.ConfigFactory
import akka.event.Logging
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Await}
import spray.http.HttpRequest
import org.json4s.JsonAST.JValue
import spray.http._
import spray.client.pipelining._

object JiraFetcher extends App with JsonSupport {
  val system = ActorSystem()
  val log = Logging.getLogger(system, this)
  log.info("Starting jira fetcher actor system")
  val restartActor = system.actorOf(Props[RestartActor], "restart")
  val elasticsearch = system.actorOf(Props[ElasticSearchActor], "elasticsearch")
}
