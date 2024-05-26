package com.example;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
 * Descripción: Clase que se encarga de verificar que el proxy sigue en funcionamiento mandandole y recibiendo mensajes. 
 * Si este determina que el proxy dejo de funcionar cambiara al servidor de respaldo y alertara a los demás sensores para que hagan lo mismo
 */
public class HealthCheck implements Runnable {

    private int estado = 0; // 0 for proxy, 1 for backup
    private final AtomicReference<String> proxyAddress;
    private static final String proxy = "tcp://10.43.100.230:1235";
    private static final String respaldo = "tcp://10.43.100.191:4321";

    private AtomicInteger messageCounter;

    //Constructor de la clase
    public HealthCheck(AtomicReference<String> proxyAddress, AtomicInteger messageCounter) {
        this.proxyAddress = proxyAddress;
        this.messageCounter = messageCounter;
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext()) {
            try (ZMQ.Socket socket = context.createSocket(SocketType.REQ)) {
                socket.connect("tcp://10.43.100.230:5555"); //Se conecta al proxy princiapl
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000); //Se espera 1 segundo
                    String request = "OK";
                    socket.send(request.getBytes(ZMQ.CHARSET), 0); //Se envia un mensaje al proxy
                    messageCounter.incrementAndGet();

                    socket.setReceiveTimeOut(4000); //Se espera la respuesta durante 4 segundos

                    // Se recibe la respuesta
                    byte[] reply = socket.recv();

                    if (reply != null) {// Si se recibe una respuesta dentro del timeout
                        System.out.println("Received response: " + new String(reply));
                    } else {//Si no se recibe una respuesta dentro del timeout
                        handleProxyFailure(socket);
                    }

                }
                System.out.println("Mensajes enviados por el Health Check: " + messageCounter.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("Health check thread interrupted.... changing proxy server.");
        }finally { //Al finalizar se imprime el numero de mensajes enviados
            System.out.println("Mensajes enviados por el Health Check: " + messageCounter.get());
        }
    }

    // Descripción: Dependiendo del estado se desconecta al servidor proxy al que estaba conectado y se conecta a la alternativa
    private void handleProxyFailure(ZMQ.Socket socket) {
        socket.disconnect(proxyAddress.get()); //Se desconecta del servidor actual
        if (estado == 0) { //Si el estado es 0 significa que esta conectado actualmente al proxy principal
            proxyAddress.set(respaldo);
            System.out.println("Proxy down. Switching to backup proxy.");
            estado = 1;
        } else { //Si el estado es 1 significa que esta conectado actualmente al proxy de respaldo
            proxyAddress.set(proxy);
            System.out.println("Backup proxy down. Switching to primary proxy.");
            estado = 0; //Se cambia el estado
        }
        socket.connect(proxyAddress.get()); //Se conecta al otro servidor 
    }
}
