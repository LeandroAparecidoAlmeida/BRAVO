package bravo.file;

import java.security.MessageDigest;

public class SHA256Hash {
    
    public static byte[] getBytes(byte[] passwordBytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(passwordBytes);
    }
    
    public static byte[] getBytes(String password) throws Exception {
        return getBytes(password.getBytes());
    }
    
}
