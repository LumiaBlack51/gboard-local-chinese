package local.gboard.enhanced;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/** Actual cumulative selections. Dictionary import frequency is not learning. */
public final class LearningFile {
  public static final String PREFIX="# learned\t";
  public static final class Entry {
    public final String word,code;public final int count;public final long last;
    public Entry(String w,String c,int n,long t){word=w;code=c;count=n;last=t;}
    public String key(){return word+"\t"+code;}
  }
  public static boolean validWord(String word){
    int size=word.codePointCount(0,word.length());if(size<1||size>32)return false;
    for(int i=0;i<word.length();){int c=word.codePointAt(i);i+=Character.charCount(c);
      Character.UnicodeBlock b=Character.UnicodeBlock.of(c);String name=b==null?"":b.toString();
      if(c!=0x3007&&!name.startsWith("CJK_UNIFIED_IDEOGRAPHS")&&!name.startsWith("CJK_COMPATIBILITY_IDEOGRAPHS"))return false;
    }return true;
  }
  private static void add(Map<String,Entry> result,Entry e)throws IOException {
    Entry before=result.get(e.key());
    result.put(e.key(),before==null?e:new Entry(e.word,e.code,Math.max(before.count,e.count),Math.max(before.last,e.last)));
    if(result.size()>DictionaryFile.MAX_WORDS)throw new IOException("too many learning entries");
  }
  public static List<Entry> merge(List<Entry> before,List<Entry> added)throws IOException {
    Map<String,Entry> result=new TreeMap<>();for(List<Entry> rows:Arrays.asList(before,added))for(Entry e:rows)add(result,e);
    return new ArrayList<>(result.values());
  }
  public static List<Entry> read(InputStream stream,boolean packet)throws IOException {
    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
    while((n=stream.read(buffer))!=-1){if(out.size()+n>DictionaryFile.MAX_BYTES)throw new IOException("learning data too large");out.write(buffer,0,n);}
    String text;
    try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString();}
    catch(CharacterCodingException error){throw new IOException("invalid learning UTF-8",error);}
    if(text.startsWith("\ufeff"))text=text.substring(1);
    Map<String,Entry> result=new TreeMap<>();
    for(String line:text.split("\\r?\\n",-1)){
      if(packet){if(!line.startsWith(PREFIX))continue;line=line.substring(PREFIX.length());}
      else if(line.isEmpty()||line.startsWith("#"))continue;
      String[] f=line.split("\\t",-1);
      if(f.length!=4||!validWord(f[0])||!f[1].matches("[a-z]{1,128}")||!f[2].matches("[0-9]{1,7}")||!f[3].matches("[0-9]{1,16}"))throw new IOException("invalid learning record");
      try{int count=Integer.parseInt(f[2]);long last=Long.parseLong(f[3]);if(count<1||count>1000000||last<0||last>9000000000000000L)throw new NumberFormatException();add(result,new Entry(f[0],f[1],count,last));}
      catch(NumberFormatException error){throw new IOException("invalid learning counter",error);}
    }
    return new ArrayList<>(result.values());
  }
  public static byte[] encode(List<Entry> rows,boolean packet){
    StringBuilder out=new StringBuilder(packet?"":"# Local selection aggregates:1\n");
    for(Entry e:rows)out.append(packet?PREFIX:"").append(e.word).append('\t').append(e.code).append('\t').append(e.count).append('\t').append(e.last).append('\n');
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }
  public static List<Entry> rimeSnapshot(InputStream in)throws IOException {
    Map<String,Entry> result=new TreeMap<>();Pattern counter=Pattern.compile("(?:^| )c=([0-9]+)(?: |$)");
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;int total=0;
      while((line=reader.readLine())!=null){total+=line.length();if(total>DictionaryFile.MAX_BYTES)throw new IOException("Rime snapshot too large");
        if(line.startsWith("#"))continue;String[] f=line.split("\\t");if(f.length!=3)continue;
        Matcher match=counter.matcher(f[2]);String code=DictionaryFile.normalize(f[0]);
        if(!match.find()||!validWord(f[1])||!code.matches("[a-z]{1,128}"))continue;
        try{long count=Long.parseLong(match.group(1));if(count>0)add(result,new Entry(f[1],code,(int)Math.min(1000000,count),0));}
        catch(NumberFormatException ignored){}
      }
    }return new ArrayList<>(result.values());
  }
}
