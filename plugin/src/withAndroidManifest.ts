import { ConfigPlugin, withAndroidManifest } from "@expo/config-plugins";

const withBitchatAndroidManifest: ConfigPlugin = (config) => {
  return withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;

    // These are the exact permissions from your original AndroidManifest.xml
    const permissions = [
      "android.permission.BLUETOOTH",
      "android.permission.BLUETOOTH_ADMIN",
      "android.permission.BLUETOOTH_ADVERTISE",
      "android.permission.BLUETOOTH_CONNECT",
      "android.permission.BLUETOOTH_SCAN",
      "android.permission.ACCESS_FINE_LOCATION", // FINE implies COARSE
      "android.permission.ACCESS_COARSE_LOCATION",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.VIBRATE",
    ];

    if (!manifest["uses-permission"]) {
      manifest["uses-permission"] = [];
    }
    const existingPermissions = manifest["uses-permission"].map(
      (p: any) => p.$["android:name"]
    );
    for (const permission of permissions) {
      if (!existingPermissions.includes(permission)) {
        manifest["uses-permission"].push({
          $: { "android:name": permission },
        });
      }
    }

    // Add hardware features
    const features = [
      "android.hardware.bluetooth_le",
      "android.hardware.bluetooth",
    ];
    if (!manifest["uses-feature"]) {
      manifest["uses-feature"] = [];
    }
    const existingFeatures = manifest["uses-feature"].map(
      (f: any) => f.$["android:name"]
    );
    for (const feature of features) {
      if (!existingFeatures.includes(feature)) {
        manifest["uses-feature"].push({
          $: { "android:name": feature, "android:required": "true" },
        });
      }
    }

    return config;
  });
};

export default withBitchatAndroidManifest;
