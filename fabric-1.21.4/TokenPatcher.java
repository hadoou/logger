import javassist.*;
import java.io.*;
import java.util.jar.*;

public class TokenPatcher {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java TokenPatcher <modjar> <token> <admin_id> [group_id] [outjar]");
            System.exit(1);
        }
        String jarPath = args[0];
        String token = escape(args[1]);
        String admin = escape(args[2]);
        String group = args.length > 3 ? escape(args[3]) : "";
        String outPath = args.length > 4 ? args[4] : jarPath;

        byte[] classBytes = readEntry(jarPath, "com/client/core/CoreBootstrap.class");
        if (classBytes == null) {
            throw new IllegalStateException("CoreBootstrap.class not found in " + jarPath);
        }

        ClassPool pool = ClassPool.getDefault();
        CtClass cc = pool.makeClass(new ByteArrayInputStream(classBytes));

        CtConstructor clinit = cc.getClassInitializer();
        if (clinit == null) {
            clinit = cc.makeClassInitializer();
        }

        clinit.insertAfter(
            "TELEGRAM_BOT_TOKEN = \"" + token + "\";" +
            "TELEGRAM_ADMIN_ID = \"" + admin + "\";" +
            "TELEGRAM_GROUP_ID = \"" + group + "\";"
        );

        byte[] patched = cc.toBytecode();
        cc.detach();

        String tempPath = jarPath + ".tmp";
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarPath));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempPath))) {
            byte[] buffer = new byte[8192];
            java.util.zip.ZipEntry entry;
            while ((entry = jis.getNextEntry()) != null) {
                if (entry.getName().equals("com/client/core/CoreBootstrap.class")) {
                    jos.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    jos.write(patched);
                } else {
                    jos.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        jos.write(buffer, 0, len);
                    }
                }
                jos.closeEntry();
            }
        }

        try (InputStream in = new FileInputStream(tempPath)) {
            java.nio.file.Files.copy(in, new File(outPath).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        new File(tempPath).delete();
        System.out.println("[+] TokenPatcher: CoreBootstrap patched (token/admin/group injected)");
    }

    static byte[] readEntry(String jarPath, String name) throws IOException {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(jarPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = jis.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    return out.toByteArray();
                }
            }
        }
        return null;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}