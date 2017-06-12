package com.wix.sms.twilio.testkit

import java.util.{List => JList}

import com.google.api.client.http.UrlEncodedParser
import com.google.api.client.util.Base64
import com.wix.hoopoe.http.testkit.EmbeddedHttpProbe
import com.wix.sms.model.Sender
import com.wix.sms.twilio.model.{SmsResponse, SmsResponseParser}
import com.wix.sms.twilio.{Credentials, TwilioHelper}
import spray.http._

import scala.collection.JavaConversions._
import scala.collection.mutable

class TwilioDriver(port: Int) {
  private val probe = new EmbeddedHttpProbe(port, EmbeddedHttpProbe.NotFoundHandler)

  def start(): Unit = {
    probe.doStart()
  }

  def stop(): Unit = {
    probe.doStop()
  }

  def reset(): Unit = {
    probe.reset()
  }

  def aSendMessageFor(credentials: Credentials, sender: Sender, destPhone: String, text: String): SendMessageCtx = {
    new SendMessageCtx(
      credentials = credentials,
      sender = sender,
      destPhone = destPhone,
      text = text)
  }

  class SendMessageCtx(credentials: Credentials, sender: Sender, destPhone: String, text: String) {
    private val expectedParams = TwilioHelper.createRequestParams(
      sender = sender,
      destPhone = destPhone,
      text = text
    )

    def returns(msgId: String): Unit = {
      val response = SmsResponse(
        sid = Some(msgId)
      )

      val responseJson = SmsResponseParser.stringify(response)
      returnsJson(StatusCodes.OK, responseJson)
    }

    def failsDueToBlacklist(): Unit = {
      val response = SmsResponse(
        code = Some("21610"),
        message = Some("The message From/To pair violates a blacklist rule.")
      )

      val responseJson = SmsResponseParser.stringify(response)
      returnsJson(StatusCodes.BadRequest, responseJson)
    }

    def failsWith(code: String, message: String): Unit = {
      val response = SmsResponse(
        code = Some(code),
        message = Some(message)
      )

      val responseJson = SmsResponseParser.stringify(response)
      returnsJson(StatusCodes.OK, responseJson)
    }

    private def returnsJson(statusCode: StatusCode, responseJson: String): Unit = {
      val path = s"/Accounts/${credentials.accountSid}/Messages.json"
      probe.handlers += {
        case HttpRequest(
        HttpMethods.POST,
        Uri.Path(`path`),
        headers,
        entity,
        _) if isStubbedRequestEntity(entity) && isStubbedHeaders(headers) =>
          HttpResponse(
            status = statusCode,
            entity = HttpEntity(ContentTypes.`application/json`, responseJson))
      }
    }

    private def isStubbedRequestEntity(entity: HttpEntity): Boolean = {
      val params = urlDecode(entity.asString)

      params == expectedParams
    }

    private def isStubbedHeaders(headers: Seq[HttpHeader]): Boolean = {
      val expectedAuthorizationValue = s"Basic ${Base64.encodeBase64String(s"${credentials.accountSid}:${credentials.authToken}".getBytes("UTF-8"))}"

      headers.exists { header =>
        header.name == HttpHeaders.Authorization.name &&
          header.value == expectedAuthorizationValue
      }
    }

    private def urlDecode(str: String): Map[String, String] = {
      val params = mutable.LinkedHashMap[String, JList[String]]()
      UrlEncodedParser.parse(str, mutableMapAsJavaMap(params))
      params.mapValues( _.head ).toMap
    }
  }
}
