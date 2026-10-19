// object_audit.java
// xtr chain (Capsule Extractor v0, STAR-CCM+ adapter) -- second of three. Simcenter STAR-CCM+ 2606.
// One public class, no records, no modules.
//
// Walks the object managers of the open, solved .sim and checks it against the closed list built from
// card.json (H5): the Derived Parts grid_<stem> and section_<stem> and the table LLM_table_<stem> of
// every station, the boundaries card.body.body_boundaries.names and the region
// card.body.fluid_region.name. The scenes view_<s> and LLM_plane_<field> are not on the list: output_exporter
// creates them when the .sim lacks them, so they are not inputs of the case. RESULT PASS when every name of that list exists; any other
// object is counted as other and never fails the audit. object_audit.json goes to work/<card.body.alias>/,
// which output_exporter has already written (with its run_log.txt). card.json is looked up like
// output_exporter does: session directory first, then the directory of the .sim. Read-only: it
// creates, renames and deletes nothing, and never saves the simulation.
//
// Markers "TODO(unverified signature)" name the API members that no compiled source of this chain
// calls; they are the expected compile risks.

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import star.base.neo.NamedObject;
import star.common.Boundary;
import star.common.Region;
import star.common.Simulation;
import star.common.StarMacro;

public class object_audit extends StarMacro {

    // ---------------------------------------------------------------- constants
    private static final String CARD_NAME = "card.json";
    private static final String LOG_PREFIX = "audit: ";
    private static final String OUT_NAME = "object_audit.json";
    private static final String WORK_DIR = "work";
    private static final Object JSON_NULL = new Object();

    // Container keys, in walk order. Regions and boundaries are looked up by name, not walked: their
    // rows are only the ones the card asks for.
    private static final String M_REPORTS = "reports";
    private static final String M_PARTS = "parts";
    private static final String M_TABLES = "tables";
    private static final String M_SCENES = "scenes";
    private static final String M_FIELD_FUNCTIONS = "field_functions";
    private static final String M_MONITORS = "monitors";
    private static final String M_STOPPING_CRITERIA = "stopping_criteria";
    private static final String M_GLOBAL_PARAMETERS = "global_parameters";
    private static final String M_ANNOTATIONS = "annotations";
    private static final String M_REGIONS = "regions";
    private static final String M_BOUNDARIES_PREFIX = "boundaries:";

    private static final String C_OWNED = "owned";
    private static final String C_OTHER = "other";
    private static final String[] CLASSIFICATIONS = { C_OWNED, C_OTHER };

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private File sessionBase;
    private File outDir;
    private File logFile;
    private String alias;
    private String fluidRegion;
    private final List<String[]> closedList = new ArrayList<String[]>();   // {container, name}, card order
    private final List<String[]> managerRows = new ArrayList<String[]>();   // {manager, class, count}
    private final List<String[]> objectRows = new ArrayList<String[]>();    // {manager, name, class, classification}
    private final Map<String, Set<String>> ownedByContainer = new LinkedHashMap<String, Set<String>>();
    private final Set<String> ownedSeen = new HashSet<String>();
    private final List<String> missingOwned = new ArrayList<String>();
    private final List<String> pendingLog = new ArrayList<String>();

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();
        try {
            readCard(loadCard());
            resolveOutput();

            walkReports();
            walkParts();
            walkTables();
            walkScenes();
            walkFieldFunctions();
            walkMonitors();
            walkStoppingCriteria();
            walkGlobalParameters();
            walkAnnotations();
            lookUpRegionAndBoundaries();

            collectMissingOwned();

            String json = buildJson();
            File out = new File(outDir, OUT_NAME);
            writeOnce(out, json);

            flushResultLines();
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= card.json / closed list
    // Same lookup order as output_exporter.loadCard. Returns the parsed root object.
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
            throw new RuntimeException("object_audit: could not locate '" + CARD_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }
        String text;
        try {
            text = new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("object_audit: could not read '" + CARD_NAME + "'.");
        }
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return asObject(parseJson(text, CARD_NAME), CARD_NAME + " root");
    }

    // The closed list, in card order: per station grid_<stem>, section_<stem> (parts) and
    // LLM_table_<stem> (tables); the body boundaries in the fluid region; the fluid region. A name asked
    // twice is kept once.
    private void readCard(Map<String, Object> card) {
        alias = requireString(card, CARD_NAME, "body", "alias");
        if (alias.length() == 0 || alias.indexOf('/') >= 0 || alias.indexOf('\\') >= 0 || alias.startsWith(".")) {
            throw new RuntimeException("object_audit: " + CARD_NAME + " body.alias '" + alias + "' is not a directory name.");
        }
        fluidRegion = requireString(card, CARD_NAME, "body", "fluid_region", "name");

        List<Object> st = requireList(card, CARD_NAME, "stations");
        for (int i = 0; i < st.size(); i++) {
            String where = CARD_NAME + " stations[" + i + "]";
            String stem = requireString(asObject(st.get(i), where), where, "stem");
            addOwned(M_PARTS, "grid_" + stem);
            addOwned(M_PARTS, "section_" + stem);
            addOwned(M_TABLES, "LLM_table_" + stem);
        }
        List<Object> names = requireList(card, CARD_NAME, "body", "body_boundaries", "names");
        for (int i = 0; i < names.size(); i++) {
            if (!(names.get(i) instanceof String)) {
                throw new RuntimeException("object_audit: " + CARD_NAME + " body.body_boundaries.names[" + i + "] is not a string.");
            }
            addOwned(M_BOUNDARIES_PREFIX + fluidRegion, (String) names.get(i));
        }
        addOwned(M_REGIONS, fluidRegion);
        pendingLog.add("closed list from " + CARD_NAME + ": " + closedList.size() + " names");
    }

    private void addOwned(String container, String name) {
        Set<String> s = ownedByContainer.get(container);
        if (s == null) {
            s = new HashSet<String>();
            ownedByContainer.put(container, s);
        }
        if (s.add(name)) {
            closedList.add(new String[] { container, name });
        }
    }

    // ================================================================= output directory
    // work/<card.body.alias>/; the directory and its run_log.txt must already exist (output_exporter
    // wrote them).
    private void resolveOutput() {
        String sessionDir = sim.getSessionDir();
        if (sessionDir != null && sessionDir.length() > 0) {
            sessionBase = new File(sessionDir);
        } else {
            sessionBase = sim.getSessionDirFile();
        }
        String relative = WORK_DIR + File.separator + alias;
        File dir = new File(sessionBase, relative);
        if (!dir.isDirectory()) {
            throw new RuntimeException("object_audit: missing point directory '" + relative + "'.");
        }
        File log = new File(dir, "run_log.txt");
        if (!log.isFile()) {
            throw new RuntimeException("object_audit: missing '" + relative + File.separator + "run_log.txt'.");
        }
        outDir = dir;
        logFile = log;
        sim.println("object_audit: output=" + rel(outDir));
    }

    // ================================================================= walks
    // ClientServerObjectManager.getObjects() is bench-verified; run_macro.java calls it on
    // SolverStoppingCriterionManager. The result is taken as Collection<?> so the element type of each
    // manager does not have to be named.
    private void walkReports() {
        walk(M_REPORTS, sim.getReportManager(), sim.getReportManager().getObjects());
    }

    private void walkParts() {
        walk(M_PARTS, sim.getPartManager(), sim.getPartManager().getObjects());
    }

    private void walkTables() {
        walk(M_TABLES, sim.getTableManager(), sim.getTableManager().getObjects());
    }

    private void walkScenes() {
        walk(M_SCENES, sim.getSceneManager(), sim.getSceneManager().getObjects());
    }

    private void walkFieldFunctions() {
        walk(M_FIELD_FUNCTIONS, sim.getFieldFunctionManager(), sim.getFieldFunctionManager().getObjects());
    }

    private void walkMonitors() {
        walk(M_MONITORS, sim.getMonitorManager(), sim.getMonitorManager().getObjects());
    }

    private void walkStoppingCriteria() {
        walk(M_STOPPING_CRITERIA, sim.getSolverStoppingCriterionManager(), sim.getSolverStoppingCriterionManager().getObjects());
    }

    private void walkGlobalParameters() {
        walk(M_GLOBAL_PARAMETERS, sim.getGlobalParameterManager(), sim.getGlobalParameterManager().getObjects());
    }

    private void walkAnnotations() {
        // TODO(unverified signature): AnnotationManager.getObjects() -- run_macro.java calls only
        // hasObject and createSimpleAnnotation on it.
        Collection<?> all = sim.getAnnotationManager().getObjects();
        walk(M_ANNOTATIONS, sim.getAnnotationManager(), all);
    }

    private void walk(String manager, Object managerObject, Collection<?> objects) {
        if (objects == null) {
            throw new RuntimeException("object_audit: " + manager + " returned no object collection.");
        }
        String managerClass = managerObject.getClass().getSimpleName();
        managerRows.add(new String[] { manager, managerClass, String.valueOf(objects.size()) });
        pendingLog.add(manager + " " + managerClass + " count=" + objects.size());
        for (Object o : objects) {
            String name = nameOf(o);
            addRow(manager, name, o);
        }
    }

    private void addRow(String manager, String name, Object o) {
        String cls = o == null ? "null" : o.getClass().getSimpleName();
        Set<String> owned = ownedByContainer.get(manager);
        String c = owned != null && owned.contains(name) ? C_OWNED : C_OTHER;
        objectRows.add(new String[] { manager, name, cls, c });
        if (C_OWNED.equals(c)) {
            ownedSeen.add(manager + "\n" + name);
        }
    }

    // The fluid region and the body boundaries in it, by name with the lookups of run_macro.java; a
    // missing one (or one of the wrong class) is not an error here, it stays missing and the result is FAIL.
    private void lookUpRegionAndBoundaries() {
        Region fluid = null;
        try {
            fluid = region(fluidRegion);
            addRow(M_REGIONS, fluidRegion, fluid);
        } catch (RuntimeException e) {
            lookupFailed(e);
        }
        String container = M_BOUNDARIES_PREFIX + fluidRegion;
        for (int i = 0; i < closedList.size(); i++) {
            String[] c = closedList.get(i);
            if (!container.equals(c[0]) || fluid == null) {
                continue;
            }
            try {
                addRow(container, c[1], boundary(fluid, c[1]));
            } catch (RuntimeException e) {
                lookupFailed(e);
            }
        }
    }

    // An absent name is logged once, by collectMissingOwned; an object of the wrong class is logged here.
    private void lookupFailed(RuntimeException e) {
        String m = String.valueOf(e.getMessage()).replace("object_audit: ", "");
        if (!m.startsWith("missing ")) {
            pendingLog.add(m);
        }
    }

    // An object that is not a NamedObject has no presentation name to classify; it is written with an
    // empty name and counted as other.
    private static String nameOf(Object o) {
        if (o instanceof NamedObject) {
            String n = ((NamedObject) o).getPresentationName();
            return n == null ? "" : n;
        }
        return "";
    }

    private void collectMissingOwned() {
        for (int i = 0; i < closedList.size(); i++) {
            String[] c = closedList.get(i);
            if (!ownedSeen.contains(c[0] + "\n" + c[1])) {
                missingOwned.add(c[1]);
                pendingLog.add("missing " + c[0] + " " + c[1]);
            }
        }
        Collections.sort(missingOwned);
    }

    // Same lookups as run_macro.java: getObject in try/catch covers a throw or a null return alike.
    private Region region(String name) {
        Object o;
        try {
            o = sim.getRegionManager().getObject(name);
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            throw new RuntimeException("object_audit: missing region '" + name + "'.");
        }
        if (!(o instanceof Region)) {
            throw new RuntimeException("object_audit: object '" + name + "' is not a Region.");
        }
        return (Region) o;
    }

    private Boundary boundary(Region r, String name) {
        Object o;
        try {
            o = r.getBoundaryManager().getObject(name);
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            throw new RuntimeException("object_audit: missing boundary '" + name + "'.");
        }
        if (!(o instanceof Boundary)) {
            throw new RuntimeException("object_audit: object '" + name + "' is not a Boundary.");
        }
        return (Boundary) o;
    }

    // ================================================================= result
    private int count(String classification) {
        int n = 0;
        for (int i = 0; i < objectRows.size(); i++) {
            if (classification.equals(objectRows.get(i)[3])) {
                n++;
            }
        }
        return n;
    }

    private boolean passed() {
        return missingOwned.isEmpty();
    }

    private String resultLine() {
        if (passed()) {
            return "RESULT PASS (" + closedList.size() + " asked by the card, " + count(C_OTHER) + " other)";
        }
        return "RESULT FAIL (" + missingOwned.size() + " of " + closedList.size() + " asked by the card missing)";
    }

    // ================================================================= JSON
    private String buildJson() {
        List<String[]> sorted = new ArrayList<String[]>(objectRows);
        Collections.sort(sorted, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                int c = a[0].compareTo(b[0]);
                return c != 0 ? c : a[1].compareTo(b[1]);
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"audited\": ").append(str(auditedName())).append(",\n");
        sb.append("  \"produced\": ").append(str(nowUtc())).append(",\n");
        sb.append("  \"written_by\": \"object_audit\",\n");
        sb.append("  \"alias\": ").append(str(alias)).append(",\n");
        sb.append("  \"closed_list_size\": ").append(closedList.size()).append(",\n");

        sb.append("  \"closed_list\": [\n");
        for (int i = 0; i < closedList.size(); i++) {
            String[] c = closedList.get(i);
            sb.append("    {\"manager\": ").append(str(c[0])).append(", \"name\": ").append(str(c[1])).append('}')
              .append(i < closedList.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        sb.append("  \"managers\": [\n");
        for (int i = 0; i < managerRows.size(); i++) {
            String[] m = managerRows.get(i);
            sb.append("    {\"manager\": ").append(str(m[0])).append(", \"class\": ").append(str(m[1]))
              .append(", \"count\": ").append(m[2]).append('}')
              .append(i < managerRows.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        sb.append("  \"objects\": [\n");
        for (int i = 0; i < sorted.size(); i++) {
            String[] o = sorted.get(i);
            sb.append("    {\"manager\": ").append(str(o[0])).append(", \"name\": ").append(str(o[1]))
              .append(", \"class\": ").append(str(o[2])).append(", \"classification\": ").append(str(o[3]))
              .append('}').append(i < sorted.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");

        sb.append("  \"summary\": {");
        for (int i = 0; i < CLASSIFICATIONS.length; i++) {
            sb.append(str(CLASSIFICATIONS[i])).append(": ").append(count(CLASSIFICATIONS[i])).append(", ");
        }
        sb.append("\"missing_owned\": ").append(strArray(missingOwned.toArray(new String[0]))).append("},\n");

        sb.append("  \"result\": ").append(str(passed() ? "PASS" : "FAIL")).append('\n');
        sb.append("}\n");
        return sb.toString();
    }

    // File name only, never the path: Simulation.getSessionPath() is verified in run_macro.java.
    private String auditedName() {
        String path = sim.getSessionPath();
        if (path == null || path.length() == 0) {
            return null;
        }
        return new File(path).getName();
    }

    private static String strArray(String[] a) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(str(a[i]));
        }
        sb.append(']');
        return sb.toString();
    }

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

    private static String nowUtc() {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC).format(Instant.now());
    }

    // ================================================================= generic JSON read
    // Copied from output_exporter.java (JsonReader family), used here for card.json: objects become
    // LinkedHashMap (key order kept), arrays ArrayList, strings String, numbers JsonNumber (raw text
    // kept, so an integer stays an integer), true/false Boolean, null JSON_NULL. Anything malformed throws with the file name and offset.
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
            return new RuntimeException("object_audit: " + name + " is not valid JSON (" + what + " at offset " + i + ").");
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
            throw new RuntimeException("object_audit: " + what + " is not a JSON object.");
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
                throw new RuntimeException("object_audit: " + file + " has no '" + dotted + "'.");
            }
            cur = m.get(path[k]);
        }
        return cur;
    }

    private static String requireString(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof String)) {
            throw new RuntimeException("object_audit: " + file + " " + String.join(".", path) + " is not a string.");
        }
        return (String) v;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireList(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof List)) {
            throw new RuntimeException("object_audit: " + file + " " + String.join(".", path) + " is not an array.");
        }
        return (List<Object>) v;
    }

    // ================================================================= logging / files
    // Log lines are held until object_audit.json is written, then appended in order: the closed-list
    // size, one per container, any region or boundary lookup that failed, one per missing name, the
    // RESULT line and "done". Other objects are not logged one by one. A FAIL result is not a macro
    // failure: the macro returns normally.
    private void flushResultLines() {
        List<String> lines = new ArrayList<String>();
        List<String> tail = new ArrayList<String>();
        for (int i = 0; i < pendingLog.size(); i++) {
            String s = pendingLog.get(i);
            if (s.startsWith("missing ")) {
                tail.add(s);
            } else {
                lines.add(s);
            }
        }
        lines.addAll(tail);
        lines.add(resultLine());
        lines.add("done");
        for (int i = 0; i < lines.size(); i++) {
            log(lines.get(i));
        }
    }

    private void log(String s) {
        sim.println("object_audit: " + s);
        if (logFile != null) {
            appendLine(logFile, LOG_PREFIX + s);
        }
    }

    // One FAIL line per run; the exception is rethrown by the caller, without "done". Before the output
    // directory is resolved there is no log to write to, so the reason goes to the output window only.
    private void fail(RuntimeException e) {
        String reason = scrub(String.valueOf(e.getMessage())).replace('\n', ' ').replace('\r', ' ');
        sim.println("object_audit: FAIL " + reason);
        if (logFile != null) {
            try {
                appendLine(logFile, LOG_PREFIX + "FAIL " + reason);
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

    // Path relative to the session directory when inside it, else the file name only.
    private String rel(File f) {
        String abs = f.getAbsolutePath();
        if (sessionBase != null) {
            String b = sessionBase.getAbsolutePath() + File.separator;
            if (abs.startsWith(b)) {
                return abs.substring(b.length()).replace('\\', '/');
            }
        }
        return f.getName();
    }

    // The whole text goes to a side file first and is then moved over the target, so a failed write
    // never leaves a partial object_audit.json; the side file is removed on failure.
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
            throw new RuntimeException("object_audit: could not write '" + rel(f) + "'.");
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
            if (part.exists() && !part.delete()) {
                sim.println("object_audit: could not remove '" + rel(part) + "'");
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
            throw new RuntimeException("object_audit: could not append to '" + rel(f) + "'.");
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
