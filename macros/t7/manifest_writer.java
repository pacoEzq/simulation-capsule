// manifest_writer.java
// T7 chain -- session C. Simcenter STAR-CCM+ 2606.
// Runs on an already solved and exported cube_re<NNNN>.sim (after run_macro and output_exporter) and
// writes manifest.json into work/cube_re<NNNN>/. It creates, renames and deletes no simulation object
// and does not save the simulation; every object it reads is resolved by name and a missing one throws
// with its name. build_capsule.py later copies it into the capsule and fills the final plane hashes
// and the repository commit.
//
// manifest.json is a file of mechanics, not of physics: what is needed to reproduce the capsule byte
// for byte. The flow is described by summary.json, not here.
//
// Signatures come from run_macro.java and output_exporter.java (same repository, compiled against
// 2606 and used in real runs), or are the getter of a setter run_macro.java calls in mode=prepare
// (marked "getter of a verified setter"). Anything else is marked TODO(unverified signature) with the
// reason and is listed in the VERIFICATION LOG. Cameras and colour bars are not read here: since v16
// output_exporter reads them after each render and writes them to plane_render.json, with the
// "<key>_read_as" rule for a value of unexpected type, and this file copies them as written.

import java.awt.image.BufferedImage;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.imageio.ImageIO;

import star.base.neo.NamedObject;
import star.base.report.Report;
import star.common.FieldFunction;
import star.common.ScalarGlobalParameter;
import star.common.Simulation;
import star.common.StarMacro;
import star.common.XyzInternalTable;
import star.meshing.AutoMeshOperation;
import star.meshing.BaseSize;
import star.meshing.MeshOperationManager;

public class manifest_writer extends StarMacro {

    // ---------------------------------------------------------------- constants
    // Same properties file and same point directory rule as run_macro.java and output_exporter.java:
    // re_target from LLM_point.properties, point directory work/cube_re<NNNN>/ under the session
    // directory. The sweep values come from sweep.json in the session directory (the run directory).
    private static final String PROPS_NAME = "LLM_point.properties";
    private static final String SWEEP_JSON = "sweep.json";
    private static final String WORK_DIR = "work";
    private static final String LOG_PREFIX = "manifest: ";
    private static final String SPEC_VERSION = "0.4";

    private static final String[] PLANES = { "y0", "x1", "x2", "x4" };
    // grid_y0 is 32 x 12 intervals, grid_x1, grid_x2 and grid_x4 are 12 x 12; rows_expected is
    // (intervals + 1) per axis. Same order as PLANES; same values as output_exporter.
    private static final int[][] GRID_INTERVALS = { { 32, 12 }, { 12, 12 }, { 12, 12 }, { 12, 12 } };
    private static final int[] PLANE_ROWS_EXPECTED = { 33 * 13, 13 * 13, 13 * 13, 13 * 13 };

    // Columns of the raw plane CSV, in this order; since v12 they are also the names of the field
    // functions the tables hold, so there is no name mapping.
    // The final plane CSV (written by build_capsule.py) drops the coordinate the plane holds constant:
    // y_D on y0, x_D on the x planes. Index into CSV_COLUMNS, same order as PLANES.
    private static final String[] CSV_COLUMNS = {
        "x_D", "y_D", "z_D", "cp", "u_over_U", "v_over_U", "w_over_U"
    };
    private static final int[] PLANE_CONSTANT_COLUMN = { 1, 0, 0, 0 };

    // Plane images of output_exporter, described in plane_render.json.
    private static final String PLANE_SCENE = "LLM_plane_u_over_U";
    private static final String PLANE_RENDER_JSON = "plane_render.json";

    // The only view (spec v16, 8.1; names of decision 79): cp on the six cube_* boundaries, rendered by
    // output_exporter in a transient scene, with the six-argument print call. The question is the
    // exact text of 8.1.
    private static final String BODY_FILE = "views/view_cp_body.png";
    private static final String BODY_SCENE = "view_cp_body";
    private static final String BODY_FIELD = "cp";
    private static final String BODY_PART = "cube_back, cube_bottom, cube_front, cube_left, cube_right, cube_top";
    private static final String BODY_EXPORT_CALL = "printAndWait(file, 1, 1024, 739, true, false)";
    private static final String BODY_QUESTION = "Where does the flow push and where does it pull on the body surface?";

    // Size the print calls of output_exporter ask for; the image is kept as printed, no crop.
    private static final int VIEW_MAGNIFICATION = 1;
    private static final int VIEW_WIDTH = 1024;
    private static final int VIEW_HEIGHT = 739;

    // Chain files, hashed from the session directory; path is where each one lives in the
    // simulation-capsule repository.
    private static final String[] CHAIN_KEYS = {
        "run_macro", "output_exporter", "manifest_writer", "object_audit", "sweep_driver", "build_capsule"
    };
    private static final String[] CHAIN_FILES = {
        "run_macro.java", "output_exporter.java", "manifest_writer.java", "object_audit.java",
        "sweep_driver.sh", "build_capsule.py"
    };
    private static final String CHAIN_PATH_PREFIX = "macros/t7/";

    // Sentinel for a JSON null read from disk (a Map can hold null, but containsKey is clumsier).
    private static final Object JSON_NULL = new Object();

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private File sessionBase;
    private File pointDir;
    private File runLog;
    private String nnnn;
    private int reTarget;
    private int[] sweepValues;
    private int sweepBaseline;

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();
        try {
            // ---- 1. point directory and log
            Properties props = loadProperties();
            reTarget = requireIntProperty(props, "re_target");
            nnnn = four(reTarget);
            resolvePointDir();
            sweepValues = readSweepValues();
            sweepBaseline = readSweepBaseline();
            boolean known = false;
            for (int i = 0; i < sweepValues.length; i++) {
                known = known || sweepValues[i] == reTarget;
            }
            if (!known) {
                throw new RuntimeException("manifest_writer: property 're_target' is " + reTarget
                    + ", not one of the points of " + SWEEP_JSON + ".");
            }
            log("start re_target=" + reTarget + " point=" + WORK_DIR + "/cube_re" + nnnn + " capsule=" + capsuleName());
            if (new File(pointDir, "manifest.json").isFile()) {
                log("manifest.json already present, it will be replaced");
            }

            // ---- 2. what is already on disk
            Map<String, Object> summary = readSummary();
            String staleness = readStaleness();
            Map<String, Object> stalenessTree = asObject(parseJson(staleness, "staleness.json"), "staleness.json");
            SetupFields setup = readSetup();
            Map<String, Object> planeRender = readPlaneRender();

            // ---- 3. what only the session knows
            long cells = cellCount();
            long summaryCells = requireLong(summary, "summary.json", "mesh", "cells");
            if (cells != summaryCells) {
                throw new RuntimeException("manifest_writer: LLM_cell_count reads " + cells
                    + " but summary.json mesh.cells is " + summaryCells + ".");
            }
            double baseSizeOverD = baseSizeOverD();
            requireFieldFunctions();
            String planesJson = planesBlock(stalenessTree, planeRender);
            String viewsJson = viewsBlock(planeRender);

            // ---- 4. sweep inputs
            String simName = "cube_re" + nnnn + ".sim";
            String simSha = sha256OrNull(new File(sessionBase, simName), simName);
            String templateSha = sha256OrNull(new File(sessionBase, "cube_sweep_template.sim"), "cube_sweep_template.sim");

            // ---- 5. chain files
            String[] chainSha = new String[CHAIN_KEYS.length];
            for (int i = 0; i < CHAIN_KEYS.length; i++) {
                String f = CHAIN_FILES[i];
                chainSha[i] = sha256OrNull(new File(sessionBase, f), f);
            }

            // ---- 6. build the whole text, then write it once
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"capsule\": ").append(str(capsuleName())).append(",\n");
            sb.append("  \"spec_version\": ").append(str(SPEC_VERSION)).append(",\n");
            sb.append("  \"produced\": ").append(str(nowUtc())).append(",\n");
            sb.append("  \"written_by\": \"manifest_writer\",\n");
            appendSweep(sb, simName, simSha, templateSha);
            appendChain(sb, chainSha);
            appendSession(sb, setup);
            sb.append("  \"seeds\": {},\n");
            sb.append("  \"seeds_note\": \"No stochastic step in this chain. samples.csv (Part 4) is not part of the cube capsule.\",\n");
            sb.append("  \"mesh\": {\"operation\": \"mesh_cube\", \"base_size_over_D\": ").append(num(baseSizeOverD))
              .append(", \"cells\": ").append(cells)
              .append(", \"identical_across_sweep\": true, \"prism_guard\": \"LLM_prism_off_nonwall\"},\n");
            sb.append("  \"fields\": {\"u_over_U\": {\"field_function\": \"u_over_U\", \"definition\": \"Velocity[0] / U\", ")
              .append("\"component\": \"x\"}},\n");
            sb.append("  \"planes\": ").append(planesJson).append(",\n");
            sb.append("  \"views\": ").append(viewsJson).append(",\n");
            // Filled later, outside the session.
            sb.append("  \"checks\": {\"mirror_pair_max_delta\": null},\n");
            sb.append("  \"tokens\": {\"tool\": \"capsule_ledger.py\", \"eol\": \"LF\", \"files\": {}, \"total\": null}\n");
            sb.append("}\n");

            String text = sb.toString();
            parseJson(text, "manifest.json (in memory)");
            File out = new File(pointDir, "manifest.json");
            writeOnce(out, text);
            log(rel(out) + " bytes=" + text.getBytes(StandardCharsets.UTF_8).length);
            // Closing line (spec 2): "manifest: done", the same text in the output window and in run_log.txt.
            sim.println(LOG_PREFIX + "done");
            if (runLog != null) {
                appendLine(runLog, LOG_PREFIX + "done");
            }
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= properties / capsule
    // Same lookup order as run_macro.loadProperties and output_exporter.loadProperties: session
    // directory first, then the directory returned by Simulation.getSessionDirFile().
    private Properties loadProperties() {
        String sessionDir = null;
        try {
            sessionDir = sim.getSessionDir();
        } catch (Exception e) {
            sessionDir = null;
        }
        File candidate = null;
        if (sessionDir != null && sessionDir.length() > 0) {
            File f = new File(sessionDir, PROPS_NAME);
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
                File f = new File(simDir, PROPS_NAME);
                if (f.isFile()) {
                    candidate = f;
                }
            }
        }
        if (candidate == null) {
            throw new RuntimeException("manifest_writer: could not locate '" + PROPS_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }

        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(candidate);
            p.load(in);
        } catch (Exception e) {
            throw new RuntimeException("manifest_writer: could not parse '" + PROPS_NAME + "'.");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
        return p;
    }

    private int requireIntProperty(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().length() == 0) {
            throw new RuntimeException("manifest_writer: property '" + key + "' missing or empty in " + PROPS_NAME + ".");
        }
        v = v.trim();
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new RuntimeException("manifest_writer: property '" + key + "' is not an integer: '" + v + "'.");
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
        String relative = WORK_DIR + File.separator + "cube_re" + nnnn;
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

    // Name of the capsule build_capsule.py makes from this point directory.
    private String capsuleName() {
        return "capsule_cube_re" + nnnn;
    }

    // sweep.json of the run directory: "points", a non-empty list of distinct integers.
    private int[] readSweepValues() {
        File f = new File(sessionBase, SWEEP_JSON);
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing '" + SWEEP_JSON + "' in the session directory.");
        }
        Map<String, Object> root = asObject(parseJson(readText(f), SWEEP_JSON), SWEEP_JSON);
        Object points = member(root, SWEEP_JSON, "points");
        if (!(points instanceof List) || ((List<?>) points).isEmpty()) {
            throw new RuntimeException("manifest_writer: " + SWEEP_JSON + " points is not a non-empty list.");
        }
        List<?> l = (List<?>) points;
        int[] out = new int[l.size()];
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < l.size(); i++) {
            Object v = l.get(i);
            int n;
            try {
                n = Integer.parseInt(v instanceof JsonNumber ? ((JsonNumber) v).raw : "");
            } catch (NumberFormatException e) {
                throw new RuntimeException("manifest_writer: " + SWEEP_JSON + " points[" + i + "] is not an integer.");
            }
            if (!seen.add(Integer.valueOf(n))) {
                throw new RuntimeException("manifest_writer: " + SWEEP_JSON + " points lists " + n + " twice.");
            }
            out[i] = n;
        }
        log(SWEEP_JSON + " points=" + Arrays.toString(out));
        return out;
    }

    // sweep.json "baseline" (spec v16 2 and 8.3): an integer, one of "points". It names the capsule
    // the variants are diffed against.
    private int readSweepBaseline() {
        File f = new File(sessionBase, SWEEP_JSON);
        Map<String, Object> root = asObject(parseJson(readText(f), SWEEP_JSON), SWEEP_JSON);
        Object v = member(root, SWEEP_JSON, "baseline");
        int n;
        try {
            n = Integer.parseInt(v instanceof JsonNumber ? ((JsonNumber) v).raw : "");
        } catch (NumberFormatException e) {
            throw new RuntimeException("manifest_writer: " + SWEEP_JSON + " baseline is not an integer.");
        }
        boolean known = false;
        for (int i = 0; i < sweepValues.length; i++) {
            known = known || sweepValues[i] == n;
        }
        if (!known) {
            throw new RuntimeException("manifest_writer: " + SWEEP_JSON + " baseline " + n + " is not one of its points.");
        }
        log(SWEEP_JSON + " baseline=" + n);
        return n;
    }

    // ================================================================= lookups (fail loudly)
    // Measured on 2606 (30.09): FieldFunctionManager.getFunction(name) for a name that does not exist
    // returns an object of class NullFieldFunction, neither null nor an exception. That class is matched
    // by its simple name, not imported; every field function this file reads goes through this test.
    private static boolean isMissingFieldFunction(Object ff) {
        return ff == null || ff.getClass().getSimpleName().equals("NullFieldFunction");
    }

    // The seven field functions the tables hold (the CSV_COLUMNS names), looked up by name
    // with the call recorded in the 2606 GUI, FieldFunctionManager.getFunction(String).
    private void requireFieldFunctions() {
        for (int k = 0; k < CSV_COLUMNS.length; k++) {
            if (isMissingFieldFunction(sim.getFieldFunctionManager().getFunction(CSV_COLUMNS[k]))) {
                throw new RuntimeException("manifest_writer: error: missing field function '" + CSV_COLUMNS[k] + "'.");
            }
        }
        log("field functions present: " + String.join(",", CSV_COLUMNS));
    }

    // Same calls as run_macro.java / output_exporter.java.
    private Object derivedPart(String name) {
        Object p = sim.getPartManager().hasObject(name);
        if (p == null) {
            throw new RuntimeException("manifest_writer: missing derived part '" + name + "'.");
        }
        return p;
    }

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

    private XyzInternalTable table(String name) {
        Object o = sim.getTableManager().hasTable(name);
        if (o == null) {
            throw new RuntimeException("manifest_writer: missing table '" + name + "'.");
        }
        if (!(o instanceof XyzInternalTable)) {
            throw new RuntimeException("manifest_writer: table '" + name + "' is not an XyzInternalTable.");
        }
        return (XyzInternalTable) o;
    }

    // run_macro.meshCube, unchanged: MeshOperationManager has no confirmed has-method, so getObject is
    // wrapped in try/catch and a throw or a null both mean absent.
    private AutoMeshOperation meshCube() {
        MeshOperationManager mom = sim.get(MeshOperationManager.class);
        Object op;
        try {
            op = mom.getObject("mesh_cube");
        } catch (RuntimeException e) {
            op = null;
        }
        if (op == null) {
            throw new RuntimeException("manifest_writer: missing mesh operation 'mesh_cube'.");
        }
        if (!(op instanceof AutoMeshOperation)) {
            throw new RuntimeException("manifest_writer: mesh operation 'mesh_cube' is not an AutoMeshOperation.");
        }
        return (AutoMeshOperation) op;
    }

    // ================================================================= step 2: disk
    // summary.json, generic read; only sweep.value, convergence.iterations, forces.cd and mesh.cells
    // are taken. sweep.value must equal re_target; iterations and cd go to the log only.
    private Map<String, Object> readSummary() {
        File f = new File(pointDir, "summary.json");
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing 'summary.json'.");
        }
        Map<String, Object> root = asObject(parseJson(readText(f), "summary.json"), "summary.json");
        long value = requireLong(root, "summary.json", "sweep", "value");
        if (value != reTarget) {
            throw new RuntimeException("manifest_writer: summary.json sweep.value is " + value
                + " but re_target is " + reTarget + ".");
        }
        long iterations = requireLong(root, "summary.json", "convergence", "iterations");
        Object cd = member(root, "summary.json", "forces", "cd");
        long cells = requireLong(root, "summary.json", "mesh", "cells");
        log("summary.json sweep.value=" + value + " convergence.iterations=" + iterations
            + " forces.cd=" + jsonScalarText(cd) + " mesh.cells=" + cells);
        return root;
    }

    // staleness.json as written by output_exporter; missing means the exporter did not run.
    private String readStaleness() {
        File f = new File(pointDir, "staleness.json");
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing 'staleness.json' (output_exporter did not run).");
        }
        String text;
        try {
            text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("manifest_writer: could not read 'staleness.json'.");
        }
        log("staleness.json read bytes=" + f.length());
        return text;
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

    // plane_render.json as written by output_exporter: scene, colorbar and, per plane, the camera read
    // after the render and the title. Missing means the exporter did not run.
    private Map<String, Object> readPlaneRender() {
        File f = new File(pointDir, PLANE_RENDER_JSON);
        if (!f.isFile()) {
            throw new RuntimeException("manifest_writer: missing '" + PLANE_RENDER_JSON + "' (output_exporter did not run).");
        }
        Map<String, Object> root = asObject(parseJson(readText(f), PLANE_RENDER_JSON), PLANE_RENDER_JSON);
        Object scene = member(root, PLANE_RENDER_JSON, "scene");
        if (!PLANE_SCENE.equals(scene)) {
            throw new RuntimeException("manifest_writer: " + PLANE_RENDER_JSON + " scene is '" + jsonScalarText(scene)
                + "', expected '" + PLANE_SCENE + "'.");
        }
        member(root, PLANE_RENDER_JSON, "colorbar");
        asObject(member(root, PLANE_RENDER_JSON, "layout", "title"), PLANE_RENDER_JSON + " layout.title");
        asObject(member(root, PLANE_RENDER_JSON, "layout", "colorbar"), PLANE_RENDER_JSON + " layout.colorbar");
        member(root, PLANE_RENDER_JSON, "layout", "colorbar", "label_format");
        member(root, PLANE_RENDER_JSON, "layout", "scenes");
        for (int p = 0; p < PLANES.length; p++) {
            member(root, PLANE_RENDER_JSON, "planes", PLANES[p], "camera");
        }
        log(PLANE_RENDER_JSON + " read bytes=" + f.length());
        return root;
    }

    // ================================================================= step 3: session
    // run_macro.cellCount: Report.monitoredValue() cast to a long, whole and positive or it throws.
    private long cellCount() {
        double c = report("LLM_cell_count").monitoredValue();
        long n = (long) c;
        if (Double.isNaN(c) || Double.isInfinite(c) || n <= 0 || (double) n != c) {
            throw new RuntimeException("manifest_writer: report 'LLM_cell_count' returned " + c
                + ", not a positive whole cell count.");
        }
        log("LLM_cell_count=" + n);
        return n;
    }

    // Base Size of mesh_cube over the global parameter D, both read in SI.
    private double baseSizeOverD() {
        BaseSize bs = meshCube().getDefaultValues().get(BaseSize.class);
        double base = bs.getSIValue();
        Object o = sim.getGlobalParameterManager().hasObject("D");
        if (o == null) {
            throw new RuntimeException("manifest_writer: missing global parameter 'D'.");
        }
        if (!(o instanceof ScalarGlobalParameter)) {
            throw new RuntimeException("manifest_writer: global parameter 'D' is not a ScalarGlobalParameter.");
        }
        double d = ((ScalarGlobalParameter) o).getQuantity().getSIValue();
        if (!(d > 0.0) || Double.isInfinite(d)) {
            throw new RuntimeException("manifest_writer: global parameter 'D' reads " + d + ", not a positive length.");
        }
        double ratio = base / d;
        log("mesh_cube base_size_over_D=" + num(ratio));
        return ratio;
    }

    // ---- planes
    private String planesBlock(Map<String, Object> stalenessTree, Map<String, Object> planeRender) {
        Object planesNode = stalenessTree.get("planes");
        if (planesNode == null) {
            throw new RuntimeException("manifest_writer: staleness.json has no 'planes' block.");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int p = 0; p < PLANES.length; p++) {
            String plane = PLANES[p];
            String tableName = "LLM_table_" + plane;
            String partName = "grid_" + plane;
            XyzInternalTable t = table(tableName);

            // Part: confirmed on the table's own part group (same call as run_macro.provisionTable).
            Object grid = derivedPart(partName);
            if (!(grid instanceof NamedObject)) {
                throw new RuntimeException("manifest_writer: derived part '" + partName + "' is not a NamedObject.");
            }
            boolean inTable;
            try {
                inTable = t.getParts().getPart(((NamedObject) grid).getPresentationName()) != null;
            } catch (RuntimeException e) {
                inTable = false;
            }
            if (!inTable) {
                throw new RuntimeException("manifest_writer: table '" + tableName + "' does not hold part '" + partName + "'.");
            }

            List<String> functions = tableFunctionNames(t, tableName);
            Set<String> expected = new HashSet<String>(Arrays.asList(CSV_COLUMNS));
            if (functions.size() != CSV_COLUMNS.length || !expected.equals(new HashSet<String>(functions))) {
                throw new RuntimeException("manifest_writer: table '" + tableName + "' holds field functions "
                    + functions + ", expected " + Arrays.asList(CSV_COLUMNS) + ".");
            }

            Map<String, Object> entry = findPlaneEntry(planesNode, plane);
            if (entry == null) {
                throw new RuntimeException("manifest_writer: staleness.json planes block has no entry for '" + plane + "'.");
            }
            long rowsWritten = requireLong(entry, "staleness.json planes." + plane, "rows_written");
            long rowsExpected = requireLong(entry, "staleness.json planes." + plane, "rows_expected");
            long missingInBody = requireLong(entry, "staleness.json planes." + plane, "missing_in_body");
            if (rowsExpected != PLANE_ROWS_EXPECTED[p]) {
                throw new RuntimeException("manifest_writer: staleness.json planes." + plane + ".rows_expected is "
                    + rowsExpected + ", declared " + PLANE_ROWS_EXPECTED[p] + ".");
            }

            String file = "planes/plane_" + plane + ".csv";
            String rawFile = "planes/plane_" + plane + "_raw.csv";
            File raw = inCapsule(rawFile);
            if (!raw.isFile()) {
                throw new RuntimeException("manifest_writer: missing '" + rawFile + "'.");
            }
            String shaRaw = sha256(raw);

            // Plane image: file and hash from disk, camera and colorbar as output_exporter read them.
            String pngFile = "planes/plane_" + plane + ".png";
            File png = inCapsule(pngFile);
            if (!png.isFile()) {
                throw new RuntimeException("manifest_writer: missing '" + pngFile + "'.");
            }
            Object renderFile = member(planeRender, PLANE_RENDER_JSON, "planes", plane, "file");
            if (!pngFile.equals(renderFile)) {
                throw new RuntimeException("manifest_writer: " + PLANE_RENDER_JSON + " planes." + plane + ".file is '"
                    + jsonScalarText(renderFile) + "', expected '" + pngFile + "'.");
            }
            String pngCamera = toJson(member(planeRender, PLANE_RENDER_JSON, "planes", plane, "camera"));
            requireLayoutScene(planeRender, PLANE_SCENE);
            Map<String, Object> pngColorbarMap = new LinkedHashMap<String, Object>(
                asObject(member(planeRender, PLANE_RENDER_JSON, "colorbar"), PLANE_RENDER_JSON + " colorbar"));
            pngColorbarMap.putAll(layoutColorbar(planeRender));
            String pngColorbar = toJson(pngColorbarMap);
            String pngTitle = toJson(member(planeRender, PLANE_RENDER_JSON, "layout", "title"));

            sb.append("    {\"file\": ").append(str(file)).append(", \"raw_file\": ").append(str(rawFile))
              .append(", \"table\": ").append(str(tableName)).append(", \"part\": ").append(str(partName)).append(",\n");
            sb.append("     \"grid_intervals\": [").append(GRID_INTERVALS[p][0]).append(", ").append(GRID_INTERVALS[p][1])
              .append("], \"rows_expected\": ").append(PLANE_ROWS_EXPECTED[p])
              .append(", \"rows_written\": ").append(rowsWritten)
              .append(", \"missing_in_body\": ").append(missingInBody).append(",\n");
            // The six columns of the final CSV: the raw seven without the plane's constant coordinate.
            sb.append("     \"columns\": [");
            boolean firstColumn = true;
            for (int k = 0; k < CSV_COLUMNS.length; k++) {
                if (k == PLANE_CONSTANT_COLUMN[p]) {
                    continue;
                }
                sb.append(firstColumn ? "" : ", ").append(str(CSV_COLUMNS[k]));
                firstColumn = false;
            }
            sb.append("],\n");
            // sha256 of the final CSV stays null here: build_capsule.py writes the file and the hash.
            sb.append("     \"sha256_raw\": ").append(str(shaRaw)).append(", \"sha256\": null,\n");
            sb.append("     \"png\": {\"file\": ").append(str(pngFile)).append(", \"scene\": ").append(str(PLANE_SCENE))
              .append(",\n");
            sb.append("             \"camera\": ").append(pngCamera).append(",\n");
            sb.append("             \"colorbar\": ").append(pngColorbar).append(",\n");
            sb.append("             \"title\": ").append(pngTitle).append(",\n");
            sb.append("             \"sha256\": ").append(str(sha256(png))).append("}}");
            sb.append(p == PLANES.length - 1 ? "\n" : ",\n");
            log("plane " + plane + " part=" + partName + " rows " + rowsWritten + "/" + rowsExpected
                + " missing_in_body " + missingInBody + " png=" + pngFile);
        }
        sb.append("  ]");
        return sb.toString();
    }

    // XyzInternalTable.getFieldFunctions(): getter of a verified setter (setFieldFunctions(List) in
    // run_macro.provisionTable, mode=prepare). Taken as Object: the collection type is not verified.
    private List<String> tableFunctionNames(XyzInternalTable t, String tableName) {
        Object o = t.getFieldFunctions(); // getter of a verified setter
        List<Object> items = elements(o, tableName + ".getFieldFunctions()");
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < items.size(); i++) {
            Object ff = items.get(i);
            if (isMissingFieldFunction(ff)) {
                throw new RuntimeException("manifest_writer: error: " + tableName
                    + " holds a missing field function; expected " + Arrays.asList(CSV_COLUMNS) + ".");
            }
            if (!(ff instanceof FieldFunction)) {
                throw new RuntimeException("manifest_writer: " + tableName + ".getFieldFunctions() holds a "
                    + className(ff) + ", not a FieldFunction.");
            }
            names.add(((FieldFunction) ff).getPresentationName());
        }
        return names;
    }

    // Looks for the object of one plane inside the planes block without assuming its nesting: a member
    // whose key is the plane name and whose value holds rows_written, or an object holding rows_written
    // and a "plane" or "name" member equal to the plane name. Two matches throw.
    private Map<String, Object> findPlaneEntry(Object node, String plane) {
        List<Map<String, Object>> hits = new ArrayList<Map<String, Object>>();
        collectPlaneEntries(node, plane, hits);
        if (hits.size() > 1) {
            throw new RuntimeException("manifest_writer: staleness.json planes block has " + hits.size()
                + " entries for '" + plane + "'.");
        }
        return hits.isEmpty() ? null : hits.get(0);
    }

    @SuppressWarnings("unchecked")
    private static void collectPlaneEntries(Object node, String plane, List<Map<String, Object>> hits) {
        if (node instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) node;
            if (m.containsKey("rows_written")
                && (plane.equals(m.get("plane")) || plane.equals(m.get("name")))) {
                hits.add(m);
                return;
            }
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Object v = e.getValue();
                if (e.getKey().equals(plane) && v instanceof Map && ((Map<String, Object>) v).containsKey("rows_written")) {
                    hits.add((Map<String, Object>) v);
                } else {
                    collectPlaneEntries(v, plane, hits);
                }
            }
        } else if (node instanceof List) {
            List<Object> l = (List<Object>) node;
            for (int i = 0; i < l.size(); i++) {
                collectPlaneEntries(l.get(i), plane, hits);
            }
        }
    }

    // ---- render layout of session B (plane_render.json layout): the values output_exporter applied
    // before every render and did not save. Session C opens the saved .sim, so a legend read here does
    // not carry them; they are copied into the colorbar and title blocks as applied.
    private static Map<String, Object> layoutColorbar(Map<String, Object> planeRender) {
        return asObject(member(planeRender, PLANE_RENDER_JSON, "layout", "colorbar"), PLANE_RENDER_JSON + " layout.colorbar");
    }

    @SuppressWarnings("unchecked")
    private static void requireLayoutScene(Map<String, Object> planeRender, String sceneName) {
        Object scenes = member(planeRender, PLANE_RENDER_JSON, "layout", "scenes");
        if (!(scenes instanceof List) || !((List<Object>) scenes).contains(sceneName)) {
            throw new RuntimeException("manifest_writer: " + PLANE_RENDER_JSON + " layout.scenes does not name '"
                + sceneName + "'; its layout was not applied by output_exporter.");
        }
    }

    // ---- views (spec v16, 8.1): one entry, the body view. Its scene is a transient object of session
    // B (decision 77) and does not exist here, so scene, displayer, part, camera, colour bar and print
    // call come from the views entry of plane_render.json; file size and hash from disk; the question is
    // the constant of 8.1.
    @SuppressWarnings("unchecked")
    private String viewsBlock(Map<String, Object> planeRender) {
        Object views = member(planeRender, PLANE_RENDER_JSON, "views");
        if (!(views instanceof List) || ((List<Object>) views).size() != 1) {
            throw new RuntimeException("manifest_writer: " + PLANE_RENDER_JSON + " views is not a list of one entry.");
        }
        Map<String, Object> view = asObject(((List<Object>) views).get(0), PLANE_RENDER_JSON + " views[0]");
        String where = PLANE_RENDER_JSON + " views[0]";
        requireEqual(view, where, "file", BODY_FILE);
        requireEqual(view, where, "scene", BODY_SCENE);
        requireEqual(view, where, "field", BODY_FIELD);
        requireEqual(view, where, "part", BODY_PART);
        requireEqual(view, where, "export", "call", BODY_EXPORT_CALL);
        requireLayoutScene(planeRender, BODY_SCENE);

        String camera = toJson(member(view, where, "camera"));
        Map<String, Object> colorbarMap = new LinkedHashMap<String, Object>(
            asObject(member(view, where, "colorbar"), where + " colorbar"));
        colorbarMap.putAll(layoutColorbar(planeRender));
        String colorbar = toJson(colorbarMap);
        String title = toJson(member(planeRender, PLANE_RENDER_JSON, "layout", "title"));

        File fin = inCapsule(BODY_FILE);
        if (!fin.isFile()) {
            throw new RuntimeException("manifest_writer: missing '" + BODY_FILE + "'.");
        }
        int[] size = pngSize(fin, BODY_FILE);
        if (size[0] != VIEW_WIDTH || size[1] != VIEW_HEIGHT) {
            log(BODY_FILE + " measures " + size[0] + "x" + size[1] + ", print call asked "
                + VIEW_WIDTH + "x" + VIEW_HEIGHT + "; written as read");
        }
        String sha = sha256(fin);

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        sb.append("    {\"file\": ").append(str(BODY_FILE)).append(",\n");
        sb.append("     \"question\": ").append(str(BODY_QUESTION)).append(",\n");
        sb.append("     \"scene\": ").append(str(BODY_SCENE)).append(", \"field\": ").append(str(BODY_FIELD))
          .append(", \"part\": ").append(str(BODY_PART)).append(",\n");
        sb.append("     \"camera\": ").append(camera).append(",\n");
        sb.append("     \"colorbar\": ").append(colorbar).append(",\n");
        sb.append("     \"title\": ").append(title).append(",\n");
        sb.append("     \"export\": {\"call\": ").append(str(BODY_EXPORT_CALL)).append(", \"size\": [").append(size[0])
          .append(", ").append(size[1]).append("]},\n");
        sb.append("     \"sha256\": ").append(str(sha)).append("}\n");
        sb.append("  ]");
        log("view " + BODY_FILE + " size " + size[0] + "x" + size[1]);
        return sb.toString();
    }

    // A string member of plane_render.json that must hold the value this file declares.
    private static void requireEqual(Map<String, Object> node, String where, String key, String expected) {
        Object v = member(node, where, key);
        if (!expected.equals(v)) {
            throw new RuntimeException("manifest_writer: " + where + "." + key + " is '" + jsonScalarText(v)
                + "', expected '" + expected + "'.");
        }
    }

    private static void requireEqual(Map<String, Object> node, String where, String key, String sub, String expected) {
        Object v = member(node, where, key, sub);
        if (!expected.equals(v)) {
            throw new RuntimeException("manifest_writer: " + where + "." + key + "." + sub + " is '" + jsonScalarText(v)
                + "', expected '" + expected + "'.");
        }
    }

    // ---- conversions of values read as Object (fail loudly on an unexpected type)
    private static List<Object> elements(Object o, String what) {
        List<Object> out = new ArrayList<Object>();
        if (o instanceof Collection) {
            out.addAll((Collection<?>) o);
        } else if (o instanceof Object[]) {
            out.addAll(Arrays.asList((Object[]) o));
        } else if (o instanceof double[]) {
            double[] a = (double[]) o;
            for (int i = 0; i < a.length; i++) {
                out.add(Double.valueOf(a[i]));
            }
        } else {
            throw new RuntimeException("manifest_writer: " + what + " returned " + className(o)
                + "; no conversion for that type.");
        }
        return out;
    }

    private static String className(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }

    // ---- PNG size, measured with ImageIO
    private int[] pngSize(File f, String relName) {
        BufferedImage img;
        try {
            img = ImageIO.read(f);
        } catch (Exception e) {
            img = null;
        }
        if (img == null) {
            throw new RuntimeException("manifest_writer: unreadable PNG '" + relName + "'.");
        }
        return new int[] { img.getWidth(), img.getHeight() };
    }

    // ================================================================= steps 4-5: sweep and chain
    private String sha256OrNull(File f, String name) {
        if (!f.isFile()) {
            log(name + " absent from the session directory, sha256 null");
            return null;
        }
        String h = sha256(f);
        log(name + " sha256 " + h);
        return h;
    }

    private void appendSweep(StringBuilder sb, String simName, String simSha, String templateSha) {
        sb.append("  \"sweep\": {\n");
        sb.append("    \"parameter\": \"Re\",\n");
        // values and siblings come from sweep.json "points", in its order.
        sb.append("    \"values\": [");
        for (int i = 0; i < sweepValues.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(sweepValues[i]);
        }
        sb.append("],\n");
        sb.append("    \"point\": ").append(reTarget).append(",\n");
        sb.append("    \"siblings\": [");
        boolean first = true;
        for (int i = 0; i < sweepValues.length; i++) {
            if (sweepValues[i] == reTarget) {
                continue;
            }
            sb.append(first ? "" : ", ").append(str("capsule_cube_re" + four(sweepValues[i])));
            first = false;
        }
        sb.append("],\n");
        sb.append("    \"baseline\": ").append(str("capsule_cube_re" + four(sweepBaseline))).append(",\n");
        sb.append("    \"template\": {\"file\": \"cube_sweep_template.sim\", \"sha256\": ").append(str(templateSha)).append("},\n");
        sb.append("    \"sim\": {\"file\": ").append(str(simName)).append(", \"sha256\": ").append(str(simSha)).append("}\n");
        sb.append("  },\n");
    }

    // Every chain file as {file, path, sha256}; sha256 is null when the file is absent from the
    // session directory. repo.commit stays null here: build_capsule.py fills it from --repo-commit.
    private void appendChain(StringBuilder sb, String[] chainSha) {
        sb.append("  \"chain\": {\n");
        for (int i = 0; i < CHAIN_KEYS.length; i++) {
            sb.append("    ").append(str(CHAIN_KEYS[i])).append(": {\"file\": ").append(str(CHAIN_FILES[i]))
              .append(", \"path\": ").append(str(CHAIN_PATH_PREFIX + CHAIN_FILES[i]))
              .append(", \"sha256\": ").append(str(chainSha[i])).append("},\n");
        }
        sb.append("    \"repo\": {\"url\": \"https://github.com/pacoEzq/simulation-capsule\", \"commit\": null}\n");
        sb.append("  },\n");
    }

    // Version and build come from setup.txt, never from the API. np is null in this version.
    private void appendSession(StringBuilder sb, SetupFields s) {
        sb.append("  \"session\": {\n");
        sb.append("    \"solver\": \"Simcenter STAR-CCM+\",\n");
        sb.append("    \"version\": ").append(str(s.version)).append(",\n");
        sb.append("    \"build\": ").append(str(s.build)).append(",\n");
        sb.append("    \"platform\": ").append(str(s.platform)).append(",\n");
        sb.append("    \"precision\": ").append(str(s.precision)).append(",\n");
        sb.append("    \"javac\": \"25.0.1\",\n");
        sb.append("    \"np\": null\n");
        sb.append("  },\n");
    }

    // ================================================================= files of the point directory
    private File inCapsule(String relPath) {
        return new File(pointDir, relPath.replace('/', File.separatorChar));
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

    // JSON text of a value read by JsonReader, compact, key order kept: used to copy the camera and
    // colorbar of plane_render.json into manifest.json as they were written.
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

    // The whole text goes to a side file first and is then moved over manifest.json, so a failed write
    // never leaves a partial manifest.json; the side file is removed on failure.
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
