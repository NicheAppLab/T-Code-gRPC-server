package io.github.nicheapplab.tcodeserver

import org.apache.pekko
import pekko.actor.typed.{ ActorRef, ActorSystem, Behavior }
import pekko.actor.typed.scaladsl.Behaviors
import pekko.http.scaladsl.ConnectionContext
import pekko.grpc.scaladsl.ServiceHandler
import pekko.http.scaladsl.Http
import pekko.http.scaladsl.model.HttpMethods
import pekko.http.scaladsl.server.Route
import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.model.headers.HttpOrigin
import pekko.http.cors.scaladsl.CorsDirectives.cors
import pekko.http.cors.scaladsl.settings.CorsSettings
import pekko.http.cors.scaladsl.model.HttpOriginMatcher
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import io.github.nicheapplab.tcodeengine._
import com.typesafe.config.ConfigFactory

object TCodeServer {

  val conf = ConfigFactory.load()

  def createEngine(): SQLiteInteractiveEngine = {
    import java.nio.file.{ Paths, Files }
    val tcode_tbl_path = Paths.get(conf.getString("tcode-server.databases.tcode-tbl"))
    println(tcode_tbl_path)
    val mazegaki_path = Paths.get(conf.getString("tcode-server.databases.mazegaki"))
    val bushu_path = Paths.get(conf.getString("tcode-server.databases.bushu"))

    val parentDir = tcode_tbl_path.getParent
    if (parentDir != null) {
      Files.createDirectories(parentDir)
    }
    val jdbc_prefix = "jdbc:sqlite"

    new SQLiteInteractiveEngine(
      jdbc_prefix,
      tcode_tbl_path.toString,
      mazegaki_path.toString,
      bushu_path.toString
    ) with QwertyLayout
  }

  def main(args: Array[String]): Unit = {
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

    val config = ConfigFactory.load()
    val serverHost = config.getString("tcode-server.host")
    val serverPort = config.getInt("tcode-server.port")
    val configuredOrigin = config.getString("tcode-server.allowed-origin")
    val allowedOrigins = HttpOriginMatcher(HttpOrigin(configuredOrigin))
    val baseSettings = CorsSettings(sys.settings.config)

    val strictCorsSettings = baseSettings
      .withAllowedOrigins(allowedOrigins)
      .withAllowedMethods(List(HttpMethods.POST, HttpMethods.OPTIONS))
      .withExposedHeaders(List("grpc-status", "grpc-message"))

    val serviceImpl = new TCodeServiceImpl(engineActorRef)
    val serviceHandler = TCodeServiceHandler(serviceImpl)
    val routes: Route = concat(
      // 1. Check for native gRPC content-type first. If matched, bypass CORS entirely.
      headerValueByType(org.apache.pekko.http.scaladsl.model.headers.`Content-Type`) { contentType =>
        if (contentType.value.contains("application/grpc")) {
          handle(serviceHandler)
        } else {
          reject // Fall through to CORS routes if it's a browser/REST request
        }
      },

      // 2. Browser, preflight OPTIONS, and standard web application routes go here
      cors(strictCorsSettings) {
        concat(
          handle(serviceHandler), // Safe for gRPC-Web browser traffic now
          path("health") { complete("OK") }
        )
      }
    )

    val binding = Http()
      .newServerAt(serverHost, serverPort)
      .bind(routes)

    binding.onComplete {
      case scala.util.Success(bound) =>
        system.log.info(
          s"TCodeServer online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
      case scala.util.Failure(e) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", e)
        system.terminate()
    }

    binding
  }
}
