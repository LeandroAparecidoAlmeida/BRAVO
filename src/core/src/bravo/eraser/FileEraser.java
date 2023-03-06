package bravo.eraser;

import bravo.file.CipherListener;
import bravo.filter.DirectoryFilter;
import bravo.filter.FileComparator;
import bravo.filter.FileFilter;
import bravo.file.FileOperation;
import bravo.file.ProcessListener;
import bravo.environment.RootFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Classe para destruição de arquivos em disco. Implementa diversos métodos de
 * sobrescrita de arquivos presentes no mercado.
 * @author Leandro
 */
public class FileEraser {
    
    /**Random mode which repeats a 1KByte random pattern.*/
    public final int RANDOM_PATTERN = 0;
    /**Random mode which fills the file with {@link java.util.Random} bytes.*/
    public final int RANDOM = 1;
    /**Random mode which fills the file with {@link java.security.SecureRandom} bytes.*/
    public final int RANDOM_SECURE = 2;
    /**Default number of random passes.*/
    private final int RANDOM_PASSES = 10;
    private final int BUFFER_SIZE = 1024;
    /**Byte 0x00 = 00000000  (complement of 0xFF).*/
    private final byte BYTE_00 = (byte)0x00;
    /**Byte 0x55 = 01010101 (complement of 0xAA).*/
    private final byte BYTE_55 = (byte)0x55;
    /**Byte 0xAA = 10101010 (complement of 0x55).*/
    private final byte BYTE_AA = (byte)0xAA;
    /**Byte 0xFF = 11111111 (complement of 0x00).*/
    private final byte BYTE_FF = (byte)0xFF;
    /**First pattern for MFM encoding (1,3)RLL with time bit 0 (sequence #5).*/
    private final byte PATTERN_5 = BYTE_55;
    /**Second pattern for MFM encoding (1,3)RLL with time bit 0 (sequence #6).*/
    private final byte PATTERN_6 = BYTE_AA;
    /**First pattern for MFM encoding (1,3)RLL with 3 time bits (sequence #7 + #26).*/
    private final byte[] PATTERN_7_26 = {(byte)0x92, (byte)0x49, (byte)0x24};
    /**Second pattern for MFM encoding (1,3)RLL with 3 time bits (sequence #8 + #27).*/
    private final byte[] PATTERN_8_27 = {(byte)0x49, (byte)0x24, (byte)0x92};
    /**Thrid pattern for MFM encoding (1,3)RLL with 3 time bits (sequence #9 + #28)*/
    private final byte[] PATTERN_9_28 = {(byte)0x24, (byte)0x92, (byte)0x49};
    /**Single byte patterns for sequence #10 to #25 of Gutmann algorithm this 
     *array includes 16 patterns*/
    private final byte[] PATTERN_ARRAY_1_BYTE = new byte[] {(byte)0x00, (byte)0x11,
    (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55, (byte)0x66, (byte)0x77, (byte)0x88,
    (byte)0x99, (byte)0xAA, (byte)0xBB, (byte)0xCC, (byte)0xDD, (byte)0xEE, (byte)0xFF};
    /**Pattern for sequence #29 encoding (2,7)RLL.*/
    private final byte[] PATTERN_29 = {(byte)0x6D, (byte)0xB6, (byte)0xDB};
    /**Pattern for sequence #29 encoding (2,7)RLL.*/
    private final byte[] PATTERN_30 = {(byte)0xB6, (byte)0xDB, (byte)0x6D};
    /**Pattern for sequence #29 encoding (2,7)RLL.*/
    private final byte[] PATTERN_31 = {(byte)0xDB, (byte)0x6D, (byte)0xB6};
    /**Triple byte patterns for sequence #7 to #9 and #26 to #31 of Gutmann 
     * algorithmthis array includes 9 patterns*/
    private final byte[][] PATTERN_ARRAY_3_BYTES = new byte[][] {PATTERN_7_26, 
    PATTERN_8_27, PATTERN_9_28, PATTERN_29, PATTERN_30, PATTERN_31};
    private final WipeMethod method;
    private final Object[] params;
    /**Ouvintes do processamento de arquivos.*/
    private final List<ProcessListener> processlisteners;
    private final List<CipherListener> cipherListeners;
    /**Percentual de processamento de um arquivo.*/
    private int filePercentage;
    /**Percentual de processamento do conjunto de arquivos.*/
    private int totalPercentage;
    /**Número total de bytes em processamento.*/
    private long totalBytesCounter;
    /**Número total de bytes do arquivo em processamento.*/
    private long fileBytesCounter;
    /**Número de bytes processados do arquivo.*/
    private long processedFileBytes;
    /**Número total de bytes processados.*/
    private long processedTotalBytes;

    public FileEraser(WipeMethod method, Object... params) {
        processlisteners = new ArrayList<>();
        cipherListeners = new ArrayList<>();
        this.method = method;
        this.params = params;
    }
    
    public FileEraser() {
        this(WipeMethod.DOD522022M_ALGORITHM, true);
    }
    
    public int getPassesByMethod() {
        int passes = 0;
        switch (method) {
            case BYTE: passes = 1; break;
            case RANDOM_BYTES: passes = (params.length == 0 ? RANDOM_PASSES : (int)params[1]); break;
            case BRUCE_SCHNEIER_ALGORITHM: passes = 7; break;
            case DOD522022M_ALGORITHM: passes = ((boolean) params[0] ? 7 : 3); break;
            case GUTMANN_ALGORITHM: passes = 35; break;
            case VSITR_ALGORITHM: passes = 8; break;
        }
        return passes;
    }

    public void wipeCacheFiles() throws Exception {
        try {
            File cacheFolder = RootFolder.getSessionFolder();
            List<File> list = new ArrayList<>();
            list.add(cacheFolder);
            reset(calculateSize(list, getPassesByMethod()));
            wipe(list);
        } finally {
            for (ProcessListener listener : processlisteners) {
                listener.done();
            }
        }
    }
    
    public void wipeFiles(List<File> filesAndFolders) throws Exception {
        try {
            reset(calculateSize(filesAndFolders, getPassesByMethod()));
            wipe(filesAndFolders);
        } finally {
            for (ProcessListener listener : processlisteners) {
                listener.done();
            }
        }
    }
    
    private void wipe(List<File> filesAndFolders) throws Exception {
        List<File> filesList = new ArrayList<>();
        List<File> foldersList = new ArrayList<>();
        for (File file : filesAndFolders) {
            if (file.isDirectory()) {
                foldersList.add(file);
                listFilesFromFolder(file, filesList);
                List<File> subfolders = new ArrayList<>();
                listSubfoldersFromFolder(file, subfolders);
                for (File subfolder : subfolders) {
                    listFilesFromFolder(subfolder, filesList);
                }
                foldersList.addAll(subfolders);
            } else {
                filesList.add(file);
            }
        }
        for (File file : filesList) {            
            wipeFile(file);
        }
        foldersList.sort(new FileComparator());
        for (int i = foldersList.size() -1 ; i >= 0; i--) {
            foldersList.get(i).delete();
        }
    }
    
    private void wipeFile(File file) throws Exception {
        Files.setAttribute(file.toPath(), "dos:readonly", false);
        int passes = getPassesByMethod();
        updateFileInProcess(file.getAbsolutePath(), file.length(), passes);
        switch (method) {
            case BYTE: {
                wipeFileWithByte(file, (byte) params[0]);                
            } break;
            case RANDOM_BYTES: {
                switch (params.length) {
                    case 0: wipeFileWithRandomBytes(file); break;
                    case 2: wipeFileWithRandomBytes(file, (int)params[0], (int)params[1]); break;
                }
            } break;
            case BRUCE_SCHNEIER_ALGORITHM: {
                wipeFileWithBruceSchneierAlgorithm(file);
            } break;
            case DOD522022M_ALGORITHM: {
                wipeFileWithDoD522022MAlgorithm(file, (boolean) params[0]);
            } break;
            case GUTMANN_ALGORITHM: {
                wipeFileWithGutmannAlgorithm(file, (boolean) params[0]);
            } break;
            case VSITR_ALGORITHM: {
                wipeFileWithVSITRAlgorithm(file);
            } break;
        }
    }
    
    /**
     * Sobrescrever o arquivo usando um byte determinado. 
     * @param file arquivo a ser sobrescrito.
     * @param data byte a sobrescrever o arquivo
     * @throws IOException erro ao ler o arquivo.
     */
    private void wipeFileWithByte(File file, byte data) throws IOException {
        writeFileWithOneByte(file, data);
        unNameAndDeleteFile(file);
    }

    /**
     * Sobrescrever o arquivo com bytes aleatórios em 10 passos.
     * @param file arquivo a ser sobrescrito.
     */
    private void wipeFileWithRandomBytes(File file) throws IOException {
        writeFileWithRandomBytes(file, RANDOM, RANDOM_PASSES);
        unNameAndDeleteFile(file);
    }

    /**
     * Sobrescrever o arquivo com bytes aleatórios.
     * @param file arquivo a ser sobrescrito.
     * @param mode modo de sobrescrita RANDOM_PATTERN, RANDOM ou RANDOM_SECURE.
     * @param passes número de vezes que o arquivo será sobrescrito.
     */
    private void wipeFileWithRandomBytes(File file, int mode, int passes) throws IOException {
        writeFileWithRandomBytes(file, mode, passes);
        unNameAndDeleteFile(file);
    }

    /**
     * Sobrescrever o arquivo usando o algoritmo de Peter Gutmann.
     * @param file arquivo a ser sobrescrito.
     * @param floppyMode if true, only random and floppy passes will be written
     */
    private void wipeFileWithGutmannAlgorithm(File file, boolean floppyMode) throws IOException {
        writeFileWithRandomBytes(file, 4);
        Integer[] sequence = generateSequence(floppyMode);
        for(int i = 0; i < sequence.length; i++) {
            if (sequence[i] == 5) {
                writeFileWithOneByte(file, PATTERN_5);
            } else if (sequence[i] == 6) {
                writeFileWithOneByte(file, PATTERN_6);
            } else if (sequence[i] < 10) {
                writeFileWithThreeBytes(file, PATTERN_ARRAY_3_BYTES[sequence[i]-7]);
            } else if (sequence[i] < 26) {
                // not used for floppy mode
                writeFileWithOneByte(file, PATTERN_ARRAY_1_BYTE[sequence[i]-10]);
            } else {
                // not used for floppy mode
                writeFileWithThreeBytes(file, PATTERN_ARRAY_3_BYTES[sequence[i]-26]);
            }
        }
        writeFileWithRandomBytes(file, 4);
        unNameAndDeleteFile(file);
    }

    /**
     * Sobrescrever o arquivo usando o algoritmo VSITR padrão do BSI, Alemanha.<br>
     * 
     * BSI   = Bundesamt fuer Sicherheit in der Informationstechnik
     *         (German Federal Office for IT Security)<br>
     * VSITR = Verschlusssachen-IT-Richtlinien<br>
     * 
     * @param file arquivo a ser sobrescrito.
     */
    private void wipeFileWithVSITRAlgorithm(File file) throws IOException {
        writeFileWithOneByte(file, BYTE_00);
        writeFileWithOneByte(file, BYTE_FF);
        writeFileWithOneByte(file, BYTE_00);
        writeFileWithOneByte(file, BYTE_FF);
        writeFileWithOneByte(file, BYTE_00);
        writeFileWithOneByte(file, BYTE_FF);
        writeFileWithOneByte(file, BYTE_AA);
        writeFileWithRandomBytes(file, 1);
        unNameAndDeleteFile(file);
    }

    /**
     * Sobrescrever o arquivo usando o algoritmo de Bruce Schneier's.
     * @param file arquivo a ser sobrescrito.
     */
    private void wipeFileWithBruceSchneierAlgorithm(File file) throws IOException {
        writeFileWithOneByte(file, BYTE_00);
        writeFileWithOneByte(file, BYTE_FF);
        writeFileWithRandomBytes(file, 5);
        unNameAndDeleteFile(file);
    }

    /**
     * Sobrescrever o arquivo utillizando o algoritmo DoD, padrão 5220.22-M do
     * departamento de defesa dos Estados Unidos.
     * <br>
     * Versão padrão: sobrescreve o arquivo 3 vezes - 520.22-M (E)<br>
     * Versão extendida: sobrescreve o arquivo 7 vezes - 520.22-M (ECE)<br>
     * <br>
     * @param file arquivo a ser sobrescrito.
     * @param extended se true, usa a versão extendida (7 vezes), senão usa a
     * versão padrão (3 vezes).
     */
    private void wipeFileWithDoD522022MAlgorithm(File file, boolean extended) throws IOException {
        if (extended) {
            writeFileWithRandomBytes(file, 1);
            writeFileWithOneByte(file, BYTE_55);
            writeFileWithOneByte(file, BYTE_AA);
            writeFileWithRandomBytes(file, 1);
            writeFileWithOneByte(file, BYTE_00);
            writeFileWithOneByte(file, BYTE_FF);
            writeFileWithRandomBytes(file, 1);
            unNameAndDeleteFile(file);
        } else {
            writeFileWithOneByte(file, BYTE_00);
            writeFileWithOneByte(file, BYTE_FF);
            writeFileWithRandomBytes(file, 1);
            unNameAndDeleteFile(file);
        }
    }

    /**
     * Sobrescrever o arquivo com bytes aleatórios.
     * @param file arquivo a ser sobrescrito.
     * @param passes número de passos.
     * @return true, se o arquivo foi sobrescrito, false, se não.
     */
    private boolean writeFileWithRandomBytes(File file, int passes) throws IOException {
        return writeFileWithRandomBytes(file, RANDOM, passes);
    }

    /**
     * Sobrescrever o arquivo com bytes aleatórios.
     * @param file arquivo a ser sobrescrito.
     * @param mode modo de sobrescrita.
     * @param passes número de passos.
     * @return true, se o arquivo foi sobrescrito, false se não.
     */
    private boolean writeFileWithRandomBytes(File file, int mode, int passes) throws IOException {
        boolean shredOK = true;
        switch (mode) {
            case RANDOM: {
                for (int i = 0; i < passes; i++) {
                    shredOK = shredOK && writeFileWithRandomBytes(file);
                }   
            } break;
            case RANDOM_SECURE: {
                for (int i = 0; i < passes; i++) {
                    shredOK = shredOK && writeFileWithSecureRandomBytes(file);
                }
            } break;
            case RANDOM_PATTERN: {
                for (int i = 0; i < passes; i++) {
                    shredOK = shredOK && writeFileWithRandomPatterns(file);
                }
            } break;
            default: {
                shredOK = false;
            } break;
        }
        return shredOK;
    }

    /**
     * Sobrescreve o arquivo com um byte determinado.
     * @param file arquivo a ser sobrescrito.
     * @param data byte a preencher o arquivo.
     * @return true, se o arquivo foi sobrescrito, false se não.
     */
    private boolean writeFileWithOneByte(File file, byte data) throws IOException {
        byte[] patternArray = new byte[BUFFER_SIZE];
        long loops = file.length() / BUFFER_SIZE;
        int rest = (int)(file.length() % BUFFER_SIZE);
        Arrays.fill(patternArray, data);
        // Open or create the output file
        try (FileOutputStream os = new FileOutputStream(file, false)) {       
            for (long i = 1; i <= loops; i++) {
                os.write(patternArray);
                if(i % 1024 == 0) {
                    os.flush(); // flush each MB to file
                }                
                update(patternArray.length);
            }
            if (rest != 0) {
                os.write(patternArray, 0, rest);
                os.flush();
                update(rest);                       
            }        
        }
        return true;
    }

    /**
     * Sobrescrever o arquivo com um padrão de 3 bytes definidos.
     * @param file arquivo a ser sobrescrito.
     * @param data padrão de 3 bytes.
     * @return true, se o arquivo foi sobrescrito, false se não.
     */
    private boolean writeFileWithThreeBytes(File file, byte[] data) throws IOException {
        if (data.length != 3) {
            throw new IOException(
                "Use this method only with a 3 bytes pattern!"
            );
        }
        int arrayLength = 1023; // multiple of 3 (else NullpointerException!!!)
        byte[] patternArray = new byte[arrayLength];
        long loops = file.length() / arrayLength;
        int rest = (int) (file.length() % arrayLength);
        for (int i = 0; i < arrayLength; i = i + 3) {
            patternArray[i] = data[0];
            patternArray[i+1] = data[1];
            patternArray[i+2] = data[2];
        }
        try (FileOutputStream os = new FileOutputStream(file, false)) {
            for (long i = 1; i <= loops; i++) {
                os.write(patternArray);
                if(i % 1025 == 0) {
                    os.flush(); // flush +- each MB to file (1023*1025)
                }
                update(patternArray.length);
            } if (rest != 0) {
                os.write(patternArray, 0, rest);
                os.flush();
                update(rest);
            }        
        }
        return true;
    }

    /**
     * Sobrescrever o arquivo com um padrão aleatório de bytes.
     * @param file arquivo a ser sobrescrito.
     * @return true, se o arquivo foi sobrescrito, false se não.
     */
    private boolean writeFileWithRandomPatterns(File file) throws IOException{
        byte[] patternArray = new byte[BUFFER_SIZE];
        long loops = file.length() / BUFFER_SIZE;
        int rest = (int) (file.length() % BUFFER_SIZE);
        Random random = new Random();
        random.nextBytes(patternArray);
        // Open or create the output file
        try (FileOutputStream os = new FileOutputStream(file, false)) {
            for (long i = 1; i <= loops; i++) {
                os.write(patternArray);
                if(i % 1024 == 0) {
                    os.flush(); // flush each MB to file (1024*1024)
                }
                update(patternArray.length);
            }
            if (rest != 0) {
                os.write(patternArray, 0, rest);
                os.flush();
                update(rest);
            }        
        }
        return true;
    }

    /**
     * Sobrescrever o arquivo com bytes aleatórios.
     * @param file arquivo a ser sobrescrito.
     * @return true, se o arquivo foi sobrescrito, false se não.
     */
    private boolean writeFileWithRandomBytes(File file) throws IOException {
        byte[] patternArray = new byte[BUFFER_SIZE];
        long loops = file.length() / BUFFER_SIZE;
        int rest = (int) (file.length() % BUFFER_SIZE);
        Random random = new Random();
        // Open or create the output file
        try (FileOutputStream os = new FileOutputStream(file, false)) {
            for (long i = 1; i <= loops; i++) {
                random.nextBytes(patternArray);
                os.write(patternArray);
                if(i % 1024 == 0) {
                    os.flush(); // flush each MB to file (1024*1024)
                }
                update(patternArray.length);
            }
            if (rest != 0) {
                random.nextBytes(patternArray);
                os.write(patternArray, 0, rest);
                os.flush();
                update(rest);
            }        
        }
        return true;
    }

    /**
     * Sobrescrever o arquivo com bytes aleatórios gerados em um algoritmo
     * seguro.
     * @param file arquivo a ser sobrescrito.
     * @return true, se o arquivo foi sobrescrito, false se não.
     */
    private boolean writeFileWithSecureRandomBytes(File file) throws IOException {
        byte[] patternArray = new byte[BUFFER_SIZE];
        long loops = file.length() / BUFFER_SIZE;
        int rest = (int) (file.length() % BUFFER_SIZE);
        java.security.SecureRandom random = new java.security.SecureRandom();
        // Open or create the output file
        try (FileOutputStream os = new FileOutputStream(file, false)) {
            for (long i = 1; i <= loops; i++) {
                random.nextBytes(patternArray);
                os.write(patternArray);
                if(i % 1024 == 0) {
                    os.flush(); // flush each MB to file (1024*1024)
                }
                update(patternArray.length);
            }
            if (rest != 0) {
                random.nextBytes(patternArray);
                os.write(patternArray, 0, rest);
                os.flush();
                update(rest);
            }        
        }
        return true;
    }

    /**
     * Gerar uma sequência de inteiros.
     * @param floppyMode modo
     * @return sequência de inteiros.
     */
    private Integer[] generateSequence(boolean floppyMode) {
        Integer[] sequence;
        if (floppyMode) {
            // create sequence for floppy-tailored Gutmann algorithm
            sequence = new Integer[]{5, 5, 6, 6, 7, 7, 8, 8, 9, 9};
        } else {
            sequence = new Integer[]{5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
            31};
        }
        // shuffle the sequence using secure random
        List<Integer> list = Arrays.asList(sequence);
        Collections.shuffle(list);
        sequence = list.toArray(new Integer[0]);
        return sequence;
    }

    private boolean unNameAndDeleteFile(File file) throws IOException {
        int fileNameLength = file.getName().length();
        file = generateShorterFilename(file, 0);
        while (fileNameLength > 1) {
            file = generateShorterFilename(file, 1);
            fileNameLength--;
        }
        file.delete();
        return true;
    }

    private File generateShorterFilename(File file, int shorten) throws IOException {
        boolean isFile = file.isFile();
        // save directory (static part of file path)
        String filePath = file.getParent() + "/";
        int nameLength = file.getName().length();
        int point = nameLength - file.getName().lastIndexOf(".") - 1;
        nameLength = nameLength - shorten;
        int position = 0;
        String newName = "";
        Random random = new Random();
        int randomInt;
        char randomChar;
        boolean renamed;
        while(position < nameLength) {
            if (position == point && isFile) {
                newName = "." + newName;
                position++;
            } else {
                randomInt = random.nextInt(51);
                if (randomInt < 25) {
                    randomInt = randomInt + 65;
                } else {
                    randomInt = randomInt + 72; // 97 - 25 = 72
                }
                randomChar = (char)randomInt;
                newName = randomChar + newName;
                position++;
            }
        }
        if ((point == nameLength) && (nameLength != 1) && isFile) {
            newName = "." + newName.substring(1, newName.length());
        }        
        File test = new File(filePath + newName);
        if (test.exists()) {
            return generateShorterFilename(file, shorten);            
        } else {
            renamed = file.renameTo(new File(filePath + newName));
            if (!renamed) {
                throw new IOException("File could not be renamed");
            }
            return new File (filePath + newName);
        }
    }

    private void update(long length) {
        if (fileBytesCounter > 0) {
            processedFileBytes += length;
            int percentage = (int)((processedFileBytes * 100) / fileBytesCounter);
            if (percentage > filePercentage) {
                filePercentage = percentage;
                for (ProcessListener listener : processlisteners) {
                    listener.updateFilePercentage(filePercentage);
                    listener.updateTotalPercentage(totalPercentage);
                }
            }
        }
        if (totalBytesCounter > 0) {
            processedTotalBytes += length;
            int percentage = (int)((processedTotalBytes * 100) / totalBytesCounter);
            if (percentage > totalPercentage) {
                totalPercentage = percentage;
                for (ProcessListener listener : processlisteners) {
                    listener.updateFilePercentage(filePercentage);
                    listener.updateTotalPercentage(totalPercentage);
                }
            }
        }
        for (CipherListener listener : cipherListeners) {
            listener.update(length);
        }
    } 
    
    private void reset(long totalBytesCounter) {
        this.totalBytesCounter = totalBytesCounter;
        processedTotalBytes = 0;
        totalPercentage = 0;
    }
    
    private void updateFileInProcess(String fileName, long fileSize, int passes) {
        processedFileBytes = filePercentage = 0;
        fileBytesCounter = fileSize * passes;
        for (ProcessListener listener : processlisteners) {
            listener.updateFile(fileName, FileOperation.WIPE);
            listener.updateFilePercentage(filePercentage);
        }
    }
    
    private void listFilesFromFolder(File folder, List<File> files) {
        Collections.addAll(files, folder.listFiles(new FileFilter()));
    }
    
    private void listSubfoldersFromFolder(File folder, List<File> subfolders) {
        File[] folders = folder.listFiles(new DirectoryFilter());
        if (folders != null) {
            for (File subfolder : folders) {
                subfolders.add(subfolder);
                listSubfoldersFromFolder(subfolder, subfolders);
            }
        }
    }

    private long calculateSize(List<File> filesList, int passes) throws Exception {
        List<File> files = new ArrayList<>();
        for (File file : filesList) {
            if (file.isDirectory()) {
                listFilesFromFolder(file, files);
                List<File> folders = new ArrayList<>();
                listSubfoldersFromFolder(file, folders);
                for (File folder : folders) {
                    listFilesFromFolder(folder, files);
                }
            } else {
                files.add(file);
            }
        }
        long size = 0;
        for (File file : files) {
            size += (file.length() > 0 ? file.length() * passes : 0);
        }        
        return size;
    }
    
    public void addProcessListener(ProcessListener listener) {
        if (!processlisteners.contains(listener)) {
            processlisteners.add(listener);
        }
    }
    
    public void removeProcessListener(ProcessListener listener) {
        int idx = -1;
        for (int i = 0; i < processlisteners.size(); i++) {
            if (processlisteners.get(i) == listener) {
                idx = i;
                break;
            }
        }
        processlisteners.remove(idx);
    }
    
    public void addCipherListener(CipherListener listener) {
        if (!cipherListeners.contains(listener)) {
            cipherListeners.add(listener);
        }
    }
    
    public void removeCipherListener(CipherListener listener) {
        int idx = -1;
        for (int i = 0; i < cipherListeners.size(); i++) {
            if (cipherListeners.get(i) == listener) {
                idx = i;
                break;
            }
        }
        cipherListeners.remove(idx);
    }
    
}