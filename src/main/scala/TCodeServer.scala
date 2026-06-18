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

// Add these imports to your existing file
import com.github.pjfanning.pekkohttpjsoniterscala.JsoniterScalaSupport._
import Codecs.given
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._

// I'm not using a abstract trait for them, for better macro optimization

case class JsonPutRequest(char: String)
case class JsonSelectRequest(n: Int)
case class JsonEmptyRequest()

case class JsonBufferStatusResponse(
    outputBuffer: String,
    buffer: String,
    candidates: Seq[String],
    lastCharAsKey: String,
    commandSucceed: Boolean
)
case class JsonCommitResponse(output: String)

object Codecs {
  given putCodec: JsonValueCodec[JsonPutRequest] = JsonCodecMaker.make
  given selectCodec: JsonValueCodec[JsonSelectRequest] = JsonCodecMaker.make
  given bufferCodec: JsonValueCodec[JsonBufferStatusResponse] = JsonCodecMaker.make
  given commitCodec: JsonValueCodec[JsonCommitResponse] = JsonCodecMaker.make
  // also, other messages can be handled with JsonEmptyRequest
  given emptyCodec: JsonValueCodec[JsonEmptyRequest] = JsonCodecMaker.make
}

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
    def handleEmptyCommand(
        futureProtoResponse: Future[BufferStatusResponse]
    )(implicit ec: ExecutionContext): Route = {
      post {
        entity(as[JsonEmptyRequest]) { _ =>
          val futureResponse = futureProtoResponse.map { res =>
            JsonBufferStatusResponse(
              res.outputBuffer,
              res.buffer,
              res.candidates.toList,
              res.lastCharAsKey,
              res.commandSucceed
            )
          }
          complete(futureResponse)
        }
      }
    }

    val routes: Route = concat(
      path("health") {
        get {
          complete("OK")
        }
      },
      headerValueByType(org.apache.pekko.http.scaladsl.model.headers.`Content-Type`) { contentType =>
        if (contentType.value.contains("application/grpc")) {
          handle(serviceHandler)
        } else {
          reject // Fall through to CORS routes if it's a browser/REST request
        }
      },
      cors(strictCorsSettings) {
        pathPrefix("v1" / "tcode") {
          concat(
            path("put") {
              post {
                entity(as[JsonPutRequest]) { req =>
                  val futureResponse = serviceImpl.put(PutRequest(req.char)).map { res =>
                    JsonBufferStatusResponse(res.outputBuffer, res.buffer, res.candidates.toList, res.lastCharAsKey,
                      res.commandSucceed)
                  }
                  complete(futureResponse)
                }
              }
            },
            path("select") {
              post {
                entity(as[JsonSelectRequest]) { req =>
                  val futureResponse = serviceImpl.select(SelectCandidateRequest(req.n)).map { res =>
                    JsonBufferStatusResponse(res.outputBuffer, res.buffer, res.candidates.toList, res.lastCharAsKey,
                      res.commandSucceed)
                  }
                  complete(futureResponse)
                }
              }
            },

            // --- Endpoints that accept the unified JsonEmptyRequest ({}) ---
            path("left") { handleEmptyCommand(serviceImpl.left(InflexLeftRequest())) },
            path("right") { handleEmptyCommand(serviceImpl.right(InflexRightRequest())) },
            path("reset") { handleEmptyCommand(serviceImpl.reset(ResetRequest())) },
            path("convert") { handleEmptyCommand(serviceImpl.convert(ConvertRequest())) },
            path("backspace") { handleEmptyCommand(serviceImpl.backspace(BackspaceRequest())) },
            path("commit") {
              post {
                entity(as[JsonEmptyRequest]) { _ =>
                  // Commit maps uniquely to the single-string JsonCommitResponse definition
                  val futureResponse = serviceImpl.commit(CommitRequest()).map { res =>
                    JsonCommitResponse(output = res.output)
                  }
                  complete(futureResponse)
                }
              }
            }
          )
        }
      },
      handle(serviceHandler)
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
