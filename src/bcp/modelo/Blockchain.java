package bcp.modelo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// armazena os blocos e mantém a ordem em que foram adicionados
public final class Blockchain implements Serializable {

    // identifica a versão da classe durante a serialização
    private static final long serialVersionUID = 1L;

    // lista que guarda todos os blocos da cadeia
    private final ArrayList<Block> blocks;

    // cria uma blockchain começando apenas com o bloco gênese
    public Blockchain() {
        this.blocks = new ArrayList<>();

        // adiciona o primeiro bloco da cadeia
        this.blocks.add(Block.createGenesisBlock());
    }

    // construtor utilizado para criar uma cópia de outra blockchain
    private Blockchain(ArrayList<Block> blocks) {
        this.blocks = new ArrayList<>(blocks);
    }

    // adiciona um novo bloco ao final da cadeia
    public synchronized Block addBlock(String data) {

        // impede a criação de blocos sem informações
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Os dados do bloco não podem ser vazios.");
        }

        // pega o último bloco adicionado para utilizar suas informações
        Block previous = blocks.get(blocks.size() - 1);

        // cria o novo bloco com o próximo índice, horário atual e hash anterior
        Block newBlock = new Block(previous.getIndex() + 1,
                System.currentTimeMillis(), previous.getHash(), data.trim());

        // adiciona o bloco criado à lista
        blocks.add(newBlock);

        // retorna o bloco para quem chamou o método
        return newBlock;
    }

    // retorna a quantidade total de blocos armazenados
    public synchronized int size() {
        return blocks.size();
    }

    // retorna a altura da blockchain, sem contar o bloco gênese
    public synchronized int getHeight() {
        return blocks.size() - 1;
    }

    // retorna o último bloco adicionado à cadeia
    public synchronized Block getLatestBlock() {
        return blocks.get(blocks.size() - 1);
    }

    // retorna uma cópia da lista que não permite alterações externas
    public synchronized List<Block> getBlocks() {
        return Collections.unmodifiableList(new ArrayList<>(blocks));
    }

    // cria e retorna uma cópia independente da blockchain atual
    public synchronized Blockchain copy() {
        return new Blockchain(blocks);
    }
    
 // cria uma blockchain a partir de uma lista de blocos recebida pela rede
    public static Blockchain fromBlocks(List<Block> blocks) {
        return new Blockchain(new ArrayList<>(blocks));
    }
    
    // verifica se a blockchain segue as regras básicas de integridade
    public synchronized boolean isValid() {

        // uma cadeia vazia ou sem blocos não é considerada válida
        if (blocks == null || blocks.isEmpty()) return false;

        try {
            // cria um bloco gênese de referência para comparar com o primeiro bloco
            Block expectedGenesis = Block.createGenesisBlock();
            Block actualGenesis = blocks.get(0);

            // verifica se o primeiro bloco possui as informações esperadas
            if (actualGenesis == null
                    || actualGenesis.getIndex() != expectedGenesis.getIndex()
                    || actualGenesis.getTimestamp() != expectedGenesis.getTimestamp()
                    || !expectedGenesis.getPreviousHash().equals(actualGenesis.getPreviousHash())
                    || !expectedGenesis.getData().equals(actualGenesis.getData())
                    || !expectedGenesis.getHash().equals(actualGenesis.getHash())) {
                return false;
            }

            // percorre todos os blocos para verificar sua integridade
            for (int i = 0; i < blocks.size(); i++) {

                Block current = blocks.get(i);

                // verifica se o bloco existe, possui o índice correto e tem um hash válido
                if (current == null || current.getIndex() != i || !current.hasValidHash()) {
                    return false;
                }

                // a partir do segundo bloco, verifica a ligação com o bloco anterior
                if (i > 0) {
                    Block previous = blocks.get(i - 1);

                    // o hash anterior armazenado deve ser igual ao hash do bloco anterior
                    if (!previous.getHash().equals(current.getPreviousHash())) {
                        return false;
                    }
                }
            }

        } catch (RuntimeException e) {
            // bloco com campo nulo ou dado corrompido: cadeia inválida, sem derrubar o nó
            return false;
        }

        // se todas as verificações passaram, a blockchain é válida
        return true;
    }

    // retorna um resumo da blockchain em formato de texto
    @Override
    public synchronized String toString() {
        return "Blockchain{height=" + getHeight() + ", blocks=" + blocks.size() + "}";
    }
}