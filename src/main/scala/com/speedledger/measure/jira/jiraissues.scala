package com.speedledger.measure.jira

import akka.actor.{ActorLogging, Actor}
import org.json4s.JsonAST.{JObject, JValue}
import spray.client.pipelining._
import spray.http.{HttpRequest, BasicHttpCredentials}
import org.json4s.JsonDSL._
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.pipe

case class JiraIssue(issue: JValue, issueKey: IssueKey)
case class ChangelogId(id: String)
case class ElasticIssueAck(issue: IssueKey)
case class ElasticChangelogAck(changelog: ChangelogId)

/**
 * Actor that handles Issues from jira. Passing them to the ElasticSearchActor and fetching changelog for
 * each Issue and then passing the changelogs to the ElasticSearchActor.
 */
class JiraIssueActor extends Actor with ActorLogging with JsonSupport with JiraSupport {

  import context.dispatcher
  val elasticsearch = context.actorSelection("/user/elasticsearch")
  val pipeline: HttpRequest ⇒ Future[JValue] = addCredentials(BasicHttpCredentials(userName, password)) ~> (sendReceive ~> unmarshal[JValue])
  context.setReceiveTimeout(1 hours)

  override def receive = receiveJiraIssue

  def receiveJiraIssue: Receive = {
    case JiraIssue(issue, issueKey) =>
      log.debug("Processing issue")
      elasticsearch ! ElasticData(DocumentLocation("jira", "issue", issueKey.key), issue, ElasticIssueAck(issueKey))
      context.become(awaitIssueAck(issueKey))
  }

  def awaitIssueAck(awaitingKey: IssueKey): Receive = {
    case ElasticIssueAck(issue) if issue == awaitingKey =>
      fetchChangelog(issue)
  }

  def awaitChangelogAck(changelogIds: Set[String], issueKey: IssueKey): Receive = {
    case ElasticChangelogAck(changelog) =>
      val remainingIds = changelogIds - changelog.id
      if (remainingIds.isEmpty) {
        context.parent ! IssueAck(issueKey)
        context.stop(self)
      } else {
        context.become(awaitChangelogAck(remainingIds, issueKey))
      }
  }

  def fetchChangelog(issueKey: IssueKey) = {
    val key = issueKey.key
    val url = s"$issueUrl/$key?expand=changelog"
    log.debug("Getting issue changelog for issue: " + key)
    pipeline(Get(url)) pipeTo self
    context.become(awaitChangelogRequest(issueKey))
  }

  def awaitChangelogRequest(issueKey: IssueKey): Receive = {
    case response: JValue =>
      val histories = response \ "changelog" \ "histories"
      val changelogIds = histories.children.map({
        case history: JObject => processChangelog(history, issueKey.key)
      }).toSet
      if (changelogIds.isEmpty) {
        log.debug("No changelogs for issue: " + issueKey.key)
        context.parent ! IssueAck(issueKey)
        context.stop(self)
      } else {
        context.become(awaitChangelogAck(changelogIds, issueKey))
      }
    case akka.actor.Status.Failure(ex) =>
      context.parent ! ex
  }

  def processChangelog(history: JObject, key: String): String = {
    val changelogId = history.extract[ChangelogId]
    val historyWithIssueName = history ~ ("issueName" -> key)
    log.debug(s"Sending changelog $changelogId to elastic for issue: $key")
    elasticsearch ! ElasticData(DocumentLocation("jira", "changelog", changelogId.id), historyWithIssueName, ElasticChangelogAck(changelogId))
    changelogId.id
  }

  override def unhandled(message: Any): Unit = {
    message match {
      case ex: Exception =>
        throw ex
    }
  }
}