package bravo.install;

import bravo.environment.RootFolder;
import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import net.jimmc.jshortcut.JShellLink;

public class Installer {
    
    public void install() throws FileNotFoundException, IOException {
        String OSName = System.getProperty("os.name").toLowerCase();
        if (OSName.startsWith("win")) {
            installWindows();
        } else if (OSName.startsWith("linux")) {
            //Não implementado.
        } else if (OSName.startsWith("mac")) {
            //Não implementado.
        }
    }
    
    private void installWindows() throws FileNotFoundException, IOException {
        String rootPath = RootFolder.getAbsolutePath();
        File bravoIcon = new File(rootPath + "\\Bravo.ico");
        File fileIcon = new File(rootPath + "\\File.ico");
        File BATFile = new File(rootPath + "\\Bravo.bat");
        File REGFile = new File(System.getProperty("java.io.tmpdir") + "\\bravo.reg");
        //Extrair ícone do programa.
        extractResource("/bravo/resources/Bravo.ico", bravoIcon);
        //Extrair ícone do arquivo bravo.
        extractResource("/bravo/resources/File.ico", fileIcon);
        //Criar o arquivo BAT para execução do programa.
        {
            StringBuilder sb = new StringBuilder();
            sb.append("@echo off");
            sb.append("\n");
            sb.append("chcp 1252");
            sb.append("\n");
            sb.append("goto startapp");
            sb.append("\n");
            sb.append(":startapp");
            sb.append("\n");
            sb.append("    start javaw.exe -Xms512m -Xmx2048m -Xss20m -jar \"%~dp0Bravo.jar\" %1 %2 %3 %4 %5");
            sb.append("\n");
            sb.append("goto end");
            sb.append("\n");
            sb.append(":end");
            try (FileOutputStream outputStream = new FileOutputStream(BATFile, false)) {
                outputStream.write(sb.toString().getBytes());
                outputStream.flush();
            }
        }
        //Criar o arquivo de registro do Windows.
        {
            StringBuilder sb = new StringBuilder();            
            sb.append("Windows Registry Editor Version 5.00");
            sb.append("\n\n");
            sb.append("[HKEY_CLASSES_ROOT\\.bar]");
            sb.append("\n");
            sb.append("@=\"bar_file\"");
            sb.append("\n\n");
            sb.append("[HKEY_CLASSES_ROOT\\bar_file]");
            sb.append("@=\"Arquivo criptografado\"");
            sb.append("\n\n");
            sb.append("[HKEY_CLASSES_ROOT\\bar_file\\shell]");
            sb.append("\n\n");
            sb.append("[HKEY_CLASSES_ROOT\\bar_file\\shell\\open]");
            sb.append("\n\n");
            sb.append("[HKEY_CLASSES_ROOT\\bar_file\\shell\\open\\command]");
            sb.append("\n");
            sb.append("@=\"\\\"");
            sb.append(rootPath.replace("\\", "\\\\"));
            sb.append("\\\\Bravo.bat\\\" \\\"%1\\\" %*\"");
            sb.append("\n\n");
            sb.append("[HKEY_CLASSES_ROOT\\bar_file\\DefaultIcon]");
            sb.append("\n");
            sb.append("@=\"");
            sb.append(rootPath.replace("\\", "\\\\"));
            sb.append("\\\\File.ico\"");
            try (FileOutputStream outputStream = new FileOutputStream(REGFile, false)) {
                outputStream.write(sb.toString().getBytes());
                outputStream.flush();
            }
            Desktop.getDesktop().open(REGFile);
        }
        {
            JShellLink link = new JShellLink();
            String filePath = JShellLink.getDirectory("") + BATFile.getAbsolutePath();
            link.setFolder(JShellLink.getDirectory("desktop"));
            link.setName("Bravo");
            link.setPath(filePath);
            link.setIconLocation(bravoIcon.getAbsolutePath());
            link.setDescription("Programa de criptografia");
            link.save();
        }
    }
    
    private void extractResource(String resourcePath, File destinationFile) throws IOException {
        try (InputStream inputStream = this.getClass().getResourceAsStream(resourcePath); 
        FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[4096];
            int length;
            while((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
                outputStream.flush();
            }
        }
    }
    
}