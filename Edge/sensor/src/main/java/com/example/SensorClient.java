package com.example;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


/*
* Descripción: Crea un 10 threads de cada uno de los sensores: Humedad, humo y temperatura aparte de crear el healthcheck. 
* Espera a la ejecución de todos y luego imprime el numero de mensajes enviados en la capa Edge 
*/
public class SensorClient {
    public static void main(String[] args) {
        /*
         * // Obtener Argumentos del programa
         * if (args.length < 2) {
         * System.out.println("Uso: java SensorClient <nombre_sensor> <nombre_archivo>"
         * );
         * System.exit(1);
         * }
         * 
         * String sensorName = args[0];
         * String configFile = args[1];
         */
        String sensorName = "Humedad";
        String configFile = "humedadConfig.txt";

        System.out.println("Nombre sensor: " + sensorName);
        System.out.println("Archivo configuración: " + configFile);

        AtomicReference<String> proxyAddress = new AtomicReference<>("tcp://10.43.100.230:1234");
        AtomicInteger messageCounter = new AtomicInteger(0);

        // Start the health check thread
        Thread healthCheckThread = new Thread(new HealthCheck(proxyAddress, messageCounter)); //Se inicializa el healthcheck  
        healthCheckThread.start();

        String nombreArchivo = "C:/Users/Natalia Mejia/OneDrive - Gimnasio Femenino/Desktop/Entrega 2- Distribuidos/Edge/sensor/src/main/resources/"
                + configFile;
        Thread[] sensorThreads = new Thread[30];
        for (int i = 0; i < 3; i++) { //Se inicializan 10 hilos de los tres tipos de sensores
            if (i == 0) {
                sensorName = "Temperatura";
                nombreArchivo = "C:/Users/Natalia Mejia/OneDrive - Gimnasio Femenino/Desktop/Entrega 2- Distribuidos/Edge/sensor/src/main/resources/temperaturaConfig.txt";
            }
            if (i == 1) {
                sensorName = "Humedad";
                nombreArchivo = "C:/Users/Natalia Mejia/OneDrive - Gimnasio Femenino/Desktop/Entrega 2- Distribuidos/Edge/sensor/src/main/resources/humedadConfig.txt";
            }
            if (i == 2) {
                sensorName = "Humo";
                nombreArchivo = "C:/Users/Natalia Mejia/OneDrive - Gimnasio Femenino/Desktop/Entrega 2- Distribuidos/Edge/sensor/src/main/resources/humoConfig.txt";
            }
            for (int j = 0; j < 10; j++) {//Ciclo para crear los hilos de los sensores dependiendo el nombre del sensor y archivo
                Runnable sensor;
                switch (sensorName.toLowerCase()) {
                    case "humo":
                        sensor = new SensorHumo(sensorName.toLowerCase(), proxyAddress, j, nombreArchivo,
                                messageCounter);
                        break;
                    case "humedad":
                        sensor = new SensorHumedad(sensorName.toLowerCase(), proxyAddress, j, nombreArchivo,
                                messageCounter);
                        break;
                    case "temperatura":
                        sensor = new SensorTemperatura(sensorName.toLowerCase(), proxyAddress, j, nombreArchivo,
                                messageCounter);
                        break;
                    default:
                        System.out.println("Sensor desconocido: " + sensorName);
                        return;
                }
                sensorThreads[j] = new Thread(sensor);
                sensorThreads[j].start();
            }
        }
        // Se utiliza addShutdownHook para cerrar todo los hilos
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Thread thread : sensorThreads) {
                if (thread != null) {
                    thread.interrupt();
                }
            }
            healthCheckThread.interrupt();
            System.out.println("Mensajes enviados de la capa Edge: " + messageCounter.get());
        }));

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            // Handle interruption
            Thread.currentThread().interrupt();
        }
    }
}
