package com.tyron.compiler.incremental;

import com.apk.builder.ApplicationLoader;
import com.tyron.compiler.Compiler;
import com.tyron.compiler.exception.CompilerException;
import com.tyron.compiler.incremental.file.JavaFile;

import com.apk.builder.FileUtil;
import com.apk.builder.model.Project;
import com.apk.builder.model.Library;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class IncrementalJavacCompiler extends Compiler {

    private static final String TAG = "Incremental Javac";

    private Project mProject;
    private List<File> filesToCompile;

    public IncrementalJavacCompiler(Project project) {
        mProject = project;
    }

    @Override
    public void prepare() {
        List<JavaFile> oldFiles = findJavaFiles(new File(mProject.getOutputFile() + "/intermediate/java"));
        List<JavaFile> newFiles = new ArrayList<>();
        newFiles.addAll(findJavaFiles(mProject.getJavaFile()));
        newFiles.addAll(findJavaFiles(new File(mProject.getOutputFile() + "/gen")));

        filesToCompile = getModifiedFiles(oldFiles, newFiles);
    }

    @Override
    public void run() throws CompilerException, IOException {
        if (filesToCompile.isEmpty()) {
            mProject.getLogger().d(TAG, "Files are up to date, skipping compilation.");
            return;
        }

        mProject.getLogger().d(TAG, "Found " + filesToCompile.size() + " file(s) that are modified.");

        StringWriter errorWriter = new StringWriter();
        PrintWriter errPrintWriter = new PrintWriter(errorWriter);

        StringWriter outputWriter = new StringWriter();
        PrintWriter outPrintWriter = new PrintWriter(outputWriter);

        List<String> args = new ArrayList<>();
        args.add("-source");
        args.add("17"); // Set source version
        args.add("-target");
        args.add("17"); // Set target version
        args.add("-d");
        args.add(mProject.getOutputFile() + "/intermediate/classes");
        args.add("-classpath");

        StringBuilder classpath = new StringBuilder();
        classpath.append(getAndroidJarFile().getAbsolutePath()).append(File.pathSeparator);
        for (Library library : mProject.getLibraries()) {
            File classFile = library.getClassJarFile();
            if (classFile.exists()) {
                classpath.append(classFile.getAbsolutePath()).append(File.pathSeparator);
            }
        }
        classpath.append(getLambdaFactoryFile().getAbsolutePath());
        args.add(classpath.toString());

        args.add("-sourcepath");
        args.add(""); // Empty sourcepath argument to compile all sources
        for (File file : filesToCompile) {
            args.add(file.getAbsolutePath());
        }

        ProcessBuilder processBuilder = new ProcessBuilder(new File(ApplicationLoader.applicationContext.getFilesDir(), "jdk/bin/javac").getAbsolutePath());
        processBuilder.command().addAll(args);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outPrintWriter.println(line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new CompilerException(errorWriter.toString());
            }
        } catch (InterruptedException e) {
            throw new CompilerException("Compilation interrupted");
        }

        mProject.getLogger().d(TAG, "Merging modified java files");
        mergeClasses(filesToCompile);
    }

    private List<JavaFile> findJavaFiles(File input) {
        List<JavaFile> foundFiles = new ArrayList<>();

        if (input.isDirectory()) {
            File[] contents = input.listFiles();
            if (contents != null) {
                for (File child : contents) {
                    foundFiles.addAll(findJavaFiles(child));
                }
            }
        } else {
            if (input.getName().endsWith(".java")) {
                foundFiles.add(new JavaFile(input.getPath()));
            }
        }
        return foundFiles;
    }

    private List<File> getModifiedFiles(List<JavaFile> oldFiles, List<JavaFile> newFiles) {
        List<File> modifiedFiles = new ArrayList<>();

        for (JavaFile newFile : newFiles) {
            if (!oldFiles.contains(newFile)) {
                modifiedFiles.add(newFile);
            } else {
                File oldFile = oldFiles.get(oldFiles.indexOf(newFile));
                if (contentModified(oldFile, newFile)) {
                    modifiedFiles.add(newFile);
                    if (oldFile.delete()) {
                        mProject.getLogger().d(TAG, oldFile.getName() + ": Removed old class file that has been modified");
                    }
                }
                oldFiles.remove(oldFile);
            }
        }
        for (JavaFile removedFile : oldFiles) {
            mProject.getLogger().d(TAG, "Class no longer exists, deleting file: " + removedFile.getName());
            if (!removedFile.delete()) {
                mProject.getLogger().w(TAG, "Failed to delete file " + removedFile.getAbsolutePath());
            } else {
                String name = removedFile.getName().substring(0, removedFile.getName().indexOf("."));
                deleteClassInDir(name, new File(mProject.getOutputFile() + "/intermediate/classes"));
            }
        }
        return modifiedFiles;
    }

    public void mergeClasses(List<File> files) {
        for (File file : files) {
            String pkg = getPackageName(file);
            if (pkg == null) {
                continue;
            }
            String packagePath = mProject.getOutputFile() + "/intermediate/java/" + pkg;
            FileUtil.copyFile(file.getAbsolutePath(), packagePath);
        }
    }

    private boolean deleteClassInDir(String name, File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteClassInDir(name, child);
                }
            }
        } else {
            String dirName = dir.getName().substring(0, dir.getName().indexOf("."));
            if (dirName.contains("$")) {
                dirName = dirName.substring(0, dirName.indexOf("$"));
            }
            if (dirName.equals(name)) {
                return dir.delete();
            }
        }

        return false;
    }

    private boolean contentModified(File old, File newFile) {
        if (old.isDirectory() || newFile.isDirectory()) {
            throw new IllegalArgumentException("Given file must be a Java file");
        }

        if (!old.exists() || !newFile.exists()) {
            return true;
        }

        if (newFile.length() != old.length()) {
            return true;
        }

        return newFile.lastModified() > old.lastModified();
    }

    private String getPackageName(File file) {
        String packageName = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {

            while (!packageName.contains("package")) {
                packageName = reader.readLine();

                if (packageName == null) {
                    packageName = "";
                }
            }

        } catch (IOException e) {
            mProject.getLogger().e(TAG, e.getMessage());
            return null;
        }

        if (packageName == null) {
            return null;
        }

        if (packageName.contains("package")) {

            packageName = packageName.replace("package ", "")
                    .replace(";", ".")
                    .replace(".", "/");

            if (!packageName.endsWith("/")) {
                packageName = packageName.concat("/");
            }

            return packageName + file.getName();
        }

        return null;
    }

    private class CompilerOutputStream extends OutputStream {

        private final StringBuffer buffer;

        public CompilerOutputStream(StringBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                mProject.getLogger().d(TAG, buffer.toString());
                buffer.setLength(0);
                return;
            }
            buffer.append((char) b);
        }
    }
}