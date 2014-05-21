package com.speedledger.measure.jira

import com.typesafe.config.ConfigFactory
import spray.client.pipelining._
import spray.http.{HttpResponse, HttpRequest, BasicHttpCredentials}
import org.json4s.JsonAST.JValue
import spray.client.pipelining
import scala.concurrent.Future
import akka.actor.Actor
import org.json4s.{DefaultFormats, Formats}

trait JiraSupport {
  this: Actor =>
  import context.dispatcher
  val config = ConfigFactory.load().getConfig("jira")
  val searchUrl = config.getString("search-url")
  val issueUrl = config.getString("issue-url")
  val userName = config.getString("user-name")
  val password = config.getString("password")
  val maxResults = config.getInt("max-results")
//  val pipeline: HttpRequest ⇒ Future[JValue] = addCredentials(BasicHttpCredentials(userName, password)) ~> (sendReceive ~> unmarshal[JValue])
}
