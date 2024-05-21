package com.example;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class Aspersor {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            // Socket to talk to clients
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.bind("tcp://localhost:4200");
            System.out.println("Aspersor started and listening on tcp://localhost:4200");

            while (!Thread.currentThread().isInterrupted()) {
                // Block until a message is received
                byte[] reply = socket.recv(0);

                // Print the message
                System.out.println(
                    "ALARMA DE HUMO ACTIVADA: [" + new String(reply, ZMQ.CHARSET) + "]"
                );

            }
        }
        /*
        // Create a ZeroMQ context
        try (ZContext context = new ZContext()) {
            // Create a REP socket for receiving messages from sensors
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            
            // Bind to the server address
            String serverAddress = "tcp://*:1234"; // Modify this if necessary
            socket.bind(serverAddress);
            System.out.println("Aspersor esperando alarmas de humo: " + serverAddress);

            // Main loop to receive alerts
            while (!Thread.currentThread().isInterrupted()) {
                // Receive message from the Humo sensor
                byte[] data = socket.recv(0);
                String message = new String(data, ZMQ.CHARSET);
                System.out.println("--------------------------------------");
                System.out.println("Alerta de humo recibida: " + message);
                System.out.println("ASPERSOR ACTIVADO");
                System.out.println("--------------------------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
         */
    }
}
