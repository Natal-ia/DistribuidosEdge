import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

public class SistemaCalidadEdge{
    public static void main(String[] args) {

        boolean activo = false;

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:1234");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] solicitudBytes = socket.recv();
                String alerta = new String(solicitudBytes, ZMQ.CHARSET);

                if(activo == true){
                    System.out.println("Alerta de calidad en capa Edge: " + alerta);

                    String respuesta = "Alerta recibida";
                    socket.send(respuesta.getBytes(ZMQ.CHARSET), 0);
                }else{
                    String[] texto = alerta.split(" ");
                    if(texto[3].equals("humo")){
                        activo = true;
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}