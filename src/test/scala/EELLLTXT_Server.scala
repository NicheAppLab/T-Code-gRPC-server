package io.github.nicheapplab.tcodeserver

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.typed.ActorSystem
import pekko.actor.{ActorSystem => ClassicSystem}
import pekko.actor.typed.scaladsl.adapter._
import pekko.actor.typed.scaladsl.Behaviors
import pekko.grpc.GrpcClientSettings

import scala.concurrent.{ ExecutionContext, Future, Await}
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import com.typesafe.config.ConfigFactory

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EELLLTXT_Server
    extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {
  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))
  val conf = ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
    .withFallback(ConfigFactory.defaultApplication())

  val classicServerSystem = ClassicSystem("TCodeServerSystem", conf)
  val serverSystem: ActorSystem[_] = classicServerSystem.toTyped
  val bound = new TCodeServer(serverSystem).run()

  bound.futureValue

  serverSystem.log

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "TCodeClient")
  val settings = GrpcClientSettings
  .connectToServiceAt("localhost", 8080)(clientSystem)
  .withTls(false)

  val client = TCodeServiceClient(settings)

  override def afterAll(): Unit = {
    ActorTestKit.shutdown(clientSystem)
    classicServerSystem.terminate()
  }

  for(lesson <- EELLLTXT.lessons){
    "TCodeEngineService" should {
      lesson.name in {
        lesson.strokes(0).foreach{ c =>
          client.put(PutRequest(c.toString)).futureValue
        }

        val res = client.commit(CommitRequest())

        res.futureValue should ===(CommitResponse(lesson.expected(0)))
      }
    }
  }
}
