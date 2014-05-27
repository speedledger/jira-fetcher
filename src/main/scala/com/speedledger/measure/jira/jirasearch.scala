package com.speedledger.measure.jira

import akka.actor.{OneForOneStrategy, Props, ActorLogging, Actor}
import java.util.concurrent.TimeUnit
import com.github.nscala_time.time.Implicits._
import org.json4s.JsonAST.{JObject, JArray, JValue}
import scala.concurrent.Future
import spray.http._
import spray.client.pipelining._
import scala.util.{Failure, Success}
import org.json4s.JsonDSL._
import akka.actor.SupervisorStrategy.Escalate
import org.joda.time
import scala.concurrent.duration.Duration

case class IssueKey(key: String)
case class JiraQuery(jql: String, startAt: Int)
case class IssueAck(issueKey: IssueKey)
case class SearchResponse(issues: JArray, total: Int)

/**
 * Actor that handles fetching issues from jira
 */
class JiraActor extends Actor with ActorLogging with JsonSupport with JiraSupport {

  import context.dispatcher
  val pipeline: HttpRequest ⇒ Future[SearchResponse] = addCredentials(BasicHttpCredentials(userName, password)) ~> (sendReceive ~> unmarshal[SearchResponse])
  context.setReceiveTimeout(Duration(1, TimeUnit.HOURS))

  def receive = receiveUpdate

  def receiveUpdate: Receive = {
    case Update(lastTime) =>
      val jql = createJql(lastTime)
      fetchIssues(jql, startAt = 0)
  }

  def createJql(lastTime: time.DateTime): String = {
    log.debug("Fetching data from jira. Last time: " + lastTime)
    val timeAdjust = config.getDuration("time-adjustment", TimeUnit.MILLISECONDS)
    val fromTime = lastTime - timeAdjust
    val local = fromTime.toLocalDateTime
    val searchTime = local.toString("yyyy-MM-dd HH:mm")
    val jql = "updated>=\"" + searchTime + "\""
    jql
  }

  def buildQuery(jql: String, startAt: Int): JObject = {
    val query = ("jql" -> jql) ~
      ("maxResults" -> maxResults) ~
      ("startAt" -> startAt)
    query
  }

  def fetchIssues(jql: String, startAt: Int) = {
    log.info("Fetching issues starting at: " + startAt)
    val query = buildQuery(jql, startAt)
    log.debug("Querying jira with query: " + query)
    pipeline(Post(searchUrl, query)) onComplete {
      case Success(response) =>
        val moreIssues = response.total > startAt + maxResults
        val nextQuery = if (moreIssues) {
          Some(JiraQuery(jql, startAt + maxResults))
        } else {
          None
        }
        val issueKeys = response.issues.children.map(processIssue).toSet
        context.become(waitForAcc(issueKeys, nextQuery))
      case Failure(ex) =>
        log.error(ex, "Error fetching jira data")
    }
  }

  def processIssue(issue: JValue): IssueKey = {
    val issueKey = issue.extract[IssueKey]
    val issueActor = context.actorOf(Props[JiraIssueActor])
    issueActor ! JiraIssue(issue, issueKey)
    issueKey
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

  /**
   * Overrides unhandled to be abel to handle when exceptions are sent from child actors.
   * Exceptions need to be sent when exceptions are thrown within a future.
   */
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
