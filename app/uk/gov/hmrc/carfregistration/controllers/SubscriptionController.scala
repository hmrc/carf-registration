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

package uk.gov.hmrc.carfregistration.controllers

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.carfregistration.config.AppConfig
import uk.gov.hmrc.carfregistration.connectors.SubscriptionConnector
import uk.gov.hmrc.carfregistration.controllers.actions.AuthAction
import uk.gov.hmrc.carfregistration.models.ErrorDetails
import uk.gov.hmrc.carfregistration.models.requests.SubscriptionRequest
import uk.gov.hmrc.http.HttpErrorFunctions.is2xx
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class SubscriptionController @Inject() (
    val config: AppConfig,
    cc: ControllerComponents,
    authorise: AuthAction,
    subscriptionConnector: SubscriptionConnector
)(implicit executionContext: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def createSubscription: Action[JsValue] = authorise(parse.json).async { implicit request =>
    request.body
      .validate[SubscriptionRequest]
      .fold(
        invalid = _ =>
          Future.successful(
            BadRequest("SubscriptionRequest is invalid")
          ),
        valid = subscription =>
          subscriptionConnector.sendSubscriptionInformation(subscription).value.map {
            case Right(httpResponse) => convertToResult(httpResponse)
            case Left(apiError)      =>
              logger.warn(s"Error sending subscription information: $apiError")
              InternalServerError("Error sending subscription information")
          }
      )
  }

  private def convertToResult(httpResponse: HttpResponse): Result =
    httpResponse.status match {
      case status if is2xx(status) =>
        Ok(httpResponse.body)
      case NOT_FOUND               =>
        logDownStreamError(httpResponse.body)
        NotFound(httpResponse.body)
      case BAD_REQUEST             =>
        logDownStreamError(httpResponse.body)
        BadRequest(httpResponse.body)
      case SERVICE_UNAVAILABLE     =>
        logDownStreamError(httpResponse.body)
        ServiceUnavailable(httpResponse.body)
      case FORBIDDEN               =>
        logDownStreamError(httpResponse.body)
        Forbidden(httpResponse.body)
      case UNPROCESSABLE_ENTITY    =>
        logDownStreamError(httpResponse.body)
        if (isAlreadyRegistered(httpResponse.body)) {
          logger.warn(s"Already registered. ${httpResponse.status} response status")
          Json
            .parse(httpResponse.body)
            .asOpt[JsObject]
            .fold(
              UnprocessableEntity(Json.obj("status" -> "already_registered"))
            )(json => UnprocessableEntity(json + ("status" -> Json.toJson("already_registered"))))
        } else {
          logger.warn(s"Duplicate submission to ETMP. ${httpResponse.status} response status")
          UnprocessableEntity(httpResponse.body)
        }
      case _                       =>
        logDownStreamError(httpResponse.body)
        logger.warn(s"Unexpected error from ETMP. ${httpResponse.status} response status")
        InternalServerError(httpResponse.body)
    }

  private def logDownStreamError(body: String): Unit = {
    val error = Try(Json.parse(body).validate[ErrorDetails])
    error match {
      case Success(JsSuccess(value, _)) =>
        logger.warn(
          s"Error with submission: ${value.errorDetail.sourceFaultDetail.map(_.detail.mkString)}"
        )
      case _                            =>
        logger.warn("Error with submission but return is not a valid json")
    }
  }

  private def isAlreadyRegistered(responseBody: String): Boolean =
    Try(Json.parse(responseBody)).toOption
      .flatMap(json => (json \\ "errorCode").headOption.flatMap(_.asOpt[String]))
      .contains("007")

}
