package com.bitchat.android.mesh

import android.util.Log
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import kotlinx.coroutines.*

/**
 * Processes incoming packets and routes them to appropriate handlers
 * Extracted from BluetoothMeshService for better separation of concerns
 */
class PacketProcessor(private val myPeerID: String) {
    
    companion object {
        private const val TAG = "PacketProcessor"
    }
    
    // Delegate for callbacks
    var delegate: PacketProcessorDelegate? = null
    
    // Coroutines
    private val processorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Process received packet - main entry point for all incoming packets
     */
    fun processPacket(packet: BitchatPacket, peerID: String) {
        processorScope.launch {
            handleReceivedPacket(packet, peerID)
        }
    }
    
    /**
     * Handle received packet - core protocol logic (exact same as iOS)
     */
    private suspend fun handleReceivedPacket(packet: BitchatPacket, peerID: String) {
        // Basic validation and security checks
        if (!delegate?.validatePacketSecurity(packet, peerID)!!) {
            Log.d(TAG, "Packet failed security validation from $peerID")
            return
        }
        
        // Update last seen timestamp
        delegate?.updatePeerLastSeen(peerID)
        
        Log.d(TAG, "Processing packet type ${packet.type} from $peerID")
        
        // Process based on message type (exact same logic as iOS)
        when (MessageType.fromValue(packet.type)) {
            MessageType.KEY_EXCHANGE -> handleKeyExchange(packet, peerID)
            MessageType.ANNOUNCE -> handleAnnounce(packet, peerID)
            MessageType.MESSAGE -> handleMessage(packet, peerID)
            MessageType.LEAVE -> handleLeave(packet, peerID)
            MessageType.FRAGMENT_START,
            MessageType.FRAGMENT_CONTINUE,
            MessageType.FRAGMENT_END -> handleFragment(packet, peerID)
            MessageType.DELIVERY_ACK -> handleDeliveryAck(packet, peerID)
            MessageType.READ_RECEIPT -> handleReadReceipt(packet, peerID)
            else -> {
                Log.w(TAG, "Unknown message type: ${packet.type}")
            }
        }
    }
    
    /**
     * Handle key exchange message
     */
    private suspend fun handleKeyExchange(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing key exchange from $peerID")
        
        val success = delegate?.handleKeyExchange(packet, peerID) ?: false
        
        if (success) {
            // Key exchange successful, send announce and cached messages
            delay(100)
            delegate?.sendAnnouncementToPeer(peerID)
            
            delay(500)
            delegate?.sendCachedMessages(peerID)
        }
    }
    
    /**
     * Handle announce message
     */
    private suspend fun handleAnnounce(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing announce from $peerID")
        delegate?.handleAnnounce(packet, peerID)
    }
    
    /**
     * Handle regular message
     */
    private suspend fun handleMessage(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing message from $peerID")
        delegate?.handleMessage(packet, peerID)
    }
    
    /**
     * Handle leave message
     */
    private suspend fun handleLeave(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing leave from $peerID")
        delegate?.handleLeave(packet, peerID)
    }
    
    /**
     * Handle message fragments
     */
    private suspend fun handleFragment(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing fragment from $peerID")
        
        val reassembledPacket = delegate?.handleFragment(packet)
        if (reassembledPacket != null) {
            Log.d(TAG, "Fragment reassembled, processing complete message")
            handleReceivedPacket(reassembledPacket, peerID)
        }
        
        // Relay fragment regardless of reassembly
        if (packet.ttl > 0u) {
            val relayPacket = packet.copy(ttl = (packet.ttl - 1u).toUByte())
            delegate?.relayPacket(relayPacket)
        }
    }
    
    /**
     * Handle delivery acknowledgment
     */
    private suspend fun handleDeliveryAck(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing delivery ACK from $peerID")
        delegate?.handleDeliveryAck(packet, peerID)
    }
    
    /**
     * Handle read receipt
     */
    private suspend fun handleReadReceipt(packet: BitchatPacket, peerID: String) {
        Log.d(TAG, "Processing read receipt from $peerID")
        delegate?.handleReadReceipt(packet, peerID)
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Packet Processor Debug Info ===")
            appendLine("Processor Scope Active: ${processorScope.isActive}")
            appendLine("My Peer ID: $myPeerID")
        }
    }
    
    /**
     * Shutdown the processor
     */
    fun shutdown() {
        processorScope.cancel()
    }
}

/**
 * Delegate interface for packet processor callbacks
 */
interface PacketProcessorDelegate {
    // Security validation
    fun validatePacketSecurity(packet: BitchatPacket, peerID: String): Boolean
    
    // Peer management
    fun updatePeerLastSeen(peerID: String)
    
    // Message type handlers
    fun handleKeyExchange(packet: BitchatPacket, peerID: String): Boolean
    fun handleAnnounce(packet: BitchatPacket, peerID: String)
    fun handleMessage(packet: BitchatPacket, peerID: String)
    fun handleLeave(packet: BitchatPacket, peerID: String)
    fun handleFragment(packet: BitchatPacket): BitchatPacket?
    fun handleDeliveryAck(packet: BitchatPacket, peerID: String)
    fun handleReadReceipt(packet: BitchatPacket, peerID: String)
    
    // Communication
    fun sendAnnouncementToPeer(peerID: String)
    fun sendCachedMessages(peerID: String)
    fun relayPacket(packet: BitchatPacket)
}
