package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyServer {

    private static final double MAX_TEMPERATURE = 29.0; // Temperatura máxima para generar alerta
    private static final int SENSOR_COUNT = 10;
    private static final int HUMIDITY_CALCULATION_INTERVAL_MS = 5000;

    private static final List<Long> roundTripTimes = new ArrayList<>();

    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            ZMQ.Socket cloudSender = context.createSocket(SocketType.REQ);

            receiver.bind("tcp://*:1234");
            cloudSender.connect("tcp://10.139.103.205:5678"); // Conectar al servidor en la nube

            List<Double> temperatureReadings = new ArrayList<>();
            List<Double> humidityReadings = new ArrayList<>();

            long lastHumidityCalculationTime = System.currentTimeMillis();

            AtomicInteger messageCounter = new AtomicInteger(0);
            AtomicInteger messageCounter_H = new AtomicInteger(0);

            System.out.println("Proxy server started and listening on tcp://*:1234");

            // Iniciar el hilo del HealthCheckResponder
            Thread healthCheckThread = new Thread(new HealthCheckResponder(context, "tcp://*:1235", messageCounter_H));
            healthCheckThread.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                healthCheckThread.interrupt();
                try {
                    healthCheckThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int totalMessagesSent = messageCounter.get() + messageCounter_H.get();
                System.out.println("Mensajes enviados por el proxy: " + messageCounter.get());
                System.out.println("Mensajes enviados por el proxy y su HealthCheck: " + totalMessagesSent);
                double averageTime = calculateAverage(roundTripTimes);
                double stdDevTime = calculateStandardDeviation(roundTripTimes, averageTime);
                System.out.println("Tiempo promedio de envío y recibo del fog y cloud: " + averageTime + " ms");
                System.out.println("Desviación estándar del tiempo de envío y recibo del fog y cloud: " + stdDevTime + " ms");
            }));

            while (!Thread.currentThread().isInterrupted()) {
                byte[] messageBytes = receiver.recv(0);
                String message = new String(messageBytes, ZMQ.CHARSET);
                System.out.println("Received from sensor: " + message);

                String[] parts = message.split(",");
                if (parts.length < 3)
                    continue;

                String sensorId = parts[0];
                String timestamp = parts[1];
                String valueStr = parts[2];

                try {
                    if (sensorId.startsWith("temperatura")) {
                        double value = Double.parseDouble(valueStr);
                        if (value >= 11 && value <= 29.4) {
                            temperatureReadings.add(value);
                            if (temperatureReadings.size() == SENSOR_COUNT) {
                                calculoTemperatura(temperatureReadings, timestamp, cloudSender, messageCounter);
                                temperatureReadings.clear();
                            }
                        } else {
                            System.out.println("Valor de temperatura erroneo: " + value);
                            sendAlertToSC("ALERTA: Temperatura fuera de rango", messageCounter);
                            messageCounter.incrementAndGet();
                            String messageCloud = "ALERTA, Temperatura fuera de rango," + timestamp;
                            sendMessageToCloud(messageCloud, cloudSender);
                        }
                    } else if (sensorId.startsWith("humedad")) {
                        double value = Double.parseDouble(valueStr);
                        humidityReadings.add(value);
                        if (System.currentTimeMillis() - lastHumidityCalculationTime >= HUMIDITY_CALCULATION_INTERVAL_MS) {
                            humedadDiaria(humidityReadings, timestamp, cloudSender, messageCounter);
                            humidityReadings.clear();
                            lastHumidityCalculationTime = System.currentTimeMillis();
                        }
                    } else if (sensorId.startsWith("humo")) {
                        if (Boolean.parseBoolean(valueStr)) {
                            System.out.println("Alerta Humo ");
                            sendAlertToSC("ALERTA: Humo", messageCounter);
                            String messageCloud = "ALERTA, Humo detectado," + timestamp;
                            sendMessageToCloud(messageCloud, cloudSender);
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

    private static void calculoTemperatura(List<Double> temperatureReadings, String timestamp, ZMQ.Socket cloudSender,
                                           AtomicInteger messageCounter) {
        double sum = 0;
        for (double temp : temperatureReadings) {
            sum += temp;
        }
        double averageTemp = sum / temperatureReadings.size();
        System.out.println("Promedio temperatura: " + averageTemp + " at " + timestamp);

        if (averageTemp > MAX_TEMPERATURE) {
            String alertMessage = "Alerta temperatura," + averageTemp + "," + timestamp;
            sendAlertToSC("ALERTA: Temperatura fuera de rango " + averageTemp + " at " + timestamp, messageCounter);
            sendMessageToCloud(alertMessage, cloudSender);
            messageCounter.incrementAndGet();
        }
    }

    private static void humedadDiaria(List<Double> humidityReadings, String timestamp, ZMQ.Socket cloudSender,
                                      AtomicInteger messageCounter) {
        double sum = 0;
        for (double humidity : humidityReadings) {
            sum += humidity;
        }
        double averageHumidity = sum / humidityReadings.size();
        String message = "Humedad," + averageHumidity + "," + timestamp;
        sendMessageToCloud(message, cloudSender);
        messageCounter.incrementAndGet();
    }

    private static void sendAlertToSC(String message, AtomicInteger messageCounter) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:9876");
            aspersorSocket.send(message.getBytes(), 0);
            System.out.println("Alerta de humo enviada al sistema de calidad");
            messageCounter.incrementAndGet();
        }
    }

    private static void sendMessageToCloud(String message, ZMQ.Socket cloudSender) {
        long startTime = System.currentTimeMillis();
        cloudSender.send(message.getBytes(), 0);
        byte[] reply = cloudSender.recv();
        long endTime = System.currentTimeMillis();
        long roundTripTime = endTime - startTime;
        synchronized (roundTripTimes) {
            roundTripTimes.add(roundTripTime);
        }
        System.out.println("Sent to cloud: " + message + " - Round-trip time: " + roundTripTime + " ms");
    }

    private static double calculateAverage(List<Long> times) {
        double sum = 0;
        synchronized (times) {
            for (long time : times) {
                sum += time;
            }
        }
        return times.isEmpty() ? 0 : sum / times.size();
    }

    private static double calculateStandardDeviation(List<Long> times, double average) {
        double sum = 0;
        synchronized (times) {
            for (long time : times) {
                sum += Math.pow(time - average, 2);
            }
        }
        return times.isEmpty() ? 0 : Math.sqrt(sum / times.size());
    }
}
