package com.github.shaddysignal.mtproto

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, Sink, Tcp}
import akka.stream.{ActorMaterializer, FlowShape}
import akka.util.ByteString
import scodec.Attempt.{Failure, Successful}
import scodec.bits._
import scodec.codecs._
import scodec.{Attempt, Codec, Err}

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.util.Random

object ServerMain extends App {

  implicit val system: ActorSystem = ActorSystem("mtproto-server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher

  val sessionStorageRef = system.actorOf(SessionStorage.props)

  val bigIntCodec: Codec[BigInt] = bits(128).xmap(bv => BigInt(bv.toByteArray), bi => BitVector(bi.toByteArray))

  def construct[T, R](id: Int, createElement: R => T, errorMessage: String): ((Int, R)) => Attempt[T] = {
    case (i: Int, rest: R) if i == id => Successful(createElement(rest))
    case _ => Failure(Err(errorMessage))
  }

  val firstRequestCodec: Codec[FirstRequest] = (int16 ~ bigIntCodec).exmap(
    construct(1, FirstRequest, "wrong id"),
    frq => Successful((1, frq.nonce)))
  val firstResponseCodec: Codec[FirstResponse] = (int16 ~ (bigIntCodec ~ bigIntCodec)).exmap(
    construct(2, FirstResponse, "wrong id"),
    frs => Successful((2, (frs.nonce, frs.serverNonce))))
  val secondRequestCodec: Codec[SecondRequest] = (int16 ~ (bigIntCodec ~ bigIntCodec)).exmap(
    construct(3, SecondRequest, "wrong id"),
    srq => Successful((3, (srq.nonce, srq.serverNonce))))

  val firstRequestDecode: ByteString => Attempt[FirstRequest] = bs => firstRequestCodec.decode(BitVector(bs)).map(_.value)
  val firstResponseEncode: FirstResponse => Attempt[ByteString] = firstResponseCodec.encode(_).map(bv => ByteString(bv.toByteArray))
  val secondRequestDecode: ByteString => Attempt[SecondRequest] = bs => secondRequestCodec.decode(BitVector(bs)).map(_.value)
  val processFailure: Failure => ByteString = f => ByteString(f.cause.message)
  def processSuccess[A]: Successful[A] => A = s => s.value

  val decoders = Stream(firstRequestDecode, secondRequestDecode)

  val decodeFlow = Flow.fromFunction((bs: ByteString) => decoders.map(_.apply(bs)).find(_.isSuccessful).getOrElse(Failure(Err("Not recognised message"))))
  val encodeFlow = Flow.fromFunction(firstResponseEncode)
  val failureFlow = Flow.fromFunction(processFailure)
  val successEncodeFlow = Flow.fromFunction(processSuccess[ByteString])
  val logicFirstRequestFlow: Flow[FirstRequest, FirstResponse, NotUsed] = Flow.fromFunction(fr => {
    val serverNonce = BigInt(Random.nextLong())
    sessionStorageRef ! SessionStorage.Store(Session(fr.nonce, serverNonce))

    FirstResponse(fr.nonce, serverNonce)
  })
  val logicSecondRequestFlow: Flow[SecondRequest, _, NotUsed] = Flow.fromFunction(sr => {
    sessionStorageRef ! SessionStorage.Delete(sr.serverNonce)
  })


  val flow: Flow[ByteString, ByteString, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val sourceBalance = b.add(Balance[ByteString](1))
    val attemptDecodeBroadcast = b.add(Broadcast[Attempt[ExchangeElement]](2))
    val messageBroadcast = b.add(Broadcast[ExchangeElement](2))
    val attemptEncodeBroadcast = b.add(Broadcast[Attempt[ByteString]](2))
    val failureMerge = b.add(Merge[Failure](2))
    val sinkMerge = b.add(Merge[ByteString](2))

    sourceBalance.out(0) ~> decodeFlow ~> attemptDecodeBroadcast.in
    attemptDecodeBroadcast.out(0).filter(_.isSuccessful).collect({ case Successful(value) => value }) ~> messageBroadcast.in

    messageBroadcast.out(0).filter(_.isInstanceOf[FirstRequest]).map(_.asInstanceOf[FirstRequest]) ~>
      logicFirstRequestFlow ~> encodeFlow ~> attemptEncodeBroadcast.in
    messageBroadcast.out(1).filter(_.isInstanceOf[SecondRequest]).map(_.asInstanceOf[SecondRequest]) ~>
      logicSecondRequestFlow ~> Sink.cancelled

    attemptDecodeBroadcast.out(1).filter(_.isFailure).collect({ case f@Failure(_) => f }) ~> failureMerge.in(0)

    attemptEncodeBroadcast.out(0).filter(_.isSuccessful).collect({ case Successful(value) => value}) ~> sinkMerge.in(0)
    attemptEncodeBroadcast.out(1).filter(_.isFailure).collect({ case f@Failure(_) => f }) ~> failureMerge.in(1)

    failureMerge.out ~> failureFlow ~> sinkMerge.in(1)

    FlowShape(sourceBalance.in, sinkMerge.out)
  })

  val bind = Tcp().bindAndHandle(flow, "localhost", 8050)
  StdIn.readLine
  bind.onComplete(_ => system.terminate())
}