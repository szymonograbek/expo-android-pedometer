import { registerWebModule, NativeModule } from 'expo';

import { AndroidPedometerModuleEvents } from './AndroidPedometer.types';

class AndroidPedometerModule extends NativeModule<AndroidPedometerModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(AndroidPedometerModule);
