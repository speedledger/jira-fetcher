package com.speedledger.measure.jira

import akka.actor.{ActorLogging, Actor}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import spray.client.pipelining._
import com.typesafe.config.ConfigFactory
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.{HttpResponse, HttpRequest, BasicHttpCredentials}
import org.json4s.JsonAST
import org.json4s.native.JsonMethods._
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future

case class Issue(name: String, data: JValue)
case class Changelog(changelogId: ChangelogId, history: JsonAST.JObject)
case class ChangelogId(id: String)

class ElasticSearchActor extends Actor with ActorLogging with JsonSupport {

  val config = ConfigFactory.load().getConfig("elasticsearch")
  val elasticUrl = config.getString("url")
  val userName = config.getString("user-name")
  val password = config.getString("password")
  val pipeline: HttpRequest ⇒ Future[HttpResponse] = addCredentials(BasicHttpCredentials(userName, password)) ~> sendReceive

  override def receive = {
    case Issue(issueName, issueData) =>
      log.debug("Sending issue " + issueName + " to elastic")
      val issueUri = s"$elasticUrl/jira/issue/$issueName"
      val originalSender = sender()
      pipeline(Put(issueUri, issueData)) onComplete {
        case Success(response) =>
          log.debug("Inserted issue " + issueName + " into elastic")
          originalSender ! ElasticIssueAck(IssueKey(issueName))
        case Failure(ex) =>
          log.error(ex, "Error inserting into elasticsearch")
      }
    case Changelog(changelogId, history) =>
      val changelogUri = s"$elasticUrl.dsf/jira/changelog/${changelogId.id}"
      val originalSender = sender()
      pipeline(Put(changelogUri, history)) onComplete {
        case Success(response) =>
          log.debug("Inserted changelog " + changelogId.id + " into elastic")
          originalSender ! ElasticChangelogAck(changelogId)
        case Failure(ex) =>
          log.error(ex, "Error inserting changelog into elasticsearch")
          originalSender ! ex
      }
  }
}
