package io.github.nicheapplab.tcodeserver

import scala.concurrent.Future
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.{ActorRef => ClassicActorRef}
import pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class TCodeServiceImpl(system: ActorSystem[_]) extends TCodeService {
  private implicit val sys: ActorSystem[_] = system

  val tcode_tbl_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/tcode_tbl.db"
  val mazegaki_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/mazegaki.db"
  val bushu_path = System.getProperty("java.io.tempdir") ++ "/.t-code-engine/bushu.db"
  val jdbc_prefix = "jdbc:sqlite"

  val engineActor: ClassicActorRef = system.toClassic.actorOf(
    TCodeEngineActor.props(
      jdbc_prefix,
      tcode_tbl_path,
      mazegaki_path,
      bushu_path
    ),
    "engine"
  )

  def getStatus = {
    implicit val timeout: Timeout = 3.seconds

    (engineActor ? TCodeEngineActor.GetStatus)
    .mapTo[TCodeEngineActor.Status]
    .map(status => BufferStatusResponse(
           status.outputBuffer,
           status.buffer,
           status.candidates,
           status.lastCharAsKey
         ))
  }
  override def put(request: PutRequest): Future[BufferStatusResponse] = {
    implicit val timeout: Timeout = 3.seconds

    (engineActor ? TCodeEngineActor.Put(request.char))
    .mapTo[TCodeEngineActor.Status]
    .map(status => BufferStatusResponse(
           status.outputBuffer,
           status.buffer,
           status.candidates,
           status.lastCharAsKey
         )
    )
  }
  override def left(request: InflexLeftRequest): Future[BufferStatusResponse] = {
    engineActor ! TCodeEngineActor.Left
    getStatus
  }
  override def right(request: InflexRightRequest): Future[BufferStatusResponse] = {
    engineActor ! TCodeEngineActor.Right
    getStatus
  }
  override def reset(request: ResetRequest): Future[BufferStatusResponse] = {
    engineActor ! TCodeEngineActor.Reset
    getStatus
  }
  override def convert(request: ConvertRequest): Future[BufferStatusResponse] = {
    engineActor ! TCodeEngineActor.Convert
    getStatus
  }
  override def select(request: SelectCandidateRequest): Future[BufferStatusResponse] = {
    engineActor ! TCodeEngineActor.Select(request.n)
    getStatus
  }
  override def commit(request: CommitRequest): Future[CommitResponse] = {
    implicit val timeout: Timeout = 3.seconds

    (engineActor ? TCodeEngineActor.Commit)
    .mapTo[TCodeEngineActor.Output]
    .map(output => CommitResponse(output.str))
  }
  override def backspace(request: BackspaceRequest): Future[BufferStatusResponse] = {
    engineActor ! TCodeEngineActor.Backspace
    getStatus
  }

}
