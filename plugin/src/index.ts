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

    // Add step counter feature
    if (!config.modResults.manifest['uses-feature']) {
      config.modResults.manifest['uses-feature'] = [];
    }
    config.modResults.manifest['uses-feature'].push({
      $: {
        'android:name': 'android.hardware.sensor.stepcounter',
        'android:required': 'true',
      },
    });

    // Add StepCounterService
    const serviceElement = {
      $: {
        'android:name': 'expo.modules.androidpedometer.service.StepCounterService',
        'android:enabled': 'true',
        'android:exported': 'false',
        'android:foregroundServiceType': 'health',
      },
    } as const;

    // Add StepCounterServiceLauncher receiver
    const receiverElement = {
      $: {
        'android:name': 'expo.modules.androidpedometer.service.StepCounterServiceLauncher',
        'android:enabled': 'true' as const,
        'android:exported': 'false' as const,
      },
      'intent-filter': [{
        action: [{
          $: {
            'android:name': 'android.intent.action.BOOT_COMPLETED',
          },
        }],
        category: [{
          $: {
            'android:name': 'android.intent.category.DEFAULT',
          },
        }],
      }],
    };

    mainApplication.service = mainApplication.service || [];
    mainApplication.service.push(serviceElement);

    mainApplication.receiver = mainApplication.receiver || [];
    mainApplication.receiver.push(receiverElement);

    return config;
  });
};

export default withPedometer; 