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

/*
 * Descripción: Una clase que emula el funcionamiento de un sensor de humedad el cual tomas muestras cada 5 segundos y luego le envia los datos al proxy
 */
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

    //Función principal que se ejecutara de forma asincronica
    @Override
    public void run() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.PUSH);
            socket.connect(proxyAddress.get());

            int sleepInterval = 4000;

            while (!Thread.currentThread().isInterrupted()) {
                String message = "";
                String timestamp;

                double sensorValueT = generateSensorDouble(11.0, 29.4); //Se genera el valor aleatorio
                timestamp = Instant.now().toString();
                message = sensorId + "," + timestamp + "," + sensorValueT;

                // Envia un mensaje al servidor proxy
                socket.send(message.getBytes(), 0);
                System.out.println("Sent: " + message + " from thread " + threadId);
                messageCounter.incrementAndGet();

                Thread.sleep(sleepInterval);

                // Verifica si el socket a cambiado o es necesario reconectarse
                String currentAddress = proxyAddress.get();
                if (!socket.getLastEndpoint().equals(currentAddress)) {
                    socket.disconnect(socket.getLastEndpoint());
                    socket.connect(currentAddress);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("Mensajes enviados por sensor de temperatura: " + messageCounter.get()); //Al finalizar la ejecución imprime cuantos mensajes ha enviado este sensor
        }
    }

    /*
     * Descripción: Lee el archivo especificado y de este saca las posibilidades para los datos sea: Correcta, fuera de rango e incorrecta
     */
    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            String line = reader.readLine();
            if (line != null) {
                rangeProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing rangeProbability"); //Se obtiene los datos los datos  de que la información sea correcta
            }

            line = reader.readLine();
            if (line != null) {
                outOfRangeProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing outOfRangeProbability"); //Se obtiene los datos  de que la información este fuera de rango
            }

            line = reader.readLine();
            if (line != null) {
                incorrectDataProbability = Double.parseDouble(line.trim());
            } else {
                throw new IllegalArgumentException("Invalid config file: missing incorrectDataProbability"); //Se obtiene los datos  de que la información sea incorrecta
            }

        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    /*
     * Descripción: Genera un valor aleatorio dependiendo de las probabilidades dadas y los parametros de entrada que definen rango que puede tener el valor
     */
    private double generateSensorDouble(double min, double max) {
        double randomNumber = random.nextDouble();
        double sensorValue;
        if (randomNumber < rangeProbability) {
            // Genera un valor correcto
            sensorValue = generateValueWithinRange(min, max);
        } else if (randomNumber < rangeProbability + outOfRangeProbability) {
            // Genera un valor fuera de rango
            sensorValue = generateValueOutOfRange(min, max);
        } else {
            // Genera un valor incorrecto
            sensorValue = generateIncorrectData();
        }
        return sensorValue;
    }

    /*
     * Descripción: Genera un valor double aleatorio dentro de un rango definido por los parametros de entrada
     */
    private double generateValueWithinRange(double min, double max) {
        // Genera el valor dentro de un rango especifico
        return min + (max - min) * random.nextDouble();
    }

    /*
     * Descripción: Genera valores mayores a los del rango delimitado por los parametros de entrada
     */
    private double generateValueOutOfRange(double min, double max) {
        Boolean higherThanRange = random.nextBoolean();
        sendAlertToSC("ALERTA: Temperatura fuera de rango"); //Se envia una alerta al sistema de calidad
        if (higherThanRange) {
            return max + random.nextDouble() * 100; // Genera un valor mayor que el rango
        } else {
            // Genera un valor menor que el rango pero no negativo
            return min - random.nextDouble() * (Math.abs(min) + 100);
        }
    }

    /*
     * Descripción: Genera un valor incorrecto en este caso un valor negativo
     */
    private double generateIncorrectData() {
        return -random.nextDouble() * 100; // Genera un valor negativo entre 0 y -100
    }

    /*
     * Descripción: Envia una alerta al sistema de calidad
     */
    private void sendAlertToSC(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ); 
            aspersorSocket.connect("tcp://localhost:9876"); //Se conecta al sistema de calidad
            aspersorSocket.send(message.getBytes(), 0); //Envia la alerta al sistema de calidad
            System.out.println("Alerta de temperatura enviada al sistema de calidad");
            messageCounter.incrementAndGet(); //Se incrementa el contador de mensajes enviados
        }
    }
}
