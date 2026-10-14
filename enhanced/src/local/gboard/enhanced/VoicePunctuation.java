package local.gboard.enhanced;

/** Accept punctuation insertions only; retain every original character and its case. */
public final class VoicePunctuation {
  public interface Model { String apply(String text); }
  private static final String MARKS=",.?!、，。？！";
  private static final String BOUNDARIES=MARKS+";:；：\"“”‘’()（）[]【】《》\n";
  private VoicePunctuation(){}
  private static boolean identifier(char c){return c<128&&(Character.isLetterOrDigit(c)||c=='_');}

  public static String restore(String text,Model model){
    if(text==null||text.isEmpty()||text.length()>2048)return text;
    // Addresses, decimal values and code-like expressions must remain exact.
    if(text.contains("://")||text.contains("@")||text.matches("(?s).*\\bwww\\..*")||text.matches("(?s).*[0-9][.:/][0-9].*"))return text;
    StringBuilder result=new StringBuilder();int start=0;
    for(int i=0;i<=text.length();i++){
      if(i<text.length()&&BOUNDARIES.indexOf(text.charAt(i))<0)continue;
      String part=text.substring(start,i),trimmed=part.trim();
      if(trimmed.length()>1&&!trimmed.matches("[A-Za-z0-9_./+\\-]+")){
        String candidate=insertionsOnly(trimmed,model.apply(trimmed));
        // The following original mark owns this boundary; do not duplicate it.
        if(i<text.length())while(!candidate.isEmpty()&&MARKS.indexOf(candidate.charAt(candidate.length()-1))>=0)candidate=candidate.substring(0,candidate.length()-1);
        int begin=part.indexOf(trimmed);result.append(part,0,begin).append(candidate).append(part.substring(begin+trimmed.length()));
      }else result.append(part);
      if(i<text.length())result.append(text.charAt(i));start=i+1;
    }
    return result.toString();
  }

  public static String insertionsOnly(String original,String candidate){
    if(candidate==null||candidate.length()>original.length()*2+16)return original;
    boolean han=original.matches("(?s).*[\\p{IsHan}].*");
    StringBuilder out=new StringBuilder();int i=0,j=0;boolean inserted=false;
    while(j<candidate.length()){
      int c=candidate.codePointAt(j),width=Character.charCount(c);
      if(Character.isWhitespace(c)){j+=width;continue;}
      if(i<original.length()&&Character.toLowerCase(original.codePointAt(i))==Character.toLowerCase(c)){
        int n=Character.charCount(original.codePointAt(i));out.append(original,i,i+n);i+=n;j+=width;inserted=false;continue;
      }
      if(MARKS.indexOf(c)>=0){
        if(i==0||inserted)return original; // No leading or repeated invented punctuation.
        if(i<original.length()&&identifier(original.charAt(i-1))&&identifier(original.charAt(i)))return original;
        char mark=(char)c;
        if(han){if(mark==',')mark='，';if(mark=='.')mark='。';if(mark=='?')mark='？';if(mark=='!')mark='！';}
        else{if(mark=='，'||mark=='、')mark=',';if(mark=='。')mark='.';if(mark=='？')mark='?';if(mark=='！')mark='!';}
        out.append(mark);inserted=true;j+=width;
        continue;
      }
      if(i<original.length()&&Character.isWhitespace(original.codePointAt(i))){out.appendCodePoint(original.codePointAt(i));i+=Character.charCount(original.codePointAt(i));continue;}
      return original;
    }
    while(i<original.length()&&Character.isWhitespace(original.codePointAt(i))){out.appendCodePoint(original.codePointAt(i));i+=Character.charCount(original.codePointAt(i));}
    return i==original.length()?out.toString():original;
  }
}
