import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class MixinFixer {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java MixinFixer <jar>");
            System.exit(1);
        }
        String jarPath = args[0];
        String tempPath = jarPath + ".tmp";

        Set<String> mixinClasses = new HashSet<>();
        try (JarFile jar = new JarFile(jarPath)) {
            jar.stream().forEach(e -> {
                if (e.getName().equals("clientcore.mixins.json")) return;
                if (e.getName().endsWith(".class") && e.getName().contains("mixin/")) {
                    mixinClasses.add(e.getName());
                }
            });
        }

        if (mixinClasses.isEmpty()) {
            System.out.println("No mixin classes found");
            return;
        }

        System.out.println("Found " + mixinClasses.size() + " mixin classes to fix");

        Map<String, byte[]> fixed = new LinkedHashMap<>();
        for (String classPath : mixinClasses) {
            try (JarFile jar = new JarFile(jarPath)) {
                ZipEntry entry = jar.getEntry(classPath);
                if (entry == null) continue;
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] classBytes = readAllBytes(is);
                    int patchedCount = patchStaticPublicMethods(classBytes);
                    if (patchedCount > 0) {
                        fixed.put(classPath, classBytes);
                        System.out.println("  Patched " + patchedCount + " methods in " + classPath);
                    }
                }
            }
        }

        if (fixed.isEmpty()) {
            System.out.println("Nothing to fix");
            return;
        }

        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarPath));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempPath))) {
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while ((entry = jis.getNextEntry()) != null) {
                if (fixed.containsKey(entry.getName())) {
                    jos.putNextEntry(new ZipEntry(entry.getName()));
                    jos.write(fixed.get(entry.getName()));
                } else {
                    jos.putNextEntry(new ZipEntry(entry.getName()));
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        jos.write(buffer, 0, len);
                    }
                }
                jos.closeEntry();
            }
        }

        new File(jarPath).delete();
        new File(tempPath).renameTo(new File(jarPath));
        System.out.println("Done! Fixed " + fixed.size() + " mixin classes");
    }

    static int patchStaticPublicMethods(byte[] c) {
        int pos = 8;
        int cpCount = u2(c, pos);
        pos += 2;

        for (int i = 1; i < cpCount; i++) {
            int tag = c[pos] & 0xFF;
            pos++;
            switch (tag) {
                case 1: { int len = u2(c, pos); pos += 2 + len; break; }
                case 3: case 4: pos += 4; break;
                case 5: case 6: pos += 8; i++; break;
                case 7: case 8: case 16: case 19: case 20: pos += 2; break;
                case 9: case 10: case 11: case 12: case 17: case 18: pos += 4; break;
                case 15: pos += 3; break;
                default: pos += 2; break;
            }
        }

        pos += 2 + 2 + 2;
        int ifaces = u2(c, pos);
        pos += 2 + ifaces * 2;

        int fieldsCount = u2(c, pos);
        pos += 2;
        for (int f = 0; f < fieldsCount; f++) {
            pos += 2 + 2 + 2;
            int fac = u2(c, pos);
            pos += 2;
            for (int a = 0; a < fac; a++) {
                pos += 2;
                int alen = u4(c, pos);
                pos += 4 + alen;
            }
        }

        int methodsCount = u2(c, pos);
        pos += 2;

        int patched = 0;
        for (int m = 0; m < methodsCount; m++) {
            int flagsOffset = pos;
            int flags = u2(c, pos);
            pos += 2;
            pos += 2 + 2;
            int acount = u2(c, pos);
            pos += 2;
            for (int a = 0; a < acount; a++) {
                pos += 2;
                int alen = u4(c, pos);
                pos += 4 + alen;
            }

            boolean isPublic = (flags & 0x0001) != 0;
            boolean isStatic = (flags & 0x0008) != 0;
            if (isPublic && isStatic) {
                int newFlags = (flags & ~0x0001) | 0x0002;
                c[flagsOffset] = (byte)((newFlags >> 8) & 0xFF);
                c[flagsOffset + 1] = (byte)(newFlags & 0xFF);
                patched++;
            }
        }
        return patched;
    }

    static int u2(byte[] d, int p) { return ((d[p] & 0xFF) << 8) | (d[p+1] & 0xFF); }
    static int u4(byte[] d, int p) { return ((d[p]&0xFF)<<24)|((d[p+1]&0xFF)<<16)|((d[p+2]&0xFF)<<8)|(d[p+3]&0xFF); }
    static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) b.write(buf, 0, n);
        return b.toByteArray();
    }
}
