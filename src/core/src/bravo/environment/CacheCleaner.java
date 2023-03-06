package bravo.environment;

import bravo.eraser.FileEraser;
import bravo.file.ProcessListener;
import bravo.filter.DirectoryFilter;
import bravo.filter.FileFilter;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;

/**
 * Limpador de cache. 
 * @author Leandro
 */
public class CacheCleaner {
    
    /**Ouvintes do processamento de arquivos.*/
    private static final List<ProcessListener> listeners = new ArrayList<>();
    private static final String INSTANCE_FOLDER_NAME = "instance";
    
    /**
     * Limpar cache da sessão corrente. Destrói todos os arquivos gerados durante
     * a sessão, impossibilitando a recuperação dos mesmos.
     * @throws Exception erro ocorrido ao limpar cache.
     */
    public static void clearCurrentSessionCache() throws Exception {
        FileEraser fileEraser = new FileEraser();
        listeners.forEach(listener -> {fileEraser.addProcessListener(listener);});
        fileEraser.wipeCacheFiles();
        listeners.forEach(listener -> {fileEraser.removeProcessListener(listener);});
    }
    
    /**
     * Limpar cache de sessões anteriores. Eventualmente, pode ocorrer de o 
     * programa ser encerrado de forma anormal, e, neste caso, vai ficar os
     * arquivos de sessão sem serem destruídos. Neste caso, é necessário destruir
     * estes arquivos, para inviabilizar qualquer possibilidade de recuperação
     * dos mesmos.
     * @throws Exception erro ocorrido ao limpar cache.
     */
    public static void clearPreviousSessionsCache() throws Exception {
        List<String> activeInstances = new ArrayList<>();
        activeInstances.add(RootFolder.getSessionFolder().getName());
        File instanceFolder = new File(RootFolder.getAbsolutePath() + 
        File.separator + INSTANCE_FOLDER_NAME);
        if (instanceFolder.exists()) {
            File[] files = instanceFolder.listFiles(new FileFilter());
            for (File file : files) {
                if (!file.delete()) {
                    activeInstances.add(file.getName());
                }
            }
        } else {
            instanceFolder.mkdirs();
        }
        final File file = new File(instanceFolder.getAbsolutePath() + File.separator + 
        RootFolder.getSessionFolder().getName());
        final RandomAccessFile raFile = new RandomAccessFile(file, "rw");
        final FileLock fLock = raFile.getChannel().tryLock();
        boolean lock = (fLock != null);
        if (lock) {
            Runtime.getRuntime().addShutdownHook(
                new Thread() {
                    @Override
                    public void run() {                            
                        try {                                
                            fLock.release();
                            raFile.close();
                            file.delete();
                        } catch (IOException ex) {
                        }
                    }
                }
            );
        }
        List<File> foldersToDelete = new ArrayList<>();
        File[] folders = RootFolder.getCacheFolder().listFiles(new DirectoryFilter());
        for (File folder : folders) {
            if (!activeInstances.contains(folder.getName())) {
                foldersToDelete.add(folder);
            }
        }
        if (!foldersToDelete.isEmpty()) {
            FileEraser fileEraser = new FileEraser();
            listeners.forEach(listener -> {fileEraser.addProcessListener(listener);});
            fileEraser.wipeFiles(foldersToDelete);
            listeners.forEach(listener -> {fileEraser.removeProcessListener(listener);});
        } else {
            listeners.forEach(listener -> {listener.done();});
        }
    }
    
    public static boolean checkCacheFolder() {
        File instanceFolder = new File(RootFolder.getAbsolutePath() + 
        File.separator + INSTANCE_FOLDER_NAME);
        if (instanceFolder.exists()) {
            File[] files = instanceFolder.listFiles(new FileFilter());
            if (files.length > 0) return true;
        }
        File rootCacheFolder = RootFolder.getCacheFolder();
        if (rootCacheFolder.exists()) {
            File[] folders = rootCacheFolder.listFiles(new DirectoryFilter());
            if (folders.length > 0) {
                for (File folder : folders) {
                    if (!folder.equals(RootFolder.getSessionFolder())) {
                        return true;
                    }
                }                
            }
        }
        return false;
    }
    
    public static void addProcessListener(ProcessListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public static void removeProcessListener(ProcessListener listener) {
        int idx = -1;
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i) == listener) {
                idx = i;
                break;
            }
        }
        listeners.remove(idx);
    }
    
}