package bcp.rede;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import bcp.seguranca.SerializationFilter;

// responsável por iniciar conexões tcp com outros nós da rede
public final class BcpClient {

    // tempo máximo de espera para conseguir se conectar a outro nó
    private static final int CONNECT_TIMEOUT_MS = 4_000;

    // tempo máximo de espera para receber uma resposta
    private static final int READ_TIMEOUT_MS = 10_000;

    // endereço do próprio nó, enviado durante a identificação na conexão
    private final PeerAddress localAddress;

    // recebe o endereço do nó que está criando o cliente
    public BcpClient(PeerAddress localAddress) {
        this.localAddress = localAddress;
    }

    // conecta a outro nó, envia uma solicitação e aguarda uma resposta
    public NetworkMessage request(PeerAddress peer, NetworkMessage request) throws IOException {
        return communicate(peer, request, true);
    }

    // conecta a outro nó e envia uma mensagem sem esperar uma resposta da aplicação
    public void sendOneWay(PeerAddress peer, NetworkMessage message) throws IOException {
        communicate(peer, message, false);
    }

    // realiza a comunicação tcp entre os dois nós
    private NetworkMessage communicate(PeerAddress peer, NetworkMessage message,
            boolean expectsResponse) throws IOException {

        // cria a conexão e garante que o socket será fechado ao terminar
        try (Socket socket = new Socket()) {

            // tenta estabelecer a conexão com o endereço do outro nó
            socket.connect(new InetSocketAddress(peer.getHost(), peer.getPort()), CONNECT_TIMEOUT_MS);

            // define o tempo máximo de espera por dados recebidos
            socket.setSoTimeout(READ_TIMEOUT_MS);

            // permite o envio imediato dos dados pela conexão
            socket.setTcpNoDelay(true);

            // cria o fluxo de saída para enviar objetos pela rede
            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                out.flush();

                // cria o fluxo de entrada para receber objetos enviados pelo outro nó
                try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    // limita quais classes podem ser recebidas durante a desserialização
                    in.setObjectInputFilter(SerializationFilter.createBcpFilter());

                    // envia uma mensagem HELLO para se identificar ao outro participante
                    writeMessage(out, NetworkMessage.hello(localAddress));

                    // aguarda a identificação de resposta do outro nó
                    NetworkMessage helloResponse = readMessage(in);

                    // interrompe a comunicação caso o outro nó rejeite a identificação
                    if (helloResponse.getType() == MessageType.REJECT) {
                        throw new IOException("Handshake rejeitado pelo peer: " + helloResponse.getPayload());
                    }

                    // verifica se a resposta recebida confirma a identificação do outro nó
                    if (helloResponse.getType() != MessageType.HELLO
                            || NetworkMessage.parseHello(helloResponse.getPayload()) == null) {
                        throw new IOException("Resposta inesperada durante handshake: "
                                + helloResponse.getType());
                    }

                    // depois da identificação, envia a mensagem solicitada
                    writeMessage(out, message);

                    // encerra o envio sem aguardar uma resposta da aplicação
                    if (!expectsResponse) {
                        return null;
                    }

                    // aguarda e retorna a resposta enviada pelo outro nó
                    return readMessage(in);
                }

            } catch (ClassNotFoundException e) {

                // informa quando o objeto recebido utiliza uma classe desconhecida
                throw new IOException("O peer enviou uma classe não reconhecida.", e);
            }
        }
    }

    // envia uma mensagem pelo fluxo de saída
    private static void writeMessage(ObjectOutputStream out, NetworkMessage message) throws IOException {
        out.writeObject(message); // converte a mensagem em bytes e envia pela conexão
        out.flush(); // garante que os dados pendentes sejam enviados
    }

    // recebe uma mensagem pelo fluxo de entrada e verifica seu tipo
    private static NetworkMessage readMessage(ObjectInputStream in)
            throws IOException, ClassNotFoundException {

        Object received;

        try {
            // lê o objeto enviado pelo outro nó
            received = in.readObject();

        } catch (EOFException e) {

            // informa quando a conexão é encerrada antes da resposta chegar
            throw new IOException("O peer encerrou a conexão antes de responder.", e);
        }

        // verifica se o objeto recebido possui o formato esperado pelo protocolo
        if (!(received instanceof NetworkMessage)) {
            throw new IOException("Objeto recebido não é uma NetworkMessage.");
        }

        // converte o objeto recebido para o formato de mensagem utilizado pelo bcp
        return (NetworkMessage) received;
    }
}