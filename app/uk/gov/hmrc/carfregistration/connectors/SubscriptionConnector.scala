/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.carfregistration.connectors

import cats.data.EitherT
import com.google.inject.Inject
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.carfregistration.config.AppConfig
import uk.gov.hmrc.carfregistration.models.requests.SubscriptionRequest
import uk.gov.hmrc.carfregistration.models.{ApiError, InternalServerError, NotFoundError}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionConnector @Inject() (
    config: AppConfig,
    http: HttpClientV2
)(implicit ec: ExecutionContext)
    extends Logging {

  private val backendBaseUrl = config.createSubscriptionBaseUrl

  def sendSubscriptionInformation(
      subscription: SubscriptionRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, ApiError, HttpResponse] =
    createSubscription(subscription, url"$backendBaseUrl")

  private def createSubscription(request: SubscriptionRequest, endpoint: URL)(implicit
      hc: HeaderCarrier
  ): EitherT[Future, ApiError, HttpResponse] =
    logger.info(s"Calling endpoint: ${endpoint.toString}")
    EitherT {
      http
        .post(endpoint)
        .withBody(Json.toJson(request))
        .execute[HttpResponse]
        .map {
          case response if response.status == OK        =>
            Right(response)
          case response if response.status == NOT_FOUND =>
            logger.warn(
              s"No match could be found for this organisation: status code: ${response.status}, from endpoint: ${endpoint.toURI}"
            )
            Left(NotFoundError)
          case response                                 =>
            logger.warn(s"Unexpected response: status code: ${response.status}, from endpoint: ${endpoint.toURI}")
            Left(InternalServerError)
        }
    }

}
