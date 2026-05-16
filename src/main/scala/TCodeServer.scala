package io.github.nicheapplab.tcodeserver

import org.apache.pekko
import pekko.actor.typed.{ ActorSystem, ActorRef, Behavior}
import pekko.actor.typed.scaladsl.Behaviors
import pekko.http.scaladsl.ConnectionContext
import pekko.grpc.scaladsl.ServiceHandler
import pekko.http.scaladsl.Http
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import io.github.nicheapplab.tcodeengine._
import com.typesafe.config.ConfigFactory

object TCodeServer {

  def createEngine(): SQLiteInteractiveEngine = {
    val tcode_tbl_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/tcode_tbl.db"
    val mazegaki_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/mazegaki.db"
    val bushu_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/bushu.db"
    val jdbc_prefix = "jdbc:sqlite"

    new SQLiteInteractiveEngine(jdbc_prefix, tcode_tbl_path, mazegaki_path, bushu_path) with QwertyLayout
  }

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory
      .parseString("pekko.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())

    ActorSystem[Nothing](Behaviors.setup[Nothing] { context =>
      val engine = createEngine()
      val engineActorRef = context.spawn(TCodeEngineActor(engine), "EngineActor")

      val serverBootstrap = new TCodeServer(context.system, engineActorRef)
      serverBootstrap.run()

      Behaviors.empty
    }, "TCodeSystem", conf)
  }
}

class TCodeServer(system: ActorSystem[_], engineActorRef: ActorRef[TCodeEngineCommand]) {

  def run(): Future[Http.ServerBinding] = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    val serviceImpl = new TCodeServiceImpl(engineActorRef)
    val serviceHandler = TCodeServiceHandler(serviceImpl)

    val binding = Http()
      .newServerAt("localhost", 8080)
      .bind(serviceHandler)

    binding.onComplete {
      case scala.util.Success(bound) =>
        system.log.info(s"TCodeServer online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
      case scala.util.Failure(e) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", e)
        system.terminate()
    }

    binding
  }
}
