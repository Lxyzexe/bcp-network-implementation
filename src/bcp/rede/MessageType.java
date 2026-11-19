package bcp.rede;

import java.io.Serializable;

public enum MessageType implements Serializable {
    HELLO,
    GET_PEERS,
    PEERS_LIST,
    GET_BLOCKCHAIN,
    BLOCKCHAIN_COPY,
    REJECT
}
