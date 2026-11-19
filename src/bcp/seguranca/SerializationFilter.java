package bcp.seguranca;

import java.io.ObjectInputFilter;
import java.util.ArrayList;

import bcp.modelo.Block;
import bcp.modelo.Blockchain;
import bcp.rede.MessageType;
import bcp.rede.NetworkMessage;
import bcp.rede.PeerAddress;

public final class SerializationFilter {
    private static final long MAX_STREAM_BYTES = 50L * 1024L * 1024L;
    private static final long MAX_REFERENCES = 250_000L;
    private static final long MAX_DEPTH = 40L;

    private SerializationFilter() {
    }

    public static ObjectInputFilter createBcpFilter() {
        return info -> {
            if (info.depth() > MAX_DEPTH
                    || info.references() > MAX_REFERENCES
                    || info.streamBytes() > MAX_STREAM_BYTES) {
                return ObjectInputFilter.Status.REJECTED;
            }

            Class<?> type = info.serialClass();
            if (type == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            if (type.isPrimitive()) {
                return ObjectInputFilter.Status.ALLOWED;
            }

            if (type.isArray()) {
                Class<?> component = type.getComponentType();
                
                if (component.isPrimitive() || component == Object.class || isAllowed(component)) {
                    return ObjectInputFilter.Status.ALLOWED;
                }
                return ObjectInputFilter.Status.REJECTED;
            }

            return isAllowed(type)
                    ? ObjectInputFilter.Status.ALLOWED
                    : ObjectInputFilter.Status.REJECTED;
        };
    }

    private static boolean isAllowed(Class<?> type) {
        return type == String.class
                || type == Object.class
                || type == Enum.class
                || type == ArrayList.class
                || type == NetworkMessage.class
                || type == MessageType.class
                || type == PeerAddress.class
                || type == Block.class
                || type == Blockchain.class;
    }
}
