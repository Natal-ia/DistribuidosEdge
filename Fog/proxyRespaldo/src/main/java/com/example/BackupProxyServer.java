package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class BackupProxyServer {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket receiver = context.createSocket(SocketType.PULL);
            ZMQ.Socket sender = context.createSocket(SocketType.PUB);

            receiver.bind("tcp://*:4321");
            sender.bind("tcp://*:8765");

            System.out.println("Backup proxy server started and listening on tcp://*:4321");

            while (true) {
                byte[] message = receiver.recv(0);
                System.out.println("Received from sensor: " + new String(message, ZMQ.CHARSET));

                // Forward the message to the processing server or clients
                sender.send(message, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
