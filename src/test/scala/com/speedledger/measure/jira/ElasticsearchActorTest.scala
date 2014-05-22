package com.speedledger.measure.jira

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}
import akka.actor.{Props, ActorSystem}
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.{Future, Promise}
import spray.http.{StatusCodes, HttpResponse, HttpRequest}

class ElasticsearchActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("JiraActorTest"))

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ElasticSearchActor" must {
    "Send ack back when processing of Issue is done" in {
      val request = Promise[HttpRequest]()
      val elastic = TestActorRef(Props(new ElasticSearchActorMock(request)))
      val json = parse(""" { "test" : "test" } """)
      elastic ! JiraIssue(json, IssueKey("TECH-58"))
      expectMsg(ElasticIssueAck(IssueKey("TECH-58")))
    }
  }
}

class ElasticSearchActorMock(request: Promise[HttpRequest]) extends ElasticSearchActor {
  // Grab the HTTP request and mock the result
  override def sendAndReceive = in => {
    request.success(in)
    Future.successful(HttpResponse(status = StatusCodes.OK))
  }
}
