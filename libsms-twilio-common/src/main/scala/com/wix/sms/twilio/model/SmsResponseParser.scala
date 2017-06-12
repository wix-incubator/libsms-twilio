package com.wix.sms.twilio.model

import org.json4s.DefaultFormats
import org.json4s.native.Serialization

object SmsResponseParser {
  private implicit val formats = DefaultFormats

  def stringify(obj: SmsResponse): String = {
    Serialization.write(obj)
  }

  def parse(json: String): SmsResponse = {
    Serialization.read[SmsResponse](json)
  }
}
