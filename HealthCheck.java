import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class HealthCheck {

    static final String proxy = "tcp://localhost:1234";
    static final String respaldo = "tcp://otrohost:1234";

    public static void main(String[] args) {
        int estado = 0; //0 para el proxy y 1 para el respaldo
        int segundosEspera = 10;
        
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.PUSH);
            socket.bind(proxy);

            while (!Thread.currentThread().isInterrupted()) {
                String requestMessage = "Health check";
                socket.send(requestMessage.getBytes(), 0);
                
                if(socket.poll(segundosEspera*1000) != -1){
                    byte[] reply = socket.recv(0);
                    String respuesta = new String(reply, ZMQ.CHARSET);
                    String[] texto = respuesta.split("");
                    /*if(texto[0].equals("OK")){

                    }else{
                        if(estado == 0){
                            socket.bind(respaldo);
                            System.out.println("Tiempo de espera agotado. No se recibió respuesta del proxy. Cambiando al proxy de respaldo");
                        }else{
                            socket.bind(proxy);
                            System.out.println("Tiempo de espera agotado. No se recibió respuesta del respaldo. Cambiando al proxy principal");
                        }
                    }*/
                }else{
                    ZMQ.Socket socket2 = context.createSocket(SocketType.PUSH);
                    if(estado == 0){
                        socket.bind(respaldo);
                        System.out.println("Tiempo de espera agotado. No se recibió respuesta del proxy. Cambiando al proxy de respaldo");
                        String mensaje2 = "1";
                        //Colocar aqui el envio de un mensaje a los sensores para realizar el cambio
                        estado = 1;
                    }else{
                        socket.bind(proxy);
                        System.out.println("Tiempo de espera agotado. No se recibió respuesta del respaldo. Cambiando al proxy principal");
                        String mensaje2 = "0";
                        //Colocar aqui el envio de un mensaje a los sensores para realizar el cambio
                        estado = 0;
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
