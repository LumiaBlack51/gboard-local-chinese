import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.writer.pool.DexPool;

/** Rebuild all reference tables after editing package string constants. */
public class DexTool {
    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        File target = new File(args[1]);
        target.mkdirs();
        Map<String,ClassDef> replacements = new HashMap<>();
        if (args.length > 2) {
            for (ClassDef c : DexFileFactory.loadDexFile(args[2], null).getClasses())
                replacements.put(c.getType(), c);
        }
        for (File input : source.listFiles((dir,name)->name.endsWith(".dex"))) {
            DexFile dex = DexFileFactory.loadDexFile(input, null);
            DexPool pool = new DexPool(dex.getOpcodes());
            int count = 0;
            for (ClassDef c : dex.getClasses()) {
                // Changed classes are placed in a separate dex with the adapter;
                // the upstream dex is already at the 65,536 method-reference limit.
                if (replacements.containsKey(c.getType())) continue;
                pool.internClass(c);
                count++;
            }
            pool.writeTo(new org.jf.dexlib2.writer.io.FileDataStore(new File(target,input.getName())));
            System.out.println(input.getName()+" classes="+count);
        }
    }
}
