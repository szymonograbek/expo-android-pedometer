# expo-android-pedometer

A native Android pedometer module for Expo/React Native applications that provides step counting functionality with background support.

## Features

- 🚶‍♂️ Real-time step counting
- 📱 Background step tracking with persistent notification
- 📊 Historical step data access
- 🔒 Proper permission handling
- ⚡ Native implementation using Android's built-in step counter sensor

## Installation

```bash
npx expo install expo-android-pedometer
```

## API

### Methods

#### `initialize()`

Initialize the pedometer module and prepare it for use.

```typescript
const isInitialized = await AndroidPedometer.initialize();
```

Returns `Promise<boolean>` - `true` if initialization was successful. Throws an error if the device doesn't have a step counter sensor or initialization fails.

#### `getStepsCountAsync(date?: string)`

Get the step count for a specific date or today.

```typescript
// Get today's steps
const todaySteps = await AndroidPedometer.getStepsCountAsync();

// Get steps for a specific date
const specificDateSteps = await AndroidPedometer.getStepsCountAsync('2024-03-15');
```

Returns `Promise<number>` - the number of steps for the specified date. Throws an error if pedometer is not initialized or fails to get step count.

#### `getStepsCountInRangeAsync(startTimestamp: string, endTimestamp: string)`

Get the step counts for a specific time range.

```typescript
const startTime = '2024-03-15T00:00:00Z';
const endTime = '2024-03-15T23:59:59Z';
const stepCounts = await AndroidPedometer.getStepsCountInRangeAsync(startTime, endTime);
```

Returns `Promise<Record<string, number>>` - a map of ISO timestamps to step counts for each minute in the range.

#### `getActivityPermissionStatus()`

Get the current status of the activity recognition permission.

```typescript
const permissionStatus = await AndroidPedometer.getActivityPermissionStatus();
```

Returns `Promise<PermissionResponse>` with the following shape:
```typescript
type PermissionResponse = {
  status: 'granted' | 'denied' | 'undetermined';  // Current status of the permission
  granted: boolean;                               // Convenience boolean for granted status
  expires: 'never' | string;                      // When the permission expires
  canAskAgain: boolean;                          // Whether the user can be asked again
};
```

#### `getNotificationPermissionStatus()`

Get the current status of the notification permission.

```typescript
const permissionStatus = await AndroidPedometer.getNotificationPermissionStatus();
```

Returns `Promise<PermissionResponse>` with the same shape as `getActivityPermissionStatus()`.

#### `requestPermissions()`

Request necessary permissions for step counting (ACTIVITY_RECOGNITION permission on Android Q and above).

```typescript
const permissionResponse = await AndroidPedometer.requestPermissions();
```

Returns `Promise<PermissionResponse>` with the following shape:
```typescript
type PermissionResponse = {
  status: 'granted' | 'denied';
  granted: boolean;
  expires: 'never' | string;
};
```

#### `requestNotificationPermissions()`

Request notification permissions required for background service (POST_NOTIFICATIONS permission on Android 13 and above).

```typescript
const notificationPermissionResponse = await AndroidPedometer.requestNotificationPermissions();
```

Returns `Promise<PermissionResponse>` with the same shape as `requestPermissions()`.

#### `setupBackgroundUpdates(config?: NotificationConfig)`

Setup background step counting that continues even when the app is in the background or terminated.

```typescript
const config = {
  title: "Step Counter",
  contentTemplate: "You've taken %d steps today",
  style: "default", // or "bigText"
  iconResourceName: "ic_notification"
};

await AndroidPedometer.setupBackgroundUpdates(config);
```

The `NotificationConfig` type has the following properties:
```typescript
type NotificationConfig = {
  title?: string;              // Title of the notification
  contentTemplate?: string;    // Content template (%d will be replaced with steps)
  style?: 'default' | 'bigText'; // Style of the notification
  iconResourceName?: string;   // Resource name of the icon to use
};
```

Returns `Promise<boolean>` - `true` if background updates were successfully setup.

#### `subscribeToChange(listener: (event: PedometerUpdateEventPayload) => void)`

Subscribe to real-time step count updates.

```typescript
const unsubscribe = AndroidPedometer.subscribeToChange((event) => {
  console.log('Current steps:', event.steps);
  console.log('Timestamp:', event.timestamp);
});

// Later, when you want to stop listening:
unsubscribe();
```

The `PedometerUpdateEventPayload` type has the following shape:
```typescript
type PedometerUpdateEventPayload = {
  steps: number;
  timestamp: number;
};
```

## Example Usage

```typescript
import * as AndroidPedometer from 'expo-android-pedometer';

async function setupPedometer() {
  try {
    // Initialize the pedometer
    const isInitialized = await AndroidPedometer.initialize();
    
    // Check current permission status
    const hasActivityPermission = AndroidPedometer.getActivityPermissionStatus();
    const hasNotificationPermission = AndroidPedometer.getNotificationPermissionStatus();
    
    if (!hasActivityPermission || !hasNotificationPermission) {
      // Request necessary permissions
      const permissionResponse = await AndroidPedometer.requestPermissions();
      const notificationPermissionResponse = await AndroidPedometer.requestNotificationPermissions();

      if (!permissionResponse.granted || !notificationPermissionResponse.granted) {
        console.log('Required permissions were not granted');
        return;
      }
    }

    // Setup background updates with custom notification
    await AndroidPedometer.setupBackgroundUpdates({
      title: "Step Counter",
      contentTemplate: "You've taken %d steps today",
      style: "bigText",
      iconResourceName: "ic_notification"
    });

    // Subscribe to real-time step updates
    const unsubscribe = AndroidPedometer.subscribeToChange((event) => {
      console.log(`Steps: ${event.steps} at timestamp: ${event.timestamp}`);
    });

    // Get today's steps
    const todaySteps = await AndroidPedometer.getStepsCountAsync();
    console.log(`Today's steps: ${todaySteps}`);

    // Get steps for a specific date range
    const startTime = '2024-03-15T00:00:00Z';
    const endTime = '2024-03-15T23:59:59Z';
    const stepCounts = await AndroidPedometer.getStepsCountInRangeAsync(startTime, endTime);
    console.log('Step counts by minute:', stepCounts);

  } catch (error) {
    console.error('Error setting up pedometer:', error);
  }
}
```

## Notes

- The module only works on Android devices with a built-in step counter sensor
- Background tracking requires a persistent notification 
- Historical step data is stored locally on the device

## TODO
- [ ] Optional sync to Health Connect
- [ ] Ability to disable notification and background sync
- [ ] More options to customize the notification

## License

MIT
