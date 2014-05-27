package com.speedledger.measure.jira

import com.typesafe.config.ConfigFactory
import akka.actor.Actor

trait JiraSupport {
  this: Actor =>
  val config = ConfigFactory.load().getConfig("jira")
  val searchUrl = config.getString("search-url")
  val issueUrl = config.getString("issue-url")
  val userName = config.getString("username")
  val password = config.getString("password")
  val maxResults = config.getInt("max-results")
}
