import ExpoModulesCore
import CryptoKit

public class BitchatModule: Module, BitchatDelegate {
  private var meshService: BluetoothMeshService?
  
  // State provided by JavaScript and needed by the delegate
  private var nickname: String?
  private var favoritePeerIDs = Set<String>()
  private var blockedPeerIDs = Set<String>()
  private var channelKeys = [String: SymmetricKey]()

  public func definition() -> ModuleDefinition {
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

    AsyncFunction("startServices") { (promise: Promise) in
      meshService = BluetoothMeshService()
      meshService?.delegate = self
      meshService?.startServices()
      promise.resolve(true)
    }

    AsyncFunction("stopServices") { (promise: Promise) in
      meshService?.cleanup()
      meshService = nil
      promise.resolve(true)
    }
    
    // --- State setters from JS ---
    
    AsyncFunction("setNickname") { (nickname: String) in
      self.nickname = nickname
    }

    AsyncFunction("toggleFavorite") { (peerID: String, isFavorite: Bool) in
      if isFavorite {
        self.favoritePeerIDs.insert(peerID)
      } else {
        self.favoritePeerIDs.remove(peerID)
      }
    }
    
    AsyncFunction("blockUser") { (peerID: String) in
        self.blockedPeerIDs.insert(peerID)
    }

    AsyncFunction("unblockUser") { (peerID: String) in
        self.blockedPeerIDs.remove(peerID)
    }

    // --- Message Sending ---

    AsyncFunction("sendMessage") { (content: String, mentions: [String], channel: String?) in
      meshService?.sendMessage(content, mentions: mentions, channel: channel)
    }

    AsyncFunction("sendPrivateMessage") { (content: String, recipientPeerID: String, recipientNickname: String) in
      meshService?.sendPrivateMessage(content, to: recipientPeerID, recipientNickname: recipientNickname)
    }
    
    AsyncFunction("sendEncryptedChannelMessage") { (content: String, mentions: [String], channel: String) in
        guard let key = self.channelKeys[channel] else {
            // Or throw an error to JS
            return
        }
        meshService?.sendEncryptedChannelMessage(content, mentions: mentions, channel: channel, channelKey: key)
    }

    // --- Channel Management ---
    
    AsyncFunction("joinChannel") { (channel: String) -> Bool in
        // Simplified for the bridge; your ViewModel has more complex logic for this
        // In a real app, you might move that logic here or call it from here.
        return true
    }
    
    AsyncFunction("leaveChannel") { (channel: String) in
        meshService?.sendChannelLeaveNotification(channel)
    }
    
    AsyncFunction("setChannelPassword") { (channel: String, password: String?) in
        if let pass = password, !pass.isEmpty {
            let key = deriveChannelKey(from: pass, channelName: channel)
            self.channelKeys[channel] = key
            meshService?.announcePasswordProtectedChannel(channel, isProtected: true, creatorID: meshService?.myPeerID, keyCommitment: computeKeyCommitment(for: key))
        } else {
            self.channelKeys.removeValue(forKey: channel)
            meshService?.announcePasswordProtectedChannel(channel, isProtected: false, creatorID: meshService?.myPeerID, keyCommitment: nil)
        }
    }

    // --- Getters ---
    
    AsyncFunction("getConnectedPeers") {
      return meshService?.getPeerNicknames() ?? [:]
    }
    
    AsyncFunction("getPeerRSSI") {
        return meshService?.getPeerRSSI().mapValues { $0.intValue } ?? [:]
    }
    
    AsyncFunction("getDebugStatus") {
        return "N/A on iOS"
    }

    // --- Safety ---
    
    AsyncFunction("panicClearAllData") {
        meshService?.emergencyDisconnectAll()
        // Here you would also clear Keychain, UserDefaults, etc.
    }
  }

  // MARK: - BitchatDelegate Implementation

  public func didReceiveMessage(_ message: BitchatMessage) {
    // Block check
    if let peerID = message.senderPeerID, self.blockedPeerIDs.contains(peerID) {
        return
    }

    sendEvent("onMessageReceived", [
      "id": message.id,
      "sender": message.sender,
      "content": message.content,
      "timestamp": message.timestamp.timeIntervalSince1970 * 1000,
      "isPrivate": message.isPrivate,
      "channel": message.channel as Any,
      "mentions": message.mentions as Any,
      "senderPeerID": message.senderPeerID as Any,
      "isEncrypted": message.isEncrypted,
      "deliveryStatus": serializeDeliveryStatus(message.deliveryStatus)
    ])
  }

  public func didConnectToPeer(_ peerID: String) {
    sendEvent("onPeerConnected", ["peerID": peerID, "nickname": meshService?.getPeerNicknames()[peerID] ?? peerID])
  }

  public func didDisconnectFromPeer(_ peerID: String) {
    sendEvent("onPeerDisconnected", ["peerID": peerID, "nickname": meshService?.getPeerNicknames()[peerID] ?? peerID])
  }

  public func didUpdatePeerList(_ peers: [String]) {
    sendEvent("onPeerListUpdated", ["peers": peers])
  }

  public func didReceiveDeliveryAck(_ ack: DeliveryAck) {
    sendEvent("onDeliveryAck", [
        "originalMessageID": ack.originalMessageID,
        "recipientNickname": ack.recipientNickname,
        "timestamp": ack.timestamp.timeIntervalSince1970 * 1000
    ])
  }

  public func didReceiveReadReceipt(_ receipt: ReadReceipt) {
    sendEvent("onReadReceipt", [
        "originalMessageID": receipt.originalMessageID,
        "readerNickname": receipt.readerNickname,
        "timestamp": receipt.timestamp.timeIntervalSince1970 * 1000
    ])
  }
  
  public func didUpdateMessageDeliveryStatus(_ messageID: String, status: DeliveryStatus) {
    sendEvent("onDeliveryStatusUpdate", [
        "messageID": messageID,
        "status": serializeDeliveryStatus(status)
    ])
  }

  // --- Delegate methods that PROVIDE data to the service ---

  public func getNickname() -> String? {
    return self.nickname
  }

  public func isFavorite(fingerprint: String) -> Bool {
    // Note: The logic to map peerID to fingerprint would need to be handled
    // For simplicity, we check against our stored peerIDs.
    return self.favoritePeerIDs.contains(fingerprint)
  }
    
  public func decryptChannelMessage(_ encryptedContent: Data, channel: String) -> String? {
    guard let key = self.channelKeys[channel] else { return nil }
    do {
        let sealedBox = try AES.GCM.SealedBox(combined: encryptedContent)
        let decryptedData = try AES.GCM.open(sealedBox, using: key)
        return String(data: decryptedData, encoding: .utf8)
    } catch {
        return nil
    }
  }

  // Default empty implementations for unused delegate methods
  public func didReceiveChannelLeave(_ channel: String, from peerID: String) {}
  public func didReceivePasswordProtectedChannelAnnouncement(_ channel: String, isProtected: Bool, creatorID: String?, keyCommitment: String?) {}
  public func didReceiveChannelRetentionAnnouncement(_ channel: String, enabled: Bool, creatorID: String?) {}

  // MARK: - Helpers
    
  private func deriveChannelKey(from password: String, channelName: String) -> SymmetricKey {
    // This should use the same KDF as your ViewModel
    let salt = channelName.data(using: .utf8)!
    let keyData = HKDF<SHA256>.deriveKey(
        inputKeyMaterial: SymmetricKey(data: password.data(using: .utf8)!),
        salt: salt,
        outputByteCount: 32
    )
    return keyData
  }
    
  private func computeKeyCommitment(for key: SymmetricKey) -> String {
    let keyData = key.withUnsafeBytes { Data($0) }
    let hash = SHA256.hash(data: keyData)
    return hash.compactMap { String(format: "%02x", $0) }.joined()
  }
    
  private func serializeDeliveryStatus(_ status: DeliveryStatus?) -> [String: Any]? {
    guard let status = status else { return nil }
    switch status {
    case .sending:
        return ["type": "sending"]
    case .sent:
        return ["type": "sent"]
    case .delivered(let to, let at):
        return ["type": "delivered", "to": to, "at": at.timeIntervalSince1970 * 1000]
    case .read(let by, let at):
        return ["type": "read", "by": by, "at": at.timeIntervalSince1970 * 1000]
    case .failed(let reason):
        return ["type": "failed", "reason": reason]
    case .partiallyDelivered(let reached, let total):
        // Not used in this implementation, but good to have
        return ["type": "partiallyDelivered", "reached": reached, "total": total]
    }
  }
}