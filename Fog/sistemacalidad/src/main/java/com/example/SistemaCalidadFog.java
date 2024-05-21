package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SistemaCalidadFog {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://localhost:9876");
            while (!Thread.currentThread().isInterrupted()) {
                // Espera una solicitud
                byte[] solicitudBytes = socket.recv();
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);
                System.out.println("Alerta de calidad en capa Cloud: " + alerta);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}