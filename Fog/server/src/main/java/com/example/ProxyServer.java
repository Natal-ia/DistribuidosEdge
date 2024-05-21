package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;

public class ProxyServer {

    private static final double MAX_TEMPERATURE = 29.0; // Temperatura máxima para generar alerta
    private static final int SENSOR_COUNT = 10;
    private static final int HUMIDITY_CALCULATION_INTERVAL_MS = 5000;

    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            ZMQ.Socket cloudSender = context.createSocket(SocketType.PUSH);
            

            receiver.bind("tcp://*:1234");
            cloudSender.connect("tcp://localhost:5678"); // Conectar al servidor en la nube

            List<Double> temperatureReadings = new ArrayList<>();
            List<Double> humidityReadings = new ArrayList<>();

            long lastHumidityCalculationTime = System.currentTimeMillis();

            System.out.println("Proxy server started and listening on tcp://*:1234");

            // Iniciar el hilo del HealthCheckResponder
            Thread healthCheckThread = new Thread(new HealthCheckResponder(context, "tcp://*:1235"));
            healthCheckThread.start();

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
                                calculoTemperatura(temperatureReadings, timestamp, cloudSender);
                                temperatureReadings.clear();
                            }
                        }else{
                            System.out.println("Valor de temperatura erroneo: " + value);
                            sendAlertToSC("ALERTA: Temperatura fuera de rango ");

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

    private static void calculoTemperatura(List<Double> temperatureReadings, String timestamp, ZMQ.Socket cloudSender) {
        double sum = 0;
        for (double temp : temperatureReadings) {
            sum += temp;
        }
        double averageTemp = sum / temperatureReadings.size();
        System.out.println("Promedio temperatura: " + averageTemp + " at " + timestamp);

        if (averageTemp > MAX_TEMPERATURE) {
            String alertMessage = "ALERT: High temperature detected: " + averageTemp + " at " + timestamp;
            sendAlertToSC("ALERTA: Temperatura fuera de rango " + averageTemp + " at " + timestamp);
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

    private static void sendAlertToSC(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:9876");
            aspersorSocket.send(message.getBytes(), 0);
            System.out.println("Alerta de humo enviada al sistema de calidad");
        }
    }
}
