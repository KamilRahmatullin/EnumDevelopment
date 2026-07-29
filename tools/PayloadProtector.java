import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class PayloadProtector {
    public static void main(String[] args) throws Exception {
        if (args.length != 13) {
            System.err.println("Usage: <clean.jar> <obf.jar> <out.jar> <main/internal> <runtime/internal> <payload/path> <magicHex> <aad> <partAHex> <partBHex> <partCHex> <partDHex> <suffixHex>");
            System.exit(2);
        }
        Path clean=Paths.get(args[0]), obf=Paths.get(args[1]), out=Paths.get(args[2]);
        String main=args[3], runtime=args[4], payloadPath=args[5]; byte[] magic=hex(args[6]), aad=args[7].getBytes(StandardCharsets.UTF_8);
        byte[][] parts={hex(args[8]),hex(args[9]),hex(args[10]),hex(args[11])}; byte[] suffix=hex(args[12]);
        List<Entry> bootstrap=readBootstrap(clean,main), resources=readResources(clean), payload=readPayload(obf,main,runtime);
        byte[] plain=zip(payload), key=derive(parts,suffix), iv=new byte[12]; new SecureRandom().nextBytes(iv); byte[] enc;
        try { Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv)); c.updateAAD(aad); enc=c.doFinal(plain); }
        finally { Arrays.fill(key,(byte)0); Arrays.fill(plain,(byte)0); }
        ByteArrayOutputStream packed=new ByteArrayOutputStream(); packed.write(magic); packed.write(iv); packed.write(enc); Arrays.fill(iv,(byte)0); Arrays.fill(enc,(byte)0);
        Files.createDirectories(out.toAbsolutePath().getParent()); Manifest mf=new Manifest(); mf.getMainAttributes().putValue("Manifest-Version","1.0"); mf.getMainAttributes().putValue("Created-By","Enum AES-GCM protected build");
        try(JarOutputStream jos=new JarOutputStream(Files.newOutputStream(out),mf)){
            Collections.sort(bootstrap); Collections.sort(resources); for(Entry e:bootstrap)write(jos,e); write(jos,new Entry(payloadPath,packed.toByteArray())); for(Entry e:resources)write(jos,e);
        }
        System.out.println("Protected: "+out+" visible="+bootstrap.size()+" payload="+payload.size()+" size="+Files.size(out));
    }
    static List<Entry> readBootstrap(Path p,String main)throws Exception{List<Entry>r=new ArrayList<>();try(JarFile j=new JarFile(p.toFile())){Enumeration<JarEntry>es=j.entries();while(es.hasMoreElements()){JarEntry e=es.nextElement();String n=e.getName();if(!e.isDirectory()&&n.endsWith(".class")&&(n.equals(main+".class")||n.startsWith(main+"$")))r.add(new Entry(n,read(j.getInputStream(e))));}}if(r.isEmpty())throw new IOException("Main missing");return r;}
    static List<Entry> readResources(Path p)throws Exception{List<Entry>r=new ArrayList<>();try(JarFile j=new JarFile(p.toFile())){Enumeration<JarEntry>es=j.entries();while(es.hasMoreElements()){JarEntry e=es.nextElement();String n=e.getName();if(e.isDirectory()||n.equals("META-INF/MANIFEST.MF")||n.endsWith(".class")||n.startsWith("META-INF/"))continue;r.add(new Entry(n,read(j.getInputStream(e))));}}return r;}
    static List<Entry> readPayload(Path p,String main,String runtime)throws Exception{List<Entry>r=new ArrayList<>();boolean ok=false;try(JarFile j=new JarFile(p.toFile())){Enumeration<JarEntry>es=j.entries();while(es.hasMoreElements()){JarEntry e=es.nextElement();String n=e.getName();if(e.isDirectory()||!n.endsWith(".class")||n.equals(main+".class")||n.startsWith(main+"$"))continue;r.add(new Entry(n,read(j.getInputStream(e))));if(n.equals(runtime+".class"))ok=true;}}if(!ok)throw new IOException("Runtime missing");return r;}
    static byte[] zip(List<Entry>es)throws Exception{Collections.sort(es);ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){z.setLevel(9);for(Entry e:es){ZipEntry ze=new ZipEntry(e.name);ze.setTime(0);z.putNextEntry(ze);z.write(e.data);z.closeEntry();}}return b.toByteArray();}
    static byte[]derive(byte[][]p,byte[]suffix)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");for(int i=0;i<16;i++){d.update((byte)(p[0][i]^p[2][15-i]));d.update((byte)(p[1][15-i]^p[3][i]));}d.update(suffix);return d.digest();}
    static byte[]hex(String s){int n=s.length();byte[]b=new byte[n/2];for(int i=0;i<n;i+=2)b[i/2]=(byte)Integer.parseInt(s.substring(i,i+2),16);return b;}
    static byte[]read(InputStream in)throws Exception{try(InputStream i=in;ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[]b=new byte[8192];int n;while((n=i.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}}
    static void write(JarOutputStream j,Entry e)throws Exception{JarEntry x=new JarEntry(e.name);x.setTime(0);j.putNextEntry(x);j.write(e.data);j.closeEntry();}
    static final class Entry implements Comparable<Entry>{final String name;final byte[]data;Entry(String n,byte[]d){name=n;data=d;}public int compareTo(Entry o){return name.compareTo(o.name);}}
}
