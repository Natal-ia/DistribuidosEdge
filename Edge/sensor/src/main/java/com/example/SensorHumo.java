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

//Descripción: Una clase que simula las acciones de un sensor de humo el toma muestras cada 3 segundos y si detecta una señal de humo
//envia un mensaje al aspersor, una alerta al proxy y una alerta al sistema de calidad de la capa edge.
public class SensorHumo implements Runnable {
    private final String sensorId;
    private final AtomicReference<String> proxyAddress;
    private final int threadId;
    private final String configFilePath;
    private double rangeProbability;
    private double outOfRangeProbability;
    private double incorrectDataProbability;

    private AtomicInteger messageCounter;

    //Constructor
    public SensorHumo(String sensorId, AtomicReference<String> proxyAddress, int threadId, String configFilePath, AtomicInteger messageCounter) {
        this.sensorId = sensorId;
        this.proxyAddress = proxyAddress;
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
            socket.connect(proxyAddress.get()); //Se conecta al proxy principal

            // Intervalo de espera de 3 segundos
            int sleepInterval = 3000;

            while (!Thread.currentThread().isInterrupted()) {
                String message;
                String timestamp;

                boolean sensorValueB = generateSensorBoolean();
                timestamp = Instant.now().toString();
                message = sensorId + "," + timestamp + "," + sensorValueB;
                if (sensorValueB) { //Si el valor es verdadero se envia una alerta al aspersor y al sistema de calidad
                    sendAlertToAspersor(message);
                    sendAlertToSC("ALERTA: Humo detecta humo");
                }

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
            System.out.println("Mensajes enviados por sensor de humo: " + messageCounter.get()); //Al finalizar la ejecución imprime cuantos mensajes ha enviado este sensor
        }
    }

    /*
     * Descripción: Lee el archivo especificado y de este saca las posibilidades para los datos sea: Correcta, fuera de rango e incorrecta
     */
    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            String line = reader.readLine();
            if (line != null) {
                rangeProbability = Double.parseDouble(line.trim()); //Se obtiene los datos los datos  de que la información sea correcta
            } else {
                throw new IllegalArgumentException("Invalid config file: missing rangeProbability");
            }

            line = reader.readLine();
            if (line != null) {
                outOfRangeProbability = Double.parseDouble(line.trim()); //Se obtiene los datos  de que la información este fuera de rango
            } else {
                throw new IllegalArgumentException("Invalid config file: missing outOfRangeProbability");
            }

            line = reader.readLine();
            if (line != null) {
                incorrectDataProbability = Double.parseDouble(line.trim()); //Se obtiene los datos  de que la información sea incorrecta
            } else {
                throw new IllegalArgumentException("Invalid config file: missing incorrectDataProbability");
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    /*
     * Descripción: Genera un valor de respuesta aleatorio dependiendo de las configuraciones dadas. Como es un sensor de humo solo devuelve verdadero o falso
     */
    private boolean generateSensorBoolean() {
        double randomNumber = new Random().nextDouble(); //Se genera un valor aleatorio
        boolean sensorValue;

        if (randomNumber < rangeProbability) { //Si el valor es menor o igual al rango de probabilidad se devuelve el valor verdadero
            sensorValue = true;
        } else if (randomNumber < rangeProbability + incorrectDataProbability) {//Si el valor es menor o igual al rango de probabilidad más la probabilidad de datos incorrecto se devuelve el valor falso
            sensorValue = false;
        } else {
            sensorValue = false;
        }

        return sensorValue;
    }

    /*
     * Descripción: Función que envia una alerta al sistema de calidad de la capa Edge
     */
    private void sendAlertToAspersor(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:4200"); //Se conecta al sistema de calidad
            aspersorSocket.send(message.getBytes(), 0); //Envio de la alerta
            System.out.println("Alerta de humo enviada al aspersor");
            messageCounter.incrementAndGet(); //Se suma el contador de mensajes enviados
        }
    }

    private void sendAlertToSC(String message) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket aspersorSocket = context.createSocket(SocketType.REQ);
            aspersorSocket.connect("tcp://localhost:9876");
            aspersorSocket.send(message.getBytes(), 0);
            System.out.println("ALERTA: Humo detecta humo");
            messageCounter.incrementAndGet();
        }
    }
}
