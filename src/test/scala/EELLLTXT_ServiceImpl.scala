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

class EELLLTXT_ServiceImpl
    extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  implicit val patience: PatienceConfig = PatienceConfig(scaled(5.seconds), scaled(100.millis))

  val classicSystem: ClassicSystem = ClassicSystem("LocalTestSytem")
  val serverSystem: ActorSystem[_] = classicSystem.toTyped

  val serviceImpl = new TCodeServiceImpl(serverSystem)


  override def afterAll(): Unit = {
    classicSystem.terminate()
    super.afterAll()
  }

  for(lesson <- EELLLTXT.lessons){
    "TCodeEngineService" should {
      lesson.name in {
        lesson.strokes(0).foreach(c => serviceImpl.put(PutRequest(c.toString)))
        val res = serviceImpl.commit(CommitRequest())

        res.futureValue should ===(CommitResponse(lesson.expected(0)))
      }
    }
  }
}
