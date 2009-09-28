package chat;


import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.security.cert.*;

class Cifrador {
    public byte[][] cifra (PublicKey pub, byte[] textoClaro) throws NoSuchAlgorithmException,
    NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
    BadPaddingException, InvalidAlgorithmParameterException {
        byte[] textoCifrado = null;
        byte[] chaveCifrada = null;

        //-- A) Gerando uma chave simétrica de 128 bits
        KeyGenerator kg = KeyGenerator.getInstance ("AES");
        kg.init (128);
        SecretKey sk = kg.generateKey ();
        byte[] chave = sk.getEncoded();
        //-- B) Cifrando o texto com a chave simétrica gerada
        Cipher aescf = Cipher.getInstance ("AES/CBC/PKCS5Padding");
        IvParameterSpec ivspec = new IvParameterSpec (new byte[16]);
        aescf.init (Cipher.ENCRYPT_MODE, new SecretKeySpec (chave, "AES"), ivspec);
        textoCifrado = aescf.doFinal (textoClaro);
        //-- C) Cifrando a chave com a chave pública
        Cipher rsacf = Cipher.getInstance ("RSA");
        rsacf.init (Cipher.ENCRYPT_MODE, pub);
        chaveCifrada = rsacf.doFinal (chave);

        return new byte[][] { textoCifrado, chaveCifrada };
    }
}

class Decifrador {
    public byte[] decifra (PrivateKey pvk, byte[] textoCifrado, byte[] chaveCifrada) throws NoSuchAlgorithmException,
    NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
    BadPaddingException, InvalidAlgorithmParameterException  {
        byte[] textoDecifrado = null;

        //-- A) Decifrando a chave simétrica com a chave privada
        Cipher rsacf = Cipher.getInstance ("RSA");
        rsacf.init (Cipher.DECRYPT_MODE, pvk);
        byte[] chaveDecifrada = rsacf.doFinal (chaveCifrada);
        //-- B) Decifrando o texto com a chave simétrica decifrada
        Cipher aescf = Cipher.getInstance ("AES/CBC/PKCS5Padding");
        IvParameterSpec ivspec = new IvParameterSpec (new byte[16]);
        aescf.init (Cipher.DECRYPT_MODE, new SecretKeySpec (chaveDecifrada, "AES"), ivspec);
        textoDecifrado = aescf.doFinal (textoCifrado);

        return textoDecifrado;
    }
}

class CarregadorChavePublica {
    public PublicKey carregaChavePublica (File fPub) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream (new FileInputStream (fPub));
        PublicKey ret = (PublicKey) ois.readObject();
        ois.close();
        return ret;
    }
}

class CarregadorChavePrivada {
    public PrivateKey carregaChavePrivada (File fPvk) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream (new FileInputStream (fPvk));
        PrivateKey ret = (PrivateKey) ois.readObject();
        ois.close();
        return ret;
    }
}


class GeradorParChaves {
    private static final int RSAKEYSIZE = 1024;

    public void geraParChaves(File fPub, File fPvk)
        throws IOException, NoSuchAlgorithmException,
        InvalidAlgorithmParameterException,
        CertificateException, KeyStoreException {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance ("RSA");
        kpg.initialize (new RSAKeyGenParameterSpec(RSAKEYSIZE, RSAKeyGenParameterSpec.F4));
        KeyPair kpr = kpg.generateKeyPair ();
        PrivateKey priv = kpr.getPrivate();
        PublicKey pub = kpr.getPublic();
        
        //-- Gravando a chave pública em formato serializado
        ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream (fPub));
        oos.writeObject (pub);
        oos.close();

        //-- Gravando a chave privada em formato serializado
        //-- Não é a melhor forma (deveria ser guardada em um keystore, e protegida por senha),
        //-- mas isto é só um exemplo
        oos = new ObjectOutputStream (new FileOutputStream (fPvk));
        oos.writeObject (priv);
        oos.close();

    }
}

class Criptografia
{
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

    public static void main(String[] args) throws Exception {
        //-- Gera o par de chaves, em dois arquivos (chave.publica e chave.privada)
        GeradorParChaves gpc = new GeradorParChaves();
        gpc.geraParChaves (new File ("chave.publica"), new File ("chave.privada"));

        //-- Cifrando a mensagem "Hello, world!"
        byte[] textoClaro = "Hello, world!".getBytes("ISO-8859-1");
        CarregadorChavePublica ccp = new CarregadorChavePublica();
        PublicKey pub = ccp.carregaChavePublica (new File ("chave.publica"));
        Cifrador cf = new Cifrador();
        byte[][] cifrado = cf.cifra (pub, textoClaro);
        printHex (cifrado[0]);
        printHex (cifrado[1]);

        //-- Decifrando a mensagem
        CarregadorChavePrivada ccpv = new CarregadorChavePrivada();
        PrivateKey pvk = ccpv.carregaChavePrivada (new File ("chave.privada"));
        Decifrador dcf = new Decifrador();
        byte[] decifrado = dcf.decifra (pvk, cifrado[0], cifrado[1]);
        System.out.println (new String (decifrado, "ISO-8859-1")+"....");
    }
     
}









