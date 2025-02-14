import { requireNativeModule } from 'expo-modules-core';
import {
  AndroidPedometerModuleEvents,
  PermissionResponse,
  PedometerUpdateEventPayload,
} from './AndroidPedometer.types';

const AndroidPedometer = requireNativeModule<{
  initialize(): Promise<boolean>;
  getStepsCountAsync(date?: string): Promise<number>;
  requestPermissions(): Promise<PermissionResponse>;
  requestNotificationPermissions(): Promise<PermissionResponse>;
  setupBackgroundUpdates(notificationTitle?: string, notificationTemplate?: string): Promise<boolean>;
  customizeNotification(title?: string, textTemplate?: string): Promise<boolean>;
  setNotificationIcon(iconResourceId: number): Promise<boolean>;
  addListener(eventName: string, listener: (event: any) => void): { remove: () => void };
}>('AndroidPedometer');

export function initialize(): Promise<boolean> {
  return AndroidPedometer.initialize();
}

export function getStepsCountAsync(date?: string): Promise<number> {
  return AndroidPedometer.getStepsCountAsync(date);
}

export function requestPermissions(): Promise<PermissionResponse> {
  return AndroidPedometer.requestPermissions();
}

export function requestNotificationPermissions(): Promise<PermissionResponse> {
  return AndroidPedometer.requestNotificationPermissions();
}

export async function setupBackgroundUpdates(notificationTitle?: string, notificationTemplate?: string): Promise<boolean> {
  return await AndroidPedometer.setupBackgroundUpdates(notificationTitle, notificationTemplate);
}

export function customizeNotification(title?: string, textTemplate?: string): Promise<boolean> {
  return AndroidPedometer.customizeNotification(title, textTemplate);
}

export function setNotificationIcon(iconResourceId: number): Promise<boolean> {
  return AndroidPedometer.setNotificationIcon(iconResourceId);
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

export { AndroidPedometerModuleEvents, PermissionResponse };


