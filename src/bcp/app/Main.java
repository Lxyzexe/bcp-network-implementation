package bcp.app;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import bcp.no.BcpNode;
import bcp.modelo.Block;
import bcp.modelo.Blockchain;
import bcp.rede.PeerAddress;

public final class Main {

    private Main() {
        // Evita criar objetos da classe Main.
    }

    public static void main(String[] args) {

        // Define a porta do nó. Se não informar, utiliza 8333.
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 8333;

        // Endereço que será divulgado para os outros nós da rede.
        String advertisedHost = args.length >= 2 ? args[1] : "127.0.0.1";

        // Cria o nó e garante que ele será fechado ao encerrar o programa.
        try (BcpNode node = new BcpNode("0.0.0.0", advertisedHost, port)) {

            node.start(); // Inicia o servidor para receber conexões.

            System.out.println("=================================================");
            System.out.println(" BCP - Blockchain Copy Protocol (protótipo Java)");
            System.out.println(" Endereço anunciado: " + node.getLocalAddress());
            System.out.println("=================================================");

            // Verifica se foi informado um peer inicial para conectar.
            if (args.length >= 4) {

                // Cria o endereço do primeiro nó da rede.
                PeerAddress seed = new PeerAddress(args[2], Integer.parseInt(args[3]));

                node.addPeer(seed); // Adiciona o peer à lista de conhecidos.

                try {
                    // Solicita a lista de outros nós conhecidos pelo peer.
                    List<PeerAddress> discovered = node.requestPeersFrom(seed);

                    System.out.println("[bcp] Peers conhecidos: " + discovered);

                    // Solicita uma cópia da blockchain para sincronizar o nó local.
                    node.syncBlockchainFrom(seed);

                } catch (IOException e) {
                    // Informa caso não consiga se comunicar com o peer inicial.
                    System.err.println("[bcp] Não foi possível usar o peer inicial " + seed
                            + ": " + e.getMessage());
                }
            }

            printHelp(); // Mostra os comandos disponíveis.
            runConsole(node); // Inicia o terminal de comandos.

        } catch (NumberFormatException e) {
            // Trata erros quando uma porta informada não é um número.
            System.err.println("Porta inválida. Informe um número entre 1 e 65535.");

        } catch (IOException e) {
            // Informa problemas ao iniciar o servidor.
            System.err.println("Não foi possível iniciar o nó: " + e.getMessage());
        }
    }

    // Lê os comandos digitados pelo usuário e executa as operações correspondentes.
    private static void runConsole(BcpNode node) {

        Scanner scanner = new Scanner(System.in);

        while (true) { // Mantém o terminal funcionando até o usuário sair.

            System.out.print("bcp> ");

            if (!scanner.hasNextLine()) break; // Encerra se não houver mais entrada.

            String line = scanner.nextLine().trim(); // Lê o comando digitado.

            if (line.isEmpty()) continue; // Ignora comandos vazios.

            // Separa o comando dos argumentos informados pelo usuário.
            String[] parts = line.split("\\s+", 3);
            String command = parts[0].toLowerCase();

            try {
                switch (command) {

                    case "help":
                        printHelp(); // Exibe a lista de comandos.
                        break;

                    case "peers":
                        // Mostra todos os nós conhecidos pelo nó atual.
                        System.out.println("Peers: " + node.getPeers());
                        break;

                    case "add":
                        if (parts.length < 3) {
                            System.out.println("Uso: add <host> <porta>");
                            break;
                        }

                        // Adiciona manualmente um novo endereço à lista de peers.
                        node.addPeer(new PeerAddress(parts[1], Integer.parseInt(parts[2])));

                        System.out.println("Peer adicionado: " + parts[1] + ":" + parts[2]);
                        break;

                    case "discover":
                        if (parts.length < 3) {
                            System.out.println("Uso: discover <host> <porta>");
                            break;
                        }

                        // Cria o endereço do nó do qual queremos descobrir outros peers.
                        PeerAddress discoveryPeer = new PeerAddress(
                                parts[1], Integer.parseInt(parts[2]));

                        node.addPeer(discoveryPeer);

                        // Solicita a lista de peers conhecidos pelo outro nó.
                        System.out.println("Peers retornados: "
                                + node.requestPeersFrom(discoveryPeer));
                        break;

                    case "sync":
                        if (parts.length < 3) {
                            System.out.println("Uso: sync <host> <porta>");
                            break;
                        }

                        // Solicita a blockchain de outro nó para sincronizar a cadeia local.
                        node.syncBlockchainFrom(
                                new PeerAddress(parts[1], Integer.parseInt(parts[2])));
                        break;

                    case "mine":
                        if (parts.length < 2 || parts[1].trim().isEmpty()) {
                            System.out.println("Uso: mine <dados do bloco>");
                            break;
                        }

                        // Cria um novo bloco com o texto informado pelo usuário.
                        node.mineBlock(parts[1]);
                        break;

                    case "chain":
                        // Obtém uma cópia da blockchain armazenada neste nó.
                        Blockchain chain = node.getBlockchainCopy();

                        // Mostra a altura da cadeia e informa se ela é válida.
                        System.out.println("Altura: " + chain.getHeight()
                                + " | válida: " + chain.isValid());

                        // Percorre os blocos e mostra as informações de cada um.
                        for (Block block : chain.getBlocks()) {
                            System.out.println(block);
                        }
                        break;

                    case "exit":
                    case "quit":
                        return; // Sai do terminal e encerra o programa.

                    default:
                        System.out.println("Comando desconhecido. Digite help.");
                }

            } catch (NumberFormatException e) {
                // Trata portas informadas em formato incorreto.
                System.out.println("Porta inválida.");

            } catch (IOException e) {
                // Trata erros durante a comunicação com outros nós.
                System.out.println("Falha de comunicação: " + e.getMessage());

            } catch (IllegalArgumentException e) {
                // Trata argumentos inválidos enviados aos métodos.
                System.out.println("Parâmetro inválido: " + e.getMessage());
            }
        }
    }

    // Exibe os comandos disponíveis no terminal e suas respectivas funções.
    private static void printHelp() {

        System.out.println("Comandos:");
        System.out.println("  add <host> <porta>       - adiciona um peer conhecido");
        System.out.println("  discover <host> <porta>  - solicita a lista de peers (GET_PEERS)");
        System.out.println("  sync <host> <porta>      - solicita a blockchain (GET_BLOCKCHAIN)");
        System.out.println("  mine <texto>             - cria bloco e envia BLOCKCHAIN_COPY aos peers");
        System.out.println("  peers                    - lista peers conhecidos");
        System.out.println("  chain                    - mostra e valida a cadeia local");
        System.out.println("  help                     - mostra esta ajuda");
        System.out.println("  exit                     - encerra o nó");
    }
}