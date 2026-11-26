package local.gboard.enhanced;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Repeated adjacent pinyin commits can suggest a phrase before it has ever been selected. */
final class SplitPhraseLearning {
  private static final int MAX_PARTS=4, MAX_CHARS=8;
  private static final long GAP_MS=30_000;
  private final ArrayDeque<WordIndex.Entry> previous=new ArrayDeque<>();
  private long lastCommit;

  void reset(){previous.clear();}

  void committed(Context context,String raw,String word,long now){
    String code=DictionaryFile.normalize(raw);
    if(!code.matches("[a-z]{1,128}")||!LearningFile.validWord(word)||word.codePointCount(0,word.length())>=MAX_CHARS){
      reset();return;
    }
    if(now-lastCommit>GAP_MS)reset();
    lastCommit=now;
    String combinedCode=code,combinedWord=word;
    for(Iterator<WordIndex.Entry> parts=previous.descendingIterator();parts.hasNext();){
      WordIndex.Entry part=parts.next();
      combinedCode=part.code+combinedCode;combinedWord=part.word+combinedWord;
      if(combinedCode.length()>128||combinedWord.codePointCount(0,combinedWord.length())>MAX_CHARS)break;
      observe(context,combinedCode,combinedWord);
    }
    if(previous.size()==MAX_PARTS-1)previous.removeFirst();
    previous.addLast(new WordIndex.Entry(code,word,0));
  }

  // Bounded local evidence. Each bucket holds its 16 most recently observed
  // pairs. Reading one exact spelling never scans or loads a list of all words.
  private static SharedPreferences prefs(Context context){
    return context.getSharedPreferences("split-phrase-learning",Context.MODE_PRIVATE);
  }
  static void preload(Context context){prefs(context).getAll();}
  private static String bucket(String code){return Integer.toString(code.hashCode()&255);}

  private static void observe(Context context,String code,String word){
    SharedPreferences prefs=prefs(context);
    String key=bucket(code),pair=code+"\t"+word+"\t";
    StringBuilder others=new StringBuilder();int kept=0;boolean repeated=false;
    for(String row:prefs.getString(key,"").split("\n")){
      if(row.startsWith(pair)){repeated=true;continue;}
      if(!row.isEmpty()&&kept<15){others.append('\n').append(row);kept++;}
    }
    prefs.edit().putString(key,pair+(repeated?"2":"1")+others).apply();
  }

  static List<String> match(Context context,String raw){
    String code=DictionaryFile.normalize(raw);
    if(code.length()<2)return Collections.emptyList();
    String prefix=code+"\t";
    List<String> matches=new ArrayList<>();
    for(String row:prefs(context).getString(bucket(code),"").split("\n"))
      if(row.startsWith(prefix)&&row.endsWith("\t2"))
        matches.add(row.substring(prefix.length(),row.length()-2));
    return matches;
  }
}
