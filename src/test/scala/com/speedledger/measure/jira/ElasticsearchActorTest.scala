package com.speedledger.measure.jira

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}
import akka.actor.{Props, ActorSystem}
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.Future
import spray.http.{StatusCodes, HttpResponse, HttpRequest}

class ElasticsearchActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("JiraActorTest"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "ElasticSearchActor" must {
    val json = parse(""" { "test" : "test" } """)

    def success(req: HttpRequest): Future[HttpResponse] = {
      Future.successful(HttpResponse(status = StatusCodes.OK))
    }

    def failure(req: HttpRequest): Future[HttpResponse] = {
      Future.failed(new RuntimeException)
    }

    "Send ack back when processing is done" in {
      val elastic = TestActorRef(Props(new ElasticsearchActorMock(success)))
      elastic ! ElasticData(DocumentLocation("jira", "issue", "TECH-58"), json, ElasticIssueAck(IssueKey("TECH-58")))
      expectMsg(ElasticIssueAck(IssueKey("TECH-58")))
    }

    "Send back exception when failure" in {
      val elastic = TestActorRef(Props(new ElasticsearchActorMock(failure)))
      elastic ! ElasticData(DocumentLocation("jira", "issue", "TECH-58"), json, ElasticIssueAck(IssueKey("TECH-58")))
      expectMsgAllClassOf(classOf[RuntimeException])
    }
  }
}

class ElasticsearchActorMock(f: HttpRequest => Future[HttpResponse]) extends ElasticsearchActor {
  override def sendAndReceive = f
}
