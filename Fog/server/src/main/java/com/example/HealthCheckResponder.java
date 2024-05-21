package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class HealthCheckResponder implements Runnable {
    private final ZContext context;
    private final String bindAddress;

    public HealthCheckResponder(ZContext context, String bindAddress) {
        this.context = context;
        this.bindAddress = bindAddress;
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555");
            while (!Thread.currentThread().isInterrupted()) {
                byte[] reply = socket.recv();
                // Do something here.

                String response = "OK";
                socket.send(response.getBytes(ZMQ.CHARSET), 0);
                System.out.println("response");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}