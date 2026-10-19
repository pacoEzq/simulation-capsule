// manifest_writer.java
// xtr chain (Capsule Extractor v0, STAR-CCM+ adapter) -- last of three. Simcenter STAR-CCM+ 2606.
// Order of execution on an already solved .sim: output_exporter, object_audit (mode=run),
// manifest_writer. It writes adapter.json, and nothing else, into work/<card.body.alias>/: the solver
// session read from setup.txt, the .sim and its sha256, the sha256 of the three xtr macros, the mesh
// block (cells only, read from the report that card.references[key=cells].from names; no such
// reference, no block) and result and summary copied from object_audit.json. render.json is read only as proof that output_exporter ran on this point. It
// creates, renames and deletes no simulation object and does not save the simulation; every object it
// reads is resolved by name and a missing one throws with its name.
//
// Signatures come from run_macro.java and output_exporter.java (same repository, compiled against
// 2606 and used in real runs). Every call left here was already in the T7 manifest_writer.java.

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import star.base.report.Report;
import star.common.Simulation;
import star.common.StarMacro;

public class manifest_writer extends StarMacro {

    // ---------------------------------------------------------------- constants
    // card.json is looked up as output_exporter looks it up: session directory first, then the
    // directory of the .sim. The point directory is work/<card.body.alias>/, the exporter's.
    private static final String CARD_NAME = "card.json";
    private static final String WORK_DIR = "work";
    private static final String LOG_PREFIX = "manifest: ";
    private static final String OUT_NAME = "adapter.json";
    private static final String RENDER_JSON = "render.json";
    private static final String AUDIT_JSON = "object_audit.json";

    // Chain files, in order of execution, hashed from the session directory; path is where each one
    // lives in the simulation-capsule repository.
    private static final String[] CHAIN_KEYS = { "output_exporter", "object_audit", "manifest_writer" };
    private static final String CHAIN_PATH_PREFIX = "macros/extractor/";

    // Sentinel for a JSON null read from disk (a Map can hold null, but containsKey is clumsier).
    private static final Object JSON_NULL = new Object();

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private File sessionBase;
    private File pointDir;
    private File runLog;
    private String alias;
    private long groupValue;
    private String cellsReport; // card.references[key=cells].from = "report <name>"; null: no mesh block

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();
        try {
            // ---- 1. card.json and point directory
            readCard(loadCard());
            resolvePointDir();
            log("start card=" + CARD_NAME + " alias=" + alias + " group.value=" + groupValue
                + " point=" + WORK_DIR + "/" + alias);
            if (new File(pointDir, OUT_NAME).isFile()) {
                log(OUT_NAME + " already present, it will be replaced");
            }

            // ---- 2. what output_exporter and object_audit left on disk
            File simFile = simFile();
            readSummary();
            SetupFields setup = readSetup();
            readRender();
            Map<String, Object> audit = readAudit(simFile.getName());

            // ---- 3. what only the session knows
            long cells = cellsReport == null ? 0L : cellCount(cellsReport);

            // ---- 4. the .sim and the chain files
            String simSha = sha256OrNull(simFile, simFile.getName());
            String[] chainSha = new String[CHAIN_KEYS.length];
            for (int i = 0; i < CHAIN_KEYS.length; i++) {
                String f = CHAIN_KEYS[i] + ".java";
                chainSha[i] = sha256OrNull(new File(sessionBase, f), f);
            }

            // ---- 5. build the whole text, then write it once
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"adapter\": \"starccm\",\n");
            appendSession(sb, setup);
            sb.append("  \"sim\": {\"file\": ").append(str(simFile.getName()))
              .append(", \"sha256\": ").append(str(simSha)).append("},\n");
            appendChain(sb, chainSha);
            if (cellsReport != null) {
                sb.append("  \"mesh\": {\"cells\": ").append(cells).append("},\n");
            }
            sb.append("  \"audit\": {\"result\": ").append(toJson(audit.get("result")))
              .append(", \"summary\": ").append(toJson(audit.get("summary"))).append("}\n");
            sb.append("}\n");

            String text = sb.toString();
            parseJson(text, OUT_NAME + " (in memory)");
            File out = new File(pointDir, OUT_NAME);
            writeOnce(out, text);
            log(rel(out) + " bytes=" + text.getBytes(StandardCharsets.UTF_8).length);
            log("done");
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= card.json / point directory
    // Same lookup order as output_exporter.loadCard: session directory first, then the directory
    // returned by Simulation.getSessionDirFile(). Returns the parsed root object.
    private Map<String, Object> loadCard() {
        String sessionDir = null;
        try {
            sessionDir = sim.getSessionDir();
        } catch (Exception e) {
            sessionDir = null;
        }
        File candidate = null;
        if (sessionDir != null && sessionDir.length() > 0) {
            File f = new File(sessionDir, CARD_NAME);
            if (f.isFile()) {
                candidate = f;
            }
        }
        if (candidate == null) {
            File simDir = null;
            try {
                simDir = sim.getSessionDirFile();
            } catch (Exception e) {
                simDir = null;
            }
            if (simDir != null) {
                File f = new File(simDir, CARD_NAME);
                if (f.isFile()) {
                    candidate = f;
                }
            }
        }
        if (candidate == null) {
            throw new RuntimeException("manifest_writer: could not locate '" + CARD_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }
        String text = readText(candidate);
        if (text.length() > 0 && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        return asObject(parseJson(text, CARD_NAME), CARD_NAME + " root");
    }

    // Only card_version, body.alias, body.group.value and the references entry with key "cells" are
    // taken, with the checks of output_exporter.
    private void readCard(Map<String, Object> card) {
        long version = requireLong(card, CARD_NAME, "card_version");
        if (version != 0L) {
            throw new RuntimeException("manifest_writer: " + CARD_NAME + " card_version is " + version + ", expected 0.");
        }
        Object a = member(card, CARD_NAME, "body", "alias");
        if (!(a instanceof String)) {
            throw new RuntimeException("manifest_writer: " + CARD_NAME + " body.alias is not a string.");
        }
        alias = (String) a;
        if (alias.length() == 0 || alias.indexOf('/') >= 0 || alias.indexOf('\\') >= 0 || alias.startsWith(".")) {
            throw new RuntimeException("manifest_writer: " + CARD_NAME + " body.alias '" + alias + "' is not a directory name.");
        }
        groupValue = requireLong(card, CARD_NAME, "body", "group", "value");
        Object refs = card.get("references");
        if (refs instanceof List) {
            for (Object r : (List<?>) refs) {
                if (r instanceof Map && "cells".equals(((Map<?, ?>) r).get("key"))) {
                    Object from = ((Map<?, ?>) r).get("from");
                    if (!(from instanceof String) || !((String) from).startsWith("report ")
                        || ((String) from).substring("report ".length()).trim().length() == 0) {
                        throw new RuntimeException("manifest_writer: " + CARD_NAME + " references[key=cells].from is not "
                            + "\"report <name>\".");
                    }
                    cellsReport = ((String) from).substring("report ".length()).trim();
                    break;
                }
            }
        }
    }

    // Same base as output_exporter.resolvePointDir; nothing is created.
    private void resolvePointDir() {
        String sessionDir = sim.getSessionDir();
        if (sessionDir != null && sessionDir.length() > 0) {
            sessionBase = new File(sessionDir);
        } else {
            sessionBase = sim.getSessionDirFile();
        }
        String relative = WORK_DIR + File.separator + alias;
        File dir = new File(sessionBase, relative);
        if (!dir.isDirectory()) {
            throw new RuntimeException("manifest_writer: missing point directory '" + relative + "'.");
        }
        File log = new File(dir, "run_log.txt");
        if (!log.isFile()) {
            throw new RuntimeException("manifest_writer: missing '" + relative + File.separator + "run_log.txt'.");
        }
        pointDir = dir;
        runLog = log;
    }

    // The open .sim, from Simulation.getSessionPath() as in run_macro.java and object_audit.java.
    private File simFile() {
        String path = sim.getSessionPath();
        if (path == null || path.length() == 0) {
            throw new RuntimeException("manifest_writer: the simulation has no session path (never saved).");
        }
        return new File(path);
    }

    // ================================================================= lookups (fail loudly)
    // Same calls as run_macro.java / output_exporter.java.
    private Report report(String name) {
        if (sim.getReportManager().hasObject(name) == null) {
            throw new RuntimeException("manifest_writer: missing report '" + name + "'.");
        }
        Report r = sim.getReportManager().getReport(name);
        if (r == null) {
            throw new RuntimeException("manifest_writer: missing report '" + name + "'.");
        }
        return r;
    }

    // ================================================================= step 2: disk
    // summary.json, generic read; no key is required. If it holds sweep.value it must equal
    // card.body.group.value.
    private void readSummary() {
        File f = new File(pointDir, "summary.json");
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing 'summary.json'.");
        }
        Map<String, Object> root = asObject(parseJson(readText(f), "summary.json"), "summary.json");
        Object sweep = root.get("sweep");
        if (sweep instanceof Map && ((Map<?, ?>) sweep).containsKey("value")) {
            long value = requireLong(root, "summary.json", "sweep", "value");
            if (value != groupValue) {
                throw new RuntimeException("manifest_writer: summary.json sweep.value is " + value
                    + " but card.body.group.value is " + groupValue + ".");
            }
            log("summary.json sweep.value=" + value);
        } else {
            log("summary.json read, no sweep.value");
        }
    }

    // render.json as written by output_exporter; nothing of it goes into adapter.json, it is read as
    // proof that the exporter ran on this point. Missing means the exporter did not run.
    private void readRender() {
        File f = new File(pointDir, RENDER_JSON);
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing '" + RENDER_JSON + "' (output_exporter did not run).");
        }
        Map<String, Object> root = asObject(parseJson(readText(f), RENDER_JSON), RENDER_JSON);
        Object by = member(root, RENDER_JSON, "written_by");
        if (!"output_exporter".equals(by)) {
            throw new RuntimeException("manifest_writer: " + RENDER_JSON + " written_by is '" + jsonScalarText(by)
                + "', expected 'output_exporter'.");
        }
        log(RENDER_JSON + " read bytes=" + f.length());
    }

    // object_audit.json of mode=run, in the same point directory; it must be the audit of this .sim.
    // result (PASS or FAIL) and summary are copied as written; a FAIL is logged, not refused here.
    private Map<String, Object> readAudit(String simName) {
        File f = new File(pointDir, AUDIT_JSON);
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing '" + AUDIT_JSON + "' (object_audit mode=run did not run).");
        }
        Map<String, Object> root = asObject(parseJson(readText(f), AUDIT_JSON), AUDIT_JSON);
        Object audited = member(root, AUDIT_JSON, "audited");
        if (!simName.equals(audited)) {
            throw new RuntimeException("manifest_writer: " + AUDIT_JSON + " audited is '" + jsonScalarText(audited)
                + "', expected '" + simName + "'.");
        }
        Object result = member(root, AUDIT_JSON, "result");
        if (!"PASS".equals(result) && !"FAIL".equals(result)) {
            throw new RuntimeException("manifest_writer: " + AUDIT_JSON + " result is '" + jsonScalarText(result)
                + "', expected PASS or FAIL.");
        }
        asObject(member(root, AUDIT_JSON, "summary"), AUDIT_JSON + " summary");
        log(AUDIT_JSON + " result=" + result + " bytes=" + f.length());
        return root;
    }

    private static final class SetupFields {
        String version;
        String build;
        String platform;
        String precision;
    }

    // The trimmed report header carries the build fields as "Key: value" segments joined by " | ", in
    // the published 2606 trims on one line under [Software Summary]:
    //   Version: BuildArch: <arch> | BuildEnv: <env> | PresentationVersion: <v> | ReleaseDate: <d> | ReleaseNumber: <n>
    // Every header line (before the SIMULATION PROPERTIES banner) is split on the literal " | " and
    // ": ", so the fields are found wherever the header puts them. version = PresentationVersion,
    // build = ReleaseNumber, platform = BuildArch, precision from BuildEnv (ending in -r8 is double,
    // otherwise mixed, as SPEC 4.2.1 and the environment block of output_exporter). A field not found
    // is null and logged; that is not a failure.
    private SetupFields readSetup() {
        File f = new File(pointDir, "setup.txt");
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing 'setup.txt' (output_exporter did not run).");
        }
        List<String> lines = readLines(f);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        String buildEnvLine = null;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals("SIMULATION PROPERTIES")) {
                break;
            }
            if (t.indexOf("BuildEnv: ") < 0 && t.indexOf("PresentationVersion: ") < 0
                && t.indexOf("ReleaseNumber: ") < 0 && t.indexOf("BuildArch: ") < 0) {
                continue;
            }
            if (buildEnvLine == null && t.indexOf("BuildEnv: ") >= 0) {
                buildEnvLine = t;
            }
            String[] segments = t.split(" \\| ", -1);
            for (int s = 0; s < segments.length; s++) {
                String[] kv = segments[s].split(": ", -1);
                if (kv.length >= 2) {
                    String key = kv[kv.length - 2].trim();
                    String val = kv[kv.length - 1].trim();
                    if (!fields.containsKey(key) && val.length() > 0) {
                        fields.put(key, val);
                    }
                }
            }
        }
        SetupFields sf = new SetupFields();
        sf.version = fields.get("PresentationVersion");
        sf.build = fields.get("ReleaseNumber");
        sf.platform = fields.get("BuildArch");
        String env = fields.get("BuildEnv");
        sf.precision = env == null ? null : (env.endsWith("-r8") ? "double" : "mixed");
        if (sf.version == null || sf.build == null || sf.platform == null || env == null) {
            log("setup.txt build fields incomplete: PresentationVersion=" + sf.version + " ReleaseNumber=" + sf.build
                + " BuildArch=" + sf.platform + " BuildEnv=" + env + " line=" + (buildEnvLine == null ? "(none)" : buildEnvLine));
        } else {
            log("setup.txt version=" + sf.version + " build=" + sf.build + " platform=" + sf.platform
                + " precision=" + sf.precision);
        }
        return sf;
    }
    // ================================================================= step 3: session
    // run_macro.cellCount: Report.monitoredValue() cast to a long, whole and positive or it throws.
    // The report is the one card.references[key=cells].from names.
    private long cellCount(String name) {
        double c = report(name).monitoredValue();
        long n = (long) c;
        if (Double.isNaN(c) || Double.isInfinite(c) || n <= 0 || (double) n != c) {
            throw new RuntimeException("manifest_writer: report '" + name + "' returned " + c
                + ", not a positive whole cell count.");
        }
        log(name + "=" + n);
        return n;
    }

    private String sha256OrNull(File f, String name) {
        if (!f.isFile()) {
            log(name + " absent from the session directory, sha256 null");
            return null;
        }
        String h = sha256(f);
        log(name + " sha256 " + h);
        return h;
    }

    // ================================================================= step 4: chain and session
    // The three xtr macros as {file, path, sha256}, in order of execution; sha256 is null when the file
    // is absent from the session directory.
    private void appendChain(StringBuilder sb, String[] chainSha) {
        sb.append("  \"chain\": [\n");
        for (int i = 0; i < CHAIN_KEYS.length; i++) {
            sb.append("    {\"file\": ").append(str(CHAIN_KEYS[i]))
              .append(", \"path\": ").append(str(CHAIN_PATH_PREFIX + CHAIN_KEYS[i] + ".java"))
              .append(", \"sha256\": ").append(str(chainSha[i])).append('}')
              .append(i < CHAIN_KEYS.length - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");
    }

    // Top-level keys of adapter.json; version and build come from setup.txt, never from the API.
    private void appendSession(StringBuilder sb, SetupFields s) {
        sb.append("  \"solver\": \"Simcenter STAR-CCM+\",\n");
        sb.append("  \"version\": ").append(str(s.version)).append(",\n");
        sb.append("  \"build\": ").append(str(s.build)).append(",\n");
        sb.append("  \"platform\": ").append(str(s.platform)).append(",\n");
        sb.append("  \"precision\": ").append(str(s.precision)).append(",\n");
    }

    private static String className(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }

    // ================================================================= hashing
    private String sha256(File f) {
        InputStream in = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            in = new FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(d[i] & 0xff)));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("manifest_writer: could not hash '" + rel(f) + "'.");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }

    // ================================================================= generic JSON read
    // Enough for summary.json and staleness.json: objects become LinkedHashMap (key order kept), arrays
    // ArrayList, strings String, numbers JsonNumber (raw text kept, so an integer stays an integer),
    // true/false Boolean, null JSON_NULL. Anything malformed throws with the file name and offset.
    private static final class JsonNumber {
        final String raw;

        JsonNumber(String raw) {
            this.raw = raw;
        }
    }

    private static final class JsonReader {
        private final String s;
        private final String name;
        private int i;

        JsonReader(String s, String name) {
            this.s = s;
            this.name = name;
        }

        Object document() {
            Object v = value();
            skip();
            if (i != s.length()) {
                throw error("trailing text");
            }
            return v;
        }

        private RuntimeException error(String what) {
            return new RuntimeException("manifest_writer: " + name + " is not valid JSON (" + what + " at offset " + i + ").");
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private Object value() {
            skip();
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(i);
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return string();
            }
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            if (s.startsWith("null", i)) {
                i += 4;
                return JSON_NULL;
            }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            i++;
            skip();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                skip();
                if (i >= s.length() || s.charAt(i) != '"') {
                    throw error("expected a key");
                }
                String k = string();
                skip();
                if (i >= s.length() || s.charAt(i) != ':') {
                    throw error("expected ':'");
                }
                i++;
                if (m.containsKey(k)) {
                    throw error("duplicate key '" + k + "'");
                }
                m.put(k, value());
                skip();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                throw error("expected ',' or '}'");
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<Object>();
            i++;
            skip();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                skip();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                throw error("expected ',' or ']'");
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    break;
                }
                char e = s.charAt(i++);
                if (e == 'u') {
                    if (i + 4 > s.length()) {
                        throw error("short \\u escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad \\u escape");
                    }
                    i += 4;
                } else if (e == 'n') {
                    sb.append('\n');
                } else if (e == 'r') {
                    sb.append('\r');
                } else if (e == 't') {
                    sb.append('\t');
                } else if (e == 'b') {
                    sb.append('\b');
                } else if (e == 'f') {
                    sb.append('\f');
                } else if (e == '"' || e == '\\' || e == '/') {
                    sb.append(e);
                } else {
                    throw error("bad escape");
                }
            }
            throw error("unterminated string");
        }

        private JsonNumber number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String raw = s.substring(start, i);
            if (raw.length() == 0) {
                throw error("unexpected character");
            }
            try {
                Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw error("bad value '" + raw + "'");
            }
            return new JsonNumber(raw);
        }
    }

    private static Object parseJson(String text, String name) {
        return new JsonReader(text, name).document();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object o, String what) {
        if (!(o instanceof Map)) {
            throw new RuntimeException("manifest_writer: " + what + " is not a JSON object.");
        }
        return (Map<String, Object>) o;
    }

    // Member at a path of object keys; missing throws with the dotted path.
    private static Object member(Map<String, Object> root, String file, String... path) {
        Object cur = root;
        String dotted = "";
        for (int k = 0; k < path.length; k++) {
            dotted = k == 0 ? path[k] : dotted + "." + path[k];
            Map<String, Object> m = asObject(cur, file + " " + (k == 0 ? "root" : dotted.substring(0, dotted.lastIndexOf('.'))));
            if (!m.containsKey(path[k])) {
                throw new RuntimeException("manifest_writer: " + file + " has no '" + dotted + "'.");
            }
            cur = m.get(path[k]);
        }
        return cur;
    }

    private static long requireLong(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        String dotted = String.join(".", path);
        if (!(v instanceof JsonNumber)) {
            throw new RuntimeException("manifest_writer: " + file + " " + dotted + " is not a number.");
        }
        try {
            return Long.parseLong(((JsonNumber) v).raw);
        } catch (NumberFormatException e) {
            throw new RuntimeException("manifest_writer: " + file + " " + dotted + " is not an integer: '"
                + ((JsonNumber) v).raw + "'.");
        }
    }

    // JSON text of a value read by JsonReader, compact, key order kept: used to copy result and
    // summary of object_audit.json into adapter.json as they were written.
    @SuppressWarnings("unchecked")
    private static String toJson(Object v) {
        if (v == JSON_NULL || v == null) {
            return "null";
        }
        if (v instanceof JsonNumber) {
            return ((JsonNumber) v).raw;
        }
        if (v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof String) {
            return str((String) v);
        }
        StringBuilder sb = new StringBuilder();
        if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                sb.append(first ? "" : ", ").append(str(e.getKey())).append(": ").append(toJson(e.getValue()));
                first = false;
            }
            return sb.append('}').toString();
        }
        if (v instanceof List) {
            sb.append('[');
            List<Object> l = (List<Object>) v;
            for (int i = 0; i < l.size(); i++) {
                sb.append(i == 0 ? "" : ", ").append(toJson(l.get(i)));
            }
            return sb.append(']').toString();
        }
        throw new IllegalStateException("manifest_writer: no JSON form for " + className(v) + ".");
    }

    private static String jsonScalarText(Object v) {
        if (v instanceof JsonNumber) {
            return ((JsonNumber) v).raw;
        }
        if (v == JSON_NULL) {
            return "null";
        }
        return String.valueOf(v);
    }

    // ================================================================= logging / files
    private void log(String s) {
        sim.println("manifest_writer: " + s);
        if (runLog != null) {
            appendLine(runLog, LOG_PREFIX + s);
        }
    }

    // One FAIL line per run; the exception is rethrown by the caller. Before the point is resolved
    // there is no run_log.txt to write to, so the reason goes to the output window only.
    private void fail(RuntimeException e) {
        String reason = scrub(String.valueOf(e.getMessage())).replace('\n', ' ').replace('\r', ' ');
        sim.println("manifest_writer: FAIL " + reason);
        if (runLog != null) {
            try {
                appendLine(runLog, LOG_PREFIX + "FAIL " + reason);
            } catch (RuntimeException ignored) {
                // the original failure is the one to report
            }
        }
    }

    // Replaces the session directory's absolute path with "." so no absolute path reaches the log.
    private String scrub(String s) {
        if (sessionBase == null || s == null) {
            return s;
        }
        String abs = sessionBase.getAbsolutePath();
        return abs.length() == 0 ? s : s.replace(abs, ".");
    }

    // Path relative to the point directory when inside it, else to the session directory.
    private String rel(File f) {
        String abs = f.getAbsolutePath();
        if (pointDir != null) {
            String c = pointDir.getAbsolutePath() + File.separator;
            if (abs.startsWith(c)) {
                return abs.substring(c.length()).replace('\\', '/');
            }
        }
        if (sessionBase != null) {
            String b = sessionBase.getAbsolutePath() + File.separator;
            if (abs.startsWith(b)) {
                return abs.substring(b.length()).replace('\\', '/');
            }
        }
        return f.getName();
    }

    private static String nowUtc() {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC).format(Instant.now());
    }

    private static String four(int n) {
        String s = Integer.toString(n);
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "null";
        }
        return Double.toString(v);
    }

    private static String numArray(double[] v) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(num(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    // Same escaping as run_macro.str.
    private static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c < 0x20) {
                sb.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(c)));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private List<String> readLines(File f) {
        List<String> out = new ArrayList<String>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                out.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("manifest_writer: could not read '" + rel(f) + "'.");
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
        return out;
    }

    private String readText(File f) {
        List<String> lines = readLines(f);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    // The whole text goes to a side file first and is then moved over adapter.json, so a failed write
    // never leaves a partial adapter.json; the side file is removed on failure.
    private void writeOnce(File f, String content) {
        File part = new File(f.getParentFile(), f.getName() + ".part");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(part), StandardCharsets.UTF_8));
            w.write(content);
            w.close();
            w = null;
            Files.move(part.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("manifest_writer: could not write '" + rel(f) + "'.");
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
            if (part.exists() && !part.delete()) {
                sim.println("manifest_writer: could not remove '" + rel(part) + "'");
            }
        }
    }

    private void appendLine(File f, String line) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
            w.write(line);
            w.write('\n');
        } catch (Exception e) {
            throw new RuntimeException("manifest_writer: could not append to '" + rel(f) + "'.");
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }
}
