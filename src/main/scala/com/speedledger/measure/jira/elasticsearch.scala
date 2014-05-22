package com.speedledger.measure.jira

import akka.actor.{ActorLogging, Actor}
import spray.client.pipelining._
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.{HttpResponse, HttpRequest, BasicHttpCredentials}
import org.json4s.JsonAST
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future

case class JiraChangelog(changelogId: ChangelogId, history: JsonAST.JObject)
case class ChangelogId(id: String)

/**
 * Actor that handles communication with elasticsearch. It can handle JiraIssue and JiraChangelog
 */
class ElasticSearchActor extends Actor with ActorLogging with JsonSupport {

  val config = ConfigFactory.load().getConfig("elasticsearch")
  val elasticUrl = config.getString("url")
  val userName = config.getString("user-name")
  val password = config.getString("password")
  def sendAndReceive = sendReceive
  val pipeline: HttpRequest ⇒ Future[HttpResponse] = addCredentials(BasicHttpCredentials(userName, password)) ~> sendAndReceive

  override def receive = {
    case JiraIssue(issueData, issueName) =>
      log.debug(s"Sending issue ${issueName.key} to elastic")
      val issueUri = s"$elasticUrl/jira/issue/${issueName.key}"
      val originalSender = sender()
      pipeline(Put(issueUri, issueData)) onComplete {
        case Success(response) =>
          log.debug(s"Inserted issue ${issueName.key} into elastic")
          originalSender ! ElasticIssueAck(IssueKey(issueName.key))
        case Failure(ex) =>
          log.error(ex, s"Error inserting issue ${issueName.key} into elasticsearch")
          originalSender ! ex
      }
    case JiraChangelog(changelogId, history) =>
      val changelogUri = s"$elasticUrl.dsf/jira/changelog/${changelogId.id}"
      val originalSender = sender()
      pipeline(Put(changelogUri, history)) onComplete {
        case Success(response) =>
          log.debug(s"Inserted changelog ${changelogId.id} into elastic")
          originalSender ! ElasticChangelogAck(changelogId)
        case Failure(ex) =>
          log.error(ex, s"Error inserting changelog ${changelogId.id} into elasticsearch")
          originalSender ! ex
      }
  }
}
