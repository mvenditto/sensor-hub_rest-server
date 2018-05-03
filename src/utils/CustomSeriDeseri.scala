package utils

import java.net.URI
import java.time.Instant

import api.sensors.DevicesManager
import api.sensors.Sensors.{DataStream, ObservationType, ObservedProperty, UnitOfMeasurement}
import org.json4s.JsonAST.{JField, JInt, JObject, JString}
import org.json4s.JsonDSL._
import org.json4s.{CustomSerializer, Extraction}

object CustomSeriDeseri {

  implicit val fmt = org.json4s.DefaultFormats ++
    Seq(
      new InstantSerializer(),
      new URISerializer(),
      new ObservedPropertySerializer(),
      new DataStreamSerializer(),
      new UnitOfMeasurementSerializer())

  class InstantSerializer extends CustomSerializer[Instant](_ => ( {
    case json: JObject =>
      val epochSeconds = (json \ "epochSeconds").extract[Long]
      Instant.ofEpochSecond(epochSeconds)
  }, {
    case i: Instant => "secondsEpoch" -> i.getEpochSecond
  }
  ))

  class URISerializer extends CustomSerializer[URI](_ => ( {
    case json: JObject => new URI(json.toString)
  }, {
    case uri: URI => uri.toString
  }
  ))

  class ObservedPropertySerializer extends CustomSerializer[ObservedProperty](_ => ( {
    case json: JObject =>
      ObservedProperty(
        (json \ "name").extract[String],
        (json \ "definition").extract[URI],
        (json \ "description").extract[String])
  }, {
    case op: ObservedProperty =>
      JObject(
        JField("name", JString(op.name)) ::
          JField("definition", JString(op.definition.toString)) ::
          JField("description", JString(op.description)) :: Nil)
  }
  ))

  class UnitOfMeasurementSerializer extends CustomSerializer[UnitOfMeasurement](_ => ( {
    case json: JObject =>
      UnitOfMeasurement(
        (json \ "name").extract[String],
        (json \ "symbol").extract[String],
        (json \ "definition").extract[URI])
  }, {
    case op: UnitOfMeasurement =>
      JObject(
        JField("name", JString(op.name)) ::
          JField("definition", JString(op.definition.toString)) ::
          JField("symbol", JString(op.symbol)) :: Nil)
  }))

  class DataStreamSerializer extends CustomSerializer[DataStream](_ => ( {
    case json: JObject =>
      DataStream(
        name = (json \ "name").extract[String],
        description = (json \ "description").extract[String],
        observationType = ObservationType((json \ "observationType").extract[String]),
        observedProperty = (json \ "observedProperty").extract[ObservedProperty],
        sensor = DevicesManager.getDevice((json \ "sensor_id").extract[Int]).orNull, //TODO not working, needs fix!
        procedure = () => null,
        unitOfMeasurement = (json \ "unitOfMeasurement").extract[UnitOfMeasurement])
  }, {
    case ds: DataStream =>
      JObject(
          JField("name", JString(ds.name)) ::
          JField("description", JString(ds.description)) ::
          JField("observationType", JString(ds.observationType.name)) ::
          JField("observedProperty", Extraction.decompose(ds.observedProperty)) ::
          JField("unitOfMeasurement", Extraction.decompose(ds.unitOfMeasurement)) ::
          JField("sensor", JObject(
            JField("id", JInt(ds.sensor.id)) ::
            JField("name", JString(ds.sensor.name)) ::
            JField("driver", Extraction.decompose(ds.sensor.driver.metadata)) ::
            JField("description", JString(ds.sensor.description)) :: Nil)) :: Nil)
  }))

}