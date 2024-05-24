package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HealthCheck implements Runnable {

    private int estado = 0; // 0 for proxy, 1 for backup
    private final AtomicReference<String> proxyAddress;
    private static final String proxy = "tcp://10.43.100.230:1235";
    private static final String respaldo = "tcp://10.43.100.191:4321";

    private AtomicInteger messageCounter;

    public HealthCheck(AtomicReference<String> proxyAddress, AtomicInteger messageCounter) {
        this.proxyAddress = proxyAddress;
        this.messageCounter = messageCounter;
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext()) {
            try (ZMQ.Socket socket = context.createSocket(SocketType.REQ)) {
                socket.connect("tcp://10.43.100.230:5555");
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                    String request = "OK";
                    socket.send(request.getBytes(ZMQ.CHARSET), 0);
                    messageCounter.incrementAndGet();

                    socket.setReceiveTimeOut(4000);

                    // Receive the reply with a timeout
                    byte[] reply = socket.recv();

                    if (reply != null) {
                        // If there's a reply within the timeout
                        System.out.println("Received response: " + new String(reply));
                    } else {
                        // If no reply received within the timeout
                        handleProxyFailure(socket);
                    }

                }
                System.out.println("Mensajes enviados por el Health Check: " + messageCounter.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
        } catch (Exception e) {
            System.out.println("Health check thread interrupted.... changing proxy server.");
        }finally {
            System.out.println("Mensajes enviados por el Health Check: " + messageCounter.get());
        }
    }

    private void handleProxyFailure(ZMQ.Socket socket) {
        socket.disconnect(proxyAddress.get());
        if (estado == 0) {
            proxyAddress.set(respaldo);
            System.out.println("Proxy down. Switching to backup proxy.");
            estado = 1;
        } else {
            proxyAddress.set(proxy);
            System.out.println("Backup proxy down. Switching to primary proxy.");
            estado = 0;
        }
        socket.connect(proxyAddress.get());
    }
}
