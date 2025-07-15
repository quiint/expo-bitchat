# expo-bitchat

> [!WARNING]
> This software has not received external security review and may contain vulnerabilities and may not necessarily meet its stated security goals. Do not use it for sensitive use cases, and do not rely on its security until it has been reviewed. Work in progress.

**An Expo native module for secure, decentralized, peer-to-peer messaging over Bluetooth mesh networks.**

This is the **Expo/React Native module** that provides the backend infrastructure for Bitchat mobile apps, enabling cross-platform encrypted messaging without internet, servers, or phone numbers.

## Features

- **✅ Cross-Platform Support**: iOS and Android native implementations
- **✅ Bluetooth Mesh Networking**: Automatic peer discovery and multi-hop message relay
- **✅ End-to-End Encryption**: X25519 key exchange + AES-256-GCM for private messages
- **✅ Channel-Based Messaging**: Topic-based group chats with optional password protection
- **✅ Store & Forward**: Messages cached for offline peers and delivered when they reconnect
- **✅ Privacy First**: No accounts, no phone numbers, no persistent identifiers
- **✅ TypeScript Support**: Full type definitions included
- **✅ Event-Driven API**: React to messages, peer connections, and delivery status
- **✅ Message Compression**: LZ4 compression for bandwidth efficiency
- **✅ Battery Optimization**: Adaptive scanning and power management

## Installation

```sh
npx expo install expo-bitchat
```

### Platform Requirements

- **iOS**: iOS 13.0+ with Bluetooth LE support
- **Android**: API level 26 (Android 8.0+) with Bluetooth LE support
- **Expo**: SDK 50+ (uses new architecture modules)

### Permissions

The module requires the following permissions (configured in your app.json/app.config.js):

#### iOS (Info.plist)

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth for secure peer-to-peer messaging</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth for mesh networking</string>
```

#### Android (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

## Usage

### Basic Setup

```typescript
import BitchatAPI, { BitchatMessage } from "expo-bitchat";

// Start the mesh service with your nickname
await BitchatAPI.startServices("YourNickname");

// Listen for incoming messages
const messageSubscription = BitchatAPI.addMessageListener(
  (message: BitchatMessage) => {
    console.log("Received message:", message.content);
    console.log("From:", message.sender);
    console.log("Channel:", message.channel);
  }
);

// Send a public message
await BitchatAPI.sendMessage("Hello mesh network!", [], "#general");

// Send a private message
const peers = await BitchatAPI.getConnectedPeers();
const peerID = Object.keys(peers)[0];
await BitchatAPI.sendPrivateMessage("Secret message", peerID, peers[peerID]);

// Clean up
messageSubscription.remove();
await BitchatAPI.stopServices();
```

### Event Listeners

```typescript
// Listen for peer connections
const peerConnectedSub = BitchatAPI.addPeerConnectedListener(
  ({ peerID, nickname }) => {
    console.log(`${nickname} connected`);
  }
);

// Listen for peer disconnections
const peerDisconnectedSub = BitchatAPI.addPeerDisconnectedListener(
  ({ peerID, nickname }) => {
    console.log(`${nickname} disconnected`);
  }
);

// Listen for delivery acknowledgments
const deliveryAckSub = BitchatAPI.addDeliveryAckListener((ack) => {
  console.log(`Message delivered to ${ack.recipientNickname}`);
});

// Listen for read receipts
const readReceiptSub = BitchatAPI.addReadReceiptListener((receipt) => {
  console.log(`Message read by ${receipt.readerNickname}`);
});
```

### Channel Management

```typescript
// Set a password for a channel (owner only)
await BitchatAPI.setChannelPassword("#private-channel", "secretpassword");

// Remove password (owner only)
await BitchatAPI.setChannelPassword("#private-channel");
```

### Message Types

```typescript
interface BitchatMessage {
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

type DeliveryStatus =
  | { type: "sending" }
  | { type: "sent" }
  | { type: "delivered"; to: string; at: number }
  | { type: "read"; by: string; at: number }
  | { type: "failed"; reason: string };
```

## API Reference

### Core Methods

#### `startServices(nickname: string): Promise<boolean>`

Initializes the Bluetooth mesh service with the given nickname.

#### `stopServices(): Promise<boolean>`

Stops the mesh service and disconnects from all peers.

#### `sendMessage(content: string, mentions?: string[], channel?: string): Promise<void>`

Sends a public message to a channel with optional @mentions.

#### `sendPrivateMessage(content: string, recipientPeerID: string, recipientNickname: string): Promise<void>`

Sends an encrypted private message to a specific peer.

#### `setChannelPassword(channel: string, password?: string): Promise<void>`

Sets or removes a password for a channel (channel owner only).

#### `getConnectedPeers(): Promise<PeerInfo>`

Returns a map of connected peer IDs to their nicknames.

### Event Listeners

All event listeners return a `Subscription` object with a `remove()` method.

- `addMessageListener(listener)` - New message received
- `addPeerConnectedListener(listener)` - Peer joined the mesh
- `addPeerDisconnectedListener(listener)` - Peer left the mesh
- `addPeerListUpdatedListener(listener)` - Peer list changed
- `addDeliveryAckListener(listener)` - Message delivery confirmed
- `addReadReceiptListener(listener)` - Message read receipt
- `addDeliveryStatusUpdateListener(listener)` - Message status changed

## Security & Privacy

### Encryption

- **Private Messages**: X25519 key exchange + AES-256-GCM encryption
- **Channel Messages**: Argon2id password derivation + AES-256-GCM
- **Digital Signatures**: Ed25519 for message authenticity
- **Forward Secrecy**: New key pairs generated each session

### Privacy Features

- **No Registration**: No accounts, emails, or phone numbers required
- **Ephemeral by Default**: Messages exist only in device memory
- **Cover Traffic**: Random delays and dummy messages prevent traffic analysis
- **Local-First**: Works completely offline, no servers involved

## Technical Architecture

### Native Modules

The module consists of native implementations for both platforms:

#### iOS Implementation

- **BitchatModule.swift**: Main Expo module interface
- **BluetoothMeshService.swift**: Core BLE mesh networking
- **EncryptionService.swift**: Cryptographic operations
- **BinaryProtocol.swift**: Protocol encoding/decoding

#### Android Implementation

- **BitchatModule.kt**: Main Expo module interface
- **BluetoothMeshService.kt**: Core BLE mesh networking
- **EncryptionService.kt**: Cryptographic operations
- **BinaryProtocol.kt**: Protocol encoding/decoding

### Protocol Compatibility

The module maintains 100% binary protocol compatibility between platforms:

- **Header Format**: Identical packet structure
- **Encryption**: Same cryptographic algorithms
- **UUIDs**: Shared Bluetooth service identifiers
- **Message Routing**: Compatible TTL and relay behavior

### Performance Optimizations

- **Message Compression**: LZ4 compression for messages >100 bytes
- **Battery Management**: Adaptive scanning based on battery level
- **Connection Pooling**: Efficient peer connection management
- **Bloom Filters**: Fast duplicate message detection

## Development

### Building the Module

```bash
# Install dependencies
npm install

# Build TypeScript
npm run build

# Run linting
npm run lint

# Run tests
npm run test

# Open example app
npm run open:ios     # iOS
npm run open:android # Android
```

### Example App

The module includes an example app demonstrating basic usage:

```bash
cd example
npm install
npx expo run:ios     # iOS
npx expo run:android # Android
```

## Troubleshooting

### Common Issues

**Bluetooth permissions denied**

- Ensure all required permissions are added to app.json
- Request permissions at runtime before calling `startServices()`

**Messages not reaching distant peers**

- Check that intermediate devices are running the mesh service
- Verify Bluetooth is enabled and devices are in range
- Messages have a maximum TTL of 7 hops

**Connection issues on Android**

- Enable location services (required for BLE scanning)
- Disable battery optimization for your app
- Ensure target API level is compatible

## Contributing

Contributions are welcome! Key areas for enhancement:

1. **Performance**: Battery optimization and connection reliability
2. **Security**: Enhanced cryptographic features
3. **Testing**: Unit and integration test coverage
4. **Documentation**: API documentation and usage examples
5. **Platform Support**: Additional Expo/React Native features

### Development Setup

1. Clone the repository
2. Install dependencies: `npm install`
3. Build the module: `npm run build`
4. Test with example app: `cd example && npx expo run:ios`

## License

MIT

---

Made with [create-expo-native-module](https://github.com/expo/expo/tree/main/packages/create-expo-native-module)
