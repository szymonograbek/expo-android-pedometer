import {
  AndroidPedometerModuleEvents,
  NotificationConfig,
  PermissionResponse,
  PedometerUpdateEventPayload,
} from './AndroidPedometer.types';

const WARNING_MESSAGE = 'This module is designed for Android. For iOS, please use HealthKit or expo-sensors instead.';


export function initialize(): Promise<boolean> {
  console.warn(WARNING_MESSAGE);
  return Promise.resolve(false);
}

export function getStepsCountAsync(date?: string): Promise<number> {
  console.warn(WARNING_MESSAGE);
  return Promise.resolve(0);
}

export function requestPermissions(): Promise<PermissionResponse> {
  console.warn(WARNING_MESSAGE);
  return Promise.resolve({
    status: 'denied',
    granted: false,
    expires: 'never'
  });
}

export function requestNotificationPermissions(): Promise<PermissionResponse> {
  console.warn(WARNING_MESSAGE);
  return Promise.resolve({
    status: 'denied',
    granted: false,
    expires: 'never'
  });
}

export async function setupBackgroundUpdates(config?: NotificationConfig): Promise<boolean> {
  console.warn(WARNING_MESSAGE);
  return Promise.resolve(false);
}

export function subscribeToChange(
  listener: (event: PedometerUpdateEventPayload) => void
): () => void {
  console.warn(WARNING_MESSAGE);
  return () => {};
}

export function getStepsCountInRangeAsync(startTimestamp: string, endTimestamp: string): Promise<Record<string, number>> {
  console.warn(WARNING_MESSAGE);
  return Promise.resolve({});
}

export { AndroidPedometerModuleEvents, PermissionResponse };


