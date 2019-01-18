package cromwell.services.womtool.models

import io.circe.{Decoder, Encoder, HCursor, Json}
import wom.types._

object WomTypeJsonSupport {
  implicit val womTypeEncoder: Encoder[WomType] = new Encoder[WomType] {
    final def apply(a: WomType): Json = {
      a match {
        case a: WomMapType =>
          Json.obj(
            ("typeName", Json.fromString("Map")),
            ("mapType",
              Json.obj(
                ("keyType", womTypeEncoder.apply(a.keyType)),
                ("valueType", womTypeEncoder.apply(a.valueType))
              )
            )
          )
        case a: WomArrayType =>
          Json.obj(
            ("typeName", Json.fromString("Array")),
            ("arrayType", womTypeEncoder.apply(a.memberType))
          )
        case a: WomOptionalType =>
          Json.obj(
            ("typeName", Json.fromString("Optional")),
            ("optionalType", womTypeEncoder.apply(a.memberType))
          )
        case _ =>
          Json.obj(
            ("typeName", Json.fromString(a.toDisplayString))
          )
      }
    }
  }

  implicit val womTypeDecoder: Decoder[WomType] = new Decoder[WomType] {
    final def apply(c: HCursor): Decoder.Result[WomType] = ???
  }
}