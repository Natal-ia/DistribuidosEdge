package com.example;

import java.util.concurrent.atomic.AtomicReference;

public class SensorClient {
    public static void main(String[] args) {
        // Obtener Argumentos del programa
        if (args.length < 2) {
            System.out.println("Uso: java SensorClient <nombre_sensor> <nombre_archivo>");
            System.exit(1);
        }

        String sensorName = args[0];
        String configFile = args[1];

        System.out.println("Nombre sensor: " + sensorName);
        System.out.println("Archivo configuración: " + configFile);

        AtomicReference<String> proxyAddress = new AtomicReference<>("tcp://localhost:1234");

        // Start the health check thread
        Thread healthCheckThread = new Thread(new HealthCheck(proxyAddress));
        healthCheckThread.start();

        String nombreArchivo = "Edge/sensor/src/main/resources/" + configFile;
        Thread[] sensorThreads = new Thread[10];

        for (int j = 0; j < 10; j++) {
            Runnable sensor;
            switch (sensorName.toLowerCase()) {
                case "humo":
                    sensor = new SensorHumo(sensorName.toLowerCase(), proxyAddress, j, nombreArchivo);
                    break;
                case "humedad":
                    sensor = new SensorHumedad(sensorName.toLowerCase(), proxyAddress, j, nombreArchivo);
                    break;
                case "temperatura":
                    sensor = new SensorTemperatura(sensorName.toLowerCase(), proxyAddress, j, nombreArchivo);
                    break;
                default:
                    System.out.println("Sensor desconocido: " + sensorName);
                    return;
            }
            sensorThreads[j] = new Thread(sensor);
            sensorThreads[j].start();
        }

        // Add shutdown hook to gracefully shut down threads
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Thread thread : sensorThreads) {
                if (thread != null) {
                    thread.interrupt();
                }
            }
            healthCheckThread.interrupt();
        }));

        // Let the main thread sleep to keep the program running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            // Handle interruption
            Thread.currentThread().interrupt();
        }
    }
}
