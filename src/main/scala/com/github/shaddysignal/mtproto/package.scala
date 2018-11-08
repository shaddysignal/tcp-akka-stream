package com.github.shaddysignal

package object mtproto {
  trait ExchangeElement {}
  case class FirstRequest(nonce: BigInt) extends ExchangeElement
  case class FirstResponse(nonce: BigInt, serverNonce: BigInt) extends ExchangeElement
  case class SecondRequest(nonce: BigInt, serverNonce: BigInt) extends ExchangeElement

  case class Session(clientNonce: BigInt, serverNonce: BigInt)

  trait Message {
    def authKeyId: Long
    def messageId: Long
  }
  trait UnencryptedMessage extends Message {
    def messageDataLength: Int
  }
  trait EncryptedMessage extends Message {
    def encryptedData: EncryptedData
  }

  case class EncryptedData(salt: Long, sessionId: Long, messageId: Long, seqNo: Int, messageDataLength: Int, messageData: Array[Byte])

  case class ReqPQ(authKeyId: Long = 0, messageId: Long, messageDataLength: Int = 16, nonce: BigInt) extends UnencryptedMessage
  case class ResPQ(authKeyId: Long = 0, messageId: Long, messageDataLength: Int,
                   nonce: BigInt, server_nonce: BigInt, pq: String, serverPublicKeyFingerprints: Vector[Long]) extends UnencryptedMessage

  case class ReqDHParams(authKeyId: Long = 0, messageId: Long, messageDataLength: Int,
                         nonce: BigInt, serverNonce: BigInt, p: String, q: String, publicKeyFingerprint: Long,
                         encryptedData: Array[Byte]) extends UnencryptedMessage
}
