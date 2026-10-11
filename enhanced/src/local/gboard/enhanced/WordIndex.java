package local.gboard.enhanced;
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
public final class WordIndex {
  public static final class Entry {
    final String code,word;final int weight;
    Entry(String c,String w,int n){code=c;word=w;weight=n;}
  }
  private static volatile ByteBuffer data;private static int count,size,blob,trees;
  public static void load(File file)throws IOException{
    try(FileInputStream in=new FileInputStream(file)){
      ByteBuffer map=in.getChannel().map(FileChannel.MapMode.READ_ONLY,0,file.length());
      if(map.getInt(0)!=0x47424933)throw new IOException("Invalid word index");
      count=map.getInt(4);size=map.getInt(8);trees=12+count*8;blob=trees+size*16;data=map;
    }
  }
  private static Entry read(int position,boolean chinese){
    ByteBuffer b=data.duplicate();int index=chinese?b.getInt(12+count*4+position*4):position;
    b.position(blob+b.getInt(12+index*4));
    byte[] code=new byte[b.getShort()&65535];b.get(code);
    byte[] word=new byte[b.getShort()&65535];b.get(word);
    return new Entry(new String(code,StandardCharsets.UTF_8),new String(word,StandardCharsets.UTF_8),b.getInt());
  }
  private static int lower(String key,boolean chinese){
    int l=0,r=count;
    while(l<r){int m=(l+r)>>>1;Entry e=read(m,chinese);String value=chinese?e.word:e.code;if(value.compareTo(key)<0)l=m+1;else r=m;}
    return l;
  }
  public static List<Entry> complete(String raw){
    String key=raw.replace("'","");
    if(key.length()<4||data==null)return Collections.emptyList();
    return matches(key,false,8);
  }
  public static List<Entry> following(String previous){
    for(int n=Math.min(6,previous.length());n>=1;n--){
      if(data==null)return Collections.emptyList();
      List<Entry> list=matches(previous.substring(previous.length()-n),true,12);
      if(!list.isEmpty())return list;
    }
    return Collections.emptyList();
  }
  private static List<Entry> matches(String prefix,boolean chinese,int limit){
    PriorityQueue<Range> pending=new PriorityQueue<>((a,b)->Float.compare(b.score,a.score));
    addRange(pending,lower(prefix+"\u0000",chinese),lower(prefix+"\uffff",chinese),chinese);
    ArrayList<Entry> result=new ArrayList<>();Set<String> seen=new HashSet<>();int examined=0;
    while(!pending.isEmpty()&&result.size()<limit&&examined++<256){
      Range range=pending.poll();Entry e=read(range.index,chinese);
      if((!chinese||e.word.length()-prefix.length()<=6)&&seen.add(e.word))result.add(e);
      addRange(pending,range.start,range.index,chinese);addRange(pending,range.index+1,range.end,chinese);
    }
    if(chinese){ArrayList<Entry> suffix=new ArrayList<>();for(Entry e:result)suffix.add(new Entry(e.code,e.word.substring(prefix.length()),e.weight));return suffix;}
    return result;
  }
  private static class Range{int start,end,index;float score;Range(int l,int r,int i,float s){start=l;end=r;index=i;score=s;}}
  private static void addRange(PriorityQueue<Range> q,int start,int end,boolean chinese){
    if(start>=end)return;int base=trees+(chinese?size*8:0),l=start+size,r=end+size,node=0;float score=Float.NEGATIVE_INFINITY;
    while(l<r){if((l&1)!=0){float s=data.getFloat(base+l*4);if(s>score){score=s;node=l;}l++;}if((r&1)!=0){--r;float s=data.getFloat(base+r*4);if(s>score){score=s;node=r;}}l>>>=1;r>>>=1;}
    if(node==0)return;while(node<size){int child=node*2;node=data.getFloat(base+child*4)>=data.getFloat(base+(child+1)*4)?child:child+1;}
    q.add(new Range(start,end,node-size,score));
  }
}
