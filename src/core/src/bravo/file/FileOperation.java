package bravo.file;

/**
 * Modo de operação com o arquivo.
 * @author Leandro Aparecido de Almeida
 */
public enum FileOperation {
    /**Adicionar arquivo ao ZIP.*/
    ADD,
    /**Remover arquivo do ZIP.*/
    REMOVE,
    /**Destruir arquivo.*/
    WIPE,
    /**Extrair arquivo do ZIP.*/
    EXTRACT,
    /**Encriptar arquivo.*/
    ENCRYPT;    
}
