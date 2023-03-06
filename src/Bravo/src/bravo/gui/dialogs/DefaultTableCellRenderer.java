package bravo.gui.dialogs;

import bravo.environment.RootFolder;
import java.awt.Component;
import java.awt.Font;
import java.io.File;
import java.util.Date;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileSystemView;

/**
 * Classe para desenho da tabela de arquivos criptografados da tela principal do
 * programa. A característica desta classe é formatar as datas e tamanho do 
 * arquivo e a associação de ícone padrão do sistema para aquele tipo de arquivo
 * em cada linha da tabela.
 * @author Leandro Aparecido de Almeida
 */
class DefaultTableCellRenderer implements javax.swing.table.TableCellRenderer {
    
    public static final int ICON_SIZE = 32;
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
    boolean isSelected, boolean hasFocus, int row, int column) {
        //Componente de exibição é uma JLabel.
        JLabel label = new JLabel();
        label.setOpaque(true);
        Font font = new Font("tahoma", Font.PLAIN, 12);
        label.setFont(font); 
        label.setBorder(null);
        switch (column) {
            //Coluna 1: Ícone e nome do arquivo.
            case 1: {                
                label.setText((String)value); 
                if ((boolean)table.getValueAt(row, 0)) {                    
                    try {
                        String fileName = (String)value;
                        String extension = null;
                        int idx = fileName.lastIndexOf(".");
                        if (idx != -1) {
                            extension = fileName.substring(idx, fileName.length());
                        }
                        //Cria um arquivo vazio no diretório thumbnails, e extrai
                        //o ícone do sistema para este arquivo. Cada extensão
                        //de arquivo terá um arquivo vazio associado com o nome
                        //thumbnail.[extensão do arquivo]. Ex.: thumbnail.pdf
                        String tmpFileName = RootFolder.getThumbnailsFolder()
                        .getAbsolutePath() + File.separator + "thumbnail" +
                        (extension != null ? extension : "");
                        File tmpFile = new File(tmpFileName);
                        if (!tmpFile.exists()) {
                            tmpFile.createNewFile();
                        }
                        Icon icon = FileSystemView.getFileSystemView()
                        .getSystemIcon(tmpFile, ICON_SIZE, ICON_SIZE);
                        label.setIcon(icon);                        
                    } catch (Exception ex) {  
                    }
                    label.setText((String)value);
                } else {
                    Icon icon = FileSystemView.getFileSystemView()
                    .getSystemIcon(RootFolder.getThumbnailsFolder(), ICON_SIZE,
                    ICON_SIZE);
                    label.setIcon(icon);                
                }
            } break;
            //Coluna 2: Tamanho do arquivo (em byte, kilobyte, megabyte).
            case 2: {
                if ((boolean)table.getValueAt(row, 0)) {
                    label.setText(Formatter.formatSize((long)value) + "  ");
                    label.setHorizontalAlignment(SwingConstants.RIGHT);
                } else {
                    label.setText("");
                }
            } break;
            //Coluna 3: Data da criação do arquivo formatada como dd/mm/aaaa hh:mm
            case 3: {
                if ((boolean)table.getValueAt(row, 0)) {
                    label.setText(" " + Formatter.formatDate((Date)value));
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                } else {
                    label.setText("");
                }
            } break;
            //Coluna 4: Data da alteração do arquivo formatada como dd/mm/aaaa hh:mm
            case 4: {
                if ((boolean)table.getValueAt(row, 0)) {
                    label.setText(" " + Formatter.formatDate((Date)value) + " ");
                    label.setHorizontalAlignment(SwingConstants.LEFT);
                } else {
                    label.setText("");
                }
            } break;
        }
        if (isSelected) {
            label.setBackground(table.getSelectionBackground());
            label.setForeground(table.getSelectionForeground());
        } else {
            label.setForeground(table.getForeground());
            label.setBackground(table.getBackground());
        }        
        return label;
    }
    
}