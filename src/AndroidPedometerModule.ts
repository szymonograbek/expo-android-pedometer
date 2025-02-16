import { requireNativeModule } from 'expo-modules-core';
import {
  AndroidPedometerModuleEvents,
  NotificationConfig,
  PermissionResponse,
  PedometerUpdateEventPayload,
} from './AndroidPedometer.types';

const AndroidPedometer = requireNativeModule<{
  initialize(): Promise<boolean>;
  getStepsCountAsync(date?: string): Promise<number>;
  requestPermissions(): Promise<PermissionResponse>;
  requestNotificationPermissions(): Promise<PermissionResponse>;
  setupBackgroundUpdates(config?: NotificationConfig): Promise<boolean>;
  addListener(eventName: string, listener: (event: any) => void): { remove: () => void };
  simulateMidnightReset(): Promise<boolean>;
  getStepsCountInRangeAsync(startTimestamp: string, endTimestamp: string): Promise<Record<string, number>>;
  getActivityPermissionStatus(): boolean;
  getNotificationPermissionStatus(): boolean;
}>('AndroidPedometer');

export function initialize(): Promise<boolean> {
  return AndroidPedometer.initialize();
}

export function getStepsCountAsync(date?: string): Promise<number> {
  return AndroidPedometer.getStepsCountAsync(date);
}

export function getStepsCountInRangeAsync(startTimestamp: string, endTimestamp: string): Promise<Record<string, number>> {
  return AndroidPedometer.getStepsCountInRangeAsync(startTimestamp, endTimestamp);
}

export function requestPermissions(): Promise<PermissionResponse> {
  return AndroidPedometer.requestPermissions();
}

export function requestNotificationPermissions(): Promise<PermissionResponse> {
  return AndroidPedometer.requestNotificationPermissions();
}

export async function setupBackgroundUpdates(config?: NotificationConfig): Promise<boolean> {
  return await AndroidPedometer.setupBackgroundUpdates(config);
}

export function subscribeToChange(
  listener: (event: PedometerUpdateEventPayload) => void
): () => void {
  const eventSubscription = AndroidPedometer.addListener(
    'AndroidPedometer.pedometerUpdate',
    listener 
  );
  return () => {
    eventSubscription.remove();
  };
}

export function getActivityPermissionStatus(): boolean {
  return AndroidPedometer.getActivityPermissionStatus();
}

export function getNotificationPermissionStatus(): boolean {
  return AndroidPedometer.getNotificationPermissionStatus();
}

export { AndroidPedometerModuleEvents, PermissionResponse };


