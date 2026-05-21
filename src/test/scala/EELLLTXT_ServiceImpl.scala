package io.github.nicheapplab.tcodeserver

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pekko.actor.typed.ActorSystem
import pekko.actor.{ ActorSystem => ClassicSystem }
import pekko.actor.typed.scaladsl.adapter._
import pekko.actor.typed.scaladsl.Behaviors
import pekko.grpc.GrpcClientSettings

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import com.typesafe.config.ConfigFactory

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import io.github.nicheapplab.tcodeengine._

class EELLLTXT_ServiceImpl
    extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  def createEngine: SQLiteInteractiveEngine = {
    val tcode_tbl_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/tcode_tbl.db"
    val mazegaki_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/mazegaki.db"
    val bushu_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/bushu.db"
    val jdbc_prefix = "jdbc:sqlite"

    new SQLiteInteractiveEngine(jdbc_prefix, tcode_tbl_path, mazegaki_path, bushu_path) with QwertyLayout
  }

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  val testKit = ActorTestKit()
  val engine = createEngine

  val system = testKit.system
  val engineActor = system.systemActorOf(TCodeEngineActor(engine), "EngineActor")
  val serviceImpl = new TCodeServiceImpl(engineActor)(system)

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  for (lesson <- EELLLTXT.lessons) {
    "TCodeEngineService" should {
      lesson.name in {
        lesson.strokes(0).foreach(c => serviceImpl.put(PutRequest(c.toString)))
        val res = serviceImpl.commit(CommitRequest())

        res.futureValue should ===(CommitResponse(lesson.expected(0)))
      }
    }
  }
}
