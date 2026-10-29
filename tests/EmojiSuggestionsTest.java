package local.gboard.enhanced;
import java.nio.file.*;import java.util.*;import java.io.*;
public final class EmojiSuggestionsTest{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  EmojiSuggestions.load(Files.newInputStream(Paths.get(args[0])));
  check(EmojiSuggestions.suggest("ok",Collections.emptyList()).contains("👌"),"lowercase OK alias");
  check(EmojiSuggestions.suggest("OK",Collections.emptyList()).contains("👌"),"uppercase OK alias");
  check(EmojiSuggestions.suggest("xiexie",Arrays.asList("谢谢")).contains("🙏"),"Chinese candidate alias");
  check(EmojiSuggestions.suggest("zaijian",Arrays.asList("再见")).contains("👋"),"goodbye alias");
  check(EmojiSuggestions.suggest("weixiao",Arrays.asList("微笑")).contains("😊"),"smile alias");
  check(EmojiSuggestions.suggest("yandai",Arrays.asList("眼袋")).contains("🫩"),"newer emoji preserved on older Unicode runtimes");
  List<String> ok=EmojiSuggestions.suggest("ok",Arrays.asList("好","好的"));
  check(ok.size()<=3&&new HashSet<>(ok).size()==ok.size(),"bounded and unique");
  check(EmojiSuggestions.suggest("",Arrays.asList("好")).isEmpty(),"no predictions without composition");
  check(EmojiSuggestions.suggest("unexpected",Arrays.asList("中文测试哨兵")).isEmpty(),"no invented mappings");
  check(EmojiSuggestions.suggest("ok",Collections.emptyList()).stream().noneMatch("OK"::equals),"skip literal source token");
  System.out.println("PASS: 10 offline emoji checks, complete bundled dictionary");
 }
}
