package com.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/*
 * Descripción: Clase que se encarga de recibir las alertas de que la humedad promedio esta por debajo del limite
 */
public class SistemaCalidadCloud {
    public static void main(String[] args) {
        AtomicInteger alertCounter = new AtomicInteger(0);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://localhost:9876"); //Se establece el socket para recibir las alertas
            System.out.println("El sistema de calidad de la capa Cloud se ha inicializado correctamente y esta a la espera de alertas");
            while (!Thread.currentThread().isInterrupted()) {
                // Espera una solicitud
                byte[] solicitudBytes = socket.recv(); //Se recibe el mensaje
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);
                System.out.println("Alerta de calidad en capa Cloud: " + alerta);
                alertCounter.incrementAndGet(); //Se incrementa el contador de alertas recibidas
            }//Al finalizar la ejecución se imprimen las alertas recibidas
            System.out.println("Alertas recibidas en la capa Cloud: " + alertCounter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



