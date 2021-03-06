package json.schema.parser

import java.net.URI
import java.util.NoSuchElementException

import argonaut.Argonaut._
import argonaut._

import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex
import scalaz.NonEmptyList


trait Decoders extends DecodeJsons {

  implicit lazy val RegexDecoder: DecodeJson[Regex] = StringDecodeJson.map(_.r)

  implicit lazy val UriDecoder: CodecJson[URI] = CodecJson.derived(
    EncodeJson(v => jString(v.toString)),
    StringDecodeJson.flatMap {
      uri =>
        DecodeJson(
          j =>
            Try {
              DecodeResult.ok(new URI(uri))
            } match {
              case Success(result) => result
              case Failure(_) => DecodeResult.fail("Uri", j.history)
            }
        )
    })

  implicit lazy val FormatCodec: CodecJson[Format.Format] =
    CodecJson[Format.Format]((v: Format.Format) => v.toString.asJson, (j: HCursor) => j.as[String].flatMap {
      s: String =>
        try {
          DecodeResult.ok(Format.withName(s))
        } catch {
          case e: NoSuchElementException => DecodeResult.fail("Format", j.history)
        }
    })

  implicit lazy val SimpleTypeCodec: CodecJson[SimpleType.SimpleType] =
    CodecJson[SimpleType.SimpleType](
    (v: SimpleType.SimpleType) => v.toString.asJson,
    (j: HCursor) => j.as[String].flatMap {
      s: String =>
        try {
          DecodeResult.ok(SimpleType.withName(s))
        } catch {
          case e: NoSuchElementException => DecodeResult.fail("SimpleType", j.history)
        }
    })

  implicit def nonEmptyListDecodeJson[A: DecodeJson]: DecodeJson[NonEmptyList[A]] =
    implicitly[DecodeJson[List[A]]] flatMap {
      list =>
        if (list.nonEmpty) DecodeJson(_ => DecodeResult.ok(NonEmptyList.nel(list.head, list.tail)))
        else DecodeJson[NonEmptyList[A]](c => DecodeResult.fail("[A]NonEmptyList[A]", c.history))
    } setName "[A]List[A]"

  def oneOrNonEmptyList[T](implicit e: DecodeJson[T]): DecodeJson[NonEmptyList[T]] =
    nonEmptyListDecodeJson[T] ||| e.map(NonEmptyList(_))

  def aSetDecodeJsonStrict[A: DecodeJson]: DecodeJson[Set[A]] =
    implicitly[DecodeJson[List[A]]] flatMap {
      list =>
        val set = list.toSet
        if (set.size == list.size) DecodeJson(_ => DecodeResult.ok(set))
        else DecodeJson[Set[A]](c => DecodeResult.fail("[A]List[A]", c.history))
    } setName "[A]Set[A]"

  def nonEmptySetDecodeJsonStrict[A: DecodeJson]: DecodeJson[Set[A]] =
    aSetDecodeJsonStrict[A] flatMap {
      set =>
        if (set.nonEmpty) DecodeJson(_ => DecodeResult.ok(set))
        else DecodeJson[Set[A]](c => DecodeResult.fail("[A]Set[A]", c.history))
    } setName "[A]Set[A]"


  def oneOrSetStrict[T](implicit e: DecodeJson[T]): DecodeJson[Set[T]] =
    nonEmptySetDecodeJsonStrict[T] ||| e.map(Set(_))

  implicit def either[A, B](implicit x: DecodeJson[A], y: DecodeJson[B]): DecodeJson[Either[A, B]] =
    DecodeJson(c => {
      val q: DecodeResult[Either[A, B]] = x(c).map(Left(_))
      q.result.fold(_ => y(c).map(Right(_)), _ => q)
    })


}
