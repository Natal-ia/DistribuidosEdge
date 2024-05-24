package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * Descripción: Almacena y recibe todas las alertas de las otras capas además de que calcula la humedad promedio y envia alertas si el promedio esta por debajo del limite
 */
public class CloudServer {

    private static final int CALCULATION_INTERVAL = 20000; // 20 seconds
    private static final double minimo_humedad = 70.0;

    private static Map<String, List<Double>> dailyHumidityReadings = new HashMap<>();
    private static Map<String, List<Double>> monthlyHumidityReadings = new HashMap<>();

    //Función principal
    public static void main(String[] args) {
        AtomicInteger messageCounter = new AtomicInteger(0);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5678"); //Se abre el socket para recibir alertas y datos
            System.out.println("Cloud server started and listening on tcp://*:5678");

            long lastCalculationTime = System.currentTimeMillis();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = socket.recv(0); //Se recibe un mensaje
                String messageStr = new String(message, ZMQ.CHARSET);
                System.out.println("Received from proxy: " + messageStr);

                // Se procesa el mensaje
                processMessage(messageStr);

                socket.send("ACK".getBytes(ZMQ.CHARSET), 0); //Se ea una respuesta
                messageCounter.incrementAndGet(); //Se incrementa el contador de mensajes enviados

                // Se calcula la humedad mensual promedio cada 20 segundos
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCalculationTime >= CALCULATION_INTERVAL) {
                    calculateMonthlyHumidityAverage(messageCounter);
                    lastCalculationTime = currentTime;
                }
            }
        } finally { //Al finalizar la ejecución se imprimen todos los mensajes enviados
            System.out.println("Total de mensajes enviados por la Capa Cloud: " + messageCounter.get());
        }
    }

    /*
     * Descripción: Se procesa el mensaje recibido
     */
    private static void processMessage(String messageStr) {

        String[] parts = messageStr.split(",");//Se separa el mensaje por las comass
        if (parts.length == 3) {
            String sensorId = parts[0];
            String valueStr = parts[1];
            String timestamp = parts[2];

            System.out.println(sensorId + " " + valueStr + " " + timestamp);

            if (sensorId.contains("Humedad")) {//Si el mensaje es de la humedad se añade a la lista de humedad
                dailyHumidityReadings.computeIfAbsent(sensorId, k -> new ArrayList<>())
                        .add(Double.parseDouble(valueStr));

                monthlyHumidityReadings.computeIfAbsent(timestamp, k -> new ArrayList<>())
                        .add(Double.parseDouble(valueStr));
            }

            if (sensorId.contains("Alerta") || sensorId.contains("ALERTA")) { //Si el mensaje es una alerta esta se almacena
                storeAlert(messageStr);
            }
        }

    }

    /*
     * Descripción: Se calcula la humedad mensual promedio
     */
    private static void calculateMonthlyHumidityAverage(AtomicInteger messageCounter) {
        for (Map.Entry<String, List<Double>> entry : monthlyHumidityReadings.entrySet()) {//Se recorre todas las humedades guardadas y se les halla el promedio
            String timestamp = entry.getKey();
            List<Double> readings = entry.getValue();

            double sum = 0;
            for (double reading : readings) {
                sum += reading;
            }
            double monthlyAverage = sum / readings.size();
            System.out.println("Monthly average humidity for " + timestamp + ": " + monthlyAverage);

            if (monthlyAverage < minimo_humedad) {
                generateAlert(timestamp, monthlyAverage, messageCounter);
            }
        }
        // Se limpia todas las lecturas
        monthlyHumidityReadings.clear();
    }

    /*
     * Descripción: Se envia una alerta al sistema de calidad
     */
    private static void generateAlert(String timeStamp, double monthlyAverage, AtomicInteger messageCounter) {
        String alertMessage = "ALERTA: Humedad fuera de rango en el " + timeStamp + ": " + monthlyAverage;
        System.out.println(alertMessage);
        // Se almacena la alerta
        storeAlert("Alerta " + Instant.now().toString() + ". Valor: " + monthlyAverage);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:9876");//Se conecta al sistema de calidad

            socket.send(alertMessage.getBytes(ZMQ.CHARSET), 0); //Se envia la alerta
            messageCounter.incrementAndGet();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /*
     * Descripción: Se almacena la alerta recibida
     */
    private static void storeAlert(String alert) {
        File myObj = new File("alertas.txt");
        try {//Se abre el archivo de texto y se escribe la nueva alerta
            if (myObj.createNewFile()) {
                System.out.println("Archivo de alertas creado: " + myObj.getName());
            }
            // Se escribe la alerta
            try (FileWriter myWriter = new FileWriter(myObj, true)) {
                myWriter.write(alert);
                myWriter.write(System.lineSeparator()); // Se añade una nueva linea tras cada alerta
                System.out.println("Alerta guardada: " + alert);
            }
        } catch (IOException e) {
            System.err.println("Error writing alert to file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}