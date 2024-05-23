package com.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class SistemaCalidadEdge {
    public static void main(String[] args) {
        AtomicInteger alertCounter_T = new AtomicInteger(0); //Temperatura
        AtomicInteger alertCounter_S = new AtomicInteger(0); //Humo
        AtomicInteger alertCounter_H = new AtomicInteger(0); //Humedad
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://localhost:9876");
            while (!Thread.currentThread().isInterrupted()) {
                // Espera una solicitud
                byte[] solicitudBytes = socket.recv();
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);
                System.out.println("Alerta de calidad en capa Cloud: " + alerta);
                String[] partes = alerta.split(" ");
                String t = partes[1].trim();
                String s = partes[3].trim();

                if(t.equals("Temperatura")){
                    alertCounter_T.incrementAndGet();
                }else{
                    if(s.equals("humo")){
                        alertCounter_S.incrementAndGet();
                    }else{
                        alertCounter_H.incrementAndGet();
                    }
                }
            }
            int total = alertCounter_H.get() + alertCounter_S.get() + alertCounter_T();
            System.out.println("Número total de alertas del sensor de temperatura: " + alertCounter_T.get());
            System.out.println("Número total de alertas del sensor de humo: " + alertCounter_S.get());
            System.out.println("Número total de alertas del sensor de humedad: " + alertCounter_H.get());
            System.out.println("Total de alertas recibidas en la capa Edge: " + total);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}