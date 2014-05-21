package com.speedledger.measure.jira

import akka.actor.{Props, OneForOneStrategy, Actor, ActorLogging}
import akka.actor.SupervisorStrategy.Restart
import akka.actor.Actor.Receive
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import akka.actor.OneForOneStrategy
import scala.concurrent.ExecutionContext.Implicits.global

class RestartActor extends Actor with ActorLogging {

  val updater = context.actorOf(Props[UpdaterActor], "updater")
  val config = ConfigFactory.load()
  val interval = FiniteDuration(config.getDuration("updater.interval", SECONDS), SECONDS)
  log.debug(s"Update interval is $interval")
  context.system.scheduler.schedule(initialDelay = 1.second, interval, updater, Tick)

  override val supervisorStrategy =
    OneForOneStrategy() {
      case ex: Exception =>
        log.error(ex, "Exception! Restarting...")
        Restart
    }

  override def receive: Receive = Actor.emptyBehavior
}
