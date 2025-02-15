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

#### `getStepsCountAsync(date?: string)`

Get the step count for a specific date or today.

```typescript
// Get today's steps
const todaySteps = await AndroidPedometer.getStepsCountAsync();

// Get steps for a specific date
const specificDateSteps = await AndroidPedometer.getStepsCountAsync('2024-03-15');
```

#### `requestPermissions()`

Request necessary permissions for step counting (ACTIVITY_RECOGNITION permission on Android Q and above).

```typescript
const permissionResponse = await AndroidPedometer.requestPermissions();
```

#### `requestNotificationPermissions()`

Request notification permissions required for background service (POST_NOTIFICATIONS permission on Android 13 and above).

```typescript
const notificationPermissionResponse = await AndroidPedometer.requestNotificationPermissions();
```

#### `setupBackgroundUpdates(config?: NotificationConfig)`

Setup background step counting that continues even when the app is in the background or terminated.

```typescript
const config = {
  title: "Step Counter",
  contentTemplate: "You've taken %d steps today",
  style: "default",
  iconResourceName: "ic_notification"
};

await AndroidPedometer.setupBackgroundUpdates(config);
```

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


## Example Usage

```typescript
import * as AndroidPedometer from 'expo-android-pedometer';

async function setupPedometer() {
  try {
    // Initialize the pedometer
    await AndroidPedometer.initialize();

    // Request necessary permissions
    const permissionResponse = await AndroidPedometer.requestPermissions();
    const notificationPermissionResponse = await AndroidPedometer.requestNotificationPermissions();

    if (permissionResponse.granted && notificationPermissionResponse.granted) {
      // Setup background updates
      await AndroidPedometer.setupBackgroundUpdates({
        title: "Step Counter",
        contentTemplate: "You've taken %d steps today",
        style: "default"
      });

      // Subscribe to step updates
      const unsubscribe = AndroidPedometer.subscribeToChange((event) => {
        console.log(`Steps: ${event.steps}`);
      });
    }
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
[] Optional sync to Health Connect
[] Ability to disable notification and background sync
[] More options to customize the notification
[] Organize code

## License

MIT
