// Reexport the native module. On web, it will be resolved to AndroidPedometerModule.web.ts
// and on native platforms to AndroidPedometerModule.ts
export { default } from './AndroidPedometerModule';
export { default as AndroidPedometerView } from './AndroidPedometerView';
export * from  './AndroidPedometer.types';
