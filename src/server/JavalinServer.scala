package server

import java.nio.file.Paths

import api.events.SensorsHubEvents.DeviceCreated
import api.sensors.DevicesManager
import api.sensors.Sensors.DataStream
import io.javalin.Javalin
import io.javalin.embeddedserver.Location
import io.javalin.embeddedserver.jetty.websocket.{WebSocketConfig, WebSocketHandler, WsSession}
import org.json4s.jackson.JsonMethods._
import rx.lang.scala.Subscription
import server.Actions._
import utils.CustomSeriDeseri.fmt
import pureconfig._

import scala.util.Try

case class DataStreamWebSocket(ds: DataStream) extends WebSocketConfig {

  private[this] var sessions = Map.empty[String, Subscription]

  override def configure(ws: WebSocketHandler): Unit = {
    ws.onConnect((session: WsSession) => {
      val sub = ds.observable.subscribe(obs => session.getRemote.sendString(obs.result.toString))
      sessions = sessions ++ Map(session.getId -> sub)
    })

    ws.onMessage((session: WsSession, msg: String) => println(msg))

    ws.onError((session: WsSession, throwable: Throwable) => println(throwable.getMessage))

    ws.onClose((session: WsSession, statusCode: Int, reason: String) => {
      sessions.get(session.getId).foreach(_.unsubscribe())
      sessions = sessions.filter(_._1 != session.getId)
    })
  }
}

object JavalinServer extends App {

  case class ServerConfig(context: String, port: Int)

  private val cfg = loadConfigFromFiles[ServerConfig](Seq(Paths.get(args.head, "server.conf")))
    .getOrElse(ServerConfig("/", 8081))

  val server = Javalin.create()
    .enableCorsForAllOrigins()
    .contextPath(cfg.context)
    .enableStaticFiles(Paths.get(args.head, "public/").toString, Location.EXTERNAL)
    .port(cfg.port)

  server.get("/drivers", ctx => ctx.result(getDrivers))
  server.get("/unitsOfMeasurement", ctx => ctx.result(getUnitsOfMeasurement))
  server.get("/dataStreams", ctx => ctx.result(getDataStreams))
  server.get("/observedProperties", ctx => ctx.result(getObservedProperties))
  server.get("/services", ctx => ctx.result(getServices))
  server.get("/devices", ctx => ctx.result(getDevices))
  server.get("/dataStreams/:id", ctx => getDataStreamObs(ctx.param("id")).fold(ctx.status(404))(ctx.result))

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


  DevicesManager.events.subscribe(evt => evt match {
    case DeviceCreated(dev) =>
      dev.dataStreams.foreach(ds =>
        server.ws(s"/${ds.sensor.id+"_"+ds.name}", DataStreamWebSocket(ds)))
    case _ => ()
  })


  def start(): Unit = server.start()

  def stop(): Unit = server.stop()

  def restart(): Unit = server.stop(); server.start()

}
