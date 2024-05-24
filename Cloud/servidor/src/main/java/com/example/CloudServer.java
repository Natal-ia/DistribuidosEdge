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
import java.io.File;
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
        } finally {
            System.out.println("Total de mensajes enviados por la Capa Cloud: " + messageCounter.get());
        }
    }

    private static void processMessage(String messageStr) {
        // Deserialize the message

        String[] parts = messageStr.split(",");
        if (parts.length == 3) {
            String sensorId = parts[0];
            String valueStr = parts[1];
            String timestamp = parts[2];

            System.out.println(sensorId + " " + valueStr + " " + timestamp);

            if (sensorId.contains("Humedad")) {
                dailyHumidityReadings.computeIfAbsent(sensorId, k -> new ArrayList<>())
                        .add(Double.parseDouble(valueStr));

                monthlyHumidityReadings.computeIfAbsent(timestamp, k -> new ArrayList<>())
                        .add(Double.parseDouble(valueStr));
            }

            // Handle alerts

            if (sensorId.contains("Alerta") || sensorId.contains("ALERTA")) {
                storeAlert(messageStr);
            }
        }

    }

    private static void calculateMonthlyHumidityAverage(AtomicInteger messageCounter) {
        for (Map.Entry<String, List<Double>> entry : monthlyHumidityReadings.entrySet()) {
            String timestamp = entry.getKey();
            List<Double> readings = entry.getValue();

            double sum = 0;
            for (double reading : readings) {
                sum += reading;
            }
            double monthlyAverage = sum / readings.size();
            System.out.println("Monthly average humidity for " + timestamp + ": " + monthlyAverage);

            if (monthlyAverage < minimo_humedad) {
                generateAlert(timestamp, monthlyAverage, messageCounter);
            }
        }
        // Clear the readings after calculation
        monthlyHumidityReadings.clear();
    }

    private static void generateAlert(String timeStamp, double monthlyAverage, AtomicInteger messageCounter) {
        String alertMessage = "ALERTA: Humedad fuera de rango en el " + timeStamp + ": " + monthlyAverage;
        System.out.println(alertMessage);
        // Store the alert in the cloud
        storeAlert("Alerta " + Instant.now().toString() + ". Valor: " + monthlyAverage);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:9876");

            socket.send(alertMessage.getBytes(ZMQ.CHARSET), 0);
            messageCounter.incrementAndGet();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void storeAlert(String alert) {
        File myObj = new File("alertas.txt");
        try {
            if (myObj.createNewFile()) {
                System.out.println("Archivo de alertas creado: " + myObj.getName());
            }
            // Use FileWriter in append mode
            try (FileWriter myWriter = new FileWriter(myObj, true)) {
                myWriter.write(alert);
                myWriter.write(System.lineSeparator()); // Add a new line after each alert
                System.out.println("Alerta guardada: " + alert);
            }
        } catch (IOException e) {
            System.err.println("Error writing alert to file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}