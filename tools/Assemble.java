import java.io.File;
import java.nio.file.*;
import brut.androlib.src.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
public class Assemble {
  public static void main(String[] args) throws Exception {
    File dir=new File(args[0]);
    DexBuilder dex=new DexBuilder(new Opcodes(28,-1));
    SmaliBuilder builder=new SmaliBuilder(dir,28);
    try(var paths=Files.walk(dir.toPath())) {
      paths.filter(p->p.toString().endsWith(".smali")).forEach(p->builder.buildFile(dir.toPath().relativize(p).toString(),dex));
    }
    dex.writeTo(new FileDataStore(new File(args[1])));
    System.out.println("ASSEMBLED "+args[1]);
  }
}
