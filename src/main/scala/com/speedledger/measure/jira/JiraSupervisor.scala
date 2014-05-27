package com.speedledger.measure.jira

import akka.actor.{Props, OneForOneStrategy, Actor, ActorLogging}
import akka.actor.SupervisorStrategy.Restart
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import akka.actor.OneForOneStrategy

/**
 * Actor to handle restart of the UpdaterActor when a failure occurs
 */
class JiraSupervisor extends Actor with ActorLogging {

  import context.dispatcher
  val updater = context.actorOf(Props[UpdaterActor], "updater")
  val config = ConfigFactory.load()
  val interval = FiniteDuration(config.getDuration("updater.interval", SECONDS), SECONDS)
  log.debug(s"Update interval is $interval")
  context.system.scheduler.schedule(initialDelay = 1.second, interval, updater, Tick)
  context.setReceiveTimeout(1 hours)

  override val supervisorStrategy =
    OneForOneStrategy() {
      case ex: Exception =>
        log.error(ex, "Exception during jira fetch. Restarting actor...")
        Restart
    }

  override def receive: Receive = Actor.emptyBehavior
}
