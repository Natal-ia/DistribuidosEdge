package com.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SistemaCalidadFog {
    public static void main(String[] args) {
        AtomicInteger alertCounter = new AtomicInteger(0);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://localhost:9876");
            while (!Thread.currentThread().isInterrupted()) {
                // Espera una solicitud
                byte[] solicitudBytes = socket.recv();
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);
                System.out.println("Alerta de calidad en capa Fog: " + alerta);
                alertCounter.incrementAndGet();
            }

            System.out.println("Alertas en la capa Fog: " + alertCounter);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}