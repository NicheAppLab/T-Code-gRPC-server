package io.github.nicheapplab.tcodeserver

import org.apache.pekko.actor.{Actor, Props}

import io.github.nicheapplab.tcodeengine._

object TCodeEngineActor{
  case class Put(char: String)
  case object GetStatus
  case class Status(
    outputBuffer: String,
    buffer: String,
    candidates: IndexedSeq[String],
    lastCharAsKey: String
  )
  case object Left
  case object Right
  case object Convert
  case class Select(n: Int)
  case object Commit
  case object Backspace
  case object GetOutput
  case class Output(str: String)
  case object Reset

  def props(
    jdbc_prefix: String,
    tcode_tbl_path: String,
    mazegaki_path: String,
    bushu_path: String
  ) = Props(
    new TCodeEngineActor(
      jdbc_prefix: String,
      tcode_tbl_path: String,
      mazegaki_path: String,
      bushu_path: String
    )
  )
}
class TCodeEngineActor(
      jdbc_prefix: String,
      tcode_tbl_path: String,
      mazegaki_path: String,
      bushu_path: String
) extends Actor {
  import TCodeEngineActor._

  val engine = new SQLiteInteractiveEngine(
    jdbc_prefix,
    tcode_tbl_path,
    mazegaki_path,
    bushu_path
  ) with QwertyLayout

  def receive = {
    case Put(c) =>
      engine.put(c.head)
      sender() ! getStatus
    case Left =>
      engine.inflexLeft()
      sender() ! getStatus
    case Right =>
      engine.inflexRight()
      sender() ! getStatus
    case Reset =>
      engine.reset()
      sender() ! getStatus
    case Convert =>
      engine.convert()
      sender() ! getStatus
    case Select(n) =>
      engine.selectCandidate(n)
      sender() ! getStatus
    case Commit =>
      val str = engine.commit()
      sender() ! Output(str)
    case Backspace =>
      engine.backspace()
      sender() ! getStatus
    case GetStatus =>
      sender() ! getStatus
  }

  def getStatus = Status(
    engine.outputBuffer.mkString,
    engine.buffer.mkString,
    engine.candidates.to(IndexedSeq),
    engine.lastCharAsKey.toString
  )

}
