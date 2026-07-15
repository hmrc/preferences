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

package uk.gov.hmrc

import _root_.play.api.mvc.PathBindable
import _root_.play.api.mvc.QueryStringBindable
import org.mongodb.scala.bson.ObjectId
import uk.gov.hmrc.preferences.model.EntityId

import scala.Right
import scala.util.{ Failure, Success, Try }

package object preferences {

  val emptyString = ""

  implicit val entityIdBinder: PathBindable[EntityId] = new PathBindable[EntityId] {
    override def bind(key: String, value: String): Either[String, EntityId] = Right(EntityId(value))

    override def unbind(key: String, entityId: EntityId): String = entityId.value
  }

  implicit val ObjectIdBinder: QueryStringBindable[ObjectId] = new QueryStringBindable[ObjectId] {
    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ObjectId]] =
      params
        .get(key)
        .flatMap(_.headOption)
        .map { value =>
          Try(new ObjectId(value)) match {
            case Success(v) => Right(v)
            case _          => Left(s"Cannot parse parameter '$key' with parameters '$params' as 'ObjectId'")
          }
        }

    def unbind(key: String, value: ObjectId): String =
      QueryStringBindable.bindableString.unbind(key, value.toString)
  }

  implicit def bsonIdBinder(implicit stringBinder: PathBindable[String]): PathBindable[ObjectId] =
    new PathBindable[ObjectId] {

      def bind(key: String, value: String): Either[String, ObjectId] =
        stringBinder.bind(key, value) match {
          case Left(msg) => Left(msg)
          case Right(id) =>
            Try(new ObjectId(id)) match {
              case Success(boid) => Right(boid)
              case Failure(_)    => Left(s"ID $id was invalid")
            }
        }

      def unbind(key: String, value: ObjectId): String = value.toString
    }

  case class TaxIdParams(taxRegime: String, taxId: String)
  case class ResolveParams(resolve: Boolean)

  implicit val taxIdBindable: QueryStringBindable[TaxIdParams] = new QueryStringBindable[TaxIdParams] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, TaxIdParams]] =
      for {
        taxRegime <- QueryStringBindable.bindableString.bind("taxRegime", params)
        taxId     <- QueryStringBindable.bindableString.bind("taxId", params)
      } yield for {
        r <- taxRegime
        i <- taxId
      } yield TaxIdParams(r, i)

    override def unbind(key: String, taxIdParams: TaxIdParams): String =
      Seq(
        QueryStringBindable.bindableString.unbind("taxRegime", taxIdParams.taxRegime),
        QueryStringBindable.bindableString.unbind("taxId", taxIdParams.taxId)
      ).mkString("&")
  }

  implicit val resolveBindable: QueryStringBindable[ResolveParams] = new QueryStringBindable[ResolveParams] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ResolveParams]] =
      for {
        maybeResolve <- QueryStringBindable.bindableBoolean.bind("resolve", params)
      } yield for {
        r <- maybeResolve
      } yield ResolveParams(r)

    override def unbind(key: String, resolveParams: ResolveParams): String =
      Seq(
        QueryStringBindable.bindableBoolean.unbind("resolve", resolveParams.resolve)
      ).mkString("&")
  }

  case class PreferencesParams(taxIdParams: Option[TaxIdParams], resolveParams: Option[ResolveParams])

  private val allowedKeys: Set[String] = Set("taxRegime", "taxId", "resolve")

  implicit val preferencesParamsBindable: QueryStringBindable[PreferencesParams] =
    new QueryStringBindable[PreferencesParams] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, PreferencesParams]] = {
        val invalidKeys = params.keys.filterNot(allowedKeys.contains)
        if (invalidKeys.nonEmpty)
          Some(Left(s"Invalid query string keys: ${invalidKeys.mkString(", ")}"))
        else
          Some(
            Right(
              PreferencesParams(
                taxIdBindable.bind("", params).fold(None)(_.toOption),
                resolveBindable.bind("", params).fold(None)(_.toOption)
              )
            )
          )
      }

      override def unbind(key: String, searchParams: PreferencesParams): String =
        Seq(
          searchParams.taxIdParams.fold("")(p => taxIdBindable.unbind("", p)),
          searchParams.resolveParams.fold("")(p => resolveBindable.unbind("", p))
        ).filterNot(_.isEmpty).mkString("&")
    }

}
