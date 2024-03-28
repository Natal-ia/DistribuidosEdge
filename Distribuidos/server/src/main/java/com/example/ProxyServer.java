package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class ProxyServer {
    public static void main(String[] args) {
        // Create a ZeroMQ context
        try (ZContext context = new ZContext()) {
            // Create a socket for communication
            ZMQ.Socket socket = context.createSocket(SocketType.PULL);

            // Bind to the server address
            socket.bind("tcp://*:1234");
            System.out.println("Server (proxy) started and listening on tcp://*:1234");

            while (true) {
                // Receive a message from a sensor
                byte[] message = socket.recv(0);
                System.out.println("Received from sensor: " + new String(message, ZMQ.CHARSET));

                // Process the received message (if needed)

                // Optionally, send a response back to the sensor

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
