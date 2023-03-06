package bravo.environment;

import java.io.File;
import java.util.Date;

/**
 * Classe que representa o diretório raiz do programa. Todos os arquivos em 
 * processamento serão gravados em subdiretórios do diretório raiz, bem como
 * arquivos necessários ao seu funcionamento. Nesta classe, será feito todo
 * o processo de criação de diretórios inexistentes na inicialização do programa,
 * bem como a chamada a todos os subdiretórios essenciais.
 * @author Leandro Aparecido de Almeida
 */
public final class RootFolder {

    /**Diretório raiz do programa.*/
    private static final File rootFolder;
    /**Diretório raiz do programa.*/
    private static final File rootCacheFolder;
    /**Subdiretório aonde extraem-se os arquivos abertos no programa.*/
    private static final File extractFolder1;
    /**Subdiretório aonde extraem-se os arquivos para troca da senha.*/
    private static final File extractFolder2;
    /**Subdiretório cache, aonde se extraem todos os arquivos no contexto do processamento do programa.*/
    private static final File cacheFolder;
    /**Subdiretório aonde cria-se um arquivo vazio para cada extensão, afim de obter seu ícone.*/
    private static final File thumbnailsFolder;
    /**Subdiretório aonde serão gravados os arquivos criptografados pelo programa.*/
    private static final File encryptFolder;
    /**Subdiretório aonde serão extraídos os arquivos copiados para o clipboard.*/
    private static final File clipboardFolder;
    
    //Cria todos os diretórios necessários.
    static {
        File path = null;
        try {
            path = new File(RootFolder.class.getProtectionDomain().getCodeSource()
            .getLocation().toURI()).getParentFile().getParentFile();
        } catch (Exception ex) {            
        }
        String rootPath = path.getAbsolutePath() + File.separator;
        String dateDigits = String.format("%1$td%1$tm%1$tY%1$tH%1$tM%1$tS", new Date());
        String cachePath = rootPath + "cache" + File.separator + dateDigits;
        rootFolder = new File(path.getAbsolutePath());
        rootCacheFolder = new File(rootPath + "cache");
        cacheFolder = new File(cachePath);
        encryptFolder = new File(cachePath + File.separator  + "encrypted");
        thumbnailsFolder = new File(cachePath + File.separator + "thumbnails");
        clipboardFolder = new File(cachePath + File.separator + "clipboard");
        extractFolder1 = new File(cachePath + File.separator + "extracted1");
        extractFolder2 = new File(cachePath + File.separator  + "extracted2");
        if (!cacheFolder.exists()) cacheFolder.mkdir(); 
        if (!encryptFolder.exists()) encryptFolder.mkdirs();               
        if (!thumbnailsFolder.exists()) thumbnailsFolder.mkdirs();
        if (!extractFolder1.exists()) extractFolder1.mkdirs();        
    }
    
    /**
     * Obter o path do diretório cache. O diretório cache recebe um nome distinto
     * para cada instância em execução do programa, vizando que múltiplas instâncias
     * possam estar em execução sem conflitos. Dessa forma, pode-se trabalhar com
     * múltiplos arquivos bravo simultâneamente.
     * @return path do diretório cache.
     */
    public static File getSessionFolder() {
        return cacheFolder;
    }
    
    /**
     * Obter o diretório para extração dos arquivos que serão abertos na interface
     * do programa.
     * @return diretório.
     */
    public static File getExtractFolder1() {
        return extractFolder1;
    }
    
    /**
     * Obter o diretório de extração dos arquivos para a troca da senha do arquivo
     * Bravo.
     * @return diretório.
     */
    public static File getExtractFolder2() {
        if (!extractFolder2.exists()) extractFolder2.mkdirs();
        return extractFolder2;
    }
    
    /**
     * Obter o diretório para criação de um arquivo vazio para cada extensão,
     * afim de obter seu ícone no sistema.
     * @return diretório.
     */
    public static File getThumbnailsFolder() {
        return thumbnailsFolder;
    }
    
    /**
     * Obter o diretório aonde serão extraídos os arquivos copiados para o clipboard.
     * @return diretório.
     */
    public static File getClipboardFolder() {
        if (!clipboardFolder.exists()) clipboardFolder.mkdirs();
        return clipboardFolder;
    }
    
    public static File getEncryptFolder() {
        return encryptFolder;
    }
    
    public static File getCacheFolder() {
        return rootCacheFolder;
    }

    public static String getAbsolutePath() {
        return rootFolder.getAbsolutePath();
    }

}