package bravo.gui.dialogs;

import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.JOptionPane;

/**
 * Tela para entrada da senha de criptografia/decriptografia de arquivos.
 * @author Leandro Aparecido de Almeida
 */
public class PasswordDialog extends javax.swing.JDialog {

    /**Habilitar/desabilitar o campo para confirmação da senha.*/
    private final boolean confirmPassword;
    /**Senha digitada.*/
    private String password = null;    
    
    /**
     * Constructor da classe.
     * @param parent tela proprietária.
     * @param title título da tela.
     * @param file arquivo a ser aberto/criado.
     * @param confirmPassword habilitar/desabilitar o campo para confirmação
     * da senha.
     */
    public PasswordDialog(java.awt.Frame parent, String title, File file, 
    boolean confirmPassword) {
        super(parent, true);
        this.confirmPassword = confirmPassword;
        initComponents();
        setTitle(title);
        jtfPath.setText(file.getAbsolutePath());
        jtfPath.setToolTipText(jtfPath.getText());
        showPasswords();
        setLocationRelativeTo(parent);
        if (!this.confirmPassword) {
            jLabel3.setVisible(false);
            jpfPassword2.setVisible(false);
            jLabel2.setText("Senha:");
            jcbShowPassword.setText("Mostrar Senha");
        }
    }
    
    /**
     * Confirmar a senha digitada. No caso de confirmação da senha, será feita
     * a validação para comparar se ambos os campos têm a mesma senha digitada.
     */
    private void confirm() {
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        try {            
            if (confirmPassword) {
                if (jpfPassword1.getPassword().length == 0) {                
                    jpfPassword1.requestFocus();
                    throw new Exception("Campo Senha (1) não pode estar vazio.");
                }
                if (jpfPassword2.getPassword().length == 0) {                
                    jpfPassword2.requestFocus();
                    throw new Exception("Campo Senha (2) não pode estar vazio.");
                }
                String password1 = new String(jpfPassword1.getPassword());
                String password2 = new String(jpfPassword2.getPassword());
                if (!password1.equals(password2)) {
                    throw new Exception("Campos Senha (1) e Senha (2) contém valores\ndiferentes.");
                }
            } else {
                if (jpfPassword1.getPassword().length == 0) {                
                    jpfPassword1.requestFocus();
                    throw new Exception("Campo Senha não pode estar vazio.");
                }
            }
            password = new String(jpfPassword1.getPassword());
            setVisible(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Erro",
                JOptionPane.ERROR_MESSAGE
            );
        }
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }
    
    /**
     * Mostrar/ocultar o texto da senha, de acordo com o status do controle
     * de interface denominado <i>Mostrar Senhas</i> (JCheckBox).
     */
    private void showPasswords() {
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        if (jcbShowPassword.isSelected()) {
            jpfPassword1.setEchoChar('\u0000');
            jpfPassword2.setEchoChar('\u0000');
        } else {
            jpfPassword1.setEchoChar('\u25cf');
            jpfPassword2.setEchoChar('\u25cf');
        }
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }
    
    /**
     * Retornar a senha digitada.
     * @return senha digitada.
     */
    public String getPassword() {
        return password;
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jtfPath = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jpfPassword1 = new javax.swing.JPasswordField();
        jpfPassword2 = new javax.swing.JPasswordField();
        jLabel3 = new javax.swing.JLabel();
        jbCancel = new javax.swing.JButton();
        jbConfirm = new javax.swing.JButton();
        jcbShowPassword = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Criar Arquivo");

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Arquivo"));

        jtfPath.setEditable(false);
        jtfPath.setFocusable(false);

        jLabel1.setText("Caminho:");

        jLabel2.setText("Senha (1):");

        jpfPassword1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jpfPassword1KeyPressed(evt);
            }
        });

        jpfPassword2.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jpfPassword2KeyPressed(evt);
            }
        });

        jLabel3.setText("Senha (2):");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(17, 17, 17)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(jLabel1)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jtfPath, javax.swing.GroupLayout.PREFERRED_SIZE, 391, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jpfPassword1)
                    .addComponent(jpfPassword2))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jtfPath, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jpfPassword1, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jpfPassword2, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addContainerGap(45, Short.MAX_VALUE))
        );

        jbCancel.setText("Cancelar");
        jbCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbCancelActionPerformed(evt);
            }
        });

        jbConfirm.setText("Confirmar");
        jbConfirm.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jbConfirmActionPerformed(evt);
            }
        });

        jcbShowPassword.setText("Mostrar Senhas");
        jcbShowPassword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jcbShowPasswordActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jcbShowPassword)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 180, Short.MAX_VALUE)
                        .addComponent(jbConfirm, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jbCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jbCancel)
                    .addComponent(jbConfirm)
                    .addComponent(jcbShowPassword))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jbConfirmActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbConfirmActionPerformed
        confirm();
    }//GEN-LAST:event_jbConfirmActionPerformed

    private void jbCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jbCancelActionPerformed
        setVisible(false);
    }//GEN-LAST:event_jbCancelActionPerformed

    private void jcbShowPasswordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jcbShowPasswordActionPerformed
        showPasswords();
    }//GEN-LAST:event_jcbShowPasswordActionPerformed

    private void jpfPassword2KeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jpfPassword2KeyPressed
        if (confirmPassword) {
            if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
                confirm();
            }
        }
    }//GEN-LAST:event_jpfPassword2KeyPressed

    private void jpfPassword1KeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jpfPassword1KeyPressed
        if (!confirmPassword) {
            if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
                confirm();
            }
        }
    }//GEN-LAST:event_jpfPassword1KeyPressed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JButton jbCancel;
    private javax.swing.JButton jbConfirm;
    private javax.swing.JCheckBox jcbShowPassword;
    private javax.swing.JPasswordField jpfPassword1;
    private javax.swing.JPasswordField jpfPassword2;
    private javax.swing.JTextField jtfPath;
    // End of variables declaration//GEN-END:variables

}