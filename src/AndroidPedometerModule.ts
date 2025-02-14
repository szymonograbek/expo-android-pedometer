import { NativeModule, requireNativeModule } from 'expo';

import { AndroidPedometerModuleEvents } from './AndroidPedometer.types';

declare class AndroidPedometerModule extends NativeModule<AndroidPedometerModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<AndroidPedometerModule>('AndroidPedometer');
