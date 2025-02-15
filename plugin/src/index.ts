import {
  ConfigPlugin,
  withAndroidManifest,
  AndroidConfig,
} from 'expo/config-plugins';

const withPedometer: ConfigPlugin<{} | void> = (config) => {
  // Add Android configuration
  return withAndroidManifest(config, async config => {
    const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);

    // Add required permissions
    [
      'android.permission.ACTIVITY_RECOGNITION',
      'android.permission.FOREGROUND_SERVICE',
      'android.permission.POST_NOTIFICATIONS', 
      'android.permission.RECEIVE_BOOT_COMPLETED',
      'android.permission.FOREGROUND_SERVICE_HEALTH'
    ].forEach(permission => {
      AndroidConfig.Permissions.addPermission(config.modResults, permission);
    });

    // Add PedometerService
    const serviceElement = {
      $: {
        'android:name': 'expo.modules.androidpedometer.PedometerService',
        'android:enabled': 'true',
        'android:exported': 'false',
        'android:foregroundServiceType': 'health',
      },
    } as const;

    mainApplication.service = mainApplication.service || [];
    mainApplication.service.push(serviceElement);

    return config;
  });
};

export default withPedometer; 