package com.speedledger.measure.jira

import akka.actor.{ActorLogging, Actor}
import spray.client.pipelining._
import com.typesafe.config.ConfigFactory
import scala.concurrent.ExecutionContext.Implicits.global
import spray.http.{Uri, HttpResponse, HttpRequest, BasicHttpCredentials}
import org.json4s.JsonAST
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Future

case class DocumentLocation(index: String, typeName: String, id: String)
case class ElasticData(location: DocumentLocation, document: JsonAST.JValue, ack: Any)

/**
 * Actor that handles communication with elasticsearch.
 */
class ElasticsearchActor extends Actor with ActorLogging with JsonSupport {

  val config = ConfigFactory.load().getConfig("elasticsearch")
  val elasticUrl = config.getString("url")
  val userName = config.getString("username")
  val password = config.getString("password")
  def sendAndReceive = sendReceive
  val pipeline: HttpRequest ⇒ Future[HttpResponse] = addCredentials(BasicHttpCredentials(userName, password)) ~> sendAndReceive

  override def receive = {
    case ElasticData(location, document, ack) =>
      val issueUri = s"$elasticUrl/${location.index}/${location.typeName}/${location.id}"
      val originalSender = sender()
      pipeline(Put(issueUri, document)) onComplete {
        case Success(response) =>
          log.debug(s"Inserted ${location.typeName} ${location.id} into index ${location.index}")
          originalSender ! ack
        case Failure(ex) =>
          log.error(ex, s"Error inserting ${location.typeName} ${location.id} into elasticsearch")
          originalSender ! ex
      }
  }
}
