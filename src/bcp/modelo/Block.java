package bcp.modelo;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

// representa um bloco da blockchain e mantém uma ligação com o bloco anterior por meio do hash
public final class Block implements Serializable {

    // identifica a versão da classe durante a serialização
    private static final long serialVersionUID = 2L;

    // quantidade de zeros hexadecimais exigidos no início do hash (Proof of Work)
    public static final int DIFFICULTY = 4;

    // prefixo que todo hash válido precisa ter
    private static final String TARGET = "0".repeat(DIFFICULTY);

    // bloco gênesis compartilhado (declarado depois do TARGET, que ele usa)
    private static final Block GENESIS = new Block(0, 0L, "0", "BCP_GENESIS");

    // posição do bloco dentro da cadeia
    private final long index;

    // momento em que o bloco foi criado
    private final long timestamp;

    // hash do bloco anterior, usado para manter a ligação entre os blocos
    private final String previousHash;

    // informação que será armazenada no bloco
    private final String data;

    // número encontrado na mineração que faz o hash começar com zeros
    private final long nonce;

    // hash gerado a partir dos dados do próprio bloco
    private final String hash;

    // construtor responsável por criar (minerar) um novo bloco
    public Block(long index, long timestamp, String previousHash, String data) {

        // impede a criação de blocos com índice negativo
        if (index < 0) throw new IllegalArgumentException("Índice negativo.");

        this.index = index;
        this.timestamp = timestamp;

        // garante que o hash anterior não seja nulo
        this.previousHash = Objects.requireNonNull(previousHash, "previousHash");

        // garante que os dados do bloco não sejam nulos
        this.data = Objects.requireNonNull(data, "data");

        // Proof of Work: incrementa o nonce até o hash começar com os zeros exigidos
        long n = 0;
        String h = calculateHash(index, timestamp, previousHash, data, n);
        while (!h.startsWith(TARGET)) {
            n++;
            h = calculateHash(index, timestamp, previousHash, data, n);
        }
        this.nonce = n;
        this.hash = h;
    }

    // retorna o primeiro bloco da blockchain, conhecido como bloco gênesis
    public static Block createGenesisBlock() {
        return GENESIS;
    }

    // retorna a posição do bloco na cadeia
    public long getIndex() {
        return index;
    }

    // retorna o horário de criação do bloco
    public long getTimestamp() {
        return timestamp;
    }

    // retorna o hash do bloco anterior
    public String getPreviousHash() {
        return previousHash;
    }

    // retorna os dados armazenados no bloco
    public String getData() {
        return data;
    }

    // retorna o nonce encontrado na mineração
    public long getNonce() {
        return nonce;
    }

    // retorna o hash atual do bloco
    public String getHash() {
        return hash;
    }

    // recalcula o hash, confere o Proof of Work e verifica se bate com o valor armazenado
    public boolean hasValidHash() {
        if (hash == null || previousHash == null || data == null) return false;
        return hash.startsWith(TARGET)
                && hash.equals(calculateHash(index, timestamp, previousHash, data, nonce));
    }

    // gera um hash usando os dados do bloco e o algoritmo sha-256
    public static String calculateHash(long index, long timestamp, String previousHash,
                                       String data, long nonce) {

        // organiza as informações que serão utilizadas no cálculo do hash
        // os comprimentos ajudam a evitar ambiguidades na separação dos dados
        String input = index + "|" + timestamp + "|" + previousHash.length() + ":" + previousHash
                + "|" + data.length() + ":" + data + "|" + nonce;

        try {
            // cria uma instância do algoritmo sha-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // transforma os dados em bytes e calcula o hash
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // prepara uma variável para armazenar o resultado em hexadecimal
            StringBuilder hex = new StringBuilder(bytes.length * 2);

            // percorre os bytes gerados e converte cada um para hexadecimal
            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }

            // retorna o hash calculado
            return hex.toString();

        } catch (NoSuchAlgorithmException e) {

            // informa um erro caso o algoritmo não esteja disponível na jvm
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.", e);
        }
    }

    // transforma as informações do bloco em texto para facilitar sua visualização
    @Override
    public String toString() {
        return "Block{index=" + index + ", timestamp=" + timestamp
                + ", previousHash='" + previousHash + "', hash='" + hash
                + "', nonce=" + nonce + ", data='" + data + "'}";
    }
}