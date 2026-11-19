package bcp.rede;

import java.io.Serializable;
import java.util.Objects;

public final class PeerAddress implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;

    public PeerAddress(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("O host do peer não pode ser vazio.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Porta inválida: " + port);
        }
        this.host = host.trim();
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public static PeerAddress parse(String s) {
        try {
            int c = s.lastIndexOf(':');
            return new PeerAddress(s.substring(0, c), Integer.parseInt(s.substring(c + 1)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PeerAddress)) return false;
        PeerAddress that = (PeerAddress) other;
        return port == that.port && host.equalsIgnoreCase(that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host.toLowerCase(), port);
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}