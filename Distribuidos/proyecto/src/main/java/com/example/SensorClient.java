package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SensorClient {
    public static void main(String[] args) {
        // Create a ZeroMQ context
        try (ZContext context = new ZContext()) {
            
            // Create a PUSH socket for sending messages to the server
            ZMQ.Socket socket = context.createSocket(SocketType.PUSH);

            // Connect to the server (proxy) address
            String serverAddress = "tcp://localhost:1234"; // Modify this if necessary
            socket.connect(serverAddress);
            System.out.println("Connected to server: " + serverAddress);
            

            // Create and start sensors
            for (int i = 0; i < 3; i++) { 
                String sensorType = "Sensor" + i;
                String config = "";
                if(i == 0){
                    sensorType = "Humo";
                    config = "src/main/resources/humoConfig.txt";
                } else if(i == 1){
                    sensorType = "Humedad";
                    config = "src/main/resources/humedadConfig.txt";
                } else if(i == 2){
                    sensorType = "Temperatura";
                    config = "src/main/resources/temperaturaConfig.txt";
                }
 
                for (int j = 0; j < 10; j++) { 
                    Sensor sensor = new Sensor(sensorType, serverAddress, j, socket, config);
                    System.out.println("Starting sensor " + sensorType);
                    Thread sensorThread = new Thread(sensor);
                    sensorThread.start();
                }
            }

            // Let the main thread sleep to keep the program running
            Thread.sleep(Long.MAX_VALUE);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

