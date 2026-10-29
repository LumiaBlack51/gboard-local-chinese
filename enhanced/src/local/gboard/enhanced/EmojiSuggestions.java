package local.gboard.enhanced;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Static offline aliases shared by normal and private Chinese input. */
public final class EmojiSuggestions {
  private static volatile Map<String,List<String>> aliases=Collections.emptyMap();
  private EmojiSuggestions(){}
  public static void load(InputStream stream)throws IOException {
    Map<String,List<String>> next=new HashMap<>();
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
      String line;
      while((line=reader.readLine())!=null){
        if(line.isEmpty()||line.startsWith("#"))continue;
        String[] fields=line.split("\t",2);if(fields.length!=2)continue;
        List<String> values=new ArrayList<>();
        for(String value:fields[1].split(" +"))if(emoji(value)&&!values.contains(value))values.add(value);
        if(!values.isEmpty())next.put(fields[0].toLowerCase(Locale.ROOT),Collections.unmodifiableList(values));
      }
    }
    aliases=Collections.unmodifiableMap(next);
  }
  private static boolean emoji(String text){
    for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);
      if((cp>=0x1f000&&cp<=0x1faff)||Character.getType(cp)==Character.OTHER_SYMBOL||cp==0xfe0f)return true;
    }return false;
  }
  public static List<String> suggest(String raw,List<String> nativeWords){
    if(raw.isEmpty())return Collections.emptyList();
    LinkedHashSet<String> result=new LinkedHashSet<>();
    add(result,aliases.get(raw.toLowerCase(Locale.ROOT)));
    for(int i=0;i<Math.min(3,nativeWords.size());i++)add(result,aliases.get(nativeWords.get(i).toLowerCase(Locale.ROOT)));
    return new ArrayList<>(result);
  }
  private static void add(Set<String> result,List<String> values){
    if(values==null)return;
    for(String value:values){if(result.size()>=3)return;result.add(value);}
  }
}
