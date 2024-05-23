package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
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

                // Calculate monthly humidity average every 20 seconds
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCalculationTime >= CALCULATION_INTERVAL) {
                    calculateMonthlyHumidityAverage();
                    lastCalculationTime = currentTime;
                }
            }
        }
    }

    private static void processMessage(String messageStr) {
        // Deserialize the message
        
        String[] parts = messageStr.split(",");
        if (parts.length == 3){
            String sensorId = parts[0];
            String valueStr = parts[1];
            String timestamp = parts[2];

            if (sensorId.contains("humedad")) {
                dailyHumidityReadings.computeIfAbsent(sensorId, k -> new ArrayList<>()).add(Double.parseDouble(valueStr));
    
                String monthKey = getMonthKey(timestamp);
                monthlyHumidityReadings.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(Double.parseDouble(valueStr));
            }

        // Handle alerts
        if (sensorId.contains("Alerta") || sensorId.contains("ALERTA")) {
            storeAlert(messageStr);
        }
        }

    }

    private static String getMonthKey(String timestamp) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        LocalDateTime dateTime = LocalDateTime.parse(timestamp, formatter);
        return dateTime.getMonth().toString() + "-" + dateTime.getYear();
    }

    private static void calculateMonthlyHumidityAverage() {
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
                generateAlert(monthKey, monthlyAverage);
            }
        }
        // Clear the readings after calculation
        monthlyHumidityReadings.clear();
    }

    private static void generateAlert(String monthKey, double monthlyAverage) {
        String alertMessage = "ALERTA: Humedad fuera de rango en el " + monthKey + ": " + monthlyAverage;
        System.out.println(alertMessage);
        // Store the alert in the cloud
        storeAlert("Alerta "+ Instant.now().toString()+". Valor: "+ monthlyAverage);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:9876");

            socket.send(alertMessage.getBytes(ZMQ.CHARSET), 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void storeAlert(String alert) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("alertas.txt", true))) {
            writer.write(alert);
            writer.newLine(); // Add a new line after each alert
            System.out.println("Alerta guardada: " + alert);
        } catch (IOException e) {
            System.err.println("Error writing alert to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
