import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public final class PayloadJarObfuscator {
    private static final int ASM = Opcodes.ASM9;
    private static final class ClassData {
        final byte[] bytes; final String name;
        ClassData(byte[] bytes) { this.bytes = bytes; this.name = new ClassReader(bytes).getClassName(); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("Usage: <input.jar> <output.jar> <mapping.txt> <root/internal> <main/internal> <runtime/internal> <version>");
            System.exit(2);
        }
        Path input=Paths.get(args[0]), output=Paths.get(args[1]), mappingFile=Paths.get(args[2]);
        String root=args[3], main=args[4], runtime=args[5], version=args[6];
        LinkedHashMap<String,byte[]> resources=new LinkedHashMap<>(); List<ClassData> classes=new ArrayList<>();
        try(JarInputStream jis=new JarInputStream(Files.newInputStream(input))){ JarEntry e; while((e=jis.getNextJarEntry())!=null){
            if(e.isDirectory()) continue; byte[] data=readAll(jis);
            if(e.getName().endsWith(".class")) classes.add(new ClassData(data));
            else if(!e.getName().startsWith("META-INF/") || e.getName().equals("META-INF/MANIFEST.MF")) resources.put(e.getName(),data);
        }}
        classes.sort(Comparator.comparing(c->c.name));
        Map<String,String> classMapping=buildClassMapping(classes,root,main,runtime);
        Map<String,String> mapping=new LinkedHashMap<>(classMapping);
        for(ClassData cd:classes){ final String owner=cd.name; final NameSequence fields=new NameSequence("a"), methods=new NameSequence("a");
            new ClassReader(cd.bytes).accept(new ClassVisitor(ASM){
                @Override public FieldVisitor visitField(int access,String name,String desc,String sig,Object value){
                    if((access&Opcodes.ACC_PRIVATE)!=0 && !"serialVersionUID".equals(name)) mapping.put(owner+"."+name,fields.next()); return null; }
                @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex){
                    if((access&Opcodes.ACC_PRIVATE)!=0 && !name.startsWith("<")) mapping.put(owner+"."+name+desc,methods.next()); return null; }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        SimpleRemapper remapper=new SimpleRemapper(mapping); Files.createDirectories(output.toAbsolutePath().getParent());
        Manifest mf=new Manifest(); mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,"1.0"); mf.getMainAttributes().putValue("Created-By","Enum protected payload obfuscator");
        Set<String>written=new HashSet<>();
        try(JarOutputStream jos=new JarOutputStream(Files.newOutputStream(output),mf)){
            for(ClassData cd:classes){ ClassReader cr=new ClassReader(cd.bytes); ClassWriter cw=new ClassWriter(0);
                ClassVisitor cv=new ClassRemapper(cw,remapper){ @Override public void visitSource(String source,String debug){} };
                cr.accept(cv,ClassReader.SKIP_DEBUG); byte[] out=cw.toByteArray(); String name=new ClassReader(out).getClassName()+".class"; put(jos,written,name,out);
            }
            for(Map.Entry<String,byte[]> re:resources.entrySet()){ String name=re.getKey(); if(name.equals("META-INF/MANIFEST.MF"))continue; byte[] data=re.getValue();
                if(name.equals("plugin.yml")){ String t=new String(data,StandardCharsets.UTF_8); t=t.replaceFirst("(?m)^version:\\s*.*$","version: "+version); data=t.getBytes(StandardCharsets.UTF_8); }
                put(jos,written,name,data);
            }
        }
        writeMapping(mappingFile,classes,classMapping,mapping);
        verify(output,classMapping,main,runtime);
        System.out.println("Obfuscated: "+output+" classes="+classes.size());
    }
    private static Map<String,String> buildClassMapping(List<ClassData> classes,String root,String main,String runtime){
        Map<String,List<String>> topsByPkg=new TreeMap<>(), inners=new TreeMap<>();
        for(ClassData cd:classes){ if(!cd.name.startsWith(root+"/") && !cd.name.equals(main))continue; String top=cd.name.contains("$")?cd.name.substring(0,cd.name.indexOf('$')):cd.name;
            if(cd.name.equals(top)) topsByPkg.computeIfAbsent(pkg(top),k->new ArrayList<>()).add(top); else inners.computeIfAbsent(top,k->new ArrayList<>()).add(cd.name); }
        Map<String,String> pkgMap=new LinkedHashMap<>(); NameSequence pseq=new NameSequence("a");
        for(String p:topsByPkg.keySet()) pkgMap.put(p,p.equals(root)?root:root+"/"+pseq.next());
        Map<String,String> result=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> pe:topsByPkg.entrySet()){ List<String> tops=pe.getValue(); Collections.sort(tops); NameSequence cseq=new NameSequence("a");
            for(String top:tops){ String mt=(top.equals(main)||top.equals(runtime))?top:pkgMap.get(pe.getKey())+"/"+cseq.next(); result.put(top,mt);
                List<String> ins=inners.getOrDefault(top,Collections.emptyList()); Collections.sort(ins); NameSequence iseq=new NameSequence("a"); for(String in:ins) result.put(in,mt+"$"+iseq.next()); }
        }
        NameSequence fallback=new NameSequence("z"); for(ClassData cd:classes) if(cd.name.startsWith(root+"/")&&!result.containsKey(cd.name)) result.put(cd.name,root+"/z/"+fallback.next());
        return result;
    }
    private static void writeMapping(Path f,List<ClassData> classes,Map<String,String> cm,Map<String,String> all)throws IOException{
        Files.createDirectories(f.toAbsolutePath().getParent()); try(BufferedWriter w=Files.newBufferedWriter(f,StandardCharsets.UTF_8)){
            w.write("# Keep private.\n# Classes\n"); for(ClassData cd:classes){String m=cm.get(cd.name);if(m!=null)w.write(cd.name.replace('/','.')+" -> "+m.replace('/','.')+"\n");}
            w.write("\n# Private members\n"); for(Map.Entry<String,String>e:all.entrySet())if(!cm.containsKey(e.getKey()))w.write(e.getKey().replace('/','.')+" -> "+e.getValue()+"\n"); }
    }
    private static void verify(Path out,Map<String,String> cm,String main,String runtime)throws IOException{
        Set<String> es=new HashSet<>();try(JarInputStream jis=new JarInputStream(Files.newInputStream(out))){JarEntry e;while((e=jis.getNextJarEntry())!=null)es.add(e.getName());}
        if(!es.contains(main+".class"))throw new IOException("Main missing"); if(!es.contains(runtime+".class"))throw new IOException("Runtime missing");
        for(String mapped:cm.values())if(!es.contains(mapped+".class"))throw new IOException("Mapped class missing: "+mapped);
    }
    private static void put(JarOutputStream jos,Set<String>w,String n,byte[]d)throws IOException{if(!w.add(n))throw new IOException("Duplicate "+n);JarEntry e=new JarEntry(n);e.setTime(0);jos.putNextEntry(e);jos.write(d);jos.closeEntry();}
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toByteArray();}
    private static String pkg(String n){int i=n.lastIndexOf('/');return i<0?"":n.substring(0,i);}    
    private static final class NameSequence{int i;final String p;NameSequence(String p){this.p=p;}String next(){return p+enc(i++);}static String enc(int n){StringBuilder s=new StringBuilder();do{s.append((char)('a'+n%26));n=n/26-1;}while(n>=0);return s.reverse().toString();}}
}
