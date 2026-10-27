package local.gboard.enhanced;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/** Bounded, all-or-nothing reader for explicit local word lists. No Android dependencies. */
public final class DictionaryFile {
  public static final int MAX_WORDS=50000, MAX_BYTES=16*1024*1024;
  public static final class Entry {
    public final String word,code;
    public final int weight;
    Entry(String w,String c,int n){word=w;code=c;weight=n;}
  }
  private static byte[] bounded(InputStream in,int limit)throws IOException {
    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
    while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("词库文件过大（文本最多 16 MiB）");out.write(buffer,0,n);}
    return out.toByteArray();
  }
  public static List<Entry> read(InputStream in)throws IOException {
    return read(in,false);
  }
  public static List<Entry> readStored(InputStream in)throws IOException {return read(in,true);}
  private static List<Entry> read(InputStream in,boolean stored)throws IOException {
    byte[] raw=bounded(in,MAX_BYTES);
    if(raw.length>=2&&raw[0]=='P'&&raw[1]=='K'){
      byte[] dictionary=null;int files=0;
      try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(raw))){
        ZipEntry entry;
        while((entry=zip.getNextEntry())!=null){
          if(++files>8)throw new IOException("请使用只含 dictionary.txt 的 Gboard 词库 ZIP");
          if(!"dictionary.txt".equals(entry.getName())||entry.isDirectory()||dictionary!=null)
            throw new IOException("请使用只含 dictionary.txt 的 Gboard 词库 ZIP");
          dictionary=bounded(zip,MAX_BYTES);zip.closeEntry();
        }
      }
      if(dictionary==null)throw new IOException("ZIP 中没有 dictionary.txt");
      raw=dictionary;
    }
    String text;
    try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    catch(CharacterCodingException error){throw new IOException("需要 UTF-8 文本；讯飞二进制或加密备份不能直接导入");}
    if(text.startsWith("\ufeff"))text=text.substring(1);
    if(stored&&text.equals("# empty\n"))return Collections.emptyList();
    LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();boolean gboard=false;int number=0;
    for(String line:text.split("\\r?\\n",-1)){
      number++;line=line.trim();if(line.isEmpty())continue;
      if(line.equals("# Gboard Dictionary version:1")){gboard=true;continue;}
      if(line.startsWith("#"))continue;
      String[] fields=line.split("\\t",-1);String word,code;int weight=100;
      if(gboard){
        if(fields.length!=3)throw invalid(number);
        if(!fields[2].trim().replace('_','-').equalsIgnoreCase("zh-CN")&&!fields[2].trim().equalsIgnoreCase("zh"))
          throw new IOException("第 "+number+" 行不是中文词条");
        code=fields[0];word=fields[1];
      }else{
        if(fields.length<2||fields.length>3)throw invalid(number);
        word=fields[0];code=fields[1];
        if(fields.length==3)try{weight=Integer.parseInt(fields[2].trim());}catch(NumberFormatException error){throw invalid(number);}
      }
      word=word.trim();code=normalize(code);
      if(!word.matches("[\\p{IsHan}]{1,32}")||!code.matches("[a-z]{1,128}")||weight<1||weight>1000000)throw invalid(number);
      String key=code+"\t"+word;Entry previous=entries.get(key);
      entries.put(key,new Entry(word,code,previous==null?weight:Math.max(previous.weight,weight)));
      if(entries.size()>MAX_WORDS)throw new IOException("最多导入 50000 条词条，请拆分并核对词库");
    }
    if(entries.isEmpty()&&stored)return Collections.emptyList();
    if(entries.isEmpty())throw new IOException("文件没有有效词条；需要「词语<Tab>无声调拼音」或 Gboard 词库 ZIP");
    return Collections.unmodifiableList(new ArrayList<>(entries.values()));
  }
  private static IOException invalid(int line){return new IOException("第 "+line+" 行格式不正确；使用「中文词语<Tab>无声调拼音<Tab>词频（可选）」；词频为 1～1000000");}
  public static String normalize(String code){return code.trim().toLowerCase(Locale.ROOT).replace("u:","v").replace("ü","v").replaceAll("[\\s'’]","");}
  public static List<Entry> merge(List<Entry> old,List<Entry> added)throws IOException {
    LinkedHashMap<String,Entry> result=new LinkedHashMap<>();
    for(List<Entry> batch:Arrays.asList(old,added))for(Entry entry:batch){
      String key=entry.code+"\t"+entry.word;Entry previous=result.get(key);
      result.put(key,previous==null||entry.weight>previous.weight?entry:previous);
    }
    if(result.size()>MAX_WORDS)throw new IOException("合并后超过 50000 条，原词库保持不变");
    return Collections.unmodifiableList(new ArrayList<>(result.values()));
  }
  public static byte[] encode(List<Entry> entries){
    StringBuilder text=new StringBuilder("# Local Chinese dictionary version:1\n");
    for(Entry e:entries)text.append(e.word).append('\t').append(e.code).append('\t').append(e.weight).append('\n');
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }
}
