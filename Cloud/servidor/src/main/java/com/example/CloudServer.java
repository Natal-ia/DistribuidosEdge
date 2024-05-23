package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CloudServer {

    private static final int CALCULATION_INTERVAL = 20000; // 20 seconds
    private static final double minimo_humedad = 70.0; 

    private static Map<String, List<Double>> dailyHumidityReadings = new HashMap<>();
    private static Map<String, List<Double>> monthlyHumidityReadings = new HashMap<>();

    public static void main(String[] args) {
        AtomicInteger messageCounter = new AtomicInteger(0);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5678");
            System.out.println("Cloud server started and listening on tcp://*:5678");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Total de mensajes enviados de la capa Cloud: " + messageCounter.get());
            }));

            long lastCalculationTime = System.currentTimeMillis();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = socket.recv(0);
                String messageStr = new String(message, ZMQ.CHARSET);
                System.out.println("Received from proxy: " + messageStr);

                // Process the message
                processMessage(messageStr);

                socket.send("ACK".getBytes(ZMQ.CHARSET), 0);
                messageCounter.incrementAndGet();

                // Calculate monthly humidity average every 20 seconds
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCalculationTime >= CALCULATION_INTERVAL) {
                    calculateMonthlyHumidityAverage(messageCounter);
                    lastCalculationTime = currentTime;
                }
            }
        }
    }

    private static void processMessage(String messageStr) {
        // Deserialize the message
        Gson gson = new Gson();
        Measurement measurement = gson.fromJson(messageStr, Measurement.class);

        // Store the humidity readings for daily and monthly calculations
        if (measurement.sensorId.contains("humedad")) {
            dailyHumidityReadings.computeIfAbsent(measurement.sensorId, k -> new ArrayList<>()).add(measurement.value);

            String monthKey = getMonthKey(measurement.timestamp);
            monthlyHumidityReadings.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(measurement.value);
        }

        // Handle alerts
        if (measurement.sensorId.contains("alerta")) {
            storeAlert(measurement);
        }
    }

    private static String getMonthKey(String timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        LocalDateTime dateTime = LocalDateTime.parse(timestamp, formatter);
        return dateTime.getMonth().toString() + "-" + dateTime.getYear();
    }

    private static void calculateMonthlyHumidityAverage(AtomicInteger messageCounter) {
        for (Map.Entry<String, List<Double>> entry : monthlyHumidityReadings.entrySet()) {
            String monthKey = entry.getKey();
            List<Double> readings = entry.getValue();

            double sum = 0;
            for (double reading : readings) {
                sum += reading;
            }
            double monthlyAverage = sum / readings.size();
            System.out.println("Monthly average humidity for " + monthKey + ": " + monthlyAverage);

            if (monthlyAverage < minimo_humedad) {
                generateAlert(monthKey, monthlyAverage, messageCounter);
            }
        }
        // Clear the readings after calculation
        monthlyHumidityReadings.clear();
    }

    private static void generateAlert(String monthKey, double monthlyAverage, AtomicInteger messageCounter) {
        String alertMessage = "ALERTA: Humedad fuera de rango en el " + monthKey + ": " + monthlyAverage;
        System.out.println(alertMessage);
        // Store the alert in the cloud
        storeAlert(new Measurement("alerta", Instant.now().toString(), monthlyAverage));
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:9876");

            socket.send(alertMessage.getBytes(ZMQ.CHARSET), 0);
            messageCounter.incrementAndGet();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    
    private static void storeAlert(Measurement alert) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("alertas.txt", true))) {
            // Construir la línea de la alerta
            writer.write(alert.toString());
            writer.newLine(); // Agregar un salto de línea
            System.out.println("Alerta guardada: " + alert.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Measurement {
        String sensorId;
        String timestamp;
        double value;

        Measurement(String sensorId, String timestamp, double value) {
            this.sensorId = sensorId;
            this.timestamp = timestamp;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Measurement{" +
                    "sensorId='" + sensorId + '\'' +
                    ", timestamp='" + timestamp + '\'' +
                    ", value=" + value +
                    '}';
        }
    }
}
