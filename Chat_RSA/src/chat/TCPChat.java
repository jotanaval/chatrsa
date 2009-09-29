package chat;

import java.lang.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import java.net.*;


////////////////////////////////////////////////////////////////////

// Action adapter for easy event-listener coding
public class TCPChat implements Runnable {
   // Connect status constants
   public final static int NULL = 0;
   public final static int DISCONNECTED = 1;
   public final static int DISCONNECTING = 2;
   public final static int BEGIN_CONNECT = 3;
   public final static int CONNECTED = 4;

   // Other constants
   public final static String statusMessages[] = {
      " Erro! Não foi possível conectar!", " Desconectado",
      " Desconectando...", " Conectando...", " Conectado"
   };
   public final static TCPChat tcpObj = new TCPChat();
   public final static String END_CHAT_SESSION =
      new Character((char)0).toString(); // Indicates the end of a session

   // Connection atate info
   public static String hostIP = "localhost";
   public static int port = 1234;
   public static int connectionStatus = DISCONNECTED;
   public static boolean isHost = true;
   public static String statusString = statusMessages[connectionStatus];
   public static StringBuffer toAppend = new StringBuffer("");
   public static StringBuffer toAppend2 = new StringBuffer("");
   public static StringBuffer toSend = new StringBuffer("");

   // Various GUI components and info
   public static JFrame mainFrame = null;
   public static JTextArea chatText = null;
   public static JTextField chatLine = null;
   public static JPanel statusBar = null;
   public static JLabel statusField = null;
   public static JTextField statusColor = null;
   public static JTextField ipField = null;
   public static JTextField portField = null;
   public static JTextField nameField = null;
   public static JRadioButton hostOption = null;
   public static JRadioButton guestOption = null;
   public static JButton connectButton = null;
   public static JButton disconnectButton = null;

   // TCP Components
   public static ServerSocket hostServer = null;
   public static Socket socket = null;
  public static BufferedReader in = null;
   public static PrintWriter out = null;
    private static JTextArea chatText2;
    private static JTextField chatLine2;
  
    private static PublicKey chave_publica;
    private static byte[] chave_simetrica;
    private static BufferedOutputStream bos;
    private static BufferedInputStream bis;



   /////////////////////////////////////////////////////////////////

   private static JPanel initOptionsPane() {
      JPanel pane = null;
      ActionAdapter buttonListener = null;

      // Create an options pane
      JPanel optionsPane = new JPanel(new GridLayout(5, 1));

      // IP address input
      pane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      pane.add(new JLabel("Host IP:"));
      ipField = new JTextField(10); ipField.setText(hostIP);
      ipField.setEnabled(false);
      ipField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
               ipField.selectAll();
               // Should be editable only when disconnected
               if (connectionStatus != DISCONNECTED) {
                  changeStatusNTS(NULL, true);
               }
               else {
                  hostIP = ipField.getText();
               }
            }
         });
      pane.add(ipField);



      optionsPane.add(pane);

      // Port input
      pane = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      pane.add(new JLabel("Porta:"));
      portField = new JTextField(10); portField.setEditable(true);
      portField.setText((new Integer(port)).toString());
      portField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
               // should be editable only when disconnected
               if (connectionStatus != DISCONNECTED) {
                  changeStatusNTS(NULL, true);
               }
               else {
                  int temp;
                  try {
                     temp = Integer.parseInt(portField.getText());
                     port = temp;
                  }
                  catch (NumberFormatException nfe) {
                     portField.setText((new Integer(port)).toString());
                     mainFrame.repaint();
                  }
               }
            }
         });
      pane.add(portField);
      optionsPane.add(pane);








      // Host/guest option
      buttonListener = new ActionAdapter() {
            public void actionPerformed(ActionEvent e) {
               if (connectionStatus != DISCONNECTED) {
                  changeStatusNTS(NULL, true);
               }
               else {
                  isHost = e.getActionCommand().equals("host");

                  // Cannot supply host IP if host option is chosen
                  if (isHost) {
                     ipField.setEnabled(false);
                     ipField.setText("localhost");
                     hostIP = "localhost";
                  }
                  else {
                     ipField.setEnabled(true);
                  }
               }
            }
         };
      ButtonGroup bg = new ButtonGroup();
      hostOption = new JRadioButton("Servidor", true);
      hostOption.setMnemonic(KeyEvent.VK_H);
      hostOption.setActionCommand("host");
      hostOption.addActionListener(buttonListener);
      guestOption = new JRadioButton("Cliente", false);
      guestOption.setMnemonic(KeyEvent.VK_G);
      guestOption.setActionCommand("guest");
      guestOption.addActionListener(buttonListener);
      bg.add(hostOption);
      bg.add(guestOption);
      pane = new JPanel(new GridLayout(1, 2));
      pane.add(hostOption);
      pane.add(guestOption);
      optionsPane.add(pane);

      // Connect/disconnect buttons
      JPanel buttonPane = new JPanel(new GridLayout(1, 2));
      buttonListener = new ActionAdapter() {
            public void actionPerformed(ActionEvent e) {
               // Request a connection initiation
               if (e.getActionCommand().equals("connect")) {
                  changeStatusNTS(BEGIN_CONNECT, true);
               }
               // Disconnect
               else {
                  changeStatusNTS(DISCONNECTING, true);
               }
            }
         };
      connectButton = new JButton("Conectar");
      connectButton.setMnemonic(KeyEvent.VK_C);
      connectButton.setActionCommand("connect");
      connectButton.addActionListener(buttonListener);
      connectButton.setEnabled(true);
      disconnectButton = new JButton("Desconectar");
      disconnectButton.setMnemonic(KeyEvent.VK_D);
      disconnectButton.setActionCommand("disconnect");
      disconnectButton.addActionListener(buttonListener);
      disconnectButton.setEnabled(false);
      buttonPane.add(connectButton);
      buttonPane.add(disconnectButton);
      optionsPane.add(buttonPane);

      return optionsPane;
   }

   /////////////////////////////////////////////////////////////////

   // Initialize all the GUI components and display the frame
   private static void initGUI() {
      // Set up the status bar
      statusField = new JLabel();
      statusField.setText(statusMessages[DISCONNECTED]);
      statusColor = new JTextField(1);
      statusColor.setBackground(Color.red);
      statusColor.setEditable(false);
      statusBar = new JPanel(new BorderLayout());
      statusBar.add(statusColor, BorderLayout.WEST);
      statusBar.add(statusField, BorderLayout.CENTER);

      // Set up the options pane
      JPanel optionsPane = initOptionsPane();

      // Set up the chat pane
      JPanel chatPane = new JPanel(new BorderLayout());

      chatText = new JTextArea(10, 20);
      chatText.setLineWrap(true);
      chatText.setEditable(false);
      chatText.setForeground(Color.blue);
      JScrollPane chatTextPane = new JScrollPane(chatText,
         JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
         JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
      chatLine = new JTextField();
      chatLine.setEnabled(true);
      chatLine.addActionListener(new ActionAdapter() {
            public void actionPerformed(ActionEvent e) {
               String s = chatLine.getText();
               if (!s.equals("")) {
                  appendToChatBox("ENVIADO: " + s + "\n");
                  chatLine.selectAll();
     
                  // Send the string
                  sendString(s);
               }
            }
         });
      chatPane.add(chatLine, BorderLayout.SOUTH);
      chatPane.add(chatTextPane, BorderLayout.WEST);
      chatPane.setPreferredSize(new Dimension(200, 200));

      chatText2 = new JTextArea(10, 20);
      chatText2.setLineWrap(true);
      chatText2.setEditable(false);
      chatText2.setForeground(Color.blue);
      JScrollPane chatTextPane2 = new JScrollPane(chatText2,
      JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
      JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
      chatLine = new JTextField();
      chatLine.setEnabled(false);
      chatLine.addActionListener(new ActionAdapter() {
            public void actionPerformed(ActionEvent e) {
               String s = chatLine.getText();
               if (!s.equals("")) {
                  appendToChatBox("ENVIADO: " + s );
               
                  chatLine.selectAll();
                  // Send the string
                  sendString(s);

               }
            }
         });

       
      chatPane.add(chatLine, BorderLayout.SOUTH);
      chatPane.add(chatTextPane2, BorderLayout.EAST);
      chatPane.setPreferredSize(new Dimension(480, 200));







      // Set up the main pane
      JPanel mainPane = new JPanel(new BorderLayout());
      mainPane.add(statusBar, BorderLayout.SOUTH);
      mainPane.add(optionsPane, BorderLayout.WEST);
      mainPane.add(chatPane, BorderLayout.CENTER);

      // Set up the main frame
      mainFrame = new JFrame("RSA MESSENGER");
      mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      mainFrame.setContentPane(mainPane);
      mainFrame.setSize(mainFrame.getPreferredSize());
      mainFrame.setLocation(200, 200);
      mainFrame.pack();
      mainFrame.setVisible(true);
      
   }

   /////////////////////////////////////////////////////////////////

   // The thread-safe way to change the GUI components while
   // changing state
   private static void changeStatusTS(int newConnectStatus, boolean noError) {
      // Change state if valid state
      if (newConnectStatus != NULL) {
         connectionStatus = newConnectStatus;
      }

      // If there is no error, display the appropriate status message
      if (noError) {
         statusString = statusMessages[connectionStatus];
      }
      // Otherwise, display error message
      else {
         statusString = statusMessages[NULL];
      }

      // Call the run() routine (Runnable interface) on the
      // error-handling and GUI-update thread
      SwingUtilities.invokeLater(tcpObj);
   }

   /////////////////////////////////////////////////////////////////

   // The non-thread-safe way to change the GUI components while
   // changing state
   private static void changeStatusNTS(int newConnectStatus, boolean noError) {
      // Change state if valid state
      if (newConnectStatus != NULL) {
         connectionStatus = newConnectStatus;
      }

      // If there is no error, display the appropriate status message
      if (noError) {
         statusString = statusMessages[connectionStatus];
      }
      // Otherwise, display error message
      else {
         statusString = statusMessages[NULL];
      }

      // Call the run() routine (Runnable interface) on the
      // current thread
      tcpObj.run();
   }

   /////////////////////////////////////////////////////////////////

   // Thread-safe way to append to the chat box
   private static void appendToChatBox(String s) {
      synchronized (toAppend) {
         toAppend.append(s);
      }
   }
    private static void appendToChatBox2(String s) {
      synchronized (toAppend) {
         toAppend.append(s);
      }
   }

   /////////////////////////////////////////////////////////////////

   // Add text to send-buffer
   private static void sendString(String s) {
      synchronized (toSend) {
         toSend.append(s + "\n");
          
      }
   }

   /////////////////////////////////////////////////////////////////

   // Cleanup for disconnect
   private static void cleanUp() throws IOException {
      try {
         if (hostServer != null) {
            hostServer.close();
            hostServer = null;
         }
      }
      catch (IOException e) { hostServer = null; }

      try {
         if (socket != null) {
            socket.close();
            socket = null;
         }
      }
      catch (IOException e) { socket = null; }

      try {
         if (bis != null) {
            bis.close();
            bis = null;
         }
      }
      catch (IOException e) { bis = null; }

      if (bos != null) {
         bos.close();
         bos = null;
      }
   }
   

   /////////////////////////////////////////////////////////////////

   // Checks the current state and sets the enables/disables
   // accordingly
  public void run() {
      switch (connectionStatus) {
      case DISCONNECTED:
         connectButton.setEnabled(true);
         disconnectButton.setEnabled(false);
         ipField.setEnabled(true);
         portField.setEnabled(true);
         hostOption.setEnabled(true);
         guestOption.setEnabled(true);
         chatLine.setText(""); chatLine.setEnabled(false);
         statusColor.setBackground(Color.red);
         break;

      case DISCONNECTING:
         connectButton.setEnabled(false);
         disconnectButton.setEnabled(false);
         ipField.setEnabled(false);
         portField.setEnabled(false);
         hostOption.setEnabled(false);
         guestOption.setEnabled(false);
         chatLine.setEnabled(false);
         statusColor.setBackground(Color.orange);
         break;

      case CONNECTED:
         connectButton.setEnabled(false);
         disconnectButton.setEnabled(true);
         ipField.setEnabled(false);
         portField.setEnabled(false);
         hostOption.setEnabled(false);
         guestOption.setEnabled(false);
         chatLine.setEnabled(true);
         statusColor.setBackground(Color.green);
         break;

      case BEGIN_CONNECT:
         connectButton.setEnabled(false);
         disconnectButton.setEnabled(false);
         ipField.setEnabled(false);
         portField.setEnabled(false);
         hostOption.setEnabled(false);
         guestOption.setEnabled(false);
         chatLine.setEnabled(false);
         chatLine.grabFocus();
         statusColor.setBackground(Color.orange);
         break;
      }

      // Make sure that the button/text field states are consistent
      // with the internal states
     
      ipField.setText(hostIP);
      portField.setText((new Integer(port)).toString());
      hostOption.setSelected(isHost);
      guestOption.setSelected(!isHost);
      statusField.setText(statusString);
      chatText.append(toAppend.toString()+"\n");   
      
      toAppend.setLength(0);

      mainFrame.repaint();
   }

   /////////////////////////////////////////////////////////////////

public static byte[] convertStringToByteArray(String s) {



        byte[] theByteArray = s.getBytes();


       return theByteArray;
    }


   public static final byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
}

   static void printHex(byte[] b) {
        if (b == null) {
            System.out.println ("(null)");
        } else {
        for (int i = 0; i < b.length; ++i) {
            if (i % 16 == 0) {
                System.out.print (Integer.toHexString ((i & 0xFFFF) | 0x10000).substring(1,5) + " - ");
            }
            System.out.print (Integer.toHexString((b[i]&0xFF) | 0x100).substring(1,3) + " ");
            if (i % 16 == 15 || i == b.length - 1)
            {
                int j;
                for (j = 16 - i % 16; j > 1; --j)
                    System.out.print ("   ");
                System.out.print (" - ");
                int start = (i / 16) * 16;
                int end = (b.length < i + 1) ? b.length : (i + 1);
                for (j = start; j < end; ++j)
                    if (b[j] >= 32 && b[j] <= 126)
                        System.out.print ((char)b[j]);
                    else
                        System.out.print (".");
                System.out.println ();
            }
        }
        System.out.println();
        }
    }

   // The main procedure
   public static void main(String args[]) throws ClassNotFoundException, IOException, InterruptedException {
      String s = null;
      Integer is=0;
      

      initGUI();
     
     while (true) {
         try { // Poll every ~10 ms
            Thread.sleep(5);
         }
         catch (InterruptedException e) {}

         switch (connectionStatus) {
         case BEGIN_CONNECT:
            try {
               // Try to set up a server if host
               if (isHost) {
                  hostServer = new ServerSocket(port);
                  socket = hostServer.accept();
               }

               // If guest, try to connect to the server
               else {
                  socket = new Socket(hostIP, port);
               }

             // in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              // out = new PrintWriter(socket.getOutputStream(), true);
                bis = new BufferedInputStream(socket.getInputStream());
                bos = new BufferedOutputStream(socket.getOutputStream());

               changeStatusTS(CONNECTED, true);
            }
            // If error, clean up and output an error message
            catch (IOException e) {
               cleanUp();
               changeStatusTS(DISCONNECTED, false);
            }
            break;

 case CONNECTED:
            try {
              // Send data

               if (toSend.length() != 0)
               {      
                    byte[] codificar = new byte[64];
                    byte[] codificar2 = new byte[64];
                    codificar = toSend.toString().getBytes("ISO-8859-1")   ;
                    codificar2=  "chave_grande_pra_testar_tamanho_do_byte".getBytes("ISO-8859-1")   ;
                   
                    
                    CarregadorChavePublica ccp = new CarregadorChavePublica();
                    PublicKey pub = ccp.carregaChavePublica (new File ("chave.publica"));
                    Cifrador cf = new Cifrador();
                    try {
                        byte[][] cifrado = cf.cifra(pub, codificar);

                            System.out.println(cifrado[0].length+"tamanho do texto_cifrado");
                
                            System.out.println(cifrado[1].length+"tamanho da chave cifrada");

                            
                            int j = 0;
                            int k = 0;
                            byte [] ciff = new byte[cifrado[0].length];
                            ciff=cifrado[0];
                            byte [] ciff2 = new byte[cifrado[1].length];
                            ciff2=cifrado[1];
                             byte[] enviar=new byte[cifrado[0].length+cifrado[1].length];
                            for (int i = 0; i < 128+ciff.length; i++)
                            {
                             
                               if(i<128)
                               {
                                   enviar[i]=ciff2[i];
                                   j=128;
                               }
                               if(i>=128 && i<128+ciff.length)
                               {
                                    enviar[j]=ciff[k];
                                    j++;
                                    k++;
                               }

                            }
                            
                            
                             System.out.println(new String(cifrado[1],"ISO-8859-1")+"  saida de chave cifrada");
                             System.out.println(new String(cifrado[0],"ISO-8859-1")+"  saida de texto cifrado");
                             System.out.println(enviar.length);
                            bos.write(enviar,0,enviar.length);
                            chatText2.append("ENVIADO: " + new String(cifrado[0],"ISO-8859-1") + "\n");

                        } catch (NoSuchAlgorithmException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (NoSuchPaddingException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InvalidKeyException ex) {
                            Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IllegalBlockSizeException ex) {
                            Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (BadPaddingException ex) {
                            Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InvalidAlgorithmParameterException ex) {
                            Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                        }
         
                    bos.flush();
                    toSend.setLength(0);
                    changeStatusTS(NULL, true);
                }

               // Receive data
                if (bis.available()>1)
                 {
                    int tamanho= bis.available();
                     System.out.println(tamanho+"tamanho da saida");
                    byte[] ler = new byte[tamanho];
                    bis.read(ler, 0, tamanho);
                     System.out.println(new String(ler,"ISO-8859-1")+"  saida de dados");
                    byte[] texto_decrypt = new byte[tamanho-128];
                    byte[] chave_decrypt = new byte[128];
                    int j=0;
                    
                     for (int i = 0; i < ler.length; i++) 
                     {  
                         if(i<128)
                         {
                             chave_decrypt[i] = ler[i];
                             j=0;
                         }
                         if(i>=128 && i<tamanho)
                         {  
                             texto_decrypt[j] = ler[i] ;
                             j++;
                         }
                         
                     }

                 System.out.println(chave_decrypt.length+"  saida de chave cifrada");
                 System.out.println(texto_decrypt.length+"  saida de texto cifrado");
                
               //-- Decifrando a mensagem
                CarregadorChavePrivada ccpv = new CarregadorChavePrivada();
                     System.out.println("carregando chave");
                PrivateKey pvk = ccpv.carregaChavePrivada (new File ("chave.privada"));
                     System.out.println("chave carregada");
                Decifrador dcf = new Decifrador();
                byte[] decifrado = null;
                    try {
                        decifrado = dcf.decifra(pvk, texto_decrypt, chave_decrypt);
                    } catch (NoSuchAlgorithmException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (NoSuchPaddingException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (InvalidKeyException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalBlockSizeException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (BadPaddingException ex) {
                        ex.printStackTrace();
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (InvalidAlgorithmParameterException ex) {
                        Logger.getLogger(TCPChat.class.getName()).log(Level.SEVERE, null, ex);
                    }
       
                    String mensagem_recebida_decriptografada=new String(decifrado,"ISO-8859-1");
                    System.out.println(mensagem_recebida_decriptografada+"  mensagem recebida");
                 
                     

    
                
                   
                 

                 

                     
                  if ((s != null) &&  (s.length() != 0))
                  {
                     // Check if it is the end of a trasmission
                     if (s.equals(END_CHAT_SESSION))
                     {
                        changeStatusTS(DISCONNECTING, true);
                     }
                     // Otherwise, receive what text
                     else
                     {
                        appendToChatBox("RECEBIDO: " + mensagem_recebida_decriptografada + "\n");
                        changeStatusTS(NULL, true);
                     }
                  }
                 }
            }
            catch (IOException e) {
               cleanUp();
               changeStatusTS(DISCONNECTED, false);
            }
            break;

         case DISCONNECTING:
            // Tell other chatter to disconnect as well
            out.write(END_CHAT_SESSION,0,END_CHAT_SESSION.length()); bos.flush();

            // Clean up (close all streams/sockets)
            cleanUp();
            changeStatusTS(DISCONNECTED, true);
            break;

         default: break; // do nothing
         }
      }
   }
}



