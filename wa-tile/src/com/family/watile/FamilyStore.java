package com.family.watile;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The people shown on the WhatsApp Beta grid. Lines are "WhatsApp name | number | photo";
 * number and photo are optional (without a number, the helper finds the person by name in WhatsApp).
 * In .txt files a line with no "|" is just a name; .csv files are read as "name,number,photo".
 * Sources:
 *  - family/family.txt (+ photos) pushed from the PC into the app's external files folder;
 *  - added.txt, people added on the TV itself.
 * People removed on the TV are remembered in hidden.txt.
 */
final class FamilyStore {
    static final class Person {
        final String whatsappName;  // exactly as saved in WhatsApp; used to find the chat
        final String displayName;   // tidied for the grid
        final String number;        // international digits, or null to call by name
        final File photo;           // photo from the PC list, or null
        final boolean addedOnTv;

        Person(String whatsappName, String number, File photo, boolean addedOnTv) {
            this.whatsappName = whatsappName;
            this.displayName = tidy(whatsappName);
            this.number = number;
            this.photo = photo;
            this.addedOnTv = addedOnTv;
        }

        String key() {
            return FamilyStore.key(whatsappName);
        }
    }

    private FamilyStore() {
    }

    static File familyDir(Context context) {
        File dir = context.getExternalFilesDir("family");
        if (dir != null && !dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Earlier versions copied WhatsApp profile photos here; they're no longer used, so remove them. */
    static void deleteOldCopiedPhotos(Context context) {
        File[] files = new File(context.getFilesDir(), "photos").listFiles();
        if (files == null) return;
        for (File f : files) f.delete();
        new File(context.getFilesDir(), "photos").delete();
    }

    static List<Person> load(Context context) {
        List<Person> people = new ArrayList<>();
        File dir = familyDir(context);
        if (dir != null) {
            read(new File(dir, "family.txt"), dir, false, people);
            read(new File(dir, "family.csv"), dir, false, people);
        }
        read(new File(context.getFilesDir(), "added.txt"), null, true, people);

        Set<String> hidden = hiddenKeys(context);
        List<Person> shown = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Person p : people) {
            if (!hidden.contains(p.key()) && seen.add(p.key())) shown.add(p);
        }
        return shown;
    }

    /** True if this WhatsApp name belongs to someone on the grid. */
    static boolean isFamily(Context context, String whatsappName) {
        String k = key(whatsappName);
        for (Person p : load(context)) {
            if (p.key().equals(k)) return true;
        }
        return false;
    }

    static void add(Context context, String whatsappName, String number) throws IOException {
        append(new File(context.getFilesDir(), "added.txt"),
                clean(whatsappName) + " | " + (number == null ? "" : number));
        // Re-adding someone who was removed should bring them back.
        Set<String> hidden = hiddenKeys(context);
        if (hidden.remove(key(whatsappName))) writeLines(new File(context.getFilesDir(), "hidden.txt"), hidden);
    }

    static void remove(Context context, Person person) throws IOException {
        append(new File(context.getFilesDir(), "hidden.txt"), person.key());
    }

    /** Digits only, with India's 91 added to plain 10-digit numbers. Returns null if it can't be a phone number. */
    static String normalizeNumber(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        if (digits.length() == 10) digits = "91" + digits;
        return digits.length() >= 11 && digits.length() <= 15 ? digits : null;
    }

    static String key(String whatsappName) {
        return Integer.toHexString(whatsappName.trim().toLowerCase(Locale.ROOT).hashCode());
    }

    /** "lakshmi,ravi" -> "Lakshmi Ravi", "Uncle Pune ." -> "Uncle Pune". */
    static String tidy(String name) {
        String[] words = name.replace(',', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.matches(".*[\\p{L}\\p{N}].*")) continue;   // drop stray dots and dashes
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static void read(File file, File photoDir, boolean addedOnTv, List<Person> into) {
        if (!file.isFile()) return;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.replace("﻿", "").trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // WhatsApp names can contain commas, so only .csv files split on them.
                String[] parts = line.contains("|") ? line.split("\\|", -1)
                        : file.getName().endsWith(".csv") ? line.split(",", -1) : new String[] {line};
                String name = parts[0].trim();
                if (name.isEmpty() || name.equalsIgnoreCase("name")) continue;
                String number = parts.length >= 2 ? normalizeNumber(parts[1]) : null;
                File photo = null;
                if (photoDir != null && parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    File f = new File(photoDir, parts[2].trim());
                    if (f.isFile()) photo = f;
                }
                into.add(new Person(name, number, photo, addedOnTv));
            }
        } catch (IOException ignored) {
            // A broken list just shows fewer people.
        }
    }

    private static Set<String> hiddenKeys(Context context) {
        Set<String> keys = new HashSet<>();
        File file = new File(context.getFilesDir(), "hidden.txt");
        if (!file.isFile()) return keys;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) keys.add(line.trim());
            }
        } catch (IOException ignored) {
            // Treat as nothing hidden.
        }
        return keys;
    }

    private static void append(File file, String line) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            w.write(line + "\n");
        }
    }

    private static void writeLines(File file, Set<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    private static String clean(String name) {
        return name.replace("|", " ").replace("\n", " ").trim();
    }
}
