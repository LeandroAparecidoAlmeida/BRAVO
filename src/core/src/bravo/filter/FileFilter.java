package bravo.filter;

import java.io.File;

/**
 * Filtro para listagem de arquivos.
 * @author Leandro Apaarecido de Almeida
 */
public final class FileFilter implements java.io.FileFilter {
    @Override
    public boolean accept(File pathname) {
        return pathname.isFile();
    }    
}