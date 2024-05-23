package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SensorHumo implements Runnable {
    private final String sensorId;
    private final AtomicReference<String> proxyAddress;
    private final int threadId;
    private final String configFilePath;
    private double rangeProbability;
    private double outOfRangeProbability;
    private double incorrectDataProbability;

    private AtomicInteger messageCounter;

    public SensorHumo(String sensorId, AtomicReference<String> proxyAddress, int threadId, String configFilePath, AtomicInteger messageCounter) {
        this.sensorId = sensorId;
        this.proxyAddress = proxyAddress;
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

            // Determine the sleep interval based on the sensor type
            int sleepInterval = 3000;

            while (!Thread.currentThread().isInterrupted()) {
                String message;
                String timestamp;

                boolean sensorValueB = generateSensorBoolean();
                timestamp = Instant.now().toString();
                message = sensorId + "," + timestamp + "," + sensorValueB;
                if (sensorValueB) {
                    sendAlertToAspersor(message);
                    sendAlertToSC(message);
                }

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
            System.out.println("Mensajes enviados por sensor de humo: " + messageCounter.get());
        }
    }

    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            String line = reader.readLine();
            if (line != null) {
                rangeProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing rangeProbability");
            }

            line = reader.readLine();
            if (line != null) {
                outOfRangeProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing outOfRangeProbability");
            }

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

    private boolean generateSensorBoolean() {
        double randomNumber = new Random().nextDouble();
        boolean sensorValue;

        if (randomNumber < rangeProbability) {
            sensorValue = true;
        } else if (randomNumber < rangeProbability + incorrectDataProbability) {
            sensorValue = false;
        } else {
            sensorValue = false;
        }

        return sensorValue;
    }

    private void sendAlertToAspersor(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:4200");
            aspersorSocket.send(message.getBytes(), 0);
            System.out.println("Alerta de humo enviada al aspersor");
            messageCounter.incrementAndGet();
        }
    }

    private void sendAlertToSC(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:9876");
            aspersorSocket.send(message.getBytes(), 0);
            System.out.println("ALERTA: Sensor de humo detecta humo");
            messageCounter.incrementAndGet();
        }
    }
}
