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

package uk.gov.hmrc.carfregistration.models.responses.errors

import play.api.libs.json.{Format, Json, OFormat}

case class ErrorCode(Value: String) extends AnyVal

object ErrorCode {
  implicit val format: Format[ErrorCode] = Json.valueFormat[ErrorCode]
}

case class ErrorMessage(Value: String) extends AnyVal

object ErrorMessage {
  implicit val format: Format[ErrorMessage] = Json.valueFormat[ErrorMessage]
}

case class ErrorDetail(
    timestamp: String,
    correlationId: String,
    errorCode: Option[ErrorCode],
    errorMessage: Option[ErrorMessage],
    source: Option[String],
    sourceFaultDetail: Option[SourceFaultDetail]
)

object ErrorDetail {
  implicit val format: OFormat[ErrorDetail] = Json.format[ErrorDetail]
}
