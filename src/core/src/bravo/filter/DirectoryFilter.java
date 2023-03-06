package bravo.filter;

import java.io.File;
import java.io.FileFilter;

/**
 * Filtro para listagem de diretórios.
 * @author Leandro Aparecido de Almeida
 */
public final class DirectoryFilter implements FileFilter {
    @Override
    public boolean accept(File pathname) {
        return pathname.isDirectory();
    }    
}
