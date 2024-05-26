package com.example;

import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/*
 * Descripción: Clase que se encarga de recibir los mensajes del healthcheack
 */
public class HealthCheckResponder implements Runnable {
    private final ZContext context;
    private final String bindAddress;

    private AtomicInteger messageCounter;

    //Constructor de la clase
    public HealthCheckResponder(ZContext context, String bindAddress, AtomicInteger messageCounter) {
        this.context = context;
        this.bindAddress = bindAddress;
        this.messageCounter = messageCounter;
    }

    //Función principal que se ejecutara de forma asincronica
    @Override
    public void run() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555"); //Se crea el socket para escuchar mensajes
            while (!Thread.currentThread().isInterrupted()) {
                byte[] reply = socket.recv(); //Se recibe el mensaje


                String response = "OK"; //Se envia una respuesta
                socket.send(response.getBytes(ZMQ.CHARSET), 0);
                System.out.println("response");
                messageCounter.incrementAndGet(); //Se incrementa el contador de mensajes enviados
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally { //Una vez finalizada la ejecución se imprime la cantidad de mensajes enviados
            System.out.println("Mensajes enviados por HealthCheckResponder: " + messageCounter.get());
        }
    }

}