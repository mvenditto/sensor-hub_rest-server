package server

import spi.service.{Service, ServiceMetadata}

class RestServerService extends Service {

  override def init(metadata: ServiceMetadata): Unit = {
    JavalinServer.main(Array(metadata.rootDir))
    JavalinServer.start()
  }

  override def restart(): Unit = JavalinServer.restart()

  override def dispose(): Unit = JavalinServer.stop()

}