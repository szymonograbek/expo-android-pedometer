import {initialize, getStepsCountAsync, requestPermissions, setupBackgroundUpdates, requestNotificationPermissions, subscribeToChange, PedometerUpdateEventPayload, simulateMidnightReset} from 'android-pedometer';
import { useEffect } from 'react';
import { useState } from 'react';
import { Button, SafeAreaView, Text, View, StyleSheet, Alert } from 'react-native';

export default function App() {
  const [stepsCount, setStepsCount] = useState(0);
  const [yesterdaySteps, setYesterdaySteps] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const subscription = subscribeToChange((event: PedometerUpdateEventPayload) => {
      setStepsCount(event.steps);
    });
    return () => subscription();
  }, []);

  const handleError = (error: any) => {
    const message = error?.message || 'Unknown error occurred';
    setError(message);
    Alert.alert('Error', message);
  };

  const handleInitialize = async () => {
    try {
      await initialize();
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  const handleGetSteps = async () => {
    try {
      const steps = await getStepsCountAsync();
      setStepsCount(steps);
      
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);
      const yesterdayDate = yesterday.toISOString().split('T')[0];
      const yesterdayStepsCount = await getStepsCountAsync(yesterdayDate);
      setYesterdaySteps(yesterdayStepsCount);
      
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  const handleRequestPermissions = async () => {
    try {
      const result = await requestPermissions();
      if (!result.granted) {
        throw new Error('Activity recognition permission was denied');
      }
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  const handleRequestNotificationPermissions = async () => {
    try {
      const result = await requestNotificationPermissions();
      if (!result.granted) {
        throw new Error('Notification permission was denied');
      }
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  const handleSetupBackgroundUpdates = async () => {
    try {
      await setupBackgroundUpdates({
        title: "Pedometer is working so it can track your steps.",
        contentTemplate: "Steps today: %d. Keep it up!",
        style: "bigText",
      });
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  const handleSimulateMidnightReset = async () => {
    try {
      await simulateMidnightReset();
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Android Pedometer Example</Text>
        
        <View style={styles.stepsContainer}>
          <Text style={styles.stepsLabel}>Today's Steps:</Text>
          <Text style={styles.stepsCount}>{stepsCount}</Text>
          
          <Text style={styles.stepsLabel}>Yesterday's Steps:</Text>
          <Text style={styles.stepsCount}>{yesterdaySteps}</Text>
        </View>

        {error && <Text style={styles.error}>{error}</Text>}
        
        <View style={styles.buttonContainer}>
          <Button title="Initialize" onPress={handleInitialize} />
          <Button title="Get Steps" onPress={handleGetSteps} />
          <Button title="Request Permissions" onPress={handleRequestPermissions} />
          <Button title="Request Notification Permissions" onPress={handleRequestNotificationPermissions} />
          <Button title="Setup Background Updates With Notification" onPress={handleSetupBackgroundUpdates} />
          <Button title="Simulate Midnight Reset" onPress={handleSimulateMidnightReset} />
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    flex: 1,
    padding: 20,
    alignItems: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 30,
  },
  stepsContainer: {
    alignItems: 'center',
    marginBottom: 30,
  },
  stepsLabel: {
    fontSize: 18,
    marginTop: 15,
  },
  stepsCount: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#007AFF',
  },
  buttonContainer: {
    width: '100%',
    gap: 10,
  },
  error: {
    color: 'red',
    marginBottom: 10,
  },
});
