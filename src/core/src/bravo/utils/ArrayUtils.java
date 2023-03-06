package bravo.utils;

import java.nio.ByteBuffer;
import static bravo.utils.Digest.SHA256PRNG;

/**
 * Classe para manipulação de arrays de objetos.
 * @author Leandro Aparecido de Almeida
 */
public class ArrayUtils {
    
    /**Instância para geração de números pseudo-aleatórios seguros.*/
    private static final SecureRandom secureRandom = SecureRandom.getInstance(SHA256PRNG);
    
    /**
     * Obter um vetor de bytes pseudoaleatórios.
     * @param length tamanho do vetor.
     * @return vetor de bytes pseudoaleatórios.
     */
    public static byte[] nextBytes(int length) {
        secureRandom.setSeed(secureRandom.generateSeed(16));
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * Converter um integer em um array de bytes correspondente.
     * @param i integer a ser convertido para array de bytes.
     * @return array de bytes correspondente ao integer.
     */
    public static byte[] intToByteArray(int i) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(i);
        return buffer.array();
    }

    /**
     * Converter um array de bytes em um integer correspondente.
     * @param bytes array de bytes a ser convertido para integer.
     * @return integer correspondente ao array de bytes.
     */
    public static int byteArrayToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return buffer.getInt();
    }
    
}
