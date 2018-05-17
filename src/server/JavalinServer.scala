package server

import java.nio.file.Paths

import api.events.EventBus
import api.events.SensorsHubEvents.{DeviceCreated, SensorsHubEvent}
import api.sensors.DevicesManager
import api.sensors.Sensors.DataStream
import io.javalin.{Javalin, LogLevel}
import io.javalin.embeddedserver.Location
import io.javalin.embeddedserver.jetty.websocket.{WebSocketConfig, WebSocketHandler, WsSession}
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.json4s.jackson.JsonMethods._
import org.slf4j.LoggerFactory
import rx.lang.scala.Subscription
import server.Actions._
import utils.CustomSeriDeseri.fmt
import pureconfig._
import utils.CustomSeriDeseri
import ws.WsObservable

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global

case class DataStreamWebSocket(ds: DataStream) extends WebSocketConfig {

  private[this] var sessions = TrieMap.empty[String, Disposable]

  override def configure(ws: WebSocketHandler): Unit = {
    ws.onConnect((session: WsSession) => {
      val sub =
        ds.observable
          .subscribeOn(Schedulers.io())
          .subscribe(obs => session.getRemote.sendString(obs.result.toString))
      sessions.put(session.getId, sub)
    })

    ws.onMessage((session: WsSession, msg: String) => println(msg))

    ws.onError((session: WsSession, throwable: Throwable) => println(throwable.getMessage))

    ws.onClose((session: WsSession, statusCode: Int, reason: String) =>
      sessions.remove(session.getId).foreach(_.dispose()))
  }
}

object JavalinServer extends App {

  private[this] val logger = LoggerFactory.getLogger("sh.rest-server")

  case class ServerConfig(context: String, port: Int)

  private val cfg = loadConfigFromFiles[ServerConfig](Seq(Paths.get(args.head, "server.conf")))
    .getOrElse(ServerConfig("/", 8081))

  private val selfAddress = s"http://localhost:${cfg.port}${cfg.context}"

  val server = Javalin.create()
    .enableCorsForAllOrigins()
    .contextPath(cfg.context)
    .enableStaticFiles(Paths.get(args.head, "public/").toString, Location.EXTERNAL)
    .requestLogLevel(LogLevel.OFF)
    .port(cfg.port)

  server.get("/drivers", ctx => ctx.result(getDrivers))
  server.get("/unitsOfMeasurement", ctx => ctx.result(getUnitsOfMeasurement))
  server.get("/dataStreams", ctx => ctx.result(getDataStreams))
  server.get("/observedProperties", ctx => ctx.result(getObservedProperties))
  server.get("/featuresOfInterest", ctx => ctx.result(getFeaturesOfInterest))
  server.get("/services", ctx => ctx.result(getServices))
  server.get("/devices", ctx => ctx.result(getDevices))
  server.get("/devices/tasks", ctx => ctx.result(getAllTasks))
  server.get("/devices/:id/tasks", ctx => ctx.result(getDeviceTasks(ctx.param("id").toInt)))
  server.get("/dataStreams/:id", ctx => getDataStreamObs(ctx.param("id")).fold(ctx.status(404))(ctx.result))

  server.get("/devices/tasks/queue/:id", ctx => {
    val id = ctx.param("id")
    Try(id.toLong).toOption.fold
    {
      ctx.status(300)
      ctx.result(s"cannot parse id: $id")
    }
    {
      id => {
        val task = getTaskResult(id)
        task match {
          case Some(future) =>
            val completed = future.isCompleted
            if (completed) {
              println(future)
              ctx.header("DataType", "application/json")
              val s = Await.result(future, Duration.Inf)
              println("res: ", s)

              val res = new StringBuilder("""{"status":"ready"""")
              if (s.isEmpty) res append "}"
              else res append s""","result":${compact(parse(s))}}"""
              ctx.result(res.toString())

            } else ctx.result(s"""{"status":"pending"}""")

            ctx.status(200)
          case _ =>
            ctx.status(404)
        }
      }
    }
  })

  server.put("/devices/:id/tasks/:task", ctx => {
    val id = ctx.param("id")
    val task = ctx.param("task")
    Try(id.toInt).toOption.fold
    {
      ctx.status(300)
      ctx.result(s"cannot parse id: $id")
    }
    {
      id => {
        val taskId = putDeviceTask(id, task, ctx.body())
        ctx.header("Location", s"${selfAddress}devices/tasks/queue/$taskId")
        ctx.status(202)
      }
    }
  })

  server.post("/devices", ctx => {
    parseOpt(ctx.body()).flatMap(_.extractOpt[DeviceMetadata]) match {
      case Some(dev) => createDevice(dev).fold(ctx.status(300))(ctx.result)
      case _ => ctx.status(300)
    }
  })

  server.delete("/devices/:id", ctx =>
    Try(ctx.param("id").toInt).toOption.fold(ctx.status(300))(id => {
      DevicesManager.deleteDevice(id)
      ctx.status(204)
    }))

  DevicesManager.devices()
    .flatMap(_.dataStreams)
    .foreach(ds => server.ws(s"/${ds.sensor.id+"_"+ds.name}", DataStreamWebSocket(ds)))


  EventBus.events
    .subscribeOn(Schedulers.io())
    .subscribe(evt => evt match {
    case DeviceCreated(dev) =>
      dev.dataStreams.foreach(ds =>
        server.ws(s"/${ds.sensor.id+"_"+ds.name}", DataStreamWebSocket(ds)))
    case _ => ()
  })

  server.ws("/audit", WsObservable[SensorsHubEvent](EventBus.events,
    e => CustomSeriDeseri.evtToJson(e)))

  def start(): Unit = server.start()

  def stop(): Unit = server.stop()

  def restart(): Unit = server.stop(); server.start()

}
