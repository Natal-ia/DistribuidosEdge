package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SensorTemperatura implements Runnable {
    private final String sensorId;
    private final AtomicReference<String> proxyAddress;
    private final Random random;
    private final int threadId;
    private final String configFilePath;
    private double rangeProbability;
    private double outOfRangeProbability;
    private double incorrectDataProbability;

    private AtomicInteger messageCounter;

    public SensorTemperatura(String sensorId, AtomicReference<String> proxyAddress, int threadId, String configFilePath, AtomicInteger messageCounter) {
        this.sensorId = sensorId;
        this.proxyAddress = proxyAddress;
        this.random = new Random();
        this.threadId = threadId;
        this.configFilePath = configFilePath;
        this.loadConfig();
        this.messageCounter = messageCounter;
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.PUSH);
            socket.connect(proxyAddress.get());

            int sleepInterval = 4000;

            while (!Thread.currentThread().isInterrupted()) {
                String message = "";
                String timestamp;

                double sensorValueT = generateSensorDouble(11.0, 29.4);
                timestamp = Instant.now().toString();
                message = sensorId + "," + timestamp + "," + sensorValueT;

                // Send the message to the proxy server
                socket.send(message.getBytes(), 0);
                System.out.println("Sent: " + message + " from thread " + threadId);
                messageCounter.incrementAndGet();

                // Sleep for the specified interval before sending the next message
                Thread.sleep(sleepInterval);

                // Check if the proxy address has changed and reconnect if necessary
                String currentAddress = proxyAddress.get();
                if (!socket.getLastEndpoint().equals(currentAddress)) {
                    socket.disconnect(socket.getLastEndpoint());
                    socket.connect(currentAddress);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
        } finally {
            System.out.println("Mensajes enviados por sensor de temperatura: " + messageCounter.get());
        }
    }

    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            // Read the first line for rangeProbability
            String line = reader.readLine();
            if (line != null) {
                rangeProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing rangeProbability");
            }

            // Read the second line for outOfRangeProbability
            line = reader.readLine();
            if (line != null) {
                outOfRangeProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing outOfRangeProbability");
            }

            // Read the third line for incorrectDataProbability
            line = reader.readLine();
            if (line != null) {
                incorrectDataProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing incorrectDataProbability");
            }

        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private double generateSensorDouble(double min, double max) {
        double randomNumber = random.nextDouble();
        double sensorValue;
        if (randomNumber < rangeProbability) {
            // Generate data within range
            sensorValue = generateValueWithinRange(min, max);
        } else if (randomNumber < rangeProbability + outOfRangeProbability) {
            // Generate data out of range
            sensorValue = generateValueOutOfRange(min, max);
        } else {
            // Generate incorrect data
            sensorValue = generateIncorrectData();
        }
        return sensorValue;
    }

    private double generateValueWithinRange(double min, double max) {
        // Generate data within the specified range
        return min + (max - min) * random.nextDouble();
    }

    private double generateValueOutOfRange(double min, double max) {
        Boolean higherThanRange = random.nextBoolean();
        sendAlertToSC("ALERTA: Temperatura fuera de rango");
        if (higherThanRange) {
            return max + random.nextDouble() * 100; // Genera un valor mayor que el rango
        } else {
            // Genera un valor menor que el rango pero no negativo
            return min - random.nextDouble() * (Math.abs(min) + 100);
        }
    }

    private double generateIncorrectData() {
        // Generate incorrect data as a negative random value
        return -random.nextDouble() * 100; // Genera un valor negativo entre 0 y -100
    }

    private void sendAlertToSC(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:9876");
            aspersorSocket.send(message.getBytes(), 0);
            System.out.println("Alerta de temperatura enviada al sistema de calidad");
            messageCounter.incrementAndGet();
        }
    }
}
