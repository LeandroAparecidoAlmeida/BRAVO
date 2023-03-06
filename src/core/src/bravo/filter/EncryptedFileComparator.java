package bravo.filter;

import bravo.file.FileEntry;
import java.util.Comparator;

/**
 * Comparador de objetos {@link EncryptedFile} baseado no nome real dos arquivos.
 * @author Leandro Aparecido de Almeida
 */
public class EncryptedFileComparator implements Comparator<FileEntry> {
    @Override
    public int compare(FileEntry o1, FileEntry o2) {
        String path1 = o1.getName().toLowerCase();
        String path2 = o2.getName().toLowerCase();
        return path1.compareTo(path2);
    }
}