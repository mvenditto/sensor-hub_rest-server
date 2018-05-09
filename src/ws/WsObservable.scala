package ws

import io.javalin.embeddedserver.jetty.websocket.{WebSocketConfig, WebSocketHandler, WsSession}
import rx.lang.scala.Observable
import rx.lang.scala.Subscription

import scala.collection.concurrent.TrieMap

case class WsObservable[T](obs: Observable[T], mapper: T => String) extends WebSocketConfig {

  private[this] val sessions = TrieMap.empty[String, Subscription]

  def configure(ws: WebSocketHandler): Unit = {
    ws.onConnect(session => {
      val sub = obs.subscribe(item => session.send(mapper(item)))
      sessions.put(session.getId, sub)
    })
    ws.onClose((session: WsSession, statusCode: Int, reason: String) =>
      sessions.get(session.getId).foreach(_.unsubscribe()))

    ws.onMessage((session: WsSession, msg: String) => println(msg))

    ws.onError((session: WsSession, throwable: Throwable) => println(throwable.getMessage))
  }
}
