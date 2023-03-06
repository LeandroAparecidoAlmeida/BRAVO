package bravo.gui.dialogs;

import java.util.Date;

/**
 * Classe para formatação de campos para exibição para o usuário.
 * @author Leandro Aparecido de Almeida
 */
final class Formatter {
    
    /**
     * Formatar o tamanho de arquivos.
     * @param value tamanho do arquivo.
     * @return tamanho do arquivo formatado.
     */
    public static String formatSize(long value) {
        double d;
        String m;
        long bytes = (long) value;                    
        if (bytes < 1024) {
            d = bytes;
            m = "B";
        } else if (bytes >= 1024 && bytes < 1048576) {
            d = bytes / 1024f;
            m = "KB";
        } else if (bytes >= 1048576 && bytes < 1073741824) {
            d = bytes / 1048576f;
            m = "MB";
        } else {
            d = bytes / 1073741824f;
            m = "GB";
        }
        return String.format("%.2f", d) + " " + m;
    }
    
    /**
     * Formatar data para o formato <i>dd/mm/aaaa, hh:mm</i>.
     * @param value data a ser formatada.
     * @return data no formato <i>dd/mm/aaaa, hh:mm</i>.
     */
    public static String formatDate(Date value) {
        return String.format("%1$td/%1$tm/%1$tY %1$tH:%1$tM", (Date)value);
    }
    
}