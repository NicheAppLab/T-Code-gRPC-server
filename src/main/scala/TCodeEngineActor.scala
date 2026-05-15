package io.github.nicheapplab.tcodeserver

import org.apache.pekko
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.LoggerOps
import pekko.actor.typed.{ ActorRef, ActorSystem, Behavior }

import io.github.nicheapplab.tcodeengine._

sealed trait TCodeEngineCommand
final case class Put(char: String, replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand
case class Left(replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand
case class Right(replyTo: ActorRef[TCodeEngineResponse] ) extends TCodeEngineCommand
case class Convert(replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand
case class Select(n: Int, replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand
case class Commit(replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand
case class Backspace(replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand
case class Reset(replyTo: ActorRef[TCodeEngineResponse]) extends TCodeEngineCommand

sealed trait TCodeEngineResponse
final case class Status(
  outputBuffer: String,
  buffer: String,
  candidates: IndexedSeq[String],
  lastCharAsKey: String
) extends TCodeEngineResponse
final case class Output(str: String) extends TCodeEngineResponse


class TCodeEngineActor(engine: SQLiteInteractiveEngine){
  def getStatus = Status(
    engine.outputBuffer.mkString,
    engine.buffer.mkString,
    engine.candidates.to(IndexedSeq),
    engine.lastCharAsKey.toString
  )
  def createBehavior(): Behavior[TCodeEngineCommand] = Behaviors.setup { context =>
    Behaviors.receiveMessage { message =>
      message match{
        case Put(c, replyTo) =>
          engine.put(c.head)
          replyTo ! getStatus
        case Left(replyTo) =>
          engine.inflexLeft()
          replyTo ! getStatus
        case Right(replyTo) =>
          engine.inflexRight()
          replyTo ! getStatus
        case Convert(replyTo) =>
          engine.convert()
          replyTo ! getStatus
        case Select(n, replyTo) =>
          engine.selectCandidate(n)
          replyTo ! getStatus
        case Commit(replyTo) =>
          val output = engine.commit()
          replyTo ! Output(output)
        case Backspace(replyTo) =>
          engine.backspace()
          replyTo ! getStatus
        case Reset(replyTo) =>
          engine.reset()
          replyTo ! getStatus
      }
      Behaviors.same
    }
  }
}
object TCodeEngineActor{
  def apply(engine: SQLiteInteractiveEngine): Behavior[TCodeEngineCommand] = {
    new TCodeEngineActor(engine).createBehavior()
  }
}
