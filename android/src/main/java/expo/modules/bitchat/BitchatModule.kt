package expo.modules.bitchat

import com.bitchat.android.crypto.EncryptionService
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryAck
import com.bitchat.android.model.ReadReceipt
import com.bitchat.android.model.DeliveryStatus
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

// FIX: Correctly implement the delegate interface
class BitchatModule : Module(), BluetoothMeshDelegate {
  private var meshService: BluetoothMeshService? = null
  private val encryptionService: EncryptionService by lazy {
    EncryptionService(appContext.reactContext!!)
  }
  // State provided by JavaScript
  private var nickname: String? = null
  private var favoritePeerIDs = mutableSetOf<String>()
  private var blockedPeerIDs = mutableSetOf<String>()
  private var channelKeys = mutableMapOf<String, SecretKeySpec>()

  override fun definition() = ModuleDefinition {
    Name("Bitchat")

    Events(
        "onMessageReceived",
        "onPeerConnected",
        "onPeerDisconnected",
        "onPeerListUpdated",
        "onDeliveryAck",
        "onReadReceipt",
        "onDeliveryStatusUpdate"
    )

    AsyncFunction("startServices") { promise: Promise ->
      try {
        meshService = BluetoothMeshService(appContext.reactContext!!)
        meshService?.delegate = this@BitchatModule // Correctly assign delegate
        meshService?.startServices()
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject(CodedException(e))
      }
    }

    AsyncFunction("stopServices") { promise: Promise ->
      try {
        meshService?.stopServices()
        meshService = null
        promise.resolve(true)
      } catch (e: Exception) {
        promise.reject(CodedException(e))
      }
    }

    AsyncFunction("setNickname") { nickname: String ->
      this@BitchatModule.nickname = nickname
    }

    AsyncFunction("toggleFavorite") { peerID: String, isFavorite: Boolean ->
      if (isFavorite) {
        this@BitchatModule.favoritePeerIDs.add(peerID)
      } else {
        this@BitchatModule.favoritePeerIDs.remove(peerID)
      }
    }
    
    AsyncFunction("blockUser") { peerID: String ->
      this@BitchatModule.blockedPeerIDs.add(peerID)
    }

    AsyncFunction("unblockUser") { peerID: String ->
      this@BitchatModule.blockedPeerIDs.remove(peerID)
    }

    AsyncFunction("sendMessage") { content: String, mentions: List<String>, channel: String? ->
      meshService?.sendMessage(content, mentions, channel)
    }

    AsyncFunction("sendPrivateMessage") { content: String, recipientPeerID: String, recipientNickname: String ->
      meshService?.sendPrivateMessage(content, recipientPeerID, recipientNickname)
    }

    AsyncFunction("sendEncryptedChannelMessage") { content: String, mentions: List<String>, channel: String ->
      channelKeys[channel]?.let { key ->
        meshService?.sendEncryptedChannelMessage(content, mentions, channel, key)
      }
    }
    
    AsyncFunction("setChannelPassword") { channel: String, password: String? ->
        if (password != null && password.isNotEmpty()) {
            val key = deriveChannelKey(password, channel)
            this@BitchatModule.channelKeys[channel] = key
        } else {
            this@BitchatModule.channelKeys.remove(channel)
        }
    }

    AsyncFunction("getConnectedPeers") {
      return@AsyncFunction meshService?.getPeerNicknames() ?: emptyMap<String, String>()
    }
    
    AsyncFunction("getPeerRSSI") {
        return@AsyncFunction meshService?.getPeerRSSI() ?: emptyMap<String, Int>()
    }

    AsyncFunction("getDebugStatus") {
      return@AsyncFunction meshService?.getDebugStatus() ?: "Service not initialized"
    }

    AsyncFunction("panicClearAllData") {
        meshService?.stopServices() // A simplified version of panic
    }
  }

  // MARK: - BluetoothMeshDelegate Implementation

  override fun didReceiveMessage(message: BitchatMessage) {
    if (message.senderPeerID != null && blockedPeerIDs.contains(message.senderPeerID)) {
        return
    }
    sendEvent("onMessageReceived", mapOf(
      "id" to message.id,
      "sender" to message.sender,
      "content" to message.content,
      "timestamp" to message.timestamp.time,
      "isPrivate" to message.isPrivate,
      "channel" to message.channel,
      "mentions" to message.mentions,
      "senderPeerID" to message.senderPeerID,
      "isEncrypted" to message.isEncrypted,
      "deliveryStatus" to serializeDeliveryStatus(message.deliveryStatus)
    ))
  }

  override fun didConnectToPeer(peerID: String) {
    sendEvent("onPeerConnected", mapOf("peerID" to peerID, "nickname" to (meshService?.getPeerNicknames()?.get(peerID) ?: peerID)))
  }

  override fun didDisconnectFromPeer(peerID: String) {
    sendEvent("onPeerDisconnected", mapOf("peerID" to peerID, "nickname" to (meshService?.getPeerNicknames()?.get(peerID) ?: peerID)))
  }

  override fun didUpdatePeerList(peers: List<String>) {
    sendEvent("onPeerListUpdated", mapOf("peers" to peers))
  }
  
  override fun didReceiveDeliveryAck(ack: DeliveryAck) {
    sendEvent("onDeliveryAck", mapOf(
        "originalMessageID" to ack.originalMessageID,
        "recipientNickname" to ack.recipientNickname,
        "timestamp" to ack.timestamp.time
    ))
  }

  override fun didReceiveReadReceipt(receipt: ReadReceipt) {
    sendEvent("onReadReceipt", mapOf(
        "originalMessageID" to receipt.originalMessageID,
        "readerNickname" to receipt.readerNickname,
        "timestamp" to receipt.timestamp.time
    ))
  }

  // --- Delegate methods that PROVIDE data to the service ---

  override fun getNickname(): String? {
    return this.nickname
  }

  override fun isFavorite(peerID: String): Boolean {
    return this.favoritePeerIDs.contains(peerID)
  }

  override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
    val key = this.channelKeys[channel] ?: return null
    // FIX: Add a call to a new decryption method in your service
    return try {
        encryptionService.decryptWithSymmetricKeyToString(encryptedContent, key)
    } catch (e: Exception) {
        null
    }
  }

  override fun registerPeerPublicKey(peerID: String, publicKeyData: ByteArray) {
    // This is where you could map a peerID to a persistent fingerprint
    // and check against a list of blocked/favorite fingerprints.
  }
  
  override fun didReceiveChannelLeave(channel: String, fromPeer: String) {}

  // MARK: - Helpers

  private fun deriveChannelKey(password: String, channelName: String): SecretKeySpec {
    val salt = channelName.toByteArray(Charsets.UTF_8)
    val combined = password.toByteArray(Charsets.UTF_8) + salt
    val md = MessageDigest.getInstance("SHA-256")
    val keyBytes = md.digest(combined)
    return SecretKeySpec(keyBytes, "AES")
  }
    
  private fun serializeDeliveryStatus(status: DeliveryStatus?): Map<String, Any>? {
    return when (status) {
        is DeliveryStatus.Sending -> mapOf("type" to "sending")
        is DeliveryStatus.Sent -> mapOf("type" to "sent")
        is DeliveryStatus.Delivered -> mapOf("type" to "delivered", "to" to status.to, "at" to status.at.time)
        is DeliveryStatus.Read -> mapOf("type" to "read", "by" to status.by, "at" to status.at.time)
        is DeliveryStatus.Failed -> mapOf("type" to "failed", "reason" to status.reason)
        else -> null
    }
  }
}