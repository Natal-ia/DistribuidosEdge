package com.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/*
 * Descripción: Clase que se encarga de recibir las alertas de que la temperatura supero el rango maximo establecido
 */
public class SistemaCalidadFog {
    public static void main(String[] args) {
        AtomicInteger alertCounter = new AtomicInteger(0);
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://localhost:9876"); //Se crea el socket para recibir alertas
            System.out.println("El sistema de calidad de la capa Fog se ha inicializado correctamente y esta a la espera de alertas");
            while (!Thread.currentThread().isInterrupted()) {
                // Espera una solicitud
                byte[] solicitudBytes = socket.recv();//Se  espera a recibir una lerta
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);
                System.out.println("Alerta de calidad en capa Fog: " + alerta);
                alertCounter.incrementAndGet(); //Se imcrementa el contador de alertas
            }
            //Una vez terminada la ejecución se imprime el total de alertas
            System.out.println("Alertas en la capa Fog: " + alertCounter);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}