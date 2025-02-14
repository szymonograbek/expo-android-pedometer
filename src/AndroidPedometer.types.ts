export type PedometerUpdateEventPayload = {
  steps: number;
  timestamp: number;
};

export type PermissionResponse = {
  status: 'granted' | 'denied';
  granted: boolean;
  expires: 'never' | string;
};

export type AndroidPedometerModuleEvents = {
  'AndroidPedometer.pedometerUpdate': (event: PedometerUpdateEventPayload) => void;
};

export interface AndroidPedometerModule {
  /**
   * Initialize the pedometer module and prepare it for use.
   * @returns Promise<boolean> - Returns true if initialization was successful, false if already initialized
   * @throws {Error} If the device doesn't have a step counter sensor or initialization fails
   */
  initialize(): Promise<boolean>;

  /**
   * Get the step count for a specific date or today if no date is provided.
   * @param date Optional ISO date string (YYYY-MM-DD) to get steps for. If not provided, returns steps for today.
   * @returns Promise<number> - Returns the number of steps for the specified date
   * @throws {Error} If pedometer is not initialized or fails to get step count
   */
  getStepsCountAsync(date?: string): Promise<number>;

  /**
   * Request necessary permissions for step counting.
   * On Android Q (API 29) and above, this will request ACTIVITY_RECOGNITION permission.
   * On earlier versions, this will automatically return granted status.
   * @returns Promise<PermissionResponse> - Returns the status of the permission request
   */
  requestPermissions(): Promise<PermissionResponse>;

  /**
   * Request notification permissions required for background service.
   * On Android 13 (API 33) and above, this will request POST_NOTIFICATIONS permission.
   * On earlier versions, this will automatically return granted status.
   * @returns Promise<PermissionResponse> - Returns the status of the permission request
   */
  requestNotificationPermissions(): Promise<PermissionResponse>;

  /**
   * Setup background step counting that continues even when the app is in the background
   * or terminated. This will create a persistent notification to keep the service alive.
   * @param notificationTitle Optional custom title for the persistent notification
   * @param notificationTemplate Optional custom template for the notification text (%d will be replaced with steps)
   * @returns Promise<boolean> - Returns true if background updates were successfully setup
   * @throws {Error} If pedometer is not initialized or fails to setup background updates
   */
  setupBackgroundUpdates(notificationTitle?: string, notificationTemplate?: string): Promise<boolean>;

  /**
   * Customize the notification appearance after background updates are started.
   * @param title Optional new title for the notification
   * @param textTemplate Optional new template for the notification text (%d will be replaced with steps)
   * @returns Promise<boolean> - Returns true if customization was successful
   */
  customizeNotification(title?: string, textTemplate?: string): Promise<boolean>;

  /**
   * Set a custom icon for the notification.
   * @param iconResourceId Android resource ID of the icon to use
   * @returns Promise<boolean> - Returns true if icon was set successfully
   */
  setNotificationIcon(iconResourceId: number): Promise<boolean>;
}
