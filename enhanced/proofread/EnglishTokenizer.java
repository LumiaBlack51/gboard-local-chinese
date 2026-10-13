package local.gboard.proofread;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import org.json.*;
/** T5's fixed unigram vocabulary, with no executable tokenizer code or remote files. */
final class EnglishTokenizer {
  final String[] pieces;final float[] scores;final Map<String,Integer> ids=new HashMap<>();int longest;
  EnglishTokenizer(File file)throws Exception{
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
    JSONArray vocab=new JSONObject(bytes.toString("UTF-8")).getJSONObject("model").getJSONArray("vocab");pieces=new String[vocab.length()];scores=new float[pieces.length];
    for(int i=0;i<pieces.length;i++){JSONArray row=vocab.getJSONArray(i);pieces[i]=row.getString(0);scores[i]=(float)row.getDouble(1);if(i>2&&!pieces[i].startsWith("<")){ids.put(pieces[i],i);longest=Math.max(longest,pieces[i].length());}}
  }
  long[] encode(String text){
    String s="▁"+Normalizer.normalize(text,Normalizer.Form.NFKC).trim().replaceAll("\\s+","▁");int n=s.length();float[] best=new float[n+1];Arrays.fill(best,Float.NEGATIVE_INFINITY);best[0]=0;int[] prev=new int[n+1],token=new int[n+1];
    for(int i=0;i<n;i++){boolean one=false;for(int len=1;len<=longest&&i+len<=n;len++){Integer id=ids.get(s.substring(i,i+len));if(id==null)continue;if(len==1)one=true;float value=best[i]+scores[id];if(value>best[i+len]){best[i+len]=value;prev[i+len]=i;token[i+len]=id;}}
      if(!one&&best[i]-100>best[i+1]){best[i+1]=best[i]-100;prev[i+1]=i;token[i+1]=2;}}
    ArrayList<Long> result=new ArrayList<>();for(int i=n;i>0;i=prev[i])result.add((long)token[i]);Collections.reverse(result);result.add(1L);long[] out=new long[result.size()];for(int i=0;i<out.length;i++)out[i]=result.get(i);return out;
  }
  String decode(List<Integer> tokens){StringBuilder b=new StringBuilder();for(int id:tokens)if(id>2&&id<pieces.length)b.append(pieces[id]);return b.toString().replace('▁',' ').trim();}
}
