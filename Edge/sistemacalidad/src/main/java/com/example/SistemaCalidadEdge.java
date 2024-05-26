package com.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/*
 * Descripción: Clase que se encarga de recibir las alertas de los sensores y este va guardando la cuenta de las alertas recibidas
 */
public class SistemaCalidadEdge {
    public static void main(String[] args) {
        AtomicInteger alertCounter_T = new AtomicInteger(0); // Temperatura
        AtomicInteger alertCounter_S = new AtomicInteger(0); // Humo
        AtomicInteger alertCounter_H = new AtomicInteger(0); // Humedad
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://localhost:9876"); //Se crea el socket para poder recibir las alertas
            System.out.println("El sistema de calidad de la capa Edge se ha inicializado correctamente y esta a la espera de alertas");
            while (!Thread.currentThread().isInterrupted()) {
                // Espera una solicitud
                byte[] solicitudBytes = socket.recv(); //Se recibe una alerta
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);
                System.out.println("Alerta de calidad en capa Edge: " + alerta);
                String[] partes = alerta.split(" "); //Se parte la alerta para saber de donde provino

                String t = partes[1].trim();
                String s = partes[3].trim();

                if (t.equals("Temperatura")) { //Dependiendo del contenido del mensaje se sabe cual tipo de alerta es
                    alertCounter_T.incrementAndGet();
                } else {
                    if (s.equals("humo")) {
                        alertCounter_S.incrementAndGet();
                    } else {
                        alertCounter_H.incrementAndGet();
                    }
                }
            }
            //Al finalizar la ejecución se imprime los resultados de las alertas recibidas
            int total = alertCounter_H.get() + alertCounter_S.get() + alertCounter_T.get();
            System.out.println("Número total de alertas del sensor de temperatura: " + alertCounter_T.get());
            System.out.println("Número total de alertas del sensor de humo: " + alertCounter_S.get());
            System.out.println("Número total de alertas del sensor de humedad: " + alertCounter_H.get());
            System.out.println("Total de alertas recibidas en la capa Edge: " + total);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}