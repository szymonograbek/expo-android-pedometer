import * as React from 'react';

import { AndroidPedometerViewProps } from './AndroidPedometer.types';

export default function AndroidPedometerView(props: AndroidPedometerViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
