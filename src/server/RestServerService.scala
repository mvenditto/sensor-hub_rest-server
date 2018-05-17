package server

import spi.service.{Service, ServiceMetadata}

class RestServerService extends Service {

  System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

  override def init(metadata: ServiceMetadata): Unit =
    JavalinServer.main(Array(metadata.rootDir))

  override def start(): Unit = JavalinServer.start()

  override def restart(): Unit = JavalinServer.restart()

  override def dispose(): Unit = JavalinServer.stop()

  override def stop(): Unit = JavalinServer.stop()
}