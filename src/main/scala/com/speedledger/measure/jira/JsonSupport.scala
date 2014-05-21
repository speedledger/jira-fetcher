package com.speedledger.measure.jira

import spray.httpx.Json4sSupport
import org.json4s.{DefaultFormats, Formats}


trait JsonSupport extends Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats
}
