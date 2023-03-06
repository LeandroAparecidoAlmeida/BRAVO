package bravo.filter;

import java.io.File;
import java.util.Comparator;

/**
 * Comparador de objetos {@link File} baseado no nome dos arquivos.
 * @author Leandro Aparecido de Almeida
 */
public class FileComparator implements Comparator<File> {
    @Override
    public int compare(File o1, File o2) {
        String path1 = o1.getAbsolutePath().toLowerCase();
        String path2 = o2.getAbsolutePath().toLowerCase();
        return path1.compareTo(path2);
    }
}
