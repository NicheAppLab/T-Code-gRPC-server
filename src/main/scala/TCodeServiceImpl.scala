package io.github.nicheapplab.tcodeserver

import scala.concurrent.Future
import org.apache.pekko
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.ActorRef
import pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class TCodeServiceImpl(engineActor: ActorRef[TCodeEngineCommand])(implicit system: ActorSystem[_]) extends TCodeService {
  private implicit val timeout: Timeout = Timeout(5.seconds)

  private def toBufferStatusResponse(status: Status): BufferStatusResponse = {
    BufferStatusResponse(
      outputBuffer = status.outputBuffer,
      buffer = status.buffer,
      candidates = status.candidates,
      lastCharAsKey = status.lastCharAsKey,
      commandSucceed = status.commandSucceed
    )
  }

  override def put(request: PutRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Put(request.char, replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
  override def left(request: InflexLeftRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Left(replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
  override def right(request: InflexRightRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Right(replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
  override def reset(request: ResetRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Reset(replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
  override def convert(request: ConvertRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Convert(replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
  override def select(request: SelectCandidateRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Select(request.n, replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
  override def commit(request: CommitRequest): Future[CommitResponse] = {
    engineActor.ask(replyTo => Commit(replyTo))
      .mapTo[Output]
      .map(output => CommitResponse(output.str))
  }
  override def backspace(request: BackspaceRequest): Future[BufferStatusResponse] = {
    engineActor.ask(replyTo => Backspace(replyTo)).mapTo[Status].map(toBufferStatusResponse)
  }
}
