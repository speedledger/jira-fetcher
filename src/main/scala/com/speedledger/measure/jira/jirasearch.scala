package com.speedledger.measure.jira

import akka.actor.{OneForOneStrategy, Props, ActorLogging, Actor}
import java.util.concurrent.TimeUnit
import com.github.nscala_time.time.Implicits._
import org.json4s.JsonAST.JValue
import scala.concurrent.Future
import spray.http._
import spray.client.pipelining._
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import org.json4s.JsonDSL._
import akka.actor.SupervisorStrategy.Escalate

case class IssueKey(key: String)
case class JiraQuery(jql: String, startAt: Int)
case class TotalNumberOfIssues(total: Int)
case class IssueAck(issueKey: IssueKey)

/**
 * Actor that handles fetching issues from jira
 */
class JiraActor extends Actor with ActorLogging with JsonSupport with JiraSupport {

  val pipeline: HttpRequest ⇒ Future[JValue] = addCredentials(BasicHttpCredentials(userName, password)) ~> (sendReceive ~> unmarshal[JValue])

  def processIssue(issue: JValue): IssueKey = {
    val issueKey = issue.extract[IssueKey]
    val issueActor = context.actorOf(Props[JiraIssueActor])
    issueActor ! JiraIssue(issue, issueKey)
    issueKey
  }

  def fetchIssues(jql: String, startAt: Int) = {
    log.info("Fetching issues starting at: " + startAt)
    val query = ("jql" -> jql) ~
      ("maxResults" -> maxResults) ~
      ("startAt" -> startAt)
    log.debug("Querying jira with query: " + query)
    pipeline(Post(searchUrl, query)) onComplete {
      case Success(response) =>
        val issues = response \ "issues"
        val totalIssues = response.extract[TotalNumberOfIssues]
        val moreIssues = totalIssues.total > startAt + maxResults
        val nextQuery = if (moreIssues) {
          Some(JiraQuery(jql, startAt + maxResults))
        } else {
          None
        }
        val issueKeys = issues.children.map(processIssue).toSet
        context.become(waitForAcc(issueKeys, nextQuery))
      case Failure(ex) =>
        log.error(ex, "Error fetching jira data")
    }
  }

  def waitForAcc(issueKeys: Set[IssueKey], nextQuery: Option[JiraQuery]): Receive = {
    case IssueAck(issueKey) =>
      log.debug("Got ack for issue: " + issueKey.key)
      val updatedIssueKeys = issueKeys - issueKey
      if (updatedIssueKeys.isEmpty) {
        nextQuery match {
          case Some(query) =>
            log.debug("No more issues but query not empty")
            fetchIssues(query.jql, query.startAt)
          case None =>
            log.debug("No more issues and empty query")
            context.become(receiveUpdate)
            context.parent ! Tock
        }
      } else {
        log.debug("Waiting for ack from more issues: " + updatedIssueKeys.toString())
        context.become(waitForAcc(updatedIssueKeys, nextQuery))
      }
  }

  def receiveUpdate: Receive = {
    case Update(lastTime) =>
      log.debug("Fetching data from jira. Last time: " + lastTime)
      val timeAdjust = config.getDuration("time-adjustment", TimeUnit.MILLISECONDS)
      val fromTime = lastTime - timeAdjust
      val local = fromTime.toLocalDateTime
      val searchTime = local.toString("yyyy-MM-dd HH:mm")
      val jql = "updated>=\"" + searchTime + "\""
      val startAt = 0
      fetchIssues(jql, startAt)
  }

  def receive = receiveUpdate

  override def unhandled(message: Any): Unit = {
    message match {
      case ex: Exception =>
        throw ex
    }
  }

  override val supervisorStrategy =
    OneForOneStrategy() {
      case _: Exception => Escalate
    }
}
