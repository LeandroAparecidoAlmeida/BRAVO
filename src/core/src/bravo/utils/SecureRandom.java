package bravo.utils;

import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.prng.RandomGenerator;
import org.bouncycastle.crypto.prng.DigestRandomGenerator;

/**
 * Algoritmo para geração de hash.
 * @author Leandro Aparecido de Almeida
 */
enum Digest {
    SHA1PRNG,
    SHA256PRNG,
    SHA3PRNG,
    SHA384PRNG,
    SHA512PRNG; 
}

/**
 * Classe para a geração de números pseudo-aleatórios seguros.
 * @author Leandro Aparecido de Almeida
 */
final class SecureRandom extends java.util.Random {
   
    /**Gerador de números aleatórios.*/
    protected RandomGenerator generator;

    //Constructor private.
    private SecureRandom(RandomGenerator generator) {
        super(0);
        this.generator = generator;
        setSeed(System.currentTimeMillis());
    }

    /**
     * Obter uma instância de {@link SecureRandom} com base no algoritmo
     * definido.
     * @param d digest.
     * @return instância de {@link SecureRandom}.
     */
    public static SecureRandom getInstance(Digest d) {
        org.bouncycastle.crypto.Digest digest = null;
        switch (d) {
            case SHA1PRNG: digest = new SHA1Digest(); break;
            case SHA256PRNG: digest = new SHA256Digest(); break;
            case SHA3PRNG: digest = new SHA3Digest(); break;
            case SHA384PRNG: digest = new SHA384Digest(); break;
            case SHA512PRNG: digest = new SHA512Digest(); break;
        }
        return new SecureRandom(new DigestRandomGenerator(digest));
    }

    /**
     * Gerar as sementes para aumento da entropia.
     * @param numBytes número de bytes.
     * @return vetor de sementes.
     */
    public byte[] generateSeed(int numBytes) {
        byte[] rv = new byte[numBytes];
        generator.addSeedMaterial(System.currentTimeMillis());
        generator.nextBytes(rv);
        return rv;
    }

    /**
     * Definir as sementes para aumento da entropia.
     * @param inSeed vetor de sementes para aumento da entropia.
     */
    public void setSeed(byte[] inSeed) {
        generator.addSeedMaterial(inSeed);
    }
    
    /**
     * Definir a semente para aumento da entropia.
     * @param rSeed semente para aumento da entropia.
     */
    @Override
    public void setSeed(long rSeed) {
        if (rSeed != 0) {
            generator.addSeedMaterial(rSeed);
        }
    }

    /**
     * Preencher o vetor com bytes aleatórios.
     * @param bytes vetor a ser preenchido.
     */
    @Override
    public void nextBytes(byte[] bytes) {
        generator.nextBytes(bytes);
    }

}