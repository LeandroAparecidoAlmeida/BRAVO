package bravo.file;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Provider;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import static bravo.utils.ArrayUtils.nextBytes;

/**
 * Classe base para encriptação/decriptação de arquivos.
 * @author Leandro Aparecido de Almeida
 */
class BravoCipher {
    
    /**Constante tamanho padrão de um buffer de leitura/escrita (4 KB).*/
    public static final int BUFFER_SIZE = 4096;
    /**Número de bytes do vetor de inicialização.*/
    public static final int BLOCK_SIZE = 16;
    /**Número de bytes do vetor de inicialização.*/
    public static final int IV_LENGTH = 16;
    /**Algoritmo para encriptação/decriptação de arquivos.*/
    private final String ALGORITHM = "AES/CFB/PKCS7Padding";

    /**
     * Encriptar o stream de entrada, direcionando os bytes criptografados para
     * o stream de saída.
     * @param istream stream de entrada.
     * @param ostream stream de saída.
     * @param fileMetadata cabeçalho de arquivo.
     * @param password senha para encriptação do arquivo.
     * @param argon2Params parâmetros para o algoritmo Argon2.
     * @param listeners ouvintes do processo de encriptação.
     */
    public void encrypt(InputStream istream, OutputStream ostream, 
    FileMetadata fileMetadata, String password, CipherListener... listeners) throws Exception {
        //Testa o tamanho da senha.
        if (password.length() == 0) {
            throw new Exception("Senha não pode estar vazia");
        }
        Provider provider = new BouncyCastleProvider();
        Cipher cipher = Cipher.getInstance(ALGORITHM, provider);
        byte[] sha256Hash = SHA256Hash.getBytes(password);
        SecretKey secretKey = new SecretKeySpec(sha256Hash, "AES");
        byte[] iv = nextBytes(IV_LENGTH);
        IvParameterSpec ivParSpec = new IvParameterSpec(iv);
        fileMetadata.setIVBytes(ivParSpec.getIV());
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParSpec);
        try (CipherOutputStream costream = new CipherOutputStream(ostream, cipher)) {            
            boolean abort = false;
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while (((length = istream.read(buffer)) != -1) && !abort) {                           
                for (CipherListener listener : listeners) {
                    if (listener.abort()) {
                        abort = true;
                        break;
                    }
                }
                costream.write(buffer, 0, length);                
                costream.flush();
                for (CipherListener listener : listeners) {
                    listener.update(length);
                }
            }
        }
    }
    
    /**
     * Decriptar o stream de entrada, direcionando os bytes decriptografados para
     * o stream de saída.
     * @param istream stream de entrada.
     * @param ostream stream de saída.
     * @param fileMetadata cabeçalho de arquivo contendo os dados para a decriptografia.
     * @param password senha para decriptação do arquivo.
     * @param listeners ouvintes do processo de decriptação.
     */
    public void decrypt(InputStream istream, OutputStream ostream, 
    FileMetadata fileMetadata, String password, CipherListener... listeners) 
    throws Exception {
        Provider provider = new BouncyCastleProvider();
        Cipher cipher = Cipher.getInstance(ALGORITHM, provider);
        IvParameterSpec ivParSpec = new IvParameterSpec(fileMetadata.getIVBytes());
        byte[] sha256Hash = SHA256Hash.getBytes(password);
        SecretKey secretKey = new SecretKeySpec(sha256Hash, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParSpec);
        try (CipherInputStream cistream = new CipherInputStream(istream, cipher)) {
            boolean abort = false;
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;            
            while (((length = cistream.read(buffer)) != -1) && !abort) {
                for (CipherListener listener : listeners) {
                    if (listener.abort()) {
                        abort = true;
                        break;
                    }
                }                  
                ostream.write(buffer, 0, length);
                ostream.flush();
                for (CipherListener listener : listeners) {
                    listener.update(length);
                }
            }
        }        
    }
    
}