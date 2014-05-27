package com.speedledger.measure.jira

import akka.actor.{OneForOneStrategy, ActorLogging, Props, Actor}
import java.io.{PrintWriter, File}
import scala.util.Try
import org.joda.time.DateTime
import scala.io.Source
import akka.actor.SupervisorStrategy.Escalate

case object Tick
case object Tock

case class Update(lastUpdateTime: DateTime)

class UpdaterActor extends Actor with ActorLogging {

  var jira = context.actorOf(Props[JiraActor], "jira")
  val timeFile = new File("nextQueryTime")
  val lastQueryTime = Try(Source.fromFile(timeFile).getLines().mkString("\n").toLong).map(new DateTime(_)).getOrElse(new DateTime(0))

  def awaitTock: Receive = {
    case Tock =>
      log.info("Update finished.")
      val nextQueryTime = DateTime.now
      val writer = new PrintWriter(timeFile)
      writer.write(nextQueryTime.getMillis.toString)
      writer.close()
      context.become(awaitTick)
  }
  
  def awaitTick: Receive = {
    case Tick =>
      log.info("Received tick with query time: " + lastQueryTime)
      jira ! Update(lastQueryTime)
      context.become(awaitTock)
  }

  def receive = awaitTick

  override val supervisorStrategy =
    OneForOneStrategy() {
      case ex: Exception => Escalate
    }
}
