package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;

public class BackupProxyServer {

    private static final double MAX_TEMPERATURE = 29.0; // Temperatura máxima para generar alerta
    private static final int SENSOR_COUNT = 10;
    private static final int HUMIDITY_CALCULATION_INTERVAL_MS = 5000;

    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            ZMQ.Socket cloudSender = context.createSocket(SocketType.PUSH);
            ZMQ.Socket alertSender = context.createSocket(SocketType.PUSH);

            receiver.bind("tcp://*:4321");
            cloudSender.connect("tcp://localhost:5678"); // Conectar al servidor en la nube
            alertSender.connect("tcp://localhost:5555"); // Conectar al sistema de calidad en la capa Fog

            List<Double> temperatureReadings = new ArrayList<>();
            List<Double> humidityReadings = new ArrayList<>();

            long lastHumidityCalculationTime = System.currentTimeMillis();

            System.out.println("Backup Proxy server started and listening on tcp://*:1234");

            while (true) {
                byte[] messageBytes = receiver.recv(0);
                String message = new String(messageBytes, ZMQ.CHARSET);
                System.out.println("Received from sensor: " + message);

                String[] parts = message.split(",");
                if (parts.length < 3)
                    continue; // Mensaje inválido

                String sensorId = parts[0];
                String timestamp = parts[1];
                String valueStr = parts[2];

                try {
                    double value = Double.parseDouble(valueStr);
                    if (sensorId.startsWith("temperatura")) {
                        if (value >= 11 && value <= 29.4) { // No erroneos
                            temperatureReadings.add(value);
                            if (temperatureReadings.size() == SENSOR_COUNT) {
                                calculoTemperatura(temperatureReadings, timestamp, alertSender, cloudSender);
                                temperatureReadings.clear();
                            }
                        }
                    } else if (sensorId.startsWith("humedad")) {
                        humidityReadings.add(value);
                        if (System.currentTimeMillis() - lastHumidityCalculationTime >= HUMIDITY_CALCULATION_INTERVAL_MS) {
                            humedadDiaria(humidityReadings, timestamp, cloudSender);
                            humidityReadings.clear();
                            lastHumidityCalculationTime = System.currentTimeMillis();
                        }
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid sensor value: " + valueStr);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void calculoTemperatura(List<Double> temperatureReadings, String timestamp, ZMQ.Socket alertSender, ZMQ.Socket cloudSender) {
        double sum = 0;
        for (double temp : temperatureReadings) {
            sum += temp;
        }
        double averageTemp = sum / temperatureReadings.size();
        System.out.println("Promedio temperatura: " + averageTemp + " at " + timestamp);

        if (averageTemp > MAX_TEMPERATURE) {
            String alertMessage = "ALERT: High temperature detected: " + averageTemp + " at " + timestamp;
            alertSender.send(alertMessage.getBytes(), 0);
            cloudSender.send(alertMessage.getBytes(), 0);
            System.out.println("Sent alert: " + alertMessage);
        }
    }

    private static void humedadDiaria(List<Double> humidityReadings, String timestamp, ZMQ.Socket cloudSender) {
        double sum = 0;
        for (double humidity : humidityReadings) {
            sum += humidity;
        }
        double averageHumidity = sum / humidityReadings.size();
        String message = "Average humidity: " + averageHumidity + " at " + timestamp;
        cloudSender.send(message.getBytes(), 0);
        System.out.println("Sent to cloud: " + message);
    }
}
