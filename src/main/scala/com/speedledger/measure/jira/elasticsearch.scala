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

case class ElasticData(data: JsonAST.JValue, index: String, typeName: String, dataId: String, ack: Any)

/**
 * Actor that handles communication with elasticsearch.
 */
class ElasticsearchActor extends Actor with ActorLogging with JsonSupport {

  val config = ConfigFactory.load().getConfig("elasticsearch")
  val elasticUrl = config.getString("url")
  val userName = config.getString("user-name")
  val password = config.getString("password")
  def sendAndReceive = sendReceive
  val pipeline: HttpRequest ⇒ Future[HttpResponse] = addCredentials(BasicHttpCredentials(userName, password)) ~> sendAndReceive

  override def receive = {
    case ElasticData(data, index, typeName, dataId, ack) =>
      val issueUri = s"$elasticUrl/$index/$typeName/$dataId"
      val originalSender = sender()
      pipeline(Put(issueUri, data)) onComplete {
        case Success(response) =>
          log.debug(s"Inserted $typeName $dataId into index $index")
          originalSender ! ack
        case Failure(ex) =>
          log.error(ex, s"Error inserting $typeName $dataId into elasticsearch")
          originalSender ! ex
      }
  }
}
