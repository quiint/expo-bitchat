import { ConfigPlugin, withInfoPlist } from "@expo/config-plugins";

const withBitchatIOSInfoPlist: ConfigPlugin = (config) => {
  return withInfoPlist(config, (config) => {
    const newConfig = config.modResults;
    // Generic descriptions for Bitchat mesh networking
    newConfig.NSBluetoothAlwaysUsageDescription =
      "This app uses Bluetooth to create a secure mesh network for peer-to-peer messaging.";
    newConfig.NSBluetoothPeripheralUsageDescription =
      "This app uses Bluetooth to discover and connect with other users for mesh networking.";

    // IMPORTANT: Required for modern Swift native modules
    if (!newConfig.EXPO_RUNTIME_VERSION) {
      newConfig.expo = newConfig.expo || {};
      (newConfig.expo as any).RequiresConcurrency = true;
    }

    return config;
  });
};

export default withBitchatIOSInfoPlist;
