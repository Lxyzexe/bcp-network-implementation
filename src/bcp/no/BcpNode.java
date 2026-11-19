package bcp.no;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bcp.modelo.Block;
import bcp.modelo.Blockchain;
import bcp.rede.BcpClient;
import bcp.rede.BcpServer;
import bcp.rede.MessageType;
import bcp.rede.NetworkMessage;
import bcp.rede.PeerAddress;

// representa um nó da rede que pode receber conexões e se conectar a outros nós
public final class BcpNode implements AutoCloseable {

    // endereço local utilizado pelo servidor para receber conexões
    private final String bindHost;

    // porta utilizada pelo nó para comunicação
    private final int port;

    // endereço que será divulgado para os outros participantes da rede
    private final PeerAddress localAddress;

    // conjunto de peers conhecidos, evitando endereços duplicados
    private final Set<PeerAddress> peers = ConcurrentHashMap.newKeySet();

    // blockchain armazenada localmente, podendo ser acessada por diferentes threads
    private volatile Blockchain blockchain = new Blockchain();

    // servidor responsável por receber conexões de outros nós
    private final BcpServer server;

    // construtor responsável por configurar o endereço e a porta do nó
    public BcpNode(String bindHost, String advertisedHost, int port) {

        // impede que o endereço utilizado pelo servidor seja vazio
        if (bindHost == null || bindHost.trim().isEmpty()) {
            throw new IllegalArgumentException("bindHost não pode ser vazio.");
        }

        this.bindHost = bindHost;
        this.port = port;

        // cria o endereço que será informado aos outros participantes
        this.localAddress = new PeerAddress(advertisedHost, port);

        // associa o servidor ao nó atual
        this.server = new BcpServer(this);
    }

    // inicia o servidor para começar a receber conexões
    public void start() throws IOException {
        server.start();
    }

    // retorna o endereço utilizado pelo servidor para receber conexões
    public String getBindHost() {
        return bindHost;
    }

    // retorna a porta utilizada pelo nó
    public int getPort() {
        return port;
    }

    // retorna o endereço anunciado aos outros nós
    public PeerAddress getLocalAddress() {
        return localAddress;
    }

    // retorna uma cópia da blockchain para evitar alterações externas na cadeia original
    public synchronized Blockchain getBlockchainCopy() {
        return blockchain.copy();
    }

    // retorna a lista de peers conhecidos pelo nó
    public List<PeerAddress> getPeers() {

        // cria uma cópia da lista atual de participantes
        ArrayList<PeerAddress> result = new ArrayList<>(peers);

        // organiza os endereços em ordem alfabética para facilitar a visualização
        Collections.sort(result, (a, b) -> a.toString().compareToIgnoreCase(b.toString()));

        // impede que a lista retornada seja modificada externamente
        return Collections.unmodifiableList(result);
    }

    // adiciona um novo peer à lista de participantes conhecidos
    public void addPeer(PeerAddress peer) {

        // ignora endereços nulos e o próprio endereço do nó
        if (peer == null || peer.equals(localAddress)) return;

        peers.add(peer);
    }

    // converte a blockchain no payload definido pela especificação: ArrayList<Block>
    private static ArrayList<Block> toPayload(Blockchain chain) {
        return new ArrayList<>(chain.getBlocks());
    }

    // converte o payload recebido (ArrayList<Block>) em uma blockchain, ou null se for inválido
    private static Blockchain toChain(Object payload) {
        if (!(payload instanceof ArrayList<?> list)) return null;

        ArrayList<Block> blocks = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Block block)) return null;
            blocks.add(block);
        }
        return Blockchain.fromBlocks(blocks);
    }

    // recebe e processa as mensagens enviadas por outros nós
    // retorna uma mensagem de resposta quando necessário
    public NetworkMessage handleIncoming(NetworkMessage message, String remoteIp) {

        // verifica se a mensagem recebida possui um tipo válido
        if (message == null || message.getType() == null) {
            return reject("Mensagem nula ou sem tipo.");
        }

        // identifica o tipo da mensagem para executar a operação correspondente
        switch (message.getType()) {

            case HELLO: {

                // o payload é uma String "V1.0-host:porta" com versão do protocolo e endereço do nó
                PeerAddress announced = NetworkMessage.parseHello(message.getPayload());
                if (announced == null) {
                    return reject("HELLO inválido ou versão incompatível (esperado "
                            + NetworkMessage.VERSION + "-host:porta).");
                }

                // adiciona o participante à lista de peers conhecidos
                addPeer(announced);

                System.out.println("[bcp] HELLO recebido de " + announced
                        + " (conexão originada de " + remoteIp + ")");

                // responde ao outro nó confirmando sua própria identificação
                return NetworkMessage.hello(localAddress);
            }

            case GET_PEERS: {

                // envia a lista "IP:Porta" dos participantes conhecidos
                ArrayList<String> list = new ArrayList<>();
                for (PeerAddress p : getPeers()) list.add(p.toString());
                return new NetworkMessage(MessageType.PEERS_LIST, list);
            }

            case GET_BLOCKCHAIN:

                // envia uma cópia da blockchain local para o outro nó
                return new NetworkMessage(MessageType.BLOCKCHAIN_COPY,
                        toPayload(getBlockchainCopy()));

            case BLOCKCHAIN_COPY: {

                // verifica se o conteúdo recebido realmente é uma lista de blocos
                Blockchain candidate = toChain(message.getPayload());
                if (candidate == null) {
                    return reject("BLOCKCHAIN_COPY precisa transportar ArrayList<Block>.");
                }

                // verifica a integridade (hashes, PoW e encadeamento) antes de aceitar
                if (!candidate.isValid()) {
                    return reject("Cadeia inválida");
                }

                boolean updated = false;

                // sincroniza o acesso à blockchain para evitar alterações simultâneas
                synchronized (this) {

                    // somente substitui a cadeia local se a recebida for mais longa
                    if (candidate.size() > blockchain.size()) {
                        blockchain = candidate;
                        updated = true;

                        System.out.println("[bcp] Blockchain atualizada. Altura local: "
                                + blockchain.getHeight());
                    }
                }

                // cadeia válida, mas não maior que a local: descarta e avisa
                if (!updated) {
                    return reject("Tamanho menor ou igual ao da cadeia local");
                }

                // propaga a cadeia nova para os demais peers (termina sozinho: só aceita cadeias maiores)
                new Thread(this::broadcastBlockchain, "bcp-relay").start();

                // não envia confirmação quando aceita uma cópia
                return null;
            }

            case REJECT:

                // informa que o outro nó rejeitou alguma operação ou mensagem
                System.err.println("[bcp] Peer reportou REJECT: " + message.getPayload());
                return null;

            case PEERS_LIST:
            default:

                // rejeita mensagens que não deveriam ser recebidas nesse contexto
                return reject("Tipo de mensagem não esperado neste contexto: " + message.getType());
        }
    }

    // cria uma mensagem REJECT com o motivo da rejeição
    private NetworkMessage reject(String reason) {
        return new NetworkMessage(MessageType.REJECT, reason);
    }

    // envia um REJECT para um peer (usado quando este nó, como cliente, descarta uma cadeia)
    private void sendReject(PeerAddress peer, String reason) {
        try {
            new BcpClient(localAddress).sendOneWay(peer,
                    new NetworkMessage(MessageType.REJECT, reason));
        } catch (IOException e) {
            System.err.println("[bcp] Não consegui enviar REJECT a " + peer);
        }
    }

    // apresenta este nó a um peer recém-descoberto (o handshake faz o outro nó nos registrar)
    private void introduceTo(PeerAddress p) {
        try {
            new BcpClient(localAddress).request(p, new NetworkMessage(MessageType.GET_PEERS, null));
        } catch (IOException e) {
            System.err.println("[bcp] Não consegui me apresentar a " + p);
        }
    }

    // conecta a outro nó e solicita a lista de peers que ele conhece
    public List<PeerAddress> requestPeersFrom(PeerAddress peer) throws IOException {

        // adiciona o endereço do nó à lista de participantes conhecidos
        addPeer(peer);

        // guarda quem já era conhecido, para saber quem é novo depois da resposta
        Set<PeerAddress> before = new HashSet<>(peers);

        // cria um cliente para realizar a comunicação com o outro nó
        BcpClient client = new BcpClient(localAddress);

        // envia uma mensagem GET_PEERS solicitando os endereços conhecidos pelo peer
        NetworkMessage response = client.request(peer,
                new NetworkMessage(MessageType.GET_PEERS, null));

        // verifica se a solicitação foi rejeitada pelo outro nó
        if (response.getType() == MessageType.REJECT) {
            throw new IOException("GET_PEERS rejeitado: " + response.getPayload());
        }

        // verifica se a resposta possui o tipo e o conteúdo esperados
        if (response.getType() != MessageType.PEERS_LIST
                || !(response.getPayload() instanceof ArrayList<?>)) {
            throw new IOException("Resposta inválida para GET_PEERS: " + response);
        }

        // recupera a lista de endereços recebida
        ArrayList<?> receivedPeers = (ArrayList<?>) response.getPayload();

        // percorre os endereços "IP:Porta" recebidos e adiciona os válidos à lista local
        for (Object item : receivedPeers) {
            if (item instanceof String s) {
                PeerAddress p = PeerAddress.parse(s);
                if (p != null) addPeer(p);
            }
        }

        // se apresenta aos peers novos, para que eles também nos conheçam
        for (PeerAddress p : getPeers()) {
            if (!before.contains(p) && !p.equals(peer)) introduceTo(p);
        }

        // retorna a lista atualizada de participantes conhecidos
        return getPeers();
    }

    // solicita a blockchain de outro nó e atualiza a cadeia local se necessário
    public boolean syncBlockchainFrom(PeerAddress peer) throws IOException {

        // adiciona o endereço do participante à lista de peers conhecidos
        addPeer(peer);

        // cria o cliente responsável por realizar a conexão
        BcpClient client = new BcpClient(localAddress);

        // envia GET_BLOCKCHAIN solicitando uma cópia da cadeia do outro nó
        NetworkMessage response = client.request(peer,
                new NetworkMessage(MessageType.GET_BLOCKCHAIN, null));

        // verifica se o outro nó rejeitou a solicitação
        if (response.getType() == MessageType.REJECT) {
            throw new IOException("GET_BLOCKCHAIN rejeitado: " + response.getPayload());
        }

        // verifica se a resposta contém uma cópia da blockchain
        Blockchain received = toChain(response.getPayload());
        if (response.getType() != MessageType.BLOCKCHAIN_COPY || received == null) {
            throw new IOException("Resposta inválida para GET_BLOCKCHAIN: " + response);
        }

        // cadeia inválida: descarta e avisa o peer com REJECT
        if (!received.isValid()) {
            sendReject(peer, "Cadeia inválida");
            throw new IOException("O peer enviou uma blockchain inválida.");
        }

        // impede que outros processos alterem a cadeia durante a sincronização
        synchronized (this) {

            // atualiza a blockchain somente se a recebida possuir mais blocos
            if (received.size() > blockchain.size()) {

                blockchain = received;

                System.out.println("[bcp] Sincronização concluída. Altura local: "
                        + blockchain.getHeight());

                // informa que a blockchain local foi atualizada
                return true;
            }
        }

        // cadeia válida, mas igual ou menor que a local: descarta e avisa o peer
        System.out.println("[bcp] Sua cadeia já é igual ou mais longa que a cadeia do peer.");
        sendReject(peer, "Tamanho menor ou igual ao da cadeia local");
        return false;
    }

    // cria um novo bloco na blockchain local e compartilha a atualização com os peers
    public Block mineBlock(String data) {

        Block block;

        // garante que apenas uma thread altere a blockchain por vez (a mineração acontece aqui dentro)
        synchronized (this) {
            block = blockchain.addBlock(data);
        }

        // mostra as informações do bloco que acabou de ser criado
        System.out.println("[bcp] Novo bloco local: " + block);

        // envia a blockchain atualizada para os outros participantes
        broadcastBlockchain();

        // retorna o bloco criado
        return block;
    }

    // envia uma cópia da blockchain para todos os peers conhecidos, em paralelo
    public void broadcastBlockchain() {

        // cria uma cópia da cadeia para utilizar durante o envio
        Blockchain snapshot = getBlockchainCopy();

        List<PeerAddress> targets = getPeers();
        if (targets.isEmpty()) return;

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(targets.size(), 8));

        for (PeerAddress peer : targets) {
            pool.submit(() -> {
                try {

                    // envia a mensagem BLOCKCHAIN_COPY sem aguardar uma resposta
                    new BcpClient(localAddress).sendOneWay(peer,
                            new NetworkMessage(MessageType.BLOCKCHAIN_COPY, toPayload(snapshot)));

                    System.out.println("[bcp] Cópia enviada para " + peer);

                } catch (IOException e) {

                    // peer inalcançável: informa e remove da lista de conhecidos
                    System.err.println("[bcp] Falha ao enviar para " + peer + ": " + e.getMessage());
                    peers.remove(peer);
                }
            });
        }

        pool.shutdown();
    }

    // fecha o servidor quando o nó deixa de funcionar
    @Override
    public void close() {
        server.close();
    }
}