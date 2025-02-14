import { requireNativeView } from 'expo';
import * as React from 'react';

import { AndroidPedometerViewProps } from './AndroidPedometer.types';

const NativeView: React.ComponentType<AndroidPedometerViewProps> =
  requireNativeView('AndroidPedometer');

export default function AndroidPedometerView(props: AndroidPedometerViewProps) {
  return <NativeView {...props} />;
}
