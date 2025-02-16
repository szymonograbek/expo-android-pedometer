import {
  initialize,
  getStepsCountAsync,
  requestPermissions,
  setupBackgroundUpdates,
  requestNotificationPermissions,
  subscribeToChange,
  PedometerUpdateEventPayload,
  getStepsCountInRangeAsync,
} from "android-pedometer";
import { useEffect } from "react";
import { useState } from "react";
import {
  Button,
  SafeAreaView,
  Text,
  View,
  StyleSheet,
  Alert,
  ScrollView,
} from "react-native";
import {
  endOfDay,
  format,
  formatISO,
  startOfDay,
} from "date-fns";
import DatePicker from "react-native-date-picker";

export default function App() {
  const [isStartDayPickerOpen, setIsStartDayPickerOpen] = useState(false);
  const [isEndDayPickerOpen, setIsEndDayPickerOpen] = useState(false);

  const [range, setRange] = useState({
    start: startOfDay(new Date()),
    end: endOfDay(new Date()),
  });

  const [stepsCount, setStepsCount] = useState(0);
  const [stepsInRange, setStepsInRange] = useState<Record<string, number>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const subscription = subscribeToChange(
      (event: PedometerUpdateEventPayload) => {
        console.log("event", event);
        setStepsCount(prev => prev + event.steps);
      }
    );
    return () => subscription();
  }, []);

  const handleError = (error: any) => {
    const message = error?.message || "Unknown error occurred";
    setError(message);
    Alert.alert("Error", message);
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
      const steps = await getStepsCountAsync(
        startOfDay(new Date()).toISOString()
      );

      console.log({steps})

      setStepsCount(steps);

      await handleGetStepsInRange();

      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  const handleRequestPermissions = async () => {
    try {
      const result = await requestPermissions();
      if (!result.granted) {
        throw new Error("Activity recognition permission was denied");
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
        throw new Error("Notification permission was denied");
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

  const handleGetStepsInRange = async () => {
    try {
      const result = await getStepsCountInRangeAsync(
        range.start.toISOString(),
        range.end.toISOString()
      );
      setStepsInRange(result);
      setError(null);
    } catch (e) {
      handleError(e);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={{ flex: 1 }} contentContainerStyle={styles.content}>
        <Text style={styles.title}>Android Pedometer Example</Text>

        <View style={styles.stepsContainer}>
          <Text style={styles.stepsLabel}>Today's Steps:</Text>
          <Text style={styles.stepsCount}>{stepsCount}</Text>

          <View style={{ flexDirection: "column", gap: 10 }}>
            <Text style={styles.stepsLabel}>
              Steps in range {format(range.start, "PP p")} -{" "}
              {format(range.end, "PP p")}:
            </Text>

            {Object.entries(stepsInRange).map(([date, steps]) => (
              <Text key={date}>
                {formatISO(date)}: {steps}
              </Text>
            ))}

            <View style={{ flexDirection: "row", gap: 10 }}>
              <Button
                title="Select Start Date"
                onPress={() => setIsStartDayPickerOpen(true)}
              />
              <Button
                title="Select End Date"
                onPress={() => setIsEndDayPickerOpen(true)}
              />
            </View>

            <DatePicker
              modal
              mode="datetime"
              open={isStartDayPickerOpen}
              date={range.start}
              maximumDate={range.end}
              onConfirm={(date) => {
                setIsStartDayPickerOpen(false);
                setRange({ ...range, start: date });
              }}
              onCancel={() => {
                setIsStartDayPickerOpen(false);
              }}
            />

            <DatePicker
              modal
              mode="datetime"
              open={isEndDayPickerOpen}
              date={range.end}
              minimumDate={range.start}
              onConfirm={(date) => {
                setIsEndDayPickerOpen(false);
                setRange({ ...range, end: date });
              }}
              onCancel={() => {
                setIsEndDayPickerOpen(false);
              }}
            />
          </View>
        </View>

        {error && <Text style={styles.error}>{error}</Text>}

        <View style={styles.buttonContainer}>
          <Button title="Initialize" onPress={handleInitialize} />
          <Button title="Get Steps" onPress={handleGetSteps} />
          <Button title="Get Steps In Range" onPress={handleGetStepsInRange} />
          <Button
            title="Request Permissions"
            onPress={handleRequestPermissions}
          />
          <Button
            title="Request Notification Permissions"
            onPress={handleRequestNotificationPermissions}
          />
          <Button
            title="Setup Background Updates With Notification"
            onPress={handleSetupBackgroundUpdates}
          />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
  },
  content: {
    padding: 20,
    alignItems: "center",
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 30,
  },
  stepsContainer: {
    alignItems: "center",
    marginBottom: 30,
  },
  stepsLabel: {
    fontSize: 18,
    marginTop: 15,
  },
  stepsCount: {
    fontSize: 36,
    fontWeight: "bold",
    color: "#007AFF",
  },
  buttonContainer: {
    width: "100%",
    gap: 10,
  },
  error: {
    color: "red",
    marginBottom: 10,
  },
});
