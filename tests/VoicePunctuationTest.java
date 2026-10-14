import local.gboard.enhanced.VoicePunctuation;
public final class VoicePunctuationTest {
  static int count;
  static void check(String input,String predicted,String expected){String actual=VoicePunctuation.insertionsOnly(input,predicted);if(!actual.equals(expected))throw new AssertionError(input+" => "+actual+" expected "+expected);count++;}
  static void restore(String input,VoicePunctuation.Model model,String expected){String actual=VoicePunctuation.restore(input,model);if(!actual.equals(expected))throw new AssertionError(actual);count++;}
  public static void main(String[] args){
    check("我买了苹果香蕉和橘子","我买了苹果、香蕉和橘子。","我买了苹果、香蕉和橘子。");
    check("WHEN YOU ARRIVE PLEASE CALL ME","when you arrive，please call me。","WHEN YOU ARRIVE, PLEASE CALL ME.");
    check("GPU型号是B27不要改成B72","GPU型号是B27，不要改成B72。","GPU型号是B27，不要改成B72。");
    check("不要取消订单","要取消订单。","不要取消订单");
    check("HELLO HOW ARE YOU I AM FINE THANK YOU","HELLO，HOW ARE YOU？I AM FINE。","HELLO HOW ARE YOU I AM FINE THANK YOU");
    check("今天开会","今天不开会。","今天开会");
    check("订单B27","订单B72。","订单B27");
    check("订单B27明天送达","订单B，27明天送达。","订单B27明天送达");
    check("GPU测试","G，PU测试。","GPU测试");
    check("你好世界","，你好世界。","你好世界");
    check("你好世界","你好，，世界。","你好世界");
    check("你好世界",null,"你好世界");
    check("你好😀世界","你好😀，世界。","你好😀，世界。");
    check("中文 GPU 测试","中文GPU，测试。","中文 GPU， 测试。");
    restore("明天开会，今天准备",s->s+"。","明天开会，今天准备。");
    restore("你好！我说“明天见”",s->s+"。","你好！我说“明天见”");
    restore("网址https://example.com不要改",s->{throw new AssertionError();},"网址https://example.com不要改");
    restore("邮箱a@example.com",s->{throw new AssertionError();},"邮箱a@example.com");
    restore("明天15:30开会",s->{throw new AssertionError();},"明天15:30开会");
    restore("B27",s->{throw new AssertionError();},"B27");
    restore("  今天开会  ",s->s+"。","  今天开会。  ");
    System.out.println("VoicePunctuation: "+count+" checks passed");
  }
}
