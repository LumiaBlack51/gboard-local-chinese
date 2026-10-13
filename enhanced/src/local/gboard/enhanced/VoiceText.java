package local.gboard.enhanced;
import java.util.*;
import java.util.regex.*;
/** Pure text policy shared by the IME and the offline proofreading component. */
public final class VoiceText {
  private static final Set<String> ACRONYMS=new HashSet<>(Arrays.asList("AI API CPU GPU NPU USB PDF DNA RNA NASA USA UK EU UN SQL HTML CSS HTTP HTTPS URL IP ID PIN GPS RAM ROM SSD HDD RTX GTX DIY FAQ CEO CTO CV PhD".toUpperCase(Locale.ROOT).split(" ")));
  private static final Set<String> CAPITALIZED=new HashSet<>(Arrays.asList("monday tuesday wednesday thursday friday saturday sunday january february march april may june july august september october november december english chinese china america europe london beijing shanghai python java android github".split(" ")));
  private static final Pattern WORD=Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)*|[0-9]+(?:[.:][0-9]+)*|[\\p{IsHan}]");
  private static final Set<String> FUNCTION=new HashSet<>(Arrays.asList("a an the am is are was were be been being have has had do does did to of in on at for with by from as than that which who whom whose it its this these those there their they them he him his she her we us our you your my me i s re ve ll d".split(" ")));
  private static final Pattern PROTECTED=Pattern.compile("[A-Za-z]*[0-9][A-Za-z0-9.:/_-]*|\\b(?:no|not|never|nothing|nobody|without|zero|one|two|three|four|five|six|seven|eight|nine|ten|hundred|thousand|million)\\b|n['’]t|[不没未无别零〇一二两三四五六七八九十百千万亿]",Pattern.CASE_INSENSITIVE);
  public static String normalize(String text){return text==null?"":text.trim().replaceAll("\\s+"," ").replaceAll("(?<=[\\p{IsHan}]) +(?=[\\p{IsHan}])","");}
  /** The acoustic model emits uppercase Latin tokens; mixed-case user text is preserved. */
  public static String sentenceCase(String input){
    String s=normalize(input);boolean upper=s.matches("(?s).*[A-Z].*")&&!s.matches("(?s).*[a-z].*");
    Matcher m=Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)*").matcher(s);StringBuffer b=new StringBuffer();
    while(m.find()){String word=m.group(),lower=word.toLowerCase(Locale.ROOT),v=word;
      boolean code=(m.end()<s.length()&&Character.isDigit(s.charAt(m.end())))||(m.start()>0&&Character.isDigit(s.charAt(m.start()-1)));
      if(upper&&!code){v=ACRONYMS.contains(word)?word:lower;}
      if(lower.equals("i")||lower.startsWith("i'")||lower.startsWith("i’"))v="I"+v.substring(1);
      if(upper&&CAPITALIZED.contains(lower))v=Character.toUpperCase(lower.charAt(0))+lower.substring(1);
      m.appendReplacement(b,Matcher.quoteReplacement(v));
    }m.appendTail(b);return capitalizeSentences(b.toString());
  }
  public static String capitalizeSentences(String text){StringBuilder b=new StringBuilder(text);boolean start=true;for(int i=0;i<b.length();i++){char c=b.charAt(i);if(start&&c>='a'&&c<='z'){b.setCharAt(i,Character.toUpperCase(c));start=false;}else if(Character.isLetterOrDigit(c))start=false;if(".!?。！？".indexOf(c)>=0)start=true;}return b.toString();}
  /** One ASR endpoint is one utterance. Do not invent internal clause boundaries. */
  public static String finishUtterance(String text){
    String s=normalize(text);if(s.isEmpty())return s;
    int end=s.length();while(end>0&&"\"”’）)]】》".indexOf(s.charAt(end-1))>=0)end--;
    if(end==0||".!?。！？…,:;，：；".indexOf(s.charAt(end-1))>=0)return s;
    String body=s.substring(0,end);boolean han=body.matches("(?s).*[\\p{IsHan}].*");
    // Preserve a lone address, URL, code or numeric value as an exact token.
    if(!han&&!body.contains(" ")&&(body.contains("@")||body.contains("://")||body.matches(".*[0-9].*")))return s;
    boolean question=han?body.matches("(?s).*(?:吗|呢|么|是不是|好不好|行不行|对不对)$"):
      body.matches("(?i)^(?:(?:what|where|when|why|who|whose|which|how)\\b.*|(?:can|could|would|will|shall|should|do|does|did|is|are|was|were|have|has)\\s+(?:I|you|we|they|he|she|it|this|that|there)\\b.*)");
    return body+(han?(question?"？":"。"):question?"?":".")+s.substring(end);
  }
  private static List<String> matches(Pattern p,String s){ArrayList<String> r=new ArrayList<>();Matcher m=p.matcher(s);while(m.find())r.add(m.group().toLowerCase(Locale.ROOT).replace('’','\''));return r;}
  private static int distance(String a,String b){int[] row=new int[b.length()+1];for(int j=0;j<row.length;j++)row[j]=j;for(int i=1;i<=a.length();i++){int prev=row[0];row[0]=i;for(int j=1;j<=b.length();j++){int old=row[j];row[j]=Math.min(Math.min(row[j]+1,row[j-1]+1),prev+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=old;}}return row[b.length()];}
  /** Reject semantic additions/deletions, protected values, large rewrites and responses to dictated commands. */
  public static boolean acceptableEnglish(String original,String candidate){
    if(candidate==null||candidate.isEmpty()||candidate.length()>original.length()*1.4+12||candidate.contains("\n")||candidate.matches("(?s).*[\\p{IsHan}].*"))return false;
    if(!matches(PROTECTED,original).equals(matches(PROTECTED,candidate)))return false;
    List<String> a=matches(WORD,original),b=matches(WORD,candidate);if(a.isEmpty()||b.isEmpty())return false;
    for(String word:a)if(ACRONYMS.contains(word.toUpperCase(Locale.ROOT))&&!b.contains(word))return false;
    int n=a.size(),m=b.size();int[][] d=new int[n+1][m+1];for(int i=0;i<=n;i++)d[i][0]=i;for(int j=0;j<=m;j++)d[0][j]=j;
    for(int i=1;i<=n;i++)for(int j=1;j<=m;j++)d[i][j]=Math.min(Math.min(d[i-1][j]+1,d[i][j-1]+1),d[i-1][j-1]+(a.get(i-1).equals(b.get(j-1))?0:1));
    if(d[n][m]>Math.max(2,(int)Math.ceil(n*.25)))return false;
    int i=n,j=m;while(i>0||j>0){
      if(i>0&&j>0&&d[i][j]==d[i-1][j-1]+(a.get(i-1).equals(b.get(j-1))?0:1)){
        String x=a.get(--i),y=b.get(--j);if(!x.equals(y)&&!(FUNCTION.contains(x)&&FUNCTION.contains(y))&&distance(x,y)>2)return false;
      }else if(j>0&&d[i][j]==d[i][j-1]+1){if(!FUNCTION.contains(b.get(--j)))return false;}
      else{String x=a.get(--i);if(!FUNCTION.contains(x)&&(i==0||!x.equals(a.get(i-1))))return false;}
    }
    return true;
  }
  public static String restoreAcronyms(String original,String candidate){
    Matcher m=Pattern.compile("\\b[A-Z]{2,}\\b").matcher(original);while(m.find()){String w=m.group();if(ACRONYMS.contains(w))candidate=candidate.replaceAll("(?i)\\b"+w+"\\b",w);}
    // An initialism is spoken letter by letter; preserve an already correct article.
    Matcher article=Pattern.compile("(?i)\\ban ([AEFHILMNORSX][A-Z]{1,})\\b").matcher(original);
    while(article.find()){String acronym=article.group(1);if(ACRONYMS.contains(acronym)){candidate=candidate.replaceAll("(?i)\\ba "+acronym+"\\b","an "+acronym);if(!Pattern.compile("(?i)\\ban "+acronym+"\\b").matcher(candidate).find())candidate=candidate.replaceAll("(?i)\\b"+acronym+"\\b","an "+acronym);}}
    return capitalizeSentences(candidate);
  }
}
