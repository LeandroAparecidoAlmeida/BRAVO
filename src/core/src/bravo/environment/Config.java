package bravo.environment;

import java.util.prefs.Preferences;

/**
 * Classe para gravar e ler as configurações do sistema.
 * @author Leandro Aparecido de Almeida
 */
public final class Config {

    /**Instância da Api Preferences do Java.*/
    private static Preferences prefs = Preferences.userRoot().node("/bravo_cipher");

    /**
     * Escrever um valor inteiro.
     * @param key chave
     * @param value valor inteiro a escrever.
     */
    public static void putInt(String key, int value) {
        int oldValue = getInt(key);
        if (oldValue != value) {
            prefs.putInt(key, value);
        } 
    }

    /**
     * Escrever um valor string.
     * @param key chave
     * @param value valor string a escrever. 
     */
    public static void putString(String key, String value) {
        String oldValue = getString(key);
        if (!oldValue.equals(value)) {
            prefs.put(key, value);
        } 
    }

    /**
     * Escrever um valor array de bytes.
     * @param key chave
     * @param value valor array de bytes a escrever. 
     */
    public static void putByteArray(String key, byte[] value) {
        byte[] oldValue = getByteArray(key);
        boolean equals = true;
        for (int i = 0; i < oldValue.length; i++) {
            if (value[i] != oldValue[i]) {
                equals = false;
                break;
            }
        }
        if (!equals) {
            prefs.putByteArray(key, value);
        }
    }

    /**
     * Ler um valor inteiro.
     * @param key chave
     * @return valor inteiro lido.
     */
    public static int getInt(String key) {
        return prefs.getInt(key, 250);
    }

    /**
     * Ler um valor string.
     * @param key chave
     * @return valor string lido.
     */
    public static String getString(String key) {
        return prefs.get(key, null);
    }

    /**
     * Ler um valor array de bytes.
     * @param key chave
     * @return valor array de bytes lido.
     */
    public static byte[] getByteArray(String key) {
        return prefs.getByteArray(key, null);
    }

}