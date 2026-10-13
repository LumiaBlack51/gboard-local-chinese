package local.gboard.proofread;
import java.io.*;
import java.util.*;
/** WordPiece offsets let us change only one original Han character, never Latin/code/number spans. */
final class ChineseTokenizer {
  final List<String> vocab=new ArrayList<>();final Map<String,Integer> ids=new HashMap<>();
  static final class Token {final int id,start,end;Token(int i,int s,int e){id=i;start=s;end=e;}}
  ChineseTokenizer(File file)throws Exception{try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){String s;while((s=r.readLine())!=null){ids.put(s,vocab.size());vocab.add(s);}}}
  List<Token> encode(String text){ArrayList<Token> out=new ArrayList<>();for(int i=0;i<text.length();){int cp=text.codePointAt(i),length=Character.charCount(cp);if(Character.isWhitespace(cp)){i+=length;continue;}
    if(Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN||!Character.isLetterOrDigit(cp)){String s=text.substring(i,i+length);out.add(new Token(ids.getOrDefault(s,100),i,i+length));i+=length;continue;}
    int start=i;i+=length;while(i<text.length()){cp=text.codePointAt(i);if(!Character.isLetterOrDigit(cp)||Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN)break;i+=Character.charCount(cp);}String word=text.substring(start,i).toLowerCase(Locale.ROOT);ArrayList<Token> parts=new ArrayList<>();int pos=0;
    while(pos<word.length()){Integer id=null;int end=word.length();for(;end>pos;end--){id=ids.get((pos==0?"":"##")+word.substring(pos,end));if(id!=null)break;}if(id==null){parts.clear();parts.add(new Token(100,start,i));break;}parts.add(new Token(id,start+pos,start+end));pos=end;}out.addAll(parts);
  }return out;}
}
