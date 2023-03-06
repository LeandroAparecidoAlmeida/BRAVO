package bravo.file;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 *
 * @author Samsung
 */
public class MemoryInputStream extends ByteArrayInputStream {
    
    private final CipherListener[] listeners;

    public MemoryInputStream(byte[] buf, CipherListener... listeners) {
        super(buf);
        this.listeners = listeners;
    }
    
    /**
     * Notificar os ouvintes do processo de encriptação.
     * @param length número de bytes processados.
     */
    private void notify(int length) {
        for (CipherListener listener : listeners) {
            if (length >= 0) {
                listener.update(length);
            }
        }
    }

    @Override
    public synchronized int read() {
        notify(1);
        return super.read(); 
    }

    @Override
    public int read(byte[] b) throws IOException {
        int length = super.read(b);
        if (length != -1) notify(length);
        return length;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) {
        int length = super.read(b, off, len);
        if (length != -1) notify(length);
        return length;
    }

    @Override
    public synchronized byte[] readAllBytes() {
        byte[] allBytes = super.readAllBytes();
        notify(allBytes.length);
        return allBytes;
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        byte[] bytes = super.readNBytes(len);
        notify(bytes.length);
        return bytes;
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) {
        int length = super.readNBytes(b, off, len);
        if (length != -1) notify(length);
        return length; 
    }

}