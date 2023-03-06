package bravo.file;

import bravo.filter.EncryptedFileComparator;
import bravo.environment.RootFolder;
import bravo.filter.FileFilter;
import bravo.filter.DirectoryFilter;
import bravo.eraser.FileEraser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import java.nio.file.attribute.FileTime;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.io.Closeable;
import java.util.Arrays;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import org.bouncycastle.util.encoders.Base64;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import net.lingala.zip4j.model.FileHeader;
import static bravo.file.BravoCipher.IV_LENGTH;
import static bravo.file.BravoCipher.BUFFER_SIZE;
import static bravo.file.FileOperation.ADD;
import static bravo.file.FileOperation.REMOVE;
import static bravo.file.FileOperation.WIPE;
import static bravo.file.FileOperation.EXTRACT;
import static bravo.file.FileOperation.ENCRYPT;
import static bravo.utils.ArrayUtils.byteArrayToInt;
import static bravo.utils.ArrayUtils.intToByteArray;
import bravo.utils.MemoryInfo;

/**
 * Classe para gerenciamento do arquivo criptografado. Um arquivo criptografado
 * por este programa é um arquivo no formato <i>ZIP</i>, sem compressão, e com
 * uma estrutura pré-definida, a saber:<br><br>
 * 
 * METADADOS DO ARQUIVO:<br>
 * 
 * Há uma pasta no ZIP chamada <i>METADATA</i> aonde ficam gravados os aquivos
 * de metadados para a recuperação dos dados criptografados inseridos. Os arquivos
 * de metadados são:<br><br>
 * 
 * <b>METADATA/File Metadata 0001.dat:</b> Neste arquivo está gravado um arranjo
 * de objetos {@link bravo.file.FileMetadata} que guardam dados sobre os 
 * arquivos adicionados ao zip, como nome, tamanho original, data de criação, 
 * data da última atualização. Também são gravados os dados que foram utilizados
 * para a encriptação dos arquivos como SALT bytes, IV bytes e parâmetros para o
 * algoritmo ARGON2. O arquivo <i>METADATA/File Metadata 0001.dat</i> é gerado a
 * partir da serialização de um arranjo <i>ArrayList</i> de objetos {@link bravo.file.FileMetadata}
 * e é criptografado na escrita e decriptogrado na leitura somente em memória RAM.
 * A encriptação deste arranjo é necessária para que fiquem ocultos os nomes originais
 * dos arquivos que foram inseridos no ZIP. Os nomes dos arquivos internamente 
 * são representados com a seguinte estrutura hierarquica:<br><br>
 * <b>/</b>Pasta1/Pasta2/Arquivo1
 * <br><br>
 * O primeiro nível (root) é representado por barra à direita (/). Segue-se pelo
 * diretório <i>Pasta1</i>, Subdiretório <i>Pasta2</i> e o arquivo <i>Arquivo1</i>.
 * É basicamente a mesma sintaxe comum aos sistemas de arquivos atuais, implementado
 * internamente ao ZIP e que guarda o caminho original do arquivo ao ser inserido
 * ao ZIP (preservando a estrutura hierarquica dos diretórios).<br><br>
 * 
 * <b>METADATA/File Metadata 0002.dat:</b> Arquivo que guarda dados para a 
 * validação da senha utilizada na encriptação dos arquivos inseridos no ZIP. O 
 * Hash para o algoritmo AES utilizado na encriptação dos arquivos tem 256 bits 
 * (32 bytes). O Hash gerado no processo de valição tem 256 bits + 64 bits (8 bytes),
 * e são estes 64 bits finais que vão determinar se se trata (provavelmente) da 
 * mesma senha utilizada na encriptação dos arquivos ou outra.<br><br>
 * <pre>
 * HASH DE SIMULAÇÃO (320 bits)
 * 
 *                      Validação (64 bits finais)
 *                      .............................
 * 0, 1, 2, 3,..., 255, [256, 257, 258, 259,..., 319]
 * </pre>
 * Ao testar a senha, compara-se os 8 bytes finais do hash gerado com os 8 bytes
 * gravados em <i>METADATA/File Metadata 0002.dat</i>. Se ambos forem os mesmos
 * bytes, provavelmente (há uma pequena probabilidade de uma senha incorreta
 * gerar os mesmos bytes finais) é a senha usada para criptografar os arquivos, 
 * caso não sejam os mesmos bytes, então não é a senha usada para criptografar os
 * arquivos.<br>
 * Como observado, é gerado um HASH de 320 bits para não colocar em risco a
 * segurança do sistema de criptografia. Serão necessárias mais chaves para 
 * descobrir os bytes finais de validação (2^320) do que quebrar o próprio HASH 
 * gerado para encriptar os arquivos (2^256).
 *
 * <b>METADATA/File Metadata 0003</b>: Arquivo que guarda o contador sequêncial de
 * entradas de arquivos. Cada arquivo que foi encriptado e inserido ao ZIP recebe
 * um nome com o padrão <i>Encrypted File XXXX</i>, onde <i>XXXX</i> é um número
 * inteiro com 4 dígitos utilizado para diferenciar os nomes dos arquivos inseridos.
 * Este número fica gravado no arquivo <i>METADATA/File Metadata 0003</i>
 * e é atualizado quando há a inserção de um novo arquivo ao ZIP e recuperado
 * na abertura do arquivo ZIP para continuidade da sequência<br><br>
 *
 * <b>METADATA/File Metadata 0004:</b> Arquivo que guarda a versão do arquivo 
 * encriptado. Como o processo de desenvolvimento do projeto segue o modelo
 * inclemental, possivelmente no futuro pode haver alterações na estrutura do
 * arquivo, método de encriptação, entre um sem-número de outras modificações que
 * podem tornar os arquivos mais antigos ilegíveis para versões mais atuais do
 * programa. Visando contornar estes problemas de incompatibilidade de versões,
 * fica registrado em cada arquivo criptografado o número da versão deste arquivo,
 * permitindo compatibilidade retroativa em futuras edições do programa.<br><br>
 * 
 * CONTEÚDO DO ARQUIVO:<br>
 * 
 * Os arquivos encriptados serão gravados no arquivo ZIP seguindo o padrão de
 * nomes <i>Encrypted File XXXX</i>, onde <i>XXXX</i> é um número inteiro sequêncial
 * com 4 dígitos. Por exemplo:<br><br>
 * 
 * Encrypted File 0001<br>
 * Encrypted File 0002<br>
 * Encrypted File 0003<br>
 * ...<br>
 * Encrypted File 9999<br><br>
 * 
 * O objetivo deste nome genérico é ocultar os nomes originais dos arquivos, que
 * estarão encriptados no arquivo <i>METADATA/File Metadata 0001</i>, preservando
 * assim o sigilo também dos nomes.<br>
 * Todos os arquivos encriptados estarão no nível <b>root</b> no ZIP, logo, a 
 * estrutura do zip ficará:<br><br>
 * METADATA<br>
 * Encrypted File 0001<br>
 * Encrypted File 0002<br>
 * Encrypted File 0003<br>
 * ...<br>
 * Encrypted File nnnn<br><br>
 * Onde, METADATA é a pasta dos arquivos de metadados e Encrypted File 0001,
 * Encrypted File 0002, etc, são os arquivos encriptados inseridos.<br><br>
 * 
 * BIBLIOTECAS DE TERCEIROS<br>
 * 
 * Para gravar e ler dados em um arquivo ZIP, a classe se utiliza da biblioteca
 * de classes denominada Zip4j de autoria de Srikanth Reddy Lingala, disponível
 * em <i>https://github.com/srikanth-lingala/zip4j</i>. A opção pela biblioteca
 * foi pela praticidade oferecida para gerenciamento do arquivo ZIP. Porém, não 
 * utiliza a função de criptografia interna à esta biblioteca, optando para isso 
 * pela API BouncyCastle disponível em <i>https://www.bouncycastle.org/</i>.
 * 
 * @author Leandro Aparecido de Almeida
 * @since 1.0
 * @version 1.0
 */
public final class BravoFile implements CipherListener, Closeable, AutoCloseable {
    
    /**Versão 1.0*/
    public static final int VERSION_1 = 100;
    /**Caracteres inválidos para o nome de arquivos e diretórios.*/
    public static final char[] INVALID_FILE_NAME_CHARACTERS = new char[]{'\\',
    '/', '|', ':', '*', '?', '"', '>', '<'};
    /**Separador interno de arquivos e pastas. Segue a convenção do pacote zip4j.*/
    public static final String SEPARATOR = net.lingala.zip4j.util.InternalZipConstants.ZIP_FILE_SEPARATOR;
    /**Pasta interna para metadados.*/
    private final String METADATA_FOLDER = "METADATA" + SEPARATOR;
    /**Arquivo contendo os cabeçalhos de arquivos encriptados.*/
    private final String METADATA_FILE_1 = METADATA_FOLDER + "File-table.dat";
    /**Arquivo contendo os bytes para validação da senha.*/
    private final String METADATA_FILE_2 = METADATA_FOLDER + "Test.dat";
    /**Arquivo contendo os bytes do contador de arquivos encriptados.*/
    private final String METADATA_FILE_3 = METADATA_FOLDER + "Index.dat";
    /**Arquivo contendo os bytes da versão do arquivo.*/
    private final String METADATA_FILE_4 = METADATA_FOLDER + "Version.dat";
    /**TAG para identificação de pasta vazia.*/
    private final String EMPTY_FOLDER_TAG = "[EMPTY_FOLDER_TAG]";
    /**Objeto da API Zip4J para leitura e escrita de arquivos em formato ZIP.*/
    private final ZipFile zipFile;
    /**Arquivo em disco.*/
    private final File bravoFile;
    /**Cabeçalhos de arquivos encriptados.*/
    private final ArrayList<FileMetadata> fileMetadataList;
    /**Ouvintes do processamento de arquivos (inserção/remoção/extração).*/
    private final List<ProcessListener> listeners;
    /**Lista de pastas de arquivos.*/
    private final List<String> folders;
    /**Objeto para encriptação de dados.*/
    private final BravoCipher bravoCipher;
    /**Senha para encriptação/decriptação de dados.*/
    private String password;
    /**Pasta selecionada como raiz para inserção de novos arquivos ao ZIP.*/
    private String rootFolder;
    /**Versão do arquivo aberto.*/
    private final int fileVersion;
    /**Contador sequêncial de arquivos encriptados.*/
    private int index = 0;
    /**Percentual de processamento do arquivo atual.*/
    private int filePercentage;
    /**Percentual total do processamento.*/
    private int totalPercentage;
    /**Número total de bytes do arquivo em processamento.*/
    private long fileLength;
    /**Número total de bytes em processamento.*/
    private long totalLength;
    /**Número de bytes processados do arquivo.*/
    private long fileBytesCounter;
    /**Número total de bytes processados.*/
    private long totalBytesCounter;
    /**Controle de interrupção do processamento de arquivos.*/
    private boolean abort;
    /**Bloqueio para interrupção do processamento de arquivos.*/
    private boolean blockAbort;
    /**Instância de FileChannel.*/
    private FileChannel fileChannel;
    /**Instância de FileLock.*/
    private FileLock fileLock;

    /**
     * Constructor padrão da classe. Ao instanciar, verifica se há o arquivo
     * ZIP criado, e se existir, carrega os dados deste arquivo para a classe.
     * Caso o arquivo não exista, será criado um novo.<br>
     * Ao carregar um arquivo existente, será feita a validação da senha, sendo
     * que se a mesma for inválida, haverá o lançamento de uma exceção e o
     * abortamento do processo de carga do arquivo.
     * @param file arquivo criptografado a ser criado ou carregado.
     * @param password senha do arquivo para encriptação/decriptação de dados.
     * @throws Exception erro na validação da senha ou na leitura do arquivo ZIP.
     */
    public BravoFile(File file, String password) throws Exception {
        bravoCipher = new BravoCipher();
        listeners = new ArrayList<>();
        folders = new ArrayList<>();
        zipFile = new ZipFile(file);
        this.bravoFile = file;
        this.password = password;
        if (!file.exists()) {
            //Criar um novo arquivo
            fileVersion = VERSION_1;
            fileMetadataList = new ArrayList<>();
            folders.add(SEPARATOR);
            writeTestBytes(this.password);
            writeVersion();
            writeIndex();
            writeComment("Encriptado com Bravo software.");            
        } else {
            //Abrir um arquivo existente.
            if (!isFilePassword(this.password)) {
                release();
                throw new Exception("Senha do arquivo está incorreta.");
            }
            fileVersion = readVersion();            
            index = readIndex();
            fileMetadataList = readFileMetadataList();
            folders.addAll(getFolders());
        }
        setRootFolder(SEPARATOR);
    }
    
    
//METADATA FILE MANAGEMENT//////////////////////////////////////////////////////
    
    /**
     * Gravar os cabeçalhos dos arquivos encriptados em <i>METADATA/File Metadata 0001.dat</i>.
     * Será realizada a encriptação desse arranjo de objetos antes da gravação
     * visando proteger os metadados dos arquivos encriptados para recuperação
     * somente com a senha correta.
     * @throws Exception erro ocorrido na gravação de <i>METADATA/File Metadata 0001.dat</i>.
     */
    private void writeFileMetadataList() throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(fileMetadataList);
        ByteArrayInputStream dataInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
        FileMetadata fileMetadata = new FileMetadata();
        bravoCipher.encrypt(dataInputStream, dataOutputStream, fileMetadata, password);
        ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream(16 + dataOutputStream.size());
        zipOutputStream.writeBytes(fileMetadata.getIVBytes());
        zipOutputStream.writeBytes(dataOutputStream.toByteArray());
        ByteArrayInputStream zipInputStream = new ByteArrayInputStream(zipOutputStream.toByteArray());
        ZipParameters parameters = getDefaultZipParameters();
        parameters.setFileNameInZip(METADATA_FILE_1);
        addStream(zipInputStream, parameters);
        folders.clear();
        folders.addAll(getFolders());
    }
    
    /**
     * Obter os cabeçalhos dos arquivos encriptados em <i>METADATA/File Metadata 0001.dat</i>.
     * @return cabeçalhos dos arquivos encriptados. Caso <i>METADATA/File Metadata 0001.dat</i>
     * ainda não tenha sido criado, retorna um array vazio.
     * @throws Exception erro ocorrido na leitura de <i>METADATA/File Metadata 0001.dat</i>.
     */
    private ArrayList<FileMetadata> readFileMetadataList() throws Exception {
        boolean exists = false;
        ArrayList<FileMetadata> object;
        try {
            release();
            for (FileHeader zipHeader : zipFile.getFileHeaders()) {
                if (zipHeader.getFileName().equals(METADATA_FILE_1)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) return new ArrayList<>();            
            try (InputStream zipInputStream = zipFile.getInputStream(zipFile.getFileHeader(METADATA_FILE_1))) {
                byte[] ivBytes = zipInputStream.readNBytes(IV_LENGTH);
                FileMetadata fileMetadata1 = new FileMetadata();
                fileMetadata1.setIVBytes(ivBytes);
                ByteArrayOutputStream encryptedObject = new ByteArrayOutputStream();
                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = zipInputStream.read(buffer)) != -1) {
                    encryptedObject.write(buffer, 0, length);
                }
                ByteArrayInputStream dataInputStream = new ByteArrayInputStream(encryptedObject.toByteArray());
                ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
                bravoCipher.decrypt(dataInputStream, dataOutputStream, fileMetadata1, password);
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dataOutputStream.toByteArray());
                try (ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
                    object = (ArrayList<FileMetadata>) objectInputStream.readObject();
                    for (FileMetadata fileMetadata2 : object) {
                        fileMetadata2.setZipHeader(zipFile.getFileHeader(fileMetadata2.getInternalFileName()));
                    }
                }
            }
        } finally {
            lock();
        }
        return object;
    }
    
    /**
     * Gravar o contador sequêncial para nomeação em <i>METADATA/File Metadata 0003.dat</i>.
     * @throws Exception erro ocorrido na gravação de <i>METADATA/File Metadata 0003.dat</i>.
     */
    private void writeIndex() throws Exception {
        ZipParameters parameters = getDefaultZipParameters();
        parameters.setFileNameInZip(METADATA_FILE_3);
        byte[] dataBytes = intToByteArray(index);
        ByteArrayInputStream zipInputStream = new ByteArrayInputStream(dataBytes);
        addStream(zipInputStream, parameters);
    }
    
    /**
     * Ler o contador sequêncial para nomeação de arquivos em <i>METADATA/File Metadata 0003.dat</i>
     * @return contador sequêncial para nomeação de arquivos.
     * @throws Exception erro ocorrido na leitura de <i>METADATA/File Metadata 0003.dat</i>.
     */
    private int readIndex() throws Exception {
        int counter;
        try {
            release();
            try (InputStream zipInputStream = zipFile.getInputStream(zipFile.getFileHeader(METADATA_FILE_3))) {
                byte[] dataBytes = zipInputStream.readAllBytes();
                counter = byteArrayToInt(dataBytes);
            }
        } finally {
            lock();
        }
        return counter;
    }
    
    private byte[] getTestBytes(byte[] bytes) {
        return new byte[] {
            bytes[0], 
            bytes[2], 
            bytes[5], 
            bytes[10], 
            bytes[12], 
            bytes[17], 
            bytes[23], 
            bytes[31]
        };
    }
    
    /**
     * Gravar os bytes para testagem da senha do arquivo em <i>METADATA/File Metadata 0002.dat</i>.
     * @param password senha do arquivo.
     * @throws Exception erro ocorrido na gravação de <i>METADATA/File Metadata 0002.dat</i>
     */
    private void writeTestBytes(String password) throws Exception {
        byte[] passwordHash1 = SHA256Hash.getBytes(password);
        byte[] passwordHash2 = SHA256Hash.getBytes(passwordHash1);
        byte[] testBytes = getTestBytes(passwordHash2);
        ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream(40);
        zipOutputStream.writeBytes(testBytes);
        ByteArrayInputStream zipInputStream = new ByteArrayInputStream(zipOutputStream.toByteArray());
        ZipParameters parameters = getDefaultZipParameters();
        parameters.setFileNameInZip(METADATA_FILE_2);
        addStream(zipInputStream, parameters);
    }
    
    /**
     * Testar se a senha passada como parâmetro é a senha usada no arquivo para
     * encriptação/decriptação de dados. Os parâmetros para a testagem estão 
     * gravados em <i>METADATA/File Metadata 0002.dat</i>.
     * @param password senha a ser testada.
     * @return true, é a senha correta do arquivo, false, não é a senha do arquivo.
     * @throws Exception erro ocorrido na leitura de <i>METADATA/File Metadata 0002.dat</i>.
     */
    private boolean isFilePassword(String password) throws Exception {
        boolean isPassword;
        try {
            release();
            try (InputStream zipInputStream = zipFile.getInputStream(zipFile.getFileHeader(METADATA_FILE_2))) {
                byte[] passwordHash1 = SHA256Hash.getBytes(password);
                byte[] passwordHash2 = SHA256Hash.getBytes(passwordHash1);
                byte[] testBytes = getTestBytes(passwordHash2);
                byte[] passwordBytes = zipInputStream.readAllBytes();
                isPassword = Arrays.equals(testBytes, passwordBytes);
            }
        } finally {
            lock();
        }
        return isPassword;
    }
    
    /**
     * Gravar o número da versão do arquivo em <i>METADATA/File Metadata 0004.dat</i>.
     * @throws Exception erro ocorrido na gravação de <i>METADATA/File Metadata 0004.dat</i>.
     */
    private void writeVersion() throws Exception {
        ZipParameters parameters = getDefaultZipParameters();
        parameters.setFileNameInZip(METADATA_FILE_4);
        byte[] dataBytes = intToByteArray(fileVersion);
        ByteArrayInputStream zipInputStream = new ByteArrayInputStream(dataBytes);
        addStream(zipInputStream, parameters);
    }
    
    /**
     * Ler o número da versão do arquivo em <i>METADATA/File Metadata 0004.dat</i>.
     * @return número da versão do arquivo.
     * @throws Exception erro ocorrido na leitura de <i>METADATA/File Metadata 0004.dat</i>.
     */
    private int readVersion() throws Exception {
        int version;
        try {
            release();
            try (InputStream zipInputStream = zipFile.getInputStream(zipFile.getFileHeader(METADATA_FILE_4))) {
                byte[] dataBytes = zipInputStream.readAllBytes();
                version = byteArrayToInt(dataBytes);
            }
        } finally {
            lock();
        }
        return version;
    }
    
    /**
     * Obter os parâmetros padrão para a geração do arquivo ZIP. Por padrão, o
     * arquivo ZIP não terá compressão, pois dados criptografados não guardam
     * padrões repetitivos (esperado), e não há encriptação no processo (pela
     * biblioteca), pois a criptografia será feita no programa de acordo com os 
     * padrões definidos nesta classe.
     * @return parâmetros padrão para a geração do arquivo ZIP.
     */
    private ZipParameters getDefaultZipParameters() {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setEncryptionMethod(EncryptionMethod.NONE);     
        zipParameters.setCompressionMethod(CompressionMethod.STORE);
        zipParameters.setRootFolderNameInZip("");
        zipParameters.setOverrideExistingFilesInZip(true);
        return zipParameters;
    }
    
    /**
     * Obter a referência para o objeto {@link FileMetadata} no arranjo 
     * associado ao nome de arquivo passado como parâmetro. Retorna null caso não
     * haja correspondência.
     * @param fileName nome de arquivo associado ao objeto a ser referenciado.
     * @return referência para o objeto {@link FileMetadata} no arranjo, 
     * ou null, caso não tenha um objeto associado ao nome de arquivo.
     */
    private FileMetadata getFileMetadata(String fileName) {
        FileMetadata ref = null;
        for (FileMetadata fileMetadata : fileMetadataList) {
            if (equals(fileMetadata.getFileName(), fileName)) {
                ref = fileMetadata;
                break;
            }
        }
        return ref;
    }
    
    /**
     * Definir os comentários do arquivo ZIP. Os comentários também serão criptografados
     * antes de serem gravados no arquivo ZIP.
     * @param comment comentários do arquivo ZIP. 
     * @throws java.lang.Exception 
     */
    public void writeComment(String comment) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.writeBytes(comment.getBytes("UTF-8"));
        ByteArrayInputStream dataInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
        FileMetadata fileMetadata = new FileMetadata();
        bravoCipher.encrypt(dataInputStream, dataOutputStream, fileMetadata, password);
        ByteArrayOutputStream zipOutputStream = new ByteArrayOutputStream(16 + dataOutputStream.size());
        zipOutputStream.writeBytes(fileMetadata.getIVBytes());
        zipOutputStream.writeBytes(dataOutputStream.toByteArray());
        try {
            release();
            zipFile.setComment(Base64.toBase64String(zipOutputStream.toByteArray()));
        } finally {
            lock();
        }
    }
    
    /**
     * Obter os comentários do arquivo ZIP.
     * @return comentários do arquivo ZIP.
     * @throws java.lang.Exception
     */
    public String readComment() throws Exception {
        ByteArrayInputStream inputStream;
        try {
            release();
            inputStream = new ByteArrayInputStream(Base64.decode(zipFile.getComment()));
        } finally {
            lock();
        }
        byte[] ivBytes = inputStream.readNBytes(IV_LENGTH);
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setIVBytes(ivBytes);
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            text.write(buffer, 0, length);
        }
        ByteArrayInputStream dataInputStream = new ByteArrayInputStream(text.toByteArray());
        ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
        bravoCipher.decrypt(dataInputStream, dataOutputStream, fileMetadata, password);
        return new String(dataOutputStream.toByteArray(), "UTF-8");
    }

    
//ENCRYPTED FILES MANAGEMENT////////////////////////////////////////////////////
    
    /**
     * Adicionar dados no arquivo ZIP. Essa operação é precedida pelo desbloqueio
     * do arquivo ZIP, a gravação do stream de entrada e o subsequente bloqueio 
     * do mesmo para impedir acesso concorrente.
     * @param inputStream dados de entrada (stream de entrada).
     * @param parameters parâmetros para a arquivo ZIP.
     * @return instância de FileHeader para o arquivo inserido no ZIP.
     * @throws Exception erro ocorrido no processo. 
     */
    private FileHeader addStream(InputStream inputStream, ZipParameters parameters) throws Exception {
        FileHeader fileHeader;
        try {
            release();
            zipFile.addStream(inputStream, parameters);
            fileHeader = zipFile.getFileHeader(parameters.getFileNameInZip());
        } finally {
            lock();
        }
        return fileHeader;
    }
    
    /**
     * Adicionar arquivos que foram criptografados ao ZIP.
     * @param encryptedFiles lista de objetos {@link FileMetadata} com informações
     * sobre os arquivos criptografados a serem adicionados ao ZIP.
     * @throws Exception erro ocorrido ao adicionar algum arquivo ao ZIP.
     */
    private void addEncryptedFiles(List<EncryptedFileData> encryptedFiles) throws Exception {
        try {
            ZipParameters parameters = getDefaultZipParameters();
            long totalBytes = 0;                
            for (EncryptedFileData encryptedFile : encryptedFiles) {
                if (encryptedFile.getInputFile() != null) {
                    long length = encryptedFile.getInputFile().length();
                    totalBytes += (3 * length) + (length % BravoCipher.BLOCK_SIZE);
                }
            }
            reset(false, totalBytes);
            for (ProcessListener listener : listeners) {
                listener.abortBlocked(blockAbort);
            }
            for (EncryptedFileData encryptedFile : encryptedFiles) {
                if (abort()) break;
                if (!isEmptyFolder(encryptedFile.getFileMetadata())) {
                    InputStream inputStream;
                    File inputFile = encryptedFile.getInputFile();
                    updateFileInProcess(encryptedFile.getInputFile()
                    .getAbsolutePath(), ENCRYPT, inputFile.length());
                    if (inputFile.length() <= Integer.MAX_VALUE &&
                    inputFile.length() <= MemoryInfo.freeMemory()) {
                        ByteArrayOutputStream bostream = new ByteArrayOutputStream((int) inputFile.length());
                        try (FileInputStream fileInputStream = new FileInputStream(inputFile)) {
                            bravoCipher.encrypt(
                                fileInputStream,
                                bostream, 
                                encryptedFile.getFileMetadata(),
                                password,
                                this
                            );
                        }
                        inputStream = new MemoryInputStream(
                            bostream.toByteArray(),
                            this
                        );                        
                    } else {
                        char[] alphabet = new char[] {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
                        'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V',
                        'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
                        java.security.SecureRandom rnd = new java.security.SecureRandom();
                        char[] chars = new char[12];
                        for (int i = 0; i < 12; i++) chars[i] = alphabet[rnd.nextInt(alphabet.length)];
                        String encryptedFolderPath = RootFolder.getEncryptFolder().getAbsolutePath();
                        File outputFile;
                        do {
                            outputFile = new File(
                                encryptedFolderPath + File.separator + 
                                new String(chars) + ".tmp"
                            );         
                        } while (outputFile.exists());
                        try (FileInputStream fileInputStream = new FileInputStream(inputFile);
                        FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
                            bravoCipher.encrypt(
                                fileInputStream,
                                fileOutputStream,
                                encryptedFile.getFileMetadata(),
                                password,
                                this
                            );
                            inputStream = new EncryptedFileInputStream(
                                outputFile,
                                this
                            );
                        }
                    }
                    if (abort()) break;
                    updateFileInProcess(encryptedFile.getInputFile()
                    .getAbsolutePath(), ADD, inputFile.length());
                    parameters.setFileNameInZip(encryptedFile.getFileMetadata()
                    .getInternalFileName());
                    FileHeader fileHeader = addStream(inputStream, parameters);
                    encryptedFile.getFileMetadata().setZipHeader(fileHeader);
                }
                if (!fileMetadataList.contains(encryptedFile.getFileMetadata())) {
                    fileMetadataList.add(encryptedFile.getFileMetadata());
                    //Caso o caminho do novo arquivo inserido pertença a um
                    //diretório vazio que está salvo, remove a entrada para
                    //este diretório vazio, pois seu caminho já aparecerá na
                    //listagem com a leitura deste arquivo.
                    String parent = getParentPath(encryptedFile.getFileMetadata());
                    List<FileMetadata> emptyFolders = getEmptyFolders();
                    FileMetadata folderToDelete = null;
                    for (FileMetadata emptyFolder : emptyFolders) {
                        if (equals(parent, emptyFolder.getFileName())) {
                            folderToDelete = emptyFolder;
                            break;
                        }
                    }
                    if (folderToDelete != null) {
                        fileMetadataList.remove(folderToDelete);
                    }
                }
            }            
        } finally {
            writeFileMetadataList();
            writeIndex();
        }
    }
    
    /**
     * Criptografar um arquivo a ser adicionado ao ZIP. Os dados sobre o arquivo
     * são passados como parâmetro para o método. Nessa etapa NÃO há a inserção
     * do arquivo no ZIP, apenas a encriptação e a geração de parâmetros para
     * o processo posterior de inserção, como o nome interno do arquivo, os bytes
     * do SALT e IV, os dados para o algoritmo argon2 e a referência para o arquivo
     * temporário encriptado gerado durante o processo.
     * @param inputFile arquivo em formato legível a ser encriptado.
     * @param folder pasta do arquivo no formato usado internamente, ex.: /Teste.
     * Caso não esteja dentro de uma pasta, passar null. A construção do caminho
     * do arquivo será relativa à pasta raiz definida, ex.: /Arquivos. Logo, ao
     * se passar /Teste como pasta do arquivo, internamente ficará como 
     * /Arquivos/Teste, e ao se passar null, a pasta do arquivo ficará como
     * /Arquivos.
     * @return instância de {@link FileMetadata} com informações sobre o 
     * arquivo criptografado a ser adicionado ao ZIP.
     * @throws Exception erro ocorrido na encriptação do arquivo.
     */
    private EncryptedFileData getEncryptedFileData(File inputFile, String folder) throws Exception {
        BasicFileAttributes fileAttributes = Files.getFileAttributeView(inputFile.toPath(),
        BasicFileAttributeView.class).readAttributes();
        updateFileInProcess(inputFile.getAbsolutePath(), ENCRYPT, fileAttributes.size());
        String filePath = getRelativeFilePath(inputFile, folder);
        FileMetadata fileMetadata = getFileMetadata(filePath);        
        if (fileMetadata == null) {
            //Não há uma entrada com o mesmo nome relativo que se está tentando
            //inserir, dessa forma cria uma nova entrada e a nomeia. Caso fileHeader
            //fosse diferente de null, iria sobrescrever o arquivo original com 
            //os novos dados. Observe no código "String.format("%04d", ...)" que
            //o índice no nome do arquivo ficará sempre com 4 dígitos, exemplo,
            //se o índice é 4, será formatado como 0004, ficando o nome interno
            //do arquivo como "Encrypted File 0004.dat".            
            StringBuilder internalFileName = new StringBuilder();
            internalFileName.append("File");
            internalFileName.append(String.format("%04d", ++index));
            internalFileName.append(".dat");
            fileMetadata = new FileMetadata(); 
            fileMetadata.setInternalFileName(internalFileName.toString());
            fileMetadata.setFileName(filePath);
        }
        fileMetadata.setLastModifiedTime(fileAttributes.lastModifiedTime().toMillis());
        fileMetadata.setCreatedTime(fileAttributes.creationTime().toMillis());
        fileMetadata.setOriginalSize(fileAttributes.size());
        return (!abort ? new EncryptedFileData(inputFile, fileMetadata) : null);
    }
    
    /**
     * Encriptar uma pasta a ser adicionada ao ZIP.
     * @param folder pasta a ser adicionada.
     * @return lista de objetos {@link FileMetadata} com informações sobre
     * os arquivos que foram encriptados da pasta.
     * @throws Exception erro ocorrido na encriptação da pasta.
     */
    private List<EncryptedFileData> encryptFolder(File folder) throws Exception {
        List<EncryptedFileData> encryptedFiles = new ArrayList<>();
        List<FileMetadata> emptyFolders = extractEmptyFolders(folder);
        emptyFolders.forEach(emptyFolder -> {
            encryptedFiles.add(new EncryptedFileData(emptyFolder));
        });        
        String folderName = getInternalFolderPath(folder);
        List<File> filesList = new ArrayList<>();
        listFilesFromFolder(folder, filesList);
        for (File file : filesList) {
            if (abort()) break;
            EncryptedFileData encryptedFile = getEncryptedFileData(file, folderName);
            if (encryptedFile != null) encryptedFiles.add(encryptedFile);            
        }
        if (!abort()) {
            List<File> subfolders = new ArrayList<>();
            listSubfoldersFromFolder(folder, subfolders);                        
            for (File subfolder : subfolders) {
                if (abort()) break;
                filesList.clear();
                listFilesFromFolder(subfolder, filesList);                
                if (abort()) break;
                String subfolderName = getInternalFolderPath(folder, subfolder);
                for (File file : filesList) {
                    if (abort()) break;
                    EncryptedFileData encryptedFile = getEncryptedFileData(file, subfolderName);
                    if (encryptedFile != null) encryptedFiles.add(encryptedFile);
                }
            }
        }
        return encryptedFiles;
    }
    
    /**
     * Adicionar arquivos e pastas no arquivo ZIP.Na primeira etapa é realizada
     * a encriptação destes arquivos e pastas e posteriormente é feita a inserção
     * no arquivo ZIP.
     * @param filesAndFolders arquivos e pastas a serem inseridos no arquivo.
     * @param destroySourceFiles se true, destrói os arquivos na origem.
     * @throws Exception erro ocorrido na encriptação de um arquivo ou na sua
     * inserção no arquivo ZIP.
     */
    public void addFilesAndFolders(List<File> filesAndFolders, boolean destroySourceFiles) 
    throws Exception {
        try {
            List<EncryptedFileData> encryptedFiles = new ArrayList<>();
            try {
                //O índice 2 no calculo dos bytes a serem processados é porque
                //o processo será em duas etapas. 1. Encriptação dos arquivos,
                //2. Inserção no arquivo ZIP.
                reset(true, 0);
                for (ProcessListener listener : listeners) {
                    listener.abortBlocked(blockAbort);
                }
                List<File> filesList = new ArrayList<>();
                List<File> foldersList = new ArrayList<>();
                for (File file : filesAndFolders) {
                    if (file.isFile()) {
                        filesList.add(file);
                    } else {
                        foldersList.add(file);
                    }
                }
                for (File file : filesList) {
                    EncryptedFileData encryptedFile = getEncryptedFileData(file, null);
                    if (encryptedFile != null) encryptedFiles.add(encryptedFile);
                }
                //Encripta as pastas.
                for (File folder : foldersList) {
                    encryptedFiles.addAll(encryptFolder(folder));                    
                }
                //Insere os arquivos e pastas encriptados no arquivo ZIP.
                addEncryptedFiles(encryptedFiles);
                if (destroySourceFiles) {
                    //Destrói os arquivos na origem.
                    FileEraser fileEraser = new FileEraser();
                    listeners.forEach(listener -> {fileEraser.addProcessListener(listener);});
                    fileEraser.wipeFiles(filesAndFolders);
                }
            } finally {
                blockAbort = false;
                for (ProcessListener listener : listeners) {
                    listener.abortBlocked(blockAbort);
                }
            }
        } finally {            
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    /**
     * Testar o processo de adição de arquivos ao ZIP. O objetivo é listar os
     * arquivos que serão sobrescritos se for realizado o processo.
     * @param filesAndFolders arquivos e pastas a serem inseridos no arquivo ZIP.
     * @return lista de arquivos que serão sobrescritos caso o processo de
     * adição seja realizado.
     * @throws Exception erro ocorrido na leitura do arquivo ZIP.
     */
    public List<File> testAddFilesAndFolders(List<File> filesAndFolders) throws Exception {
        List<File> existingFiles = new ArrayList<>();
        for (File file : filesAndFolders) {
            if (file.isDirectory()) {
                String folderName = getInternalFolderPath(file);
                List<File> filesList = new ArrayList<>();
                listFilesFromFolder(file, filesList);
                for (File file2 : filesList) {
                    if (insertedFile(getRelativeFilePath(file2, folderName))) {
                        existingFiles.add(file2);
                    }
                }            
                List<File> subfolders = new ArrayList<>();
                listSubfoldersFromFolder(file, subfolders);                        
                for (File subfolder : subfolders) {
                    filesList.clear();
                    listFilesFromFolder(subfolder, filesList);
                    String subfolderName = getInternalFolderPath(file, subfolder);
                    for (File file2 : filesList) {  
                        if (insertedFile(getRelativeFilePath(file2, subfolderName))) {
                            existingFiles.add(file2);
                        }
                    }                
                }
            } else {
                if (insertedFile(getRelativeFilePath(file, null))) {
                    existingFiles.add(file);
                }
            }
        }  
        return existingFiles;       
    }
    
    /**
     * Adicionar uma pasta vazia ao arquivo com base no diretório raiz selecionado.
     * @param folderName nome da nova pasta vazia.
     * @throws Exception erro ocorrido na escrita do arquivo ZIP. Pode ocorrer também
     * se já existe uma pasta com o mesmo nome e se o nome da pasta criada contém
     * caracteres inválidos.
     */
    public void addNewEmptyFolder(String folderName) throws Exception {
        if (!containsInvalidCharacters(folderName)) {
            FileMetadata fileMetadata = createEmptyFolder(folderName);
            if (!insertedFolder(fileMetadata.getFileName())) {
                List<EncryptedFileData> list = new ArrayList<>(1);
                list.add(new EncryptedFileData(fileMetadata));
                addEncryptedFiles(list);
            } else {
                throw new Exception("Pasta " + fileMetadata.getFileName() + 
                " já existe");
            }
        } else {
            throw new Exception("Caracteres inválidos compondo o nome da pasta");
        }
    }
    
    /**
     * Excluir o arquivo identificado no cabeçalho do arquivo ZIP.
     * @param fileMetadata cabeçalho do arquivo a ser excluído do ZIP.
     * @throws Exception erro ocorrido ao excluir o arquivo.
     */
    private void removeFile(FileMetadata fileMetadata) throws Exception {
        if (!isEmptyFolder(fileMetadata)) {
            updateFileInProcess(fileMetadata.getFileName(), REMOVE, fileMetadata.getOriginalSize());
            try {
                release();
                zipFile.removeFile(fileMetadata.getZipHeader());
            } finally {
                lock();
            }
            notify(fileMetadata.getOriginalSize());
        }
        fileMetadataList.remove(fileMetadata);
    }
    
    /**
     * Excluir os arquivos e pastas do arquivo ZIP. Nos casos em que se exclui
     * todos os arquivos de uma pasta, mas não a pasta em si, ela é mantida como
     * pasta vazia.
     * @param filesAndFolders arquivos e pastas a serem excluídos do arquivo ZIP.
     * @throws Exception erro ocorrido ao excluir os arquivos e pastas no arquivo
     * ZIP.
     */
    public void removeFilesAndFolders(List<String> filesAndFolders) throws Exception {
        try {
            List<String> parentsList = new ArrayList<>();
            try {
                List<String> filesAndFoldersCopy = new ArrayList<>(filesAndFolders.size());
                filesAndFoldersCopy.addAll(filesAndFolders);
                List<FileMetadata> deletedFiles = new ArrayList<>();
                List<String> deletedFolders = new ArrayList<>();
                for (String folder : filesAndFoldersCopy) {
                    if (abort()) break;
                    if (isFolderPath(folder)) {
                        deletedFolders.add(folder);
                        deletedFiles.addAll(getAllFilesFromFolder(folder));
                    }
                }
                if (abort()) return;
                filesAndFoldersCopy.removeAll(deletedFolders);
                for (String file : filesAndFoldersCopy) {            
                    if (abort()) break;
                    String parent = getParentPath(file);
                    if (!parentsList.contains(parent)) {
                        parentsList.add(parent);
                    }
                    deletedFiles.add(getFileMetadata(file));
                }
                if (abort()) return;
                reset(false, calculateSize2(deletedFiles, 1));
                for (FileMetadata fileMetadata : deletedFiles) {
                    if (abort()) break;                    
                    removeFile(fileMetadata);                    
                }
                for (String folder : deletedFolders) {
                    String parent = getParentPath(folder);
                    if (!parentsList.contains(parent)) {
                        parentsList.add(parent);
                    }
                    FileMetadata fileMetadata = getFileMetadata(folder);
                    if (fileMetadata != null) {
                        removeFile(fileMetadata);
                    }
                }
            } finally {
               writeFileMetadataList();
            }
            checkForEmptyFolders(parentsList);
        } finally {
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    /**
     * Verificar se há no nome do arquivo ou pasta algum caractere inválido, que
     * não faz parte do conjunto aceito para as diversas plataformas. Caso um
     * caractere inválido seja detectado, retorna true para a chamada do método.
     * @param fileName nome do arquivo ou pasta.
     * @return true, há caracteres inválidos no nome do arquivo ou pasta, false,
     * não há caracteres inválidos.
     */
    private boolean containsInvalidCharacters(String fileName) {
        boolean contains = false;
        for (int i = 0; i < fileName.length(); i++) {
            if (contains) break;
            for (int j = 0; j < INVALID_FILE_NAME_CHARACTERS.length; j++) {
                if (fileName.charAt(i) == INVALID_FILE_NAME_CHARACTERS[j]) {
                    contains = true;
                    break;
                }
            }
        }
        return contains;
    }

    /**
     * Renomear um arquivo. O nome que será alterado é o que identifica o arquivo,
     * e não o que ele usa internamente no ZIP.
     * @param fileMetadata cabeçalho do arquivo a ser renomeado.
     * @param newFileName novo nome do arquivo.
     * @throws Exception erro ocorrido ao renomear o arquivo, ou nome de arquivo
     * já existente, ou presença de caracteres inválidos no nome do arquivo.
     */
    private void renameFile(FileMetadata fileMetadata, String newFileName) throws Exception {
        if (!containsInvalidCharacters(newFileName)) {
            String parent = getParentPath(fileMetadata);
            String newFilePath = (parent.equals(SEPARATOR) ? parent + 
            newFileName : parent + SEPARATOR + newFileName);
            if (insertedFile(newFilePath)) {
                throw new Exception("Arquivo " + fileMetadata.getFileName() + 
                " não pode ser renomeado para " + newFilePath + 
                " pois esse nome de arquivo já está sendo utilizado.");
            }
            fileMetadata.setFileName(newFilePath);
            writeFileMetadataList();
        } else {
            throw new Exception("Caracteres inválidos compondo o nome do arquivo");
        }
    }

    /**
     * Renomear um arquivo. O nome que será alterado é o que identifica o arquivo,
     * e não o que ele usa internamente no ZIP.
     * @param fileName nome interno do arquivo conforme usado no ZIP.
     * @param newFileName novo nome do arquivo.
     * @throws Exception erro ocorrido ao renomear o arquivo, ou nome de arquivo
     * já existente, ou presença de caracteres inválidos no nome do arquivo.
     */
    public void renameFile(String fileName, String newFileName) throws Exception {
        FileMetadata fileMetadata = getFileMetadata(fileName);
        renameFile(fileMetadata, newFileName);
    }  
    
    /**
     * Renomear uma pasta de arquivos.
     * @param folderName nome atual da pasta de arquivos.
     * @param newFolderName novo nome da pasta de arquivos. 
     * @throws Exception 
     */
    public void renameFolder(String folderName, String newFolderName) throws Exception {
        if (!folderName.equals(SEPARATOR)) {
            if (!containsInvalidCharacters(newFolderName)) {
                String parent = getParentPath(folderName);
                String newFolderPath = (parent.equals(SEPARATOR) ? parent + 
                newFolderName : parent + SEPARATOR + newFolderName);
                if (insertedFolder(newFolderPath)) {
                    throw new Exception("Diretório " + folderName + 
                    " não pode ser renomeado para " + newFolderPath + 
                    " pois esse nome de diretório já está sendo utilizado.");
                }
                List<FileMetadata> encryptedFiles = getAllFilesFromFolder(folderName);
                for (FileMetadata fileMetadata : encryptedFiles) {
                    String filePath = fileMetadata.getFileName().replaceFirst(folderName, newFolderPath);
                    fileMetadata.setFileName(filePath);
                }
                writeFileMetadataList();
            } else {
                throw new Exception("Caracteres inválidos compondo o nome da pasta");
            }
        } else {
            throw new Exception("Operação inválida.");
        }
    }
    
    public void moveFilesAndFolders(List<String> filesAndFoldes, String destinationFolder) throws Exception {
        try {
            for (String file : filesAndFoldes) {
                if (file.equals(destinationFolder)) {
                    throw new Exception("Erro ao mover o diretório " + file);
                }
            }
            List<String> parentsList = new ArrayList<>();
            try {
                List<String> existingFiles = testMoveFilesAndFolders(filesAndFoldes, 
                destinationFolder);
                if (!existingFiles.isEmpty()) {
                    throw new Exception("Já existe arquivos com o mesmo nome em " +
                    destinationFolder);
                }
                List<String> filesList = new ArrayList<>();
                List<String> foldersList = new ArrayList<>();
                for (String file : filesAndFoldes) {
                    if (isFilePath(file)) {
                        filesList.add(file);
                    } else {
                        foldersList.add(file);
                    }
                }
                for (String file : filesList) {
                    FileMetadata fileMetadata = getFileMetadata(file);
                    String parent = getParentPath(fileMetadata);
                    if (!equals(parent, destinationFolder)) {
                        if (!parentsList.contains(parent)) {
                            parentsList.add(parent);
                        }
                        String fileName = extractFileName(file);
                        String newFilePath;
                        if (!destinationFolder.equals(SEPARATOR)) {
                            newFilePath = destinationFolder + SEPARATOR + fileName;
                        } else {
                            newFilePath = SEPARATOR + fileName;
                        }
                        fileMetadata.setFileName(newFilePath);
                    }
                }
                for (String folder : foldersList) {
                    String parent = getParentPath(folder);
                    if (!equals(parent, destinationFolder)) {
                        parent = destinationFolder;
                        boolean moveFolder = true;
                        do {
                            if (equals(folder, parent)) {
                                moveFolder = false;
                                break;
                            } else {
                                parent = getParentPath(parent);
                            }
                        } while (!parent.equals(SEPARATOR));
                        if (moveFolder) {
                            parent = getParentPath(folder);
                            if (!parentsList.contains(parent)) {
                                parentsList.add(parent);
                            }
                            String folderName = extractFileName(folder);
                            String newFolderPath;
                            if (!equals(SEPARATOR, destinationFolder)) {
                                newFolderPath = destinationFolder + SEPARATOR + 
                                folderName;
                            } else {
                                newFolderPath = SEPARATOR + folderName;
                            }
                            List<FileMetadata> encryptedFiles = getAllFilesFromFolder(folder);
                            for (FileMetadata fileMetadata : encryptedFiles) {
                                String newFileName = fileMetadata.getFileName()
                                .replaceFirst(folder, newFolderPath);
                                fileMetadata.setFileName(newFileName);
                            }
                        }
                    }
                }
            } finally {
                writeFileMetadataList();
            }
            checkForEmptyFolders(parentsList);
        } finally {
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    private List<String> testMoveFilesAndFolders(List<String> filesAndFoldes, String destinationFolder) {
        List<String> existingFiles = new ArrayList<>();
        for (String file : filesAndFoldes) {
            if (isFilePath(file)) {
                FileMetadata fileMetadata = getFileMetadata(file);
                String parent = getParentPath(fileMetadata);
                if (!equals(parent, destinationFolder)) {
                    String fileName = extractFileName(file);
                    String newFilePath;
                    if (!equals(destinationFolder, SEPARATOR)) {
                        newFilePath = destinationFolder + SEPARATOR + fileName;
                    } else {
                        newFilePath = SEPARATOR + fileName;
                    }
                    if (insertedFile(newFilePath)) {
                        existingFiles.add(file);
                    }
                }
            } else {
                String parent = getParentPath(file);
                if (!equals(parent, destinationFolder)) {
                    String folderName = extractFileName(file);
                    String newFolderPath;
                    if (!equals(destinationFolder, SEPARATOR)) {
                        newFolderPath = destinationFolder + SEPARATOR + folderName;
                    } else {
                        newFolderPath = SEPARATOR + folderName; 
                    }
                    if (insertedFolder(newFolderPath)) {
                        existingFiles.add(file);
                    }
                }
            }
        }
        return existingFiles;
    }
    
    private File extractFile(FileMetadata fileMetadata, String destinationPath) throws Exception {
        File destinationFile = getDestinationFile(fileMetadata.getFileName(), destinationPath);
        if (!isEmptyFolder(fileMetadata)) {
            updateFileInProcess(destinationFile.getAbsolutePath(), EXTRACT, fileMetadata.getOriginalSize());        
            File parentFolder = destinationFile.getParentFile();
            if (!parentFolder.exists()) parentFolder.mkdirs();
            try {
                release();
                try (ZipInputStream zipInputStream = zipFile.getInputStream(fileMetadata.getZipHeader());
                FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                    bravoCipher.decrypt(zipInputStream, fileOutputStream, fileMetadata, password, this);
                    if (!abort()) {
                        FileTime creationTime = FileTime.fromMillis(fileMetadata.getCreatedTime());
                        FileTime modifiedTime = FileTime.fromMillis(fileMetadata.getLastModifiedTime());
                        Files.setAttribute(destinationFile.toPath(), "basic:creationTime", creationTime);
                        Files.setAttribute(destinationFile.toPath(), "basic:lastModifiedTime", modifiedTime);
                    }
                }
            } finally {
                lock();
            }
        } else {
            destinationFile.mkdirs();
        }
        return destinationFile;
    }

    public List<File> extractFilesAndFolders(List<String> filesAndFolders, String destinationPath) throws Exception  {
        try {
            List<File> filesList = new ArrayList<>();
            List<String> filesAndFoldersCopy = new ArrayList<>(filesAndFolders.size());
            filesAndFoldersCopy.addAll(filesAndFolders);
            List<FileMetadata> encryptedFiles = new ArrayList<>();
            List<String> foldersList = new ArrayList<>();
            for (String folderName : filesAndFoldersCopy) {
                if (abort()) break;
                if (isFolderPath(folderName)) {
                    foldersList.add(folderName);
                    encryptedFiles.addAll(getAllFilesFromFolder(folderName));
                }
            }
            if (!abort()) {
                filesAndFoldersCopy.removeAll(foldersList);
                for (String fileName : filesAndFoldersCopy) {
                    if (abort()) break;
                    encryptedFiles.add(getFileMetadata(fileName));
                }
                if (!abort()) {
                    reset(false, calculateSize2(encryptedFiles, 1));
                    for (FileMetadata fileMetadata : encryptedFiles) {
                        if (abort()) break;
                        filesList.add(extractFile(fileMetadata, destinationPath));
                    }
                    for (String folder : foldersList) {
                        if (abort()) break;
                        FileMetadata fileMetadata = getFileMetadata(folder);
                        if (fileMetadata != null) {
                            filesList.add(extractFile(fileMetadata, destinationPath));
                        }
                    }
                }
            }
            return filesList;
        } finally {
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    public List<File> extractFilesToCacheFolder(List<String> files) throws Exception {
        try {
            List<File> filesList = new ArrayList<>();
            long bytesCounter = 0;
            for (String file : files) {
                FileMetadata fileMetadata = getFileMetadata(file);
                bytesCounter += fileMetadata.getOriginalSize();
            }
            reset(false, bytesCounter);
            for (String file : files) {
                if (abort()) break;
                FileMetadata fileMetadata = getFileMetadata(file);
                String fileName = extractFileName(file);
                String onlyName = fileName;
                String extension = null;
                int idx = fileName.lastIndexOf(".");
                if (idx != -1) {
                    onlyName = fileName.substring(0, idx);
                    extension = fileName.substring(idx, fileName.length());
                }
                File destinationFile;
                int index = 0;
                do {
                    StringBuilder filePath = new StringBuilder();
                    filePath.append(RootFolder.getExtractFolder1().getAbsolutePath());
                    filePath.append(File.separator);
                    filePath.append(onlyName);
                    if (index != 0) {
                        filePath.append(" (");
                        filePath.append(String.valueOf(index));
                        filePath.append(")");
                    }
                    if (extension != null) filePath.append(extension);
                    destinationFile = new File(filePath.toString());
                    index++;
                } while (destinationFile.exists());
                File parentFolder = destinationFile.getParentFile();
                if (!parentFolder.exists()) parentFolder.mkdirs();
                if (abort()) break;
                updateFileInProcess(destinationFile.getAbsolutePath(), EXTRACT,
                fileMetadata.getOriginalSize());
                try {
                    release();
                    try (ZipInputStream zipInputStream = zipFile.getInputStream(fileMetadata.getZipHeader());
                    FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                        bravoCipher.decrypt(zipInputStream, fileOutputStream, fileMetadata, password, this);
                        if (!abort()) {
                            FileTime creationTime = FileTime.fromMillis(fileMetadata.getCreatedTime());
                            FileTime modifiedTime = FileTime.fromMillis(fileMetadata.getLastModifiedTime());
                            Files.setAttribute(destinationFile.toPath(), "basic:creationTime", creationTime);
                            Files.setAttribute(destinationFile.toPath(), "basic:lastModifiedTime", modifiedTime);
                            Files.setAttribute(destinationFile.toPath(), "dos:readonly", true);
                        }
                    }
                } finally {
                    lock();
                }
                if (!abort()) {
                    filesList.add(destinationFile);
                }
            }
            return filesList;
        } finally {
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    public List<File> extractFilesToClipboardFolder(List<String> filesAndFolders) throws Exception {
        File clipboardFolder = RootFolder.getClipboardFolder();
        FileEraser fileEraser = new FileEraser();
        fileEraser.addCipherListener(this);
        List<File> list = new ArrayList<>();
        list.add(clipboardFolder);
        reset(false, calculateSize(list, fileEraser.getPassesByMethod()));
        updateFileInProcess(clipboardFolder.getAbsolutePath(), WIPE, totalLength);
        fileEraser.wipeFiles(list);
        fileEraser.removeCipherListener(this);
        reset(false, 0);
        for (ProcessListener listener : listeners) {
            listener.updateFile(clipboardFolder.getAbsolutePath(), EXTRACT);
            listener.updateFilePercentage(0);
            listener.updateTotalPercentage(0);
        }
        clipboardFolder = RootFolder.getClipboardFolder();
        extractFilesAndFolders(filesAndFolders, clipboardFolder.getAbsolutePath());
        File[] clipboardfiles = clipboardFolder.listFiles();
        List<File> filesList = new ArrayList<>();
        Collections.addAll(filesList, clipboardfiles);
        return filesList;
    }
    
    public List<File> testExtractFilesAndFolders(List<String> filesAndFolders, 
    String destinationPath) throws Exception  {
        List<File> existingFiles = new ArrayList<>();
        List<String> filesAndFoldersCopy = new ArrayList<>(filesAndFolders.size());
        filesAndFoldersCopy.addAll(filesAndFolders);
        List<String> foldersList = new ArrayList<>();
        for (String folderName : filesAndFoldersCopy) {
            if (isFolderPath(folderName)) {
                foldersList.add(folderName);
                List<FileMetadata> encryptedFiles = getAllFilesFromFolder(folderName);
                for (FileMetadata fileMetadata : encryptedFiles) {
                    File file = getDestinationFile(fileMetadata.getFileName(),
                    destinationPath);
                    if (file.exists()) {
                        existingFiles.add(file);
                    }
                }
            }
        }
        filesAndFoldersCopy.removeAll(foldersList);
        for (String fileName : filesAndFoldersCopy) {
            File file = getDestinationFile(fileName, destinationPath);
            if (file.exists()) {
                existingFiles.add(file);
            }
        }
        return existingFiles;
    }

    public void extractAll(String destinationPath) throws Exception {
        try {
            List<FileMetadata> encryptedFiles = getAllFilesFromFolder(rootFolder);
            reset(false, calculateSize2(encryptedFiles, 1));
            for (FileMetadata fileMetadata : encryptedFiles) {
                if (abort()) break;
                extractFile(fileMetadata, destinationPath);
            }
        } finally {
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    public void changePassword(String newPassword) throws Exception {
        FileEraser fileEraser = new FileEraser();
        fileEraser.addCipherListener(this);        
        try {
            if (!isFilePassword(newPassword)) {
                List<File> list = new ArrayList<>();
                File cacheFolder = RootFolder.getExtractFolder2();
                list.add(cacheFolder);
                reset(true, 0);
                fileEraser.wipeFiles(list);
                int passes = 3 + fileEraser.getPassesByMethod();
                reset(true, calculateSize2(fileMetadataList, passes));
                for (FileMetadata fileMetadata : fileMetadataList) {
                    if (abort()) break;
                    extractFile(fileMetadata, cacheFolder.getAbsolutePath());
                }
                writeTestBytes(newPassword);
                this.password = newPassword;
                fileMetadataList.clear();
                index = 0;        
                writeFileMetadataList();
                setRootFolder(SEPARATOR);
                List<EncryptedFileData> encryptedFiles = new ArrayList<>();
                List<File> filesList = new ArrayList<>();
                listFilesFromFolder(cacheFolder, filesList);
                for (File file : filesList) {
                    EncryptedFileData encryptedFile = getEncryptedFileData(file, null);
                    if (encryptedFile != null) encryptedFiles.add(encryptedFile);
                }
                List<File> subfolders = new ArrayList<>();
                File[] foldersList = cacheFolder.listFiles(new DirectoryFilter());
                if (foldersList != null) {
                    subfolders.addAll(Arrays.asList(foldersList));
                }
                for (File folder : subfolders) {
                    encryptedFiles.addAll(encryptFolder(folder));
                }                
                addEncryptedFiles(encryptedFiles);
                updateFileInProcess(cacheFolder.getAbsolutePath(), WIPE,
                calculateSize(list, fileEraser.getPassesByMethod()));
                fileEraser.wipeFiles(list);
            }
        } finally {
            fileEraser.removeCipherListener(this);
            for (ProcessListener listener : listeners) {
                listener.done();
            }
        }
    }
    
    public List<FileEntry> getAllFiles() {
        List<FileEntry> encryptedFiles = new ArrayList<>();
        for (FileMetadata fileMetadata : fileMetadataList) {
            encryptedFiles.add(new FileEntry(fileMetadata));
        }
        encryptedFiles.sort(new EncryptedFileComparator());
        return encryptedFiles;
    }
    
    public List<FileEntry> getFilesFromFolder(String folderName) {
        List<FileEntry> encryptedFiles = new ArrayList<>();
        for (FileMetadata fileMetadata : fileMetadataList) {
            if (!isEmptyFolder(fileMetadata)) {
                String fileName = fileMetadata.getFileName();
                String parent = getParentPath(fileName);
                if (equals(folderName, parent)) {
                    encryptedFiles.add(new FileEntry(fileMetadata));
                }
            }
        }
        encryptedFiles.sort(new EncryptedFileComparator());
        return encryptedFiles;
    }
    
    private List<FileMetadata> getAllFilesFromFolder(String folderName) {
        List<FileMetadata> filesList = new ArrayList<>();
        for (FileMetadata fileMetadata : fileMetadataList) {
            boolean isParent = false;
            String parent = fileMetadata.getFileName();
            do {
                parent = getParentPath(parent);
                if (equals(parent, folderName)) {
                    isParent = true;
                    break;
                }
            } while (!parent.equals(SEPARATOR));
            if (isParent) {
                filesList.add(fileMetadata);
            }
        }
        return filesList;
    }
    
    private List<String> getAllSubfoldersFromFolder(String folderName) {
        List<String> foldersList = new ArrayList<>();
        for (String folder : folders) {
            if (!equals(folder, folderName)) {
                boolean isParent = false;
                String parent = folder;
                do {
                    parent = getParentPath(parent);
                    if (equals(parent, folderName)) {
                        isParent = true;
                        break;
                    }
                } while (!parent.equals(SEPARATOR));
                if (isParent) {
                    foldersList.add(folder);
                }
            }
        }
        return foldersList;
    }
    
    public List<FileEntry> getFilesFromRootFolder() {
        return getFilesFromFolder(rootFolder);
    }
    
    public List<FileEntry> getSubfoldersFromFolder(String folderName) {
        List<FileEntry> encryptedFiles = new ArrayList<>();
        for (String folder1 : folders) {
            if (!equals(folderName, folder1)) {
                String parent = getParentPath(folder1);
                if (equals(folderName, parent)) {
                    encryptedFiles.add(new FileEntry(null, folder1, 0, 0, 0,
                    true, false));
                }
            }
        }
        encryptedFiles.sort(new EncryptedFileComparator());
        return encryptedFiles;
    }
    
    private void checkForEmptyFolders(List<String> folders) throws Exception {
        boolean blockAbortValue = blockAbort;
        blockAbort = true;
        List<EncryptedFileData> newEmptyFolders = new ArrayList<>();
        for (String folder : folders) {
            List<FileMetadata> filesList = getAllFilesFromFolder(folder);
            List<String> foldersList = getAllSubfoldersFromFolder(folder);
            if (filesList.isEmpty() && foldersList.isEmpty()) {
                FileMetadata fileMetadata = new FileMetadata();
                fileMetadata.setFileName(folder);
                fileMetadata.setInternalFileName(EMPTY_FOLDER_TAG);
                newEmptyFolders.add(new EncryptedFileData(null, fileMetadata));
            }
        }
        if (!newEmptyFolders.isEmpty()) {
            addEncryptedFiles(newEmptyFolders);
        }
        blockAbort = blockAbortValue;
    }
    
    public List<FileEntry> getSubfoldersFromRootFolder() {
        return getSubfoldersFromFolder(rootFolder);
    }
    
    public void setRootFolder(String rootFolder) throws Exception {
        if (!folders.contains(rootFolder)) {
            throw new Exception("Diretório raiz inválido");
        }
        this.rootFolder = rootFolder;
    }

    public String getRootFolder() {
        return rootFolder;
    }
    
    public List<String> getFoldersTree() {
        List<String> foldersList = new ArrayList<>(folders.size());
        foldersList.addAll(folders);
        return foldersList;
    }
    
    @Override
    public void close() throws IOException {
        try {
            release();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    } 
    
    private void lock() throws FileNotFoundException, IOException {
        if (fileChannel == null && fileLock == null) {
            fileChannel = new RandomAccessFile(bravoFile, "rw").getChannel();
            fileLock = fileChannel.tryLock();
        }
    }
    
    private void release() throws IOException {
        if (fileChannel != null && fileLock != null) {
            fileLock.release();
            fileChannel.close();
            fileLock = null;
            fileChannel = null;
        }
    }
    
    public File getFile() {
        return zipFile.getFile();
    }
 
    
//FILES UTILS///////////////////////////////////////////////////////////////////
    
    private void listFilesFromFolder(File folder, List<File> files) {
        Collections.addAll(files, folder.listFiles(new FileFilter()));
    }
    
    private void listSubfoldersFromFolder(File folder, List<File> subfolders) {
        File[] foldersList = folder.listFiles(new DirectoryFilter());
        if (foldersList != null) {
            for (File subfolder : foldersList) {
                if (abort()) break;
                subfolders.add(subfolder);
                listSubfoldersFromFolder(subfolder, subfolders);
            }
        }
    }
    
    private String getRelativeFilePath(File file, String folder) throws IOException {    
        StringBuilder sb = new StringBuilder();
        sb.append(rootFolder);
        if (!rootFolder.equals(SEPARATOR)) sb.append(SEPARATOR);
        if (folder != null && !folder.equals("")) {
            sb.append(folder);
            sb.append(SEPARATOR);
        }
        sb.append(file.getName());
        return adaptFilePath(sb.toString());
    }
    
    private String getRelativeFolderPath(String folder) throws IOException {    
        StringBuilder sb = new StringBuilder();
        sb.append(rootFolder);
        if (!rootFolder.equals(SEPARATOR)) sb.append(SEPARATOR);
        sb.append(folder);
        return adaptFilePath(sb.toString());
    }
    
    private String getInternalFolderPath(File folder, File subfolder) {
        //Exemplo: "C:\Teste"
        String folderPath = folder.getAbsolutePath();
        //"C:\Teste" --> "[XXX]Teste" --> "Teste"
        String folderName = folder.getName();
        //Fará a adaptação no nome do diretório para nome relativo, 
        //utilizado internamente:
        //"C:\Teste\Teste1"
        String subfolderName1 = subfolder.getAbsolutePath();
        //"C:\Teste\Teste1" --> "[XXXXXXXXXX]\Teste1" --> "Teste\Teste1"
        String subfolderName2 = subfolderName1.replace(folderPath, folderName);
        //"Teste\Teste1" --> "Teste/Teste1"
        String subfolderName3 = subfolderName2.replace(File.separator, SEPARATOR);
        return subfolderName3;
    }
    
    private String getInternalFolderPath(File folder) {
        return folder.getName();
    }
    
    private String getParentPath(FileMetadata fileMetadata) {
        return getParentPath(fileMetadata.getFileName());
    }
    
    public String getParentPath(String file) {
        int lastIndex = file.lastIndexOf(SEPARATOR);
        String parentPath = file.substring(0, lastIndex == 0 ? 1 : lastIndex);
        int index = -1;
        for (int i = 0; i < folders.size(); i++) {
            if (equals(folders.get(i), parentPath)) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            return folders.get(index);
        } else {
            return parentPath;
        }
    }
    
    private String extractFileName(String file) {
        return file.substring(file.lastIndexOf(SEPARATOR) + 1, file.length());
    }
    
    public File getDestinationFile(String fileName, String destinationPath) {
        String destFileName;
        if (!rootFolder.equals(SEPARATOR)) {
            destFileName = destinationPath + File.separator + fileName
            .replaceFirst(rootFolder, "").replace(SEPARATOR, File.separator);
        } else {
            destFileName = destinationPath + File.separator + fileName
            .replace(SEPARATOR, File.separator);
        }
        return new File(destFileName);
    }
    
    private boolean equals(String file1, String file2) {
        return file1.toLowerCase().equals(file2.toLowerCase());
    }
    
    public boolean insertedFile(String fileName) {
        boolean inserted = false;
        for (FileMetadata fileMetadata : fileMetadataList) {
            if (equals(fileMetadata.getFileName(), fileName)) {
                inserted = true;
                break;
            }
        }
        return inserted;
    }
    
    public boolean insertedFolder(String folderName) {
        boolean inserted = false;
        for (String folder : folders) {
            if (equals(folder, folderName)) {
                inserted = true;
                break;
            }
        }
        return inserted;
    }

    public boolean isFilePath(String path) {
        boolean value = false;
        for (FileMetadata fileMetadata : fileMetadataList) {
            if (equals(fileMetadata.getFileName(), path)) {
                value = true;
                break;
            }
        }
        return value;
    }
    
    public boolean isFolderPath(String path) {
        boolean value = false;
        for (String folderName : folders) {
            if (equals(path, folderName)) {
                value = true;
                break;
            }
        }
        return value;
    }
    
    private boolean isEmptyFolder(FileMetadata fileMetadata) {
        return fileMetadata.getInternalFileName().equals(EMPTY_FOLDER_TAG);
    }
    
    private String adaptFilePath(String filePath) {
        String parent = filePath;
        List<String> filesList = new ArrayList<>();
        do {
            filesList.add(extractFileName(parent));
            parent = getParentPath(parent);            
        } while (!parent.equals(SEPARATOR));
        StringBuilder sb = new StringBuilder();
        for (int i = filesList.size() - 1; i >= 0; i--) {
            sb.append(SEPARATOR);
            sb.append(filesList.get(i));           
        }
        return sb.toString();
    }
    
    private FileMetadata createEmptyFolder(String folderPath) throws Exception {
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setFileName(getRelativeFolderPath(folderPath));
        fileMetadata.setInternalFileName(EMPTY_FOLDER_TAG);
        return fileMetadata;
    }
    
    private List<FileMetadata> extractEmptyFolders(File folder) throws Exception {
        List<FileMetadata> emptyFolders = new ArrayList<>();
        int filesNumber = 0;
        int foldersNumber = 0;
        List<File> filesList = new ArrayList<>();
        List<File> foldersList = new ArrayList<>();
        listFilesFromFolder(folder, filesList);
        filesNumber += filesList.size();
        List<File> subfolders = new ArrayList<>();
        listSubfoldersFromFolder(folder, subfolders);  
        foldersNumber += subfolders.size();
        for (File subfolder : subfolders) {
            filesList.clear();
            foldersList.clear();
            listFilesFromFolder(subfolder, filesList);
            filesNumber += filesList.size();
            listSubfoldersFromFolder(subfolder, foldersList);
            foldersNumber += foldersList.size();
            if (filesList.isEmpty() && foldersList.isEmpty()) {
                String subfolderName = getInternalFolderPath(folder, subfolder);
                emptyFolders.add(createEmptyFolder(subfolderName));
            }
        }
        if ((filesNumber == 0) && (foldersNumber == 0)) {
            String folderName = getInternalFolderPath(folder);
            emptyFolders.add(createEmptyFolder(folderName));
        }
        return emptyFolders;
    }
    
    private List<String> getFolders() {
        List<String> foldersList = new ArrayList<>();
        foldersList.add(SEPARATOR);
        for (FileMetadata fileMetadata : fileMetadataList) {
            String fileName = fileMetadata.getFileName();
            int lastIndex = fileName.lastIndexOf(SEPARATOR);
            String parent = fileName.substring(0, lastIndex == 0 ? 1 : lastIndex);
            String[] splitPath = parent.split(SEPARATOR);
            for (int i = 1; i < splitPath.length; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 1; j <= i; j++) {
                    sb.append(SEPARATOR);
                    sb.append(splitPath[j]);                    
                }
                String folderPath = sb.toString();
                if (!foldersList.contains(folderPath)) {
                    foldersList.add(folderPath);
                }
            }
            if (isEmptyFolder(fileMetadata)) {
                if (!foldersList.contains(fileMetadata.getFileName())) {
                    foldersList.add(fileMetadata.getFileName());
                }
            }
        }
        return foldersList;
    }
    
    private List<FileMetadata> getEmptyFolders() {
        List<FileMetadata> emptyFolders = new ArrayList<>();
        for (FileMetadata fileMetadata : fileMetadataList) {
            if (isEmptyFolder(fileMetadata)) {
                emptyFolders.add(fileMetadata);
            }
        }
        return emptyFolders;
    }
    
    private long calculateSize(List<File> filesList, int passes) throws Exception {
        List<File> filesList2 = new ArrayList<>();
        for (File file : filesList) {
            if (file.isDirectory()) {
                listFilesFromFolder(file, filesList2);
                List<File> foldersList = new ArrayList<>();
                listSubfoldersFromFolder(file, foldersList);
                for (File folder : foldersList) {
                    listFilesFromFolder(folder, filesList2);
                }
            } else {
                filesList2.add(file);
            }
        }
        long size = 0;
        for (File file : filesList2) {
            size += (file.length() > 0 ? file.length() * passes : 0);
        }        
        return size;
    }
    
    private long calculateSize2(List<FileMetadata> filesMetadata, int passes) {
        long size = 0;
        for (FileMetadata fileMetadata : filesMetadata) {
            if (!isEmptyFolder(fileMetadata)) {
                long length = fileMetadata.getOriginalSize();
                size += (length > 0 ? length * passes : 0); 
            }
        }
        return size;
    }
    
    public int getVersion() {
        return fileVersion;
    }
    
    public int getNumberOfFiles() {
        int counter = 0;
        for (FileMetadata fileMetadata : fileMetadataList) {
            if (!isEmptyFolder(fileMetadata)) {
                counter++;
            }
        }
        return counter;
    }

    
//PROCESS LISTENERS/////////////////////////////////////////////////////////////    
    
    public void addListener(ProcessListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(ProcessListener listener) {
        int idx = -1;
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i) == listener) {
                idx = i;
                break;
            }
        }
        listeners.remove(idx);
    }

    public List<ProcessListener> getListeners() {
        return listeners;
    }
    
    private void reset(boolean blockAbort, long totalBytesCounter) {
        this.totalLength = totalBytesCounter;
        this.blockAbort = blockAbort;
        this.totalBytesCounter = 0;
        this.totalPercentage = 0;
        this.abort = false;
    }
    
    private void updateFileInProcess(String fileName, FileOperation operation, long fileLength) {
        fileBytesCounter = filePercentage = 0;
        this.fileLength = fileLength;
        for (ProcessListener listener : listeners) {
            listener.updateFile(fileName, operation);
            listener.updateFilePercentage(filePercentage);
        }
    }
    
    private void notify(long length) {
        if (fileLength > 0) {
            fileBytesCounter += length;
            int percentage = (int)((fileBytesCounter * 100) / fileLength);
            if (percentage > filePercentage) {
                filePercentage = percentage;
                for (ProcessListener listener : listeners) {
                    listener.updateFilePercentage(filePercentage);
                }
            }
        }
        if (totalLength > 0) {
            totalBytesCounter += length;
            int percentage = (int)((totalBytesCounter * 100) / totalLength);
            if (percentage > totalPercentage) {
                totalPercentage = percentage;
                for (ProcessListener listener : listeners) {
                    listener.updateTotalPercentage(totalPercentage);
                }
            }
        }
    }
    
    @Override
    public boolean abort() {
        if (blockAbort) return false;
        for (ProcessListener listener : listeners) {
            if (listener.abort()) {
                abort = true;
                break;
            }
        }
        return abort;
    }

    @Override
    public void update(long numberOfBytes) {
        notify(numberOfBytes);
    } 
    
}