// FIX: Remove 'NativeModulesProxy' as it's not used directly.
// FIX: The type for a subscription is simply 'Subscription'.
import { EventEmitter } from 'expo-modules-core';
import BitchatModule from './BitchatModule';

// --- Data Models ---
// (This section is correct and remains unchanged)
export type DeliveryStatus =
  | { type: 'sending' }
  | { type: 'sent' }
  | { type: 'delivered'; to: string; at: number }
  | { type: 'read'; by: string; at: number }
  | { type: 'failed'; reason: string };

export interface BitchatMessage {
  id: string;
  sender: string;
  content: string;
  timestamp: number;
  isPrivate: boolean;
  channel?: string;
  mentions?: string[];
  senderPeerID?: string;
  isEncrypted?: boolean;
  deliveryStatus?: DeliveryStatus;
}

export interface PeerInfo {
  [peerID: string]: string;
}

export interface DeliveryAck {
  originalMessageID: string;
  recipientNickname: string;
  timestamp: number;
}

export interface ReadReceipt {
  originalMessageID: string;
  readerNickname: string;
  timestamp: number;
}

export interface Subscription {
    /**
     * A method to remove the subscription.
     */
    remove(): void;
  }

// --- FIX #1: Define the event map with LISTENER FUNCTION SIGNATURES ---
type BitchatEvents = {
  onMessageReceived: (payload: BitchatMessage) => void;
  onPeerConnected: (payload: { peerID: string; nickname: string }) => void;
  onPeerDisconnected: (payload: { peerID: string; nickname: string }) => void;
  onPeerListUpdated: (payload: { peers: string[] }) => void;
  onDeliveryAck: (payload: DeliveryAck) => void;
  onReadReceipt: (payload: ReadReceipt) => void;
  onDeliveryStatusUpdate: (payload: { messageID: string; status: DeliveryStatus }) => void;
};

// --- Main Class ---

class BitchatAPI {
  // This now correctly satisfies the 'EventsMap' constraint.
  private readonly eventEmitter = new EventEmitter<BitchatEvents>(BitchatModule);

  // --- Core Service Lifecycle & State ---

  async startServices(nickname: string): Promise<boolean> {
    await BitchatModule.setNickname(nickname);
    return await BitchatModule.startServices();
  }

  async stopServices(): Promise<boolean> {
    return await BitchatModule.stopServices();
  }

  // --- Message Sending & Channel Management ---

  async sendMessage(content: string, mentions: string[] = [], channel?: string): Promise<void> {
    await BitchatModule.sendMessage(content, mentions, channel);
  }

  async sendPrivateMessage(content: string, recipientPeerID: string, recipientNickname: string): Promise<void> {
    await BitchatModule.sendPrivateMessage(content, recipientPeerID, recipientNickname);
  }

  async setChannelPassword(channel: string, password?: string): Promise<void> {
    await BitchatModule.setChannelPassword(channel, password ?? "");
  }

  // --- Getters ---

  getConnectedPeers(): Promise<PeerInfo> {
    return BitchatModule.getConnectedPeers();
  }

  // --- Event Listener Registration ---
  // FIX #2: The return types now correctly point to our manually defined Subscription interface.

  addMessageListener(listener: (event: BitchatMessage) => void): Subscription {
    return this.eventEmitter.addListener('onMessageReceived', listener);
  }

  addPeerConnectedListener(listener: (event: { peerID: string; nickname: string }) => void): Subscription {
    return this.eventEmitter.addListener('onPeerConnected', listener);
  }

  addPeerDisconnectedListener(listener: (event: { peerID: string; nickname: string }) => void): Subscription {
    return this.eventEmitter.addListener('onPeerDisconnected', listener);
  }

  addPeerListUpdatedListener(listener: (event: { peers: string[] }) => void): Subscription {
    return this.eventEmitter.addListener('onPeerListUpdated', listener);
  }

  addDeliveryAckListener(listener: (event: DeliveryAck) => void): Subscription {
    return this.eventEmitter.addListener('onDeliveryAck', listener);
  }

  addReadReceiptListener(listener: (event: ReadReceipt) => void): Subscription {
    return this.eventEmitter.addListener('onReadReceipt', listener);
  }

  addDeliveryStatusUpdateListener(listener: (event: { messageID: string; status: DeliveryStatus }) => void): Subscription {
    return this.eventEmitter.addListener('onDeliveryStatusUpdate', listener);
  }
}

// Export a singleton instance for easy use in your app
export default new BitchatAPI();