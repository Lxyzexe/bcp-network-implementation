package bcp.rede;

import java.io.Serializable;

public final class NetworkMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final Object payload;

    public NetworkMessage(MessageType type, Object payload) {
        if (type == null) {
            throw new IllegalArgumentException("O tipo da mensagem não pode ser nulo.");
        }
        if (payload != null && !(payload instanceof Serializable)) {
            throw new IllegalArgumentException("O payload precisa implementar Serializable.");
        }
        this.type = type;
        this.payload = payload;
    }

    public static final String VERSION = "V1.0";

    public static NetworkMessage hello(PeerAddress self) {
        return new NetworkMessage(MessageType.HELLO, VERSION + "-" + self);
    }

    public static PeerAddress parseHello(Object payload) {
        if (!(payload instanceof String s) || !s.startsWith(VERSION + "-")) return null;
        return PeerAddress.parse(s.substring(VERSION.length() + 1));
    }

    public MessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "NetworkMessage{type=" + type + ", payload=" + payload + "}";
    }
}