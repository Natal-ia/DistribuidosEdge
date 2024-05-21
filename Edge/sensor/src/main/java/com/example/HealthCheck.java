package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;

import java.util.concurrent.atomic.AtomicReference;

public class HealthCheck implements Runnable {

    private final AtomicReference<String> proxyAddress;
    private static final String proxy = "tcp://10.43.100.230:1234";
    private static final String respaldo = "tcp://10.43.100.191:4321";

    public HealthCheck(AtomicReference<String> proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    @Override
    public void run() {
        int estado = 0; // 0 for proxy, 1 for backup
        int segundosEspera = 10;

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            Poller poller = context.createPoller(1);
            poller.register(socket, Poller.POLLIN);

            while (!Thread.currentThread().isInterrupted()) {
                socket.connect(proxyAddress.get());
                String requestMessage = "Health check";
                socket.send(requestMessage.getBytes(), 0);

                if (poller.poll(segundosEspera * 1000) > 0 && poller.pollin(0)) {
                    byte[] reply = socket.recv(0);
                    String respuesta = new String(reply, ZMQ.CHARSET);
                    if (respuesta.equals("OK")) {
                        socket.disconnect(proxyAddress.get());
                    } else {
                        handleProxyFailure(socket, estado);
                    }
                } else {
                    handleProxyFailure(socket, estado);
                }

                Thread.sleep(segundosEspera * 1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    private void handleProxyFailure(ZMQ.Socket socket, int estado) {
        if (estado == 0) {
            proxyAddress.set(respaldo);
            System.out.println("Proxy down. Switching to backup proxy.");
            estado = 1;
        } else {
            proxyAddress.set(proxy);
            System.out.println("Backup proxy down. Switching to primary proxy.");
            estado = 0;
        }
        socket.disconnect(proxyAddress.get());
        socket.connect(proxyAddress.get());
    }
}
