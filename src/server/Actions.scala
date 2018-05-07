package server

import java.net.URI
import java.util.concurrent.atomic.AtomicLong

import api.devices.Devices.Device
import api.internal.{DeviceController, DriversManager, TaskingSupport}
import api.sensors.DevicesManager
import api.sensors.Sensors.Encodings
import api.services.ServicesManager
import io.reactivex.{Maybe, MaybeEmitter}
import org.json4s.jackson.Serialization.write
import utils.CustomSeriDeseri

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.collection.concurrent

//noinspection TypeAnnotation
object Actions {

  private[this] lazy val devm = DevicesManager
  private[this] lazy val drvm = DriversManager
  private[this] lazy val srvm = ServicesManager

  private[this] implicit val formats = CustomSeriDeseri.fmt

  private[this] val ids = new AtomicLong()
  private[this] val tasksQueue = concurrent.TrieMap[Long, Future[String]]()

  def newId(): Long = ids.getAndIncrement()

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

  def getDeviceTasks(id: Int): String =
    write(devm.devices().filter(_.id == id).flatMap(_.tasks).map(t => t.taskingParameters))

  def getAllTasks: String =
    write(devm.devices().map(dev => Map("deviceId" -> dev.id, "supportedTasks" -> dev.tasks.map(t => t.taskingParameters))))

  /*
  def putDeviceTask(id: Int, taskName: String, json: String): Either[Maybe[String], String] = {
    devm.devices().find(_.id == id) match {
      case Some(dev) =>
        dev.driver.controller match {
          case ctrl: DeviceController with TaskingSupport =>
            Left(ctrl.send(taskName, json))
          case _ =>
            Right(s"device $id doesn't support tasking.")
        }
      case _ => Right(s"no device for id: $id")
    }
  }*/

  def putDeviceTask(devId: Int, taskName: String, json: String): Long = {
    val id = newId()
    val task = devm.devices().find(_.id == devId) match {
      case Some(dev) =>
        dev.driver.controller match {
          case ctrl: DeviceController with TaskingSupport =>
            ctrl.send(taskName, json)
          case _ =>
            Maybe.create[String]((emitter: MaybeEmitter[String]) =>
              emitter.onError(new IllegalArgumentException(s"device $devId doesn't support tasking.")))
        }
      case _ =>
        Maybe.create[String]((emitter: MaybeEmitter[String]) =>
        emitter.onError(new IllegalArgumentException(s"no device for id: $devId")))
    }
    val future: Future[String] = Future(task.toSingle("").toFuture.get)
    tasksQueue.put(id, future)
    id
  }

  def getTaskResult(id: Long): Option[Future[String]] = tasksQueue.get(id)

  def deleteTask(id: Long): Boolean = tasksQueue.remove(id).isDefined

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