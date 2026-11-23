package arc.util;

import arc.util.reflect.ClassReflection;

import java.io.File;

public class AdvanceSharedLibraryLoader extends SharedLibraryLoader {
    private final Class calledClass = ClassReflection.getClassCaller();;
    private final Object[] input = {};

    public AdvanceSharedLibraryLoader() {
    }

    public AdvanceSharedLibraryLoader(String nativesJar) {
        super(nativesJar);
    }

    public Class calledClass() {
        return calledClass;
    }

    public void load(String libraryName) {
        if (!OS.isIos) {
            synchronized(AdvanceSharedLibraryLoader.class) {
                if (!isLoaded(libraryName)) {
                    String platformName = this.mapLibraryName(libraryName);

                    try {
                        if (OS.isAndroid) {
                            // System.loadLibrary(platformName)
                            input[0] = calledClass; input[1] = platformName;
                            Reflect.invoke(Runtime.getRuntime(), "loadLibrary0", input, Class.class, String.class);
                        } else {
                            this.loadFile(platformName);
                        }

                        setLoaded(libraryName);
                    } catch (Throwable ex) {
                        throw new ArcRuntimeException("Couldn't load shared library '" + platformName + "' for target: " + OS.osName + (OS.is64Bit ? ", 64-bit" : ", 32-bit"), ex);
                    }

                }
            }
        }
    }

    protected void loadFile(String sourcePath) {
        String sourceCrc = this.crc(this.readFile(sourcePath));
        String fileName = (new File(sourcePath)).getName();
        File file = new File(System.getProperty("java.io.tmpdir") + "/arc/" + sourceCrc, fileName);
        Throwable result;
        if ((result = this.loadFile(sourcePath, sourceCrc, file)) != null) {
            try {
                file = File.createTempFile(sourceCrc, null);
                if (file.delete() && this.loadFile(sourcePath, sourceCrc, file) == null) {
                    return;
                }
            } catch (Throwable ignored) {}

            file = new File(OS.userHome + "/.arc/" + sourceCrc, fileName);
            if (this.loadFile(sourcePath, sourceCrc, file) != null) {
                file = new File(".temp/" + sourceCrc, fileName);
                if (this.loadFile(sourcePath, sourceCrc, file) != null) {
                    file = new File(fileName);
                    if (this.loadFile(sourcePath, sourceCrc, file) != null) {
                        file = new File(System.getProperty("java.library.path"), sourcePath);
                        if (file.exists()) {
                            // System.load(file.getAbsolutePath())
                            input[0] = calledClass; input[1] = file.getAbsolutePath();
                            Reflect.invoke(Runtime.getRuntime(), "load0", input, Class.class, String.class);
                        } else {
                            throw new ArcRuntimeException(result);
                        }
                    }
                }
            }
        }
    }
    @Override
    protected Throwable loadFile(String sourcePath, String sourceCrc, File extractedFile) {
        try {
            // System.load(this.extractFile(sourcePath, sourceCrc, extractedFile).getAbsolutePath());
            input[0] = calledClass; input[1] = this.extractFile(sourcePath, sourceCrc, extractedFile).getAbsolutePath();
            Reflect.invoke(Runtime.getRuntime(), "load0", input, Class.class, String.class);
            return null;
        } catch (Throwable ex) {
            return ex;
        }
    }
}
