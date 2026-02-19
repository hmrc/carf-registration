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

package controllers

import base.SpecBase
import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results.BadRequest
import play.api.test.Helpers.{contentAsString, status}
import uk.gov.hmrc.carfregistration.config.AppConfig
import uk.gov.hmrc.carfregistration.connectors.SubscriptionConnector
import uk.gov.hmrc.carfregistration.controllers.SubscriptionController
import uk.gov.hmrc.carfregistration.models.requests.{Contact, SubscriptionRequest}
import uk.gov.hmrc.carfregistration.models.{Individual, InternalServerError}
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future

class SubscriptionControllerSpec extends SpecBase {

  val mockConnector: SubscriptionConnector   = mock[SubscriptionConnector]
  val mockAppConfig: AppConfig               = mock[AppConfig]
  val testController: SubscriptionController =
    new SubscriptionController(mockAppConfig, cc, fakeAuthAction, mockConnector)

  val testContact: Contact = Contact(
    email = "test@example.com",
    individual = Some(Individual("John", "Doe", None)),
    organisation = None,
    phone = Some("1234567890")
  )

  val testSubscriptionRequest: SubscriptionRequest = SubscriptionRequest(
    gbUser = true,
    idNumber = "XM000123456789",
    idType = "SAFE",
    primaryContact = testContact,
    secondaryContact = None,
    tradingName = Some("Test Trading Ltd")
  )

  val testSubscriptionRequestJson: JsValue = Json.toJson(testSubscriptionRequest)

  val testSuccessResponseBody: String =
    """{
      |  "success": {
      |    "CARFReference": "XCARF1234567890",
      |    "processingDate": "2001-12-17T09:30:47Z"
      |  }
      |}""".stripMargin

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConnector)
  }

  "SubscriptionController" - {
    "createSubscription" - {
      "must return success response when the connector successfully sends subscription information" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(OK, testSuccessResponseBody)
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result)          mustBe OK
        contentAsString(result) mustBe testSuccessResponseBody
      }

      "must return not found response when the connector returns NOT_FOUND status" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(NOT_FOUND, "Not found")
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result) mustBe NOT_FOUND
      }

      "must return bad request response when the connector returns BAD_REQUEST status" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(BAD_REQUEST, "Bad request")
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result) mustBe BAD_REQUEST
      }

      "must return service unavailable response when the connector returns SERVICE_UNAVAILABLE status" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(SERVICE_UNAVAILABLE, "Service unavailable")
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result) mustBe SERVICE_UNAVAILABLE
      }

      "must return forbidden response when the connector returns FORBIDDEN status" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(FORBIDDEN, "Forbidden")
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result) mustBe FORBIDDEN
      }

      "must return unprocessable entity with already_registered status when error code returned is 007" in {
        val alreadyRegisteredResponseBody =
          """{"errorDetail":{"errorCode":"007","errorMessage":"Already registered"}}"""

        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(UNPROCESSABLE_ENTITY, alreadyRegisteredResponseBody)
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result)        mustBe UNPROCESSABLE_ENTITY
        contentAsString(result) must include("already_registered")
      }

      "must return unprocessable entity 422 with 'Invalid ID type' message when error code returned is 015" in {
        val duplicateSubmissionResponseBody =
          """{"errorDetail":{"errorCode":"015","errorMessage":"Invalid ID type"}}"""

        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(UNPROCESSABLE_ENTITY, duplicateSubmissionResponseBody)
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result)        mustBe UNPROCESSABLE_ENTITY
        contentAsString(result) must include("Invalid ID type")
      }

      "must return internal server error when the connector returns an unexpected status" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(
            EitherT.rightT[Future, uk.gov.hmrc.carfregistration.models.ApiError](
              HttpResponse(502, "Bad gateway")
            )
          )

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return internal server error when the connector returns a Left error" in {
        when(mockConnector.sendSubscriptionInformation(any())(any()))
          .thenReturn(EitherT.leftT[Future, HttpResponse](InternalServerError))

        val result = testController.createSubscription()(fakeRequestWithJsonBody(testSubscriptionRequestJson))

        status(result)        mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) must include("Error sending subscription information")
      }

      "must return bad request when the request body is not valid JSON" in {
        val result = testController.createSubscription()(fakeRequestWithJsonBody(Json.toJson("invalid request")))

        result.toString mustBe Future.successful(BadRequest("")).toString
      }
    }
  }
}
