package com.github.shaddysignal.mtproto

import akka.actor.{Actor, Props}

import scala.collection.mutable

class SessionStorage extends Actor {
  val storage: mutable.Map[BigInt, Session] = mutable.Map()

  override def receive: Receive = {
    case SessionStorage.Store(session) => storage += ((session.serverNonce, session))
    case SessionStorage.Delete(key) => storage.remove(key)
    case SessionStorage.Status => sender ! storage.toMap
  }
}

object SessionStorage {
  def props = Props(classOf[SessionStorage])

  sealed trait StorageMessage
  case class Store(session: Session) extends StorageMessage
  case class Delete(key: BigInt) extends StorageMessage
  case object Status extends StorageMessage
}
