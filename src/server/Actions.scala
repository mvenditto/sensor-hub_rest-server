package server

import java.net.URI

import api.internal.DriversManager
import api.sensors.DevicesManager
import api.sensors.Sensors.Encodings
import api.services.ServicesManager
import org.json4s.jackson.Serialization.write
import utils.CustomSeriDeseri

import scala.util.{Failure, Success, Try}

//noinspection TypeAnnotation
object Actions {

  private[this] lazy val devm = DevicesManager
  private[this] lazy val drvm = DriversManager
  private[this] lazy val srvm = ServicesManager

  private[this] implicit val formats = CustomSeriDeseri.fmt

  case class DeviceMetadata(
    name: String,
    description: String,
    metadataEncoding: String,
    metadata: String,
    driverName: String
  )

  case class DeviceMetadataWithId(
    id: Int,
    name: String,
    description: String,
    metadataEncoding: String,
    metadata: String,
    driverName: String
  )

  def getObservedProperties: String =
    write(devm.devices().flatMap(_.dataStreams.map(_.observedProperty)).toSet)

  def getUnitsOfMeasurement: String =
    write(devm.devices().flatMap(_.dataStreams.map(_.unitOfMeasurement)).toSet)

  def getDataStreams: String =
    write(devm.devices().flatMap(_.dataStreams))

  //sensorid_dsname
  def getDataStreamObs(id: String): Option[String] = {
    Try {
      val t = id.split('_')
      val sensorId = t.head.toInt
      val dsName = t.last
      devm.devices()
        .withFilter(_.id == sensorId)
        .flatMap(_.dataStreams)
        .find(_.name == dsName)
        .map(ds => write(ds.procedure()))
        .get
    }.toOption
  }

  def getDrivers: String =
    write(drvm.availableDrivers)

  def getServices: String =
    write(srvm.registeredServices)

  def getDevices: String =
    write(devm.devices().map(d =>
      DeviceMetadataWithId(d.id, d.name, d.description, d.encodingType.name, d.metadata.toString, d.driver.metadata.name)))

  def createDevice(dev: DeviceMetadata): Option[String] = {
    DriversManager.instanceDriver(dev.driverName) match {
      case Some(drv) =>
        val tryCreate = Try {
          drv.controller.init()
          drv.controller.start()
          DevicesManager.createDevice(dev.name, dev.description, Encodings.PDF, new URI(dev.metadata), drv)
        }

        tryCreate.map(d => s"""{"device_id": "${d.id}"}""").toOption
      case _ => None
    }
  }

  def deleteDevice(id: Int): Unit = DevicesManager.deleteDevice(id)

}