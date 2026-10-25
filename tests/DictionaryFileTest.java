package local.gboard.enhanced;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class DictionaryFileTest {
  private static int checks;
  private static void check(boolean result){checks++;if(!result)throw new AssertionError("Check "+checks);}
  private static List<DictionaryFile.Entry> read(String text)throws IOException {
    return DictionaryFile.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }
  private static void rejects(byte[] data)throws Exception {
    try{DictionaryFile.read(new ByteArrayInputStream(data));throw new AssertionError("Accepted invalid input");}catch(IOException expected){checks++;}
  }
  private static void rejects(String text)throws Exception {rejects(text.getBytes(StandardCharsets.UTF_8));}
  private static byte[] zip(String name,byte[] contents)throws Exception {
    ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream archive=new ZipOutputStream(out)){
      archive.putNextEntry(new ZipEntry(name));archive.write(contents);archive.closeEntry();
    }return out.toByteArray();
  }
  public static void main(String[] args)throws Exception {
    List<DictionaryFile.Entry> words=read("\ufeff# 本地合成样例\r\n离线词库\tLI XIAN'CI KU\t200\r\n女绿\tnü lv\n离线词库\tlixianciku\t300\n");
    check(words.size()==2);check(words.get(0).weight==300);check(words.get(1).code.equals("nvlv"));
    String gboard="# Gboard Dictionary version:1\nlixianciku\t离线词库\tzh-CN\n";
    check(DictionaryFile.read(new ByteArrayInputStream(zip("dictionary.txt",gboard.getBytes(StandardCharsets.UTF_8)))).get(0).word.equals("离线词库"));
    check(read(new String(DictionaryFile.encode(words),StandardCharsets.UTF_8)).size()==2);
    check(DictionaryFile.readStored(new ByteArrayInputStream("# empty\n".getBytes(StandardCharsets.UTF_8))).isEmpty());
    rejects("# empty\n");rejects("离线词库\n");rejects("离线词库\tlixian1\n");rejects("离线词库\tlixianciku\t0\n");
    rejects("离线词库\tlixianciku\t1000001\n");rejects("离线词库\tlixianciku\n坏行\n");
    rejects("# Gboard Dictionary version:1\nhello\t你好\ten-US\n");rejects("../test\tli\n");rejects(new byte[]{(byte)0xff,(byte)0xfe,0,0});
    rejects(zip("../dictionary.txt",gboard.getBytes(StandardCharsets.UTF_8)));
    rejects(zip("dictionary.txt",new byte[DictionaryFile.MAX_BYTES+1]));rejects(new byte[DictionaryFile.MAX_BYTES+1]);
    ArrayList<DictionaryFile.Entry> many=new ArrayList<>();
    for(int i=0;i<5000;i++){int n=i;StringBuilder code=new StringBuilder("a");do{code.append((char)('a'+n%26));n/=26;}while(n>0);many.add(new DictionaryFile.Entry("合成测试",code.toString(),100));}
    check(DictionaryFile.read(new ByteArrayInputStream(DictionaryFile.encode(many))).size()==5000);
    List<DictionaryFile.Entry> added=read("离线词库\tlixianciku\t500\n");
    check(DictionaryFile.merge(words,added).size()==2);check(DictionaryFile.merge(words,added).get(0).weight==500);
    try{DictionaryFile.merge(many,added);throw new AssertionError("Accepted merge overflow");}catch(IOException expected){checks++;}
    many.add(added.get(0));rejects(DictionaryFile.encode(many));
    System.out.println("DictionaryFile: "+checks+" checks passed");
  }
}
