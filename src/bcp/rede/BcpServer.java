package bcp.rede;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bcp.no.BcpNode;
import bcp.seguranca.SerializationFilter;

// responsável por receber conexões de outros nós e encaminhar as mensagens para o BcpNode
public final class BcpServer implements AutoCloseable {

    // quantidade máxima de conexões que podem ser processadas simultaneamente
    private static final int MAX_CLIENT_HANDLERS = 32;

    // nó responsável por processar as mensagens recebidas
    private final BcpNode node;

    // executa o atendimento das conexões em threads separadas
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_CLIENT_HANDLERS);

    // indica se o servidor está em funcionamento
    private volatile boolean running;

    // socket responsável por receber novas conexões
    private ServerSocket serverSocket;

    // thread responsável por aceitar as conexões recebidas
    private Thread acceptThread;

    // recebe o nó que será associado ao servidor
    public BcpServer(BcpNode node) {
        this.node = node;
    }

    // inicia o servidor e começa a aceitar conexões
    public synchronized void start() throws IOException {

        // evita iniciar o servidor mais de uma vez
        if (running) return;

        // cria o socket responsável por receber conexões
        serverSocket = new ServerSocket();

        // permite reutilizar a porta após o encerramento do servidor
        serverSocket.setReuseAddress(true);

        // associa o servidor ao endereço e à porta configurados no nó
        serverSocket.bind(new java.net.InetSocketAddress(node.getBindHost(), node.getPort()));

        // indica que o servidor está ativo
        running = true;

        // cria a thread responsável por aceitar novas conexões
        acceptThread = new Thread(this::acceptLoop, "bcp-accept-" + node.getPort());

        // permite que a thread seja encerrada automaticamente com o programa
        acceptThread.setDaemon(true);

        // inicia o recebimento de conexões
        acceptThread.start();

        System.out.println("[bcp] Servidor ouvindo em " + node.getBindHost() + ":" + node.getPort());
    }

    // aguarda e distribui as conexões recebidas pelo servidor
    private void acceptLoop() {
        while (running) {
            try {

                // aceita uma nova conexão de outro nó
                Socket socket = serverSocket.accept();

                // define o tempo máximo de espera por dados recebidos
                socket.setSoTimeout(30_000);

                // permite o envio imediato dos dados pela conexão
                socket.setTcpNoDelay(true);

                // envia o atendimento da conexão para uma thread disponível
                workers.submit(() -> handleClient(socket));

            } catch (IOException e) {

                // registra erros de conexão enquanto o servidor estiver ativo
                if (running) {
                    System.err.println("[bcp] Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    // realiza a comunicação com um cliente conectado
    private void handleClient(Socket socket) {

        // obtém o endereço remoto para identificar a conexão nos registros
        String remote = String.valueOf(socket.getRemoteSocketAddress());

        try (Socket clientSocket = socket;
             ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

            // envia o cabeçalho do fluxo antes de criar o fluxo de entrada
            out.flush();

            // cria o fluxo responsável por receber objetos do cliente
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

                // limita quais classes podem ser recebidas durante a desserialização
                in.setObjectInputFilter(SerializationFilter.createBcpFilter());

                // indica se a identificação inicial entre os nós foi concluída
                boolean handshakeCompleted = false;

                // continua recebendo mensagens enquanto a conexão estiver ativa
                while (running && !clientSocket.isClosed()) {
                    Object raw;

                    try {

                        // lê o próximo objeto enviado pelo cliente
                        raw = in.readObject();

                    } catch (EOFException e) {

                        // encerra o atendimento quando o cliente fecha a conexão
                        break;
                    }

                    // verifica se o objeto recebido possui o formato esperado
                    if (!(raw instanceof NetworkMessage)) {

                        // rejeita objetos que não pertencem ao protocolo bcp
                        send(out, new NetworkMessage(MessageType.REJECT,
                                "O objeto recebido não é uma NetworkMessage."));
                        break;
                    }

                    // converte o objeto recebido para o formato de mensagem da rede
                    NetworkMessage incoming = (NetworkMessage) raw;

                    // exige que a primeira mensagem da conexão seja uma identificação
                    if (!handshakeCompleted && incoming.getType() != MessageType.HELLO) {
                        send(out, new NetworkMessage(MessageType.REJECT,
                                "A primeira mensagem da sessão precisa ser HELLO."));
                        break;
                    }

                    // encaminha a mensagem recebida para o nó processar
                    NetworkMessage response = node.handleIncoming(
                            incoming, clientSocket.getInetAddress().getHostAddress());

                    // envia uma resposta quando o processamento retornar uma mensagem
                    if (response != null) {
                        send(out, response);
                    }

                    // verifica se a identificação inicial foi concluída corretamente
                    if (!handshakeCompleted) {

                        // encerra a conexão caso a resposta de identificação seja inválida
                        if (response == null || response.getType() != MessageType.HELLO) {
                            break;
                        }

                        // libera o recebimento das próximas mensagens da sessão
                        handshakeCompleted = true;
                    }
                }

            } catch (ClassNotFoundException e) {

                // registra quando o cliente envia uma classe desconhecida
                System.err.println("[bcp] Classe desconhecida recebida de " + remote + ": " + e.getMessage());
            }

        } catch (IOException e) {

            // registra desconexões sem interromper o funcionamento do servidor
            System.err.println("[bcp] Sessão encerrada com " + remote + ": " + e.getMessage());
        }
    }

    // envia uma mensagem pelo fluxo de saída
    private static void send(ObjectOutputStream out, NetworkMessage message) throws IOException {

        // envia o objeto para o cliente
        out.writeObject(message);

        // garante que os dados pendentes sejam transmitidos
        out.flush();
    }

    // encerra o servidor e libera os recursos utilizados
    @Override
    public synchronized void close() {

        // indica que o servidor não deve mais aceitar conexões
        running = false;

        // verifica se o socket do servidor foi criado
        if (serverSocket != null) {
            try {

                // fecha o socket e interrompe a espera por novas conexões
                serverSocket.close();

            } catch (IOException ignored) {

                // ignora erros durante o encerramento do socket
            }
        }

        // interrompe as tarefas de atendimento das conexões
        workers.shutdownNow();

        System.out.println("[bcp] Servidor da porta " + node.getPort() + " encerrado.");
    }
}