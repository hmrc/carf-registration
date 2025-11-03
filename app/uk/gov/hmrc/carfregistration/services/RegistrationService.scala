/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.carfregistration.services

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{InternalServerError, NotFound, Ok}
import uk.gov.hmrc.carfregistration.connectors.RegistrationConnector
import uk.gov.hmrc.carfregistration.models.requests.{RegisterIndWithIdAPIRequest, RegisterIndWithIdFrontendRequest, RegisterOrganisationWithIdRequest, RequestCommon, RequestDetailIndividual}
import uk.gov.hmrc.carfregistration.models.responses.{AddressResponse, RegisterIndWithIdFrontendResponse, RegisterOrganisationWithIdResponse}
import uk.gov.hmrc.carfregistration.models.{ApiError, UuidGen}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RegistrationService @Inject() (connector: RegistrationConnector, clock: Clock, uuidGen: UuidGen)(implicit
    ec: ExecutionContext
) {

  def registerIndividualWithId(
      frontendRequest: RegisterIndWithIdFrontendRequest
  )(implicit hc: HeaderCarrier): Future[Either[ApiError, RegisterIndWithIdFrontendResponse]] =
    connector
      .individualWithNino(
        RegisterIndWithIdAPIRequest(
          requestCommon = RequestCommon("NINO", uuidGen, clock),
          requestDetail = RequestDetailIndividual(frontendRequest)
        )
      )
      .value
      .map {
        case Right(response) => Right(RegisterIndWithIdFrontendResponse(response))
        case Left(error)     => Left(error)
      }

  def returnResponseOrganisation(request: RegisterOrganisationWithIdRequest): Result =
    request.IDNumber.take(1) match {
      case "9" => InternalServerError("An unexpected error occurred")
      case "8" => NotFound("The match was unsuccessful")
      case "7" => Ok(Json.toJson(createEmptyOrganisationResponse(request)))
      case _   => Ok(Json.toJson(createFullOrganisationResponse(request)))
    }

  def createFullOrganisationResponse(request: RegisterOrganisationWithIdRequest): RegisterOrganisationWithIdResponse =
    RegisterOrganisationWithIdResponse(
      safeId = "test-safe-id",
      code = Some("0000"),
      organisationName = request.organisationName.getOrElse("Timmy Ltd"),
      address = AddressResponse(
        addressLine1 = "6 High Street",
        addressLine2 = Some("Birmingham"),
        addressLine3 = Some("Nowhereshire"),
        addressLine4 = Some("Down the road"),
        postalCode = Some("B23 2AZ"),
        countryCode = "GB"
      )
    )

  def createEmptyOrganisationResponse(request: RegisterOrganisationWithIdRequest): RegisterOrganisationWithIdResponse =
    RegisterOrganisationWithIdResponse(
      safeId = "test-safe-id",
      code = Some("0002"),
      organisationName = request.organisationName.getOrElse("Park Ltd"),
      address = AddressResponse(
        addressLine1 = "8 High Street",
        addressLine2 = None,
        addressLine3 = None,
        addressLine4 = None,
        postalCode = None,
        countryCode = "GB"
      )
    )
}
