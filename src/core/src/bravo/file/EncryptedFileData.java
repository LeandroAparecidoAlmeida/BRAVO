package bravo.file;

import java.io.File;

/**
 *
 * @author Leandro
 */
class EncryptedFileData {

    /**Arquivo de entrada do processo de encriptação.*/
    private File inputFile;
    private FileMetadata fileMetadata;

    public EncryptedFileData(File inputFile, FileMetadata fileMetadata) {
        this.inputFile = inputFile;
        this.fileMetadata = fileMetadata;
    }
    
    public EncryptedFileData(FileMetadata fileMetadata) {
        this(null, fileMetadata);
    }

    public File getInputFile() {
        return inputFile;
    }

    public FileMetadata getFileMetadata() {
        return fileMetadata;
    }
     
}