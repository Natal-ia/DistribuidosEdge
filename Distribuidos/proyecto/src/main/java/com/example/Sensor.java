package com.example;

import org.zeromq.ZMQ;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

public class Sensor implements Runnable {
    private final String sensorId;
    private final ZMQ.Socket socket;
    private final Random random;
    private final int threadId;
    private final String configFilePath;
    private double rangeProbability;
    private double outOfRangeProbability;
    private double incorrectDataProbability;

    public Sensor(String sensorId, String proxyAddress, int threadId, ZMQ.Socket socket, String configFilePath) {
        this.sensorId = sensorId;
        this.socket = socket;
        this.random = new Random();
        this.threadId = threadId;
        this.configFilePath = configFilePath;
        this.loadConfig();
    }

    @Override
    public void run() {
        try {
            // Determine the sleep interval based on the sensor type
            int sleepInterval = 0;
            switch (sensorId) {
                case "Temperatura":
                    sleepInterval = 6000; // 6 seconds in milliseconds
                    break;
                case "Humo":
                    sleepInterval = 3000; // 3 seconds in milliseconds
                    break;
                case "Humedad":
                    sleepInterval = 5000; // 5 seconds in milliseconds
                    break;
                default:
                    // Default sleep interval if sensor type is unknown
                    sleepInterval = 1000; // 1 second in milliseconds
                    break;
            }
            while (!Thread.currentThread().isInterrupted()) {
                String message = "";

                switch (sensorId) {
                    case "Temperatura":
                        double sensorValueT = generateSensorDouble(11.0, 29.4);
                        message = sensorId + "," + System.currentTimeMillis() + "," + sensorValueT;
                        break;
                    case "Humo":
                        boolean sensorValueB = generateSensorBoolean();
                        message = sensorId + "," + System.currentTimeMillis() + "," + sensorValueB;
                    
                        break;
                    case "Humedad":
                        double sensorValueH = generateSensorDouble(70.0, 100.0);
                        message = sensorId + "," + System.currentTimeMillis() + "," + sensorValueH;
                        break;
                    default:
                        message = "Error";
                        break;
                }

                // Send the message to the proxy server
                
                socket.send(message.getBytes(), 0);
                System.out.println("Sent: " + message + " from thread " + threadId);
                // Sleep for the specified interval before sending the next message
                Thread.sleep(sleepInterval);
            }
        } catch (InterruptedException e) {
            // Thread interrupted, clean up resources
            socket.close();
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

    private boolean generateSensorBoolean() {
        double randomNumber = random.nextDouble();
        boolean sensorValue;
    
        if (randomNumber < rangeProbability) {
            // Generate a true value within the specified rangeProbability
            sensorValue = true;
        
        } else if (randomNumber < rangeProbability + incorrectDataProbability) {
            // Generate a false value based on the incorrectDataProbability
            sensorValue = false;
        
        } else {
            // Generate a random false value
            //sensorValue = random.nextDouble() < 0.5; // 50% chance of false
            sensorValue = false;
        }
        
        return sensorValue;
    }

}
