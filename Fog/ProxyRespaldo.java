import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;


public class ProxyRespaldo {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);

            // Bind to the server address
            socket.bind("tcp://*:1234");
            System.out.println("Servidor proxy de respaldo inicializado en tcp://*:1234");

            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = socket.recv(0);
                String respuesta = new String(message, ZMQ.CHARSET);
                String[] texto = respuesta.split("");
                if(texto[0].equals("Health")){
                    String requestMessage = "OK";
                    socket.send(requestMessage.getBytes(), 0);
                }

                System.out.println("Received from sensor: " + respuesta);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
