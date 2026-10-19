// output_exporter.java
// Capsule Extractor adapter, first of three (output_exporter, object_audit, manifest_writer). Simcenter STAR-CCM+ 2606.
// Runs on an already solved .sim and exports, under work/<card.body.alias>/, the raw plane tables of
// card.stations, the views of card.views, one plane image per station, render.json, a copy of
// card.json, setup.txt and the freshness record, and replaces the environment_placeholder of
// summary.json with the environment block read from setup.txt. card.json is read from the session
// directory; every station, view, part, range, colormap and title comes from it. It creates,
// renames and deletes no simulation object, with one exception: a view of card.views whose scene
// view_<s> the .sim does not have gets it created in memory, as t7/output_exporter.java did (H4), and
// so does the plane scene LLM_plane_<field> of a station image (H6). It
// never saves the simulation, so nothing created outlives the session. Every other object it touches is
// resolved by name and a missing one throws with its name; the stations (planes, tables) must exist.
// Changing the parts of a displayer, a camera or an annotation text is not creating. The capsule itself is assembled later, outside the session,
// by tools/extract_capsule.py.
// H6: the CSVs carry no comment line (the assembler writes the formation header from card.json). Each
// station's image shows stations[].image_field, else stations[].fields[0], in scene LLM_plane_<field>
// (displayers LLM_disp_plane_<field> and LLM_body_plane_<field>, annotation LLM_title_plane_<field>),
// created in memory like a view scene when the .sim lacks it. render.json holds the field and the
// scene inside planes[<stem>].
// H5: the cameras, origins and spacings of the stations come from card.json too; a view part is any
// boundary of the fluid region, Derived Part or region named in the card. When the case macro has
// written summary.json in the session directory, the exporter writes no scalar of its own: it checks
// that every card.references[].key is in that file and copies it to the point directory with the
// environment block added. Without it, summary.json of work/<alias>/ is used as before.
//
// Signatures come from run_macro.java and manifest_writer.java (same repository, compiled against
// 2606), from TrimReportForAI.java of the public simulation-capsule repository (plain Java only), or
// from the calls recorded in the 2606 GUI on 29.09 (displayer parts, legend lookup table) and on 30.09
// (title font and height, legend width, position and shadow). Anything else is marked
// TODO(unverified signature) with the reason.

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import star.base.neo.DoubleVector;
import star.base.neo.NamedObject;
import star.base.report.Report;
import star.common.FieldFunction;
import star.common.Region;
import star.common.ScalarGlobalParameter;
import star.common.Simulation;
import star.common.SimulationSummaryReporter;
import star.common.StarMacro;
import star.common.XyzInternalTable;
import star.vis.AnnotationProp;
import star.vis.AutoRangeMode; // TODO(unverified signature): same import as run_macro.java
import star.vis.ClipMode;
import star.vis.Legend;
import star.vis.LookupTableManager;
import star.vis.ParallelScale;
import star.vis.PartDisplayer;
import star.vis.LookupTable;
import star.vis.ScalarDisplayer;
import star.vis.ScalarFillMode;
import star.vis.Scene;
import star.vis.SimpleAnnotation;
import star.vis.SimpleAnnotationProp;
import star.vis.VisProjectionMode; // TODO(unverified signature): same import as run_macro.java
// TODO(unverified signature): package of the next one, imported in t7/output_exporter.java; star.vis is
// the inferred one. If javac rejects it, the class is in star.common or star.base.neo.
import star.vis.DisplayerVisibilityOverride;

public class output_exporter extends StarMacro {

    // ---------------------------------------------------------------- constants
    // card.json is looked up like run_macro.java looked up its properties file: session directory
    // first, then the directory of the .sim. The point directory is work/<card.body.alias>/.
    private static final String CARD_NAME = "card.json";
    private static final String LOG_PREFIX = "exporter: ";
    private static final String WORK_DIR = "work";
    private static final Object JSON_NULL = new Object();

    // Coordinate columns of every raw plane CSV, before the fields of its station. Since v12 the
    // tables hold field functions under these same names, so the header is taken as exported.
    private static final String[] COORD_COLUMNS = { "x_D", "y_D", "z_D" };
    // Coordinates closer than this (in D) are the same lattice value; also the slack on the body test.
    private static final double COORD_TOL = 1.0e-6;

    // Views and plane images: resolution goes in the print call; the file is kept as printed, no crop.
    private static final int VIEW_MAGNIFICATION = 1;
    private static final int VIEW_WIDTH = 1024;
    private static final int VIEW_HEIGHT = 739;

    // A summary report older than the start of this macro (minus file-time granularity) is stale.
    private static final long FRESHNESS_SLACK_MS = 2000L;

    // Plane images (contract v10): one scene, pointed at section_<stem> and rendered once per station.
    // One scene per image field, named after the field function (v12: label and function name are the same).
    private static final String PLANE_SCENE = "LLM_plane_";
    private static final String PLANE_DISP = "LLM_disp_plane_";
    private static final String PLANE_BODY = "LLM_body_plane_";
    private static final String PLANE_TITLE = "LLM_title_plane_";
    // Every camera (card.stations[].camera, card.views[].camera) is in the lab frame in metres with
    // D = 1 m, checked in requireUnitD.

    // summary.json: written by the case macro in the session directory (H5) or, before, by run mode in
    // the point directory.
    private static final String SUMMARY_NAME = "summary.json";

    // Scene created for a view whose scene the .sim lacks (as t7/output_exporter.java, spec 3.1,
    // signature 12): createScalarScene gives the displayers "Scalar 1" and "Outline 1"; the first is
    // renamed disp_<s>, the second is hidden and keeps its name.
    private static final String NEW_SCALAR = "Scalar 1";
    private static final String NEW_OUTLINE = "Outline 1";
    // Top band of the titles, as run_macro.java sets it on the plane title; written on a created title only.
    private static final double[] TITLE_POSITION = { 0.023, 0.916, 0.0 };

    // Render layout (v12), set on the views and on the plane scene before every printAndWait and
    // never saved. Title: font on the annotation, height on the scene's prop of it; the position and
    // the shadow of the title stay as prepare sets them (a created view title gets them in createViewScene). Legend: width, position and no shadow, and
    // the label format of run_macro.java; 6 labels and the ranges stay as prepare sets them.
    private static final String TITLE_FONT = "Siemens Sans Global-Plain";
    private static final double TITLE_HEIGHT = 0.04;
    private static final double LEGEND_WIDTH = 0.3;
    private static final double[] LEGEND_POSITION = { 0.6, 0.08 };
    private static final boolean LEGEND_SHADOW = false;
    private static final String COLOR_BAR_LABEL_FORMAT = "%.2f";

    // environment of summary.json (SPEC 0.4, 4.2.1): its five keys, the Version: line field each one
    // is copied from (null: solver, which is not on that line), the product name SPEC 4.2.1 fixes for
    // solver, and the placeholder run_macro.java writes.
    private static final String[] ENVIRONMENT_KEYS = { "solver", "version", "build", "platform", "precision" };
    private static final String[] ENVIRONMENT_FIELDS = { null, "PresentationVersion", "ReleaseNumber", "BuildArch", "BuildEnv" };
    private static final String ENVIRONMENT_SOLVER = "Simcenter STAR-CCM+";
    private static final String ENVIRONMENT_PLACEHOLDER = "environment_placeholder";

    // ---------------------------------------------------------------- TrimReportForAI configuration
    // Ported from TrimReportForAI.java (simulation-capsule, macros/): top-level sections kept, and
    // property keys stripped even from kept sections.
    private static final Set<String> KEEP_SECTIONS = new HashSet<String>(Arrays.asList(
        "Continua",
        "Regions",
        "Representations",
        "Operations",
        "Derived Parts",
        "Monitors",
        "Reports",
        "Solvers",
        "Stopping Criteria",
        "Automation",
        "Motions",
        "Reference Frames"
    ));

    private static final Set<String> SKIP_PROPS = new HashSet<String>(Arrays.asList(
        "Tags", "Error Message",
        "Color", "Diffuse Color", "Specular Color", "Specular Tint",
        "Specular", "Absorption", "Reflection", "Refraction",
        "Index of Refraction", "Reverse Surface Orientation",
        "Roughness", "Transmission Weight", "Transmission Roughness",
        "Subsurface Scattering Distance", "Sheen Tint", "Sheen",
        "Metallic", "Emission Intensity", "Emission Color",
        "Clearcoat IOR", "Clear Coat Weight", "Clear Coat Roughness",
        "Attenuation Distance", "Attenuation Color", "Solid Volume",
        "Set Tangency at all Edges", "Opacity",
        "Override Upstream Color", "IsAssignedColor", "Display Resolution",
        "Maximum Plot Samples",
        "Synchronize Function Name", "Inverse Distance Weight"
    ));

    // Header keys whose value names a machine or a person. Not in TrimReportForAI: added because the
    // macro may not write user names or external identifiers.
    private static final Set<String> REDACT_KEYS = new HashSet<String>(Arrays.asList(
        "Hosts", "Host", "Hostname", "User", "User Name"
    ));

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private long startMillis;
    private File sessionBase;
    private File cardFile;
    private File pointDir;
    private File runLog;
    private String nnnn;
    private long reTarget;

    // From card.json.
    private String alias;
    private String groupSymbol;
    private String fluidRegion;
    private String colormap;
    private Double halfWidth; // card.body.half_width; null: no missing node is excused
    private String formationFields; // card.body.formation.fields, in the titles of the images
    private final List<String> bodyBoundaries = new ArrayList<String>(); // card.body.body_boundaries.names
    private final Map<String, Boolean> planeCreate = new LinkedHashMap<String, Boolean>(); // image field -> scene absent
    private final List<String> referenceKeys = new ArrayList<String>(); // card.references[].key
    private final List<Station> stations = new ArrayList<Station>();
    private final List<View> views = new ArrayList<View>();

    // summary.json of the case macro in the session directory; null: run mode's, in the point directory.
    private File caseSummary;

    // From summary.json of run mode, for the titles (not read from the case macro's).
    private String status;
    private long iterations;

    // One entry of card.stations. axes: the two in-plane coordinate columns, then the constant one;
    // grid and origin follow the order of the two in-plane ones.
    private static final class Station {
        String stem;
        String imageField; // stations[].image_field, else fields[0]
        double value;
        int[] grid;
        double[] origin;
        double spacing;
        int rowsExpected;
        int[] axes;
        String[] columns;
        int rowsWritten;
        int missingInBody;
        Camera camera;
    }

    // One entry of card.views. Scene, displayer and title are named after the file: view_<s>.png is
    // shown by scene view_<s>, displayer disp_<s> and annotation title_<s>.
    private static final class View {
        String file;
        String scene;
        String displayer;
        String title;
        String field;
        String parts; // as render.json writes it: the card's text, or its array joined by ", "
        String[] partNames;
        double[] range;
        Camera camera;
        boolean create; // scene view_<s> absent from the .sim: createViewScene builds it before the print
    }

    // A camera of the card; projection, when given, must be 1 (parallel).
    private static final class Camera {
        double[] focal;
        double[] position;
        double[] viewUp;
        double scale;
    }

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();
        startMillis = System.currentTimeMillis();
        try {
            readCard(loadCard());
            nnnn = four(reTarget);
            resolvePointDir();
            log("start card=" + CARD_NAME + " alias=" + alias + " " + groupSymbol + "=" + reTarget
                + " point=" + rel(pointDir) + " summary=" + (caseSummary != null ? "case macro" : "run mode"));

            checkObjects();
            readSummary();
            exportPlanes();
            String viewsJson = exportViews();
            exportPlaneImages(viewsJson);
            copyCard();
            File setup = writeSetupTxt();
            checkStaleness(setup);
            writeEnvironment(setup);

            log("done");
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= card.json / point directory
    // Same lookup order as run_macro.loadProperties: session directory first, then the directory
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
            throw new RuntimeException("output_exporter: could not locate '" + CARD_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }
        cardFile = candidate;
        String text = new String(readBytes(candidate), StandardCharsets.UTF_8);
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return asObject(parseJson(text, CARD_NAME), CARD_NAME + " root");
    }

    // card.body, card.stations and card.views into the session state; anything missing or of the
    // wrong type throws with its dotted path.
    private void readCard(Map<String, Object> card) {
        long version = requireLong(card, CARD_NAME, "card_version");
        if (version != 0L) {
            throw new RuntimeException("output_exporter: " + CARD_NAME + " card_version is " + version + ", expected 0.");
        }
        alias = requireString(card, CARD_NAME, "body", "alias");
        if (alias.length() == 0 || alias.indexOf('/') >= 0 || alias.indexOf('\\') >= 0 || alias.startsWith(".")) {
            throw new RuntimeException("output_exporter: " + CARD_NAME + " body.alias '" + alias + "' is not a directory name.");
        }
        groupSymbol = requireString(card, CARD_NAME, "body", "group", "symbol");
        reTarget = requireLong(card, CARD_NAME, "body", "group", "value");
        fluidRegion = requireString(card, CARD_NAME, "body", "fluid_region", "name");
        colormap = requireString(card, CARD_NAME, "body", "colormap");
        Map<String, Object> body = asObject(member(card, CARD_NAME, "body"), CARD_NAME + " body");
        // half_width is a number or, like the other entries of body, an object {"value": <number>,
        // "source": ...}; null, absent or a null value means no missing node is excused.
        Object hw = body.get("half_width");
        if (hw instanceof Map) {
            Object hv = ((Map<?, ?>) hw).get("value");
            halfWidth = hv == null || hv == JSON_NULL ? null
                : Double.valueOf(requireDouble(card, CARD_NAME, "body", "half_width", "value"));
        } else if (hw == null || hw == JSON_NULL) {
            halfWidth = null;
        } else {
            halfWidth = Double.valueOf(requireDouble(card, CARD_NAME, "body", "half_width"));
        }
        // Goes into the title of every image, so a line break cannot be copied.
        formationFields = requireString(card, CARD_NAME, "body", "formation", "fields");
        if (formationFields.indexOf('\n') >= 0 || formationFields.indexOf('\r') >= 0) {
            throw new RuntimeException("output_exporter: " + CARD_NAME + " body.formation.fields holds a line break.");
        }
        // The body outline of a plane scene created in memory shows these boundaries; optional otherwise.
        Object bb = body.get("body_boundaries");
        if (bb instanceof Map && ((Map<?, ?>) bb).get("names") instanceof List) {
            List<?> names = (List<?>) ((Map<?, ?>) bb).get("names");
            for (int k = 0; k < names.size(); k++) {
                if (!(names.get(k) instanceof String)) {
                    throw new RuntimeException("output_exporter: " + CARD_NAME + " body.body_boundaries.names[" + k
                        + "] is not a string.");
                }
                bodyBoundaries.add((String) names.get(k));
            }
        }

        List<Object> refs = requireList(card, CARD_NAME, "references");
        for (int i = 0; i < refs.size(); i++) {
            String where = CARD_NAME + " references[" + i + "]";
            referenceKeys.add(requireString(asObject(refs.get(i), where), where, "key"));
        }

        List<Object> st = requireList(card, CARD_NAME, "stations");
        if (st.isEmpty()) {
            throw new RuntimeException("output_exporter: " + CARD_NAME + " stations is empty.");
        }
        Set<String> stems = new HashSet<String>();
        for (int i = 0; i < st.size(); i++) {
            String where = "stations[" + i + "]";
            Map<String, Object> m = asObject(st.get(i), CARD_NAME + " " + where);
            Station s = new Station();
            s.stem = requireString(m, CARD_NAME + " " + where, "stem");
            if (!stems.add(s.stem)) {
                throw new RuntimeException("output_exporter: " + CARD_NAME + " has two stations '" + s.stem + "'.");
            }
            String axis = requireString(m, CARD_NAME + " " + where, "axis");
            int c = "x".equals(axis) ? 0 : "y".equals(axis) ? 1 : "z".equals(axis) ? 2 : -1;
            if (c < 0) {
                throw new RuntimeException("output_exporter: station '" + s.stem + "': axis '" + axis + "' is not x, y or z.");
            }
            s.axes = c == 0 ? new int[] { 1, 2, 0 } : c == 1 ? new int[] { 0, 2, 1 } : new int[] { 0, 1, 2 };
            s.value = requireDouble(m, CARD_NAME + " " + where, "value");
            List<Object> grid = requireList(m, CARD_NAME + " " + where, "grid");
            if (grid.size() != 2) {
                throw new RuntimeException("output_exporter: station '" + s.stem + "': grid has " + grid.size()
                    + " entries, expected 2.");
            }
            long g0 = integerOf(grid.get(0), "station '" + s.stem + "' grid[0]");
            long g1 = integerOf(grid.get(1), "station '" + s.stem + "' grid[1]");
            if (g0 < 2 || g1 < 2 || g0 * g1 > Integer.MAX_VALUE) {
                throw new RuntimeException("output_exporter: station '" + s.stem + "': grid [" + g0 + ", " + g1
                    + "] is not a lattice.");
            }
            s.grid = new int[] { (int) g0, (int) g1 };
            s.rowsExpected = (int) (g0 * g1);
            s.origin = doubles(requireList(m, CARD_NAME + " " + where, "origin"), 2, "station '" + s.stem + "' origin");
            s.spacing = requireDouble(m, CARD_NAME + " " + where, "spacing");
            if (!(s.spacing > 0.0) || Double.isInfinite(s.spacing)) {
                throw new RuntimeException("output_exporter: station '" + s.stem + "': spacing " + s.spacing + " is not positive.");
            }
            s.camera = readCamera(m, CARD_NAME + " " + where, "station '" + s.stem + "'");
            List<Object> fields = requireList(m, CARD_NAME + " " + where, "fields");
            s.columns = new String[COORD_COLUMNS.length + fields.size()];
            for (int k = 0; k < COORD_COLUMNS.length; k++) {
                s.columns[k] = COORD_COLUMNS[k];
            }
            for (int k = 0; k < fields.size(); k++) {
                if (!(fields.get(k) instanceof String)) {
                    throw new RuntimeException("output_exporter: station '" + s.stem + "': fields[" + k + "] is not a string.");
                }
                s.columns[COORD_COLUMNS.length + k] = (String) fields.get(k);
            }
            Object imf = m.get("image_field");
            if (imf == null || imf == JSON_NULL) {
                if (fields.isEmpty()) {
                    throw new RuntimeException("output_exporter: station '" + s.stem + "': no image_field and no fields.");
                }
                s.imageField = (String) fields.get(0);
            } else if (imf instanceof String && fields.contains(imf)) {
                s.imageField = (String) imf;
            } else {
                throw new RuntimeException("output_exporter: station '" + s.stem + "': image_field " + imf
                    + " is not one of its fields.");
            }
            stations.add(s);
        }

        List<Object> vw = requireList(card, CARD_NAME, "views");
        for (int i = 0; i < vw.size(); i++) {
            String where = CARD_NAME + " views[" + i + "]";
            Map<String, Object> m = asObject(vw.get(i), where);
            View v = new View();
            v.file = requireString(m, where, "file");
            if (!v.file.startsWith("view_") || !v.file.endsWith(".png") || v.file.indexOf('/') >= 0
                || v.file.indexOf('\\') >= 0 || v.file.length() <= "view_.png".length()) {
                throw new RuntimeException("output_exporter: " + where + " file '" + v.file + "' is not view_<name>.png.");
            }
            String s = v.file.substring("view_".length(), v.file.length() - ".png".length());
            v.scene = "view_" + s;
            v.displayer = "disp_" + s;
            v.title = "title_" + s;
            v.field = requireString(m, where, "field");
            // parts: one text of names separated by commas, or an array of names.
            Object parts = member(m, where, "parts");
            String[] names;
            if (parts instanceof String) {
                v.parts = (String) parts;
                names = v.parts.split(",");
            } else if (parts instanceof List) {
                List<?> l = (List<?>) parts;
                names = new String[l.size()];
                for (int k = 0; k < names.length; k++) {
                    if (!(l.get(k) instanceof String)) {
                        throw new RuntimeException("output_exporter: view '" + v.file + "': parts[" + k + "] is not a string.");
                    }
                    names[k] = (String) l.get(k);
                }
                v.parts = String.join(", ", names);
            } else {
                throw new RuntimeException("output_exporter: view '" + v.file + "': parts is neither a string nor an array.");
            }
            if (names.length == 0) {
                throw new RuntimeException("output_exporter: view '" + v.file + "': parts is empty.");
            }
            for (int k = 0; k < names.length; k++) {
                names[k] = names[k].trim();
                if (names[k].length() == 0) {
                    throw new RuntimeException("output_exporter: view '" + v.file + "': parts '" + v.parts + "' has an empty name.");
                }
            }
            v.partNames = names;
            v.range = doubles(requireList(m, where, "range"), 2, "view '" + v.file + "' range");
            v.camera = readCamera(m, where, "view '" + v.file + "'");
            views.add(v);
        }
    }

    // camera of a station or a view: focal_point, position, view_up, parallel_scale and, if given,
    // projection, which must be 1: only setProjectionMode(PARALLEL) is in the macros of this chain.
    private static Camera readCamera(Map<String, Object> m, String where, String label) {
        Camera c = new Camera();
        c.focal = doubles(requireList(m, where, "camera", "focal_point"), 3, label + " camera.focal_point");
        c.position = doubles(requireList(m, where, "camera", "position"), 3, label + " camera.position");
        c.viewUp = doubles(requireList(m, where, "camera", "view_up"), 3, label + " camera.view_up");
        c.scale = requireDouble(m, where, "camera", "parallel_scale");
        if (asObject(member(m, where, "camera"), where + " camera").containsKey("projection")) {
            long projection = requireLong(m, where, "camera", "projection");
            if (projection != 1L) {
                throw new RuntimeException("output_exporter: " + label + ": camera.projection " + projection
                    + " is not 1 (parallel).");
            }
        }
        return c;
    }

    // summary.json numbers the titles carry, read once; sweep.value must be card.body.group.value.
    // The case macro's summary.json is only checked: every card.references[].key must be a key of it,
    // at any depth; the first one missing stops the macro with its name.
    private void readSummary() {
        if (caseSummary != null) {
            Object root = parseJson(readText(caseSummary), SUMMARY_NAME);
            asObject(root, SUMMARY_NAME + " root");
            for (int i = 0; i < referenceKeys.size(); i++) {
                if (!hasKey(root, referenceKeys.get(i))) {
                    throw new RuntimeException("output_exporter: card.references key '" + referenceKeys.get(i)
                        + "' is not in " + SUMMARY_NAME + " of the case macro.");
                }
            }
            log(SUMMARY_NAME + " of the case macro holds the " + referenceKeys.size() + " card.references keys");
            return;
        }
        String json = readText(new File(pointDir, SUMMARY_NAME));
        status = jsonString(json, "convergence", "status");
        iterations = jsonLong(json, "convergence", "iterations");
        long value = jsonLong(json, "sweep", "value");
        if (value != reTarget) {
            throw new RuntimeException("output_exporter: summary.json sweep.value is " + value
                + " but card.body.group.value is " + reTarget + ".");
        }
    }

    // A key of an object anywhere in the parsed document.
    private static boolean hasKey(Object node, String key) {
        if (node instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) node;
            if (m.containsKey(key)) {
                return true;
            }
            for (Object v : m.values()) {
                if (hasKey(v, key)) {
                    return true;
                }
            }
        } else if (node instanceof List) {
            for (Object v : (List<?>) node) {
                if (hasKey(v, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    // card.json as read goes into the point directory, byte for byte.
    private void copyCard() {
        File out = new File(pointDir, CARD_NAME);
        try {
            Files.copy(cardFile.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("output_exporter: could not copy '" + CARD_NAME + "' to '" + rel(out) + "'.");
        }
        log(rel(out) + " copied bytes=" + out.length());
    }

    // Same base as run_macro.sessionRelativeDir. With summary.json of the case macro in the session
    // directory, the point directory and its run_log.txt are created if missing. Without it, the point
    // directory is not created here: run mode must already have written it. Only planes/ and views/
    // are created, inside it.
    private void resolvePointDir() {
        String sessionDir = sim.getSessionDir();
        if (sessionDir != null && sessionDir.length() > 0) {
            sessionBase = new File(sessionDir);
        } else {
            sessionBase = sim.getSessionDirFile();
        }
        String relative = WORK_DIR + File.separator + alias;
        File dir = new File(sessionBase, relative);
        File fromCase = new File(sessionBase, SUMMARY_NAME);
        if (fromCase.isFile()) {
            caseSummary = fromCase;
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new RuntimeException("output_exporter: could not create directory '" + relative + "'.");
            }
            pointDir = dir;
            runLog = new File(dir, "run_log.txt"); // appendLine creates it
            return;
        }
        if (!dir.isDirectory()) {
            throw new RuntimeException("output_exporter: missing point directory '" + relative + "'.");
        }
        if (!new File(dir, "summary.json").isFile()) {
            throw new RuntimeException("output_exporter: missing '" + relative + File.separator + "summary.json'.");
        }
        File log = new File(dir, "run_log.txt");
        if (!log.isFile()) {
            throw new RuntimeException("output_exporter: missing '" + relative + File.separator + "run_log.txt'.");
        }
        pointDir = dir;
        runLog = log;
    }

    private File subDir(File parent, String name) {
        File d = new File(parent, name);
        if (!d.isDirectory() && !d.mkdirs()) {
            throw new RuntimeException("output_exporter: could not create directory '" + rel(d) + "'.");
        }
        return d;
    }

    // ================================================================= lookups (fail loudly)
    // Same calls as run_macro.java. RegionManager.getObject: try/catch covers a throw or a null return.
    private Region region(String name) {
        Object o;
        try {
            o = sim.getRegionManager().getObject(name);
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            throw new RuntimeException("output_exporter: missing region '" + name + "'.");
        }
        if (!(o instanceof Region)) {
            throw new RuntimeException("output_exporter: object '" + name + "' is not a Region.");
        }
        return (Region) o;
    }

    private FieldFunction fieldFunction(String name) {
        Object o = sim.getFieldFunctionManager().hasObject(name);
        if (isMissingFieldFunction(o)) {
            throw new RuntimeException("output_exporter: error: missing field function '" + name + "'.");
        }
        if (!(o instanceof FieldFunction)) {
            throw new RuntimeException("output_exporter: object '" + name + "' is not a FieldFunction.");
        }
        return (FieldFunction) o;
    }

    // Measured on 2606 (30.09): FieldFunctionManager.getFunction(name) for a name that does not exist
    // returns an object of class NullFieldFunction, neither null nor an exception. That class is matched
    // by its simple name, not imported; every field function this file reads goes through this test.
    private static boolean isMissingFieldFunction(Object ff) {
        return ff == null || ff.getClass().getSimpleName().equals("NullFieldFunction");
    }

    private Object derivedPart(String name) {
        Object p = sim.getPartManager().hasObject(name);
        if (p == null) {
            throw new RuntimeException("output_exporter: missing derived part '" + name + "'.");
        }
        return p;
    }

    // A part of card.views[].parts by its name: a boundary of card.body.fluid_region, else a Derived
    // Part, else a region, first found in that order. None of them stops the macro with the name.
    private NamedObject viewPart(Region fluid, String name) {
        Object o;
        try {
            o = fluid.getBoundaryManager().getObject(name);
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            o = sim.getPartManager().hasObject(name);
        }
        if (o == null) {
            try {
                o = sim.getRegionManager().getObject(name);
            } catch (RuntimeException e) {
                o = null;
            }
        }
        if (o == null) {
            throw new RuntimeException("output_exporter: missing part '" + name + "' (no boundary of region '"
                + fluidRegion + "', Derived Part or region by that name).");
        }
        if (!(o instanceof NamedObject)) {
            throw new RuntimeException("output_exporter: part '" + name + "' is not a NamedObject.");
        }
        return (NamedObject) o;
    }

    private Report report(String name) {
        Report r = sim.getReportManager().getReport(name);
        if (r == null) {
            throw new RuntimeException("output_exporter: missing report '" + name + "'.");
        }
        return r;
    }

    private XyzInternalTable table(String name) {
        Object o = sim.getTableManager().hasTable(name);
        if (o == null) {
            throw new RuntimeException("output_exporter: missing table '" + name + "'.");
        }
        if (!(o instanceof XyzInternalTable)) {
            throw new RuntimeException("output_exporter: table '" + name + "' is not an XyzInternalTable.");
        }
        return (XyzInternalTable) o;
    }

    private Scene scene(String name) {
        Scene sc = sim.getSceneManager().hasScene(name);
        if (sc == null) {
            throw new RuntimeException("output_exporter: missing scene '" + name + "'.");
        }
        return sc;
    }

    // Every object the exporter touches, before anything is written. A station or a view whose object
    // the .sim does not have stops the macro with the station or the view named.
    private void checkObjects() {
        Region fluid = region(fluidRegion);
        for (int i = 0; i < COORD_COLUMNS.length; i++) {
            fieldFunction(COORD_COLUMNS[i]);
        }
        if (caseSummary == null) {
            // Read by the freshness check of run mode only.
            report("cd");
            report("LLM_cell_count");
        }
        for (int i = 0; i < stations.size(); i++) {
            Station st = stations.get(i);
            try {
                derivedPart("grid_" + st.stem);
                derivedPart("section_" + st.stem);
                table("LLM_table_" + st.stem);
                for (int k = COORD_COLUMNS.length; k < st.columns.length; k++) {
                    fieldFunction(st.columns[k]);
                }
            } catch (RuntimeException e) {
                throw named("station", st.stem, e);
            }
        }
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            try {
                v.create = sim.getSceneManager().hasScene(v.scene) == null;
                if (v.create) {
                    // Nothing of the view may exist yet: a second title annotation would take the same name.
                    if (sim.getAnnotationManager().hasObject(v.title) != null) {
                        throw new RuntimeException("output_exporter: scene '" + v.scene + "' is absent but annotation '"
                            + v.title + "' exists.");
                    }
                    log("view '" + v.file + "': scene '" + v.scene + "' absent, created before the print");
                } else {
                    Scene sc = scene(v.scene);
                    ScalarDisplayer sd = scalarDisplayer(sc, v.scene, v.displayer);
                    requireShownField(sd, v.displayer, v.field);
                    simpleAnnotation(v.title);
                }
                fieldFunction(v.field);
                for (int k = 0; k < v.partNames.length; k++) {
                    viewPart(fluid, v.partNames[k]);
                }
            } catch (RuntimeException e) {
                throw named("view", v.file, e);
            }
        }
        for (int i = 0; i < stations.size(); i++) {
            String f = stations.get(i).imageField;
            if (planeCreate.containsKey(f)) {
                continue;
            }
            try {
                fieldFunction(f);
                boolean create = sim.getSceneManager().hasScene(PLANE_SCENE + f) == null;
                planeCreate.put(f, Boolean.valueOf(create));
                if (create) {
                    // Nothing of the plane scene may exist yet: a second title annotation would take the same name.
                    if (sim.getAnnotationManager().hasObject(PLANE_TITLE + f) != null) {
                        throw new RuntimeException("output_exporter: scene '" + PLANE_SCENE + f + "' is absent but annotation '"
                            + PLANE_TITLE + f + "' exists.");
                    }
                    if (bodyBoundaries.isEmpty()) {
                        throw new RuntimeException("output_exporter: scene '" + PLANE_SCENE + f + "' is absent and "
                            + CARD_NAME + " body.body_boundaries.names is empty.");
                    }
                    for (int k = 0; k < bodyBoundaries.size(); k++) {
                        viewPart(fluid, bodyBoundaries.get(k));
                    }
                    log("image field '" + f + "': scene '" + PLANE_SCENE + f + "' absent, created before the print");
                } else {
                    Scene plane = scene(PLANE_SCENE + f);
                    planeDisplayer(plane, f);
                    if (!plane.getDisplayerManager().hasDisplayer(PLANE_BODY + f)) {
                        throw new RuntimeException("output_exporter: missing displayer '" + PLANE_BODY + f + "' in scene '"
                            + PLANE_SCENE + f + "'.");
                    }
                    planeTitle(f);
                }
            } catch (RuntimeException e) {
                throw named("image field", f, e);
            }
        }
        requireUnitD();
        int toCreate = 0;
        for (int i = 0; i < views.size(); i++) {
            toCreate += views.get(i).create ? 1 : 0;
        }
        for (Boolean c : planeCreate.values()) {
            toCreate += c.booleanValue() ? 1 : 0;
        }
        log("objects found: " + stations.size() + " stations, " + views.size() + " views (" + toCreate
            + " scenes to create)");
    }

    // The same failure with the card entry it belongs to in front.
    private static RuntimeException named(String kind, String name, RuntimeException e) {
        String m = String.valueOf(e.getMessage());
        if (m.startsWith("output_exporter: ")) {
            m = m.substring("output_exporter: ".length());
        }
        return new RuntimeException("output_exporter: " + kind + " '" + name + "': " + m);
    }

    // ================================================================= step 1: planes
    private void exportPlanes() {
        File planesDir = subDir(pointDir, "planes");
        for (int p = 0; p < stations.size(); p++) {
            Station st = stations.get(p);
            String plane = st.stem;
            String name = "LLM_table_" + plane;
            XyzInternalTable t = table(name);

            // Same two settings run_macro's prepare mode gives the table; re-applied, not created.
            t.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
            if (!t.getExtractVertexData()) {
                t.setExtractVertexData(true);
                log(name + " data on vertices was off, set on");
            }

            File out = new File(planesDir, "plane_" + plane + "_raw.csv");
            try {
                // XyzInternalTable.extract(): signature verified by javac 2606.
                t.extract();
                // XyzInternalTable.export(String path, String separator): signature verified by javac 2606.
                t.export(out.getAbsolutePath(), ",");
            } catch (RuntimeException e) {
                throw new RuntimeException("output_exporter: station '" + plane + "': export of '" + name + "' failed: "
                    + e.getMessage());
            }
            st.rowsWritten = normalisePlaneCsv(out, name, st.columns);
            st.missingInBody = checkLattice(out, st);
            log("plane " + plane + " rows " + st.rowsWritten + "/" + st.rowsExpected
                + " missing_in_body " + st.missingInBody + " half_width " + (halfWidth == null ? "null" : num(halfWidth.doubleValue())));
        }
    }

    // Rebuilds the lattice from the CSV just written (its comment lines skipped): the unique values of
    // the two in-plane coordinates must be grid[0] and grid[1] in number, origin + i * spacing of the
    // card, and the constant coordinate must be the station's value. A node absent from the CSV is tolerated
    // only inside the body (all three coordinates within card.body.half_width, the plane's constant one
    // included); with no half_width no node is excused. Returns the number of absent nodes inside the body.
    private int checkLattice(File f, Station st) {
        String plane = st.stem;
        int a = st.axes[0];
        int b = st.axes[1];
        int c = st.axes[2];
        List<String> lines = readLines(f);
        Map<Long, Double> valuesA = new TreeMap<Long, Double>();
        Map<Long, Double> valuesB = new TreeMap<Long, Double>();
        Set<String> present = new HashSet<String>();
        double constant = Double.NaN;
        boolean header = true;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().length() == 0 || lines.get(i).startsWith("#")) {
                continue;
            }
            if (header) {
                header = false;
                continue;
            }
            String[] cells = splitCsv(lines.get(i));
            double va;
            double vb;
            double vc;
            try {
                va = Double.parseDouble(cells[a]);
                vb = Double.parseDouble(cells[b]);
                vc = Double.parseDouble(cells[c]);
            } catch (RuntimeException e) {
                throw new RuntimeException(plane + " lattice incomplete (unreadable coordinates on line "
                    + (i + 1) + " of " + rel(f) + ")");
            }
            long ka = latticeKey(va);
            long kb = latticeKey(vb);
            if (!valuesA.containsKey(Long.valueOf(ka))) {
                valuesA.put(Long.valueOf(ka), Double.valueOf(va));
            }
            if (!valuesB.containsKey(Long.valueOf(kb))) {
                valuesB.put(Long.valueOf(kb), Double.valueOf(vb));
            }
            present.add(ka + ":" + kb);
            if (Double.isNaN(constant)) {
                constant = vc;
            }
        }
        int n1 = valuesA.size();
        int n2 = valuesB.size();
        if (n1 * n2 != st.rowsExpected) {
            throw new RuntimeException(plane + " lattice incomplete (" + st.columns[a] + " x " + st.columns[b]
                + " = " + n1 + " x " + n2 + ", rows_expected " + st.rowsExpected + ")");
        }
        if (n1 != st.grid[0] || n2 != st.grid[1]) {
            throw new RuntimeException(plane + " lattice is " + st.columns[a] + " x " + st.columns[b] + " = " + n1
                + " x " + n2 + ", card grid is [" + st.grid[0] + ", " + st.grid[1] + "]");
        }
        requireCardLattice(plane, st.columns[a], valuesA, st.origin[0], st.spacing);
        requireCardLattice(plane, st.columns[b], valuesB, st.origin[1], st.spacing);
        if (!(Math.abs(constant - st.value) <= COORD_TOL)) {
            throw new RuntimeException(plane + " " + st.columns[c] + " reads " + constant + ", card value is " + st.value);
        }
        int missingInBody = 0;
        for (Map.Entry<Long, Double> ea : valuesA.entrySet()) {
            for (Map.Entry<Long, Double> eb : valuesB.entrySet()) {
                if (present.contains(ea.getKey() + ":" + eb.getKey())) {
                    continue;
                }
                double[] xyz = new double[3];
                xyz[a] = ea.getValue().doubleValue();
                xyz[b] = eb.getValue().doubleValue();
                xyz[c] = constant;
                if (!insideBody(xyz)) {
                    throw new RuntimeException(plane + " missing node (" + xyz[0] + "," + xyz[1] + "," + xyz[2] + ")"
                        + (halfWidth == null ? ", card.body.half_width absent" : ""));
                }
                missingInBody++;
            }
        }
        return missingInBody;
    }

    // The sorted unique values of one in-plane coordinate against origin + i * spacing of the card.
    private static void requireCardLattice(String plane, String column, Map<Long, Double> values, double origin,
            double spacing) {
        int i = 0;
        for (Double v : values.values()) {
            double expected = origin + i * spacing;
            if (!(Math.abs(v.doubleValue() - expected) <= COORD_TOL)) {
                throw new RuntimeException(plane + " " + column + " node " + i + " reads " + v + ", card origin "
                    + origin + " and spacing " + spacing + " give " + expected);
            }
            i++;
        }
    }

    private static long latticeKey(double v) {
        return Math.round(v / COORD_TOL);
    }

    private boolean insideBody(double[] xyz) {
        if (halfWidth == null) {
            return false;
        }
        for (int k = 0; k < xyz.length; k++) {
            if (!(Math.abs(xyz[k]) <= halfWidth.doubleValue() + COORD_TOL)) {
                return false;
            }
        }
        return true;
    }

    // Rewrites the solver's CSV in place with the station's columns (x_D, y_D, z_D, then its fields) as
    // header and order; values are copied verbatim, with no comment line (the assembler writes the
    // formation header from card.json). Returns the number of data rows.
    // TODO(unverified behaviour): the header text XyzInternalTable.export writes is not documented in
    // either source. A column is matched when its header, stripped of quotes and of any unit suffix
    // in "(...)" or "[...]", equals the field function name. No match or two matches throws with the
    // header found, so the first real run shows the format.
    private int normalisePlaneCsv(File f, String tableName, String[] csvColumns) {
        List<String> lines = readLines(f);
        int h = 0;
        while (h < lines.size() && lines.get(h).trim().length() == 0) {
            h++;
        }
        if (h >= lines.size()) {
            throw new RuntimeException("output_exporter: " + rel(f) + " from '" + tableName + "' is empty.");
        }
        String[] header = splitCsv(lines.get(h));
        int[] col = new int[csvColumns.length];
        for (int k = 0; k < csvColumns.length; k++) {
            col[k] = -1;
            for (int j = 0; j < header.length; j++) {
                if (csvColumns[k].equals(headerName(header[j]))) {
                    if (col[k] >= 0) {
                        throw new RuntimeException("output_exporter: " + rel(f) + ": two columns match '"
                            + csvColumns[k] + "'; header is: " + lines.get(h));
                    }
                    col[k] = j;
                }
            }
            if (col[k] < 0) {
                throw new RuntimeException("output_exporter: " + rel(f) + ": no column for '"
                    + csvColumns[k] + "'; header is: " + lines.get(h));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < csvColumns.length; k++) {
            sb.append(k == 0 ? "" : ",").append(csvColumns[k]);
        }
        sb.append('\n');
        int rows = 0;
        for (int i = h + 1; i < lines.size(); i++) {
            String ln = lines.get(i);
            if (ln.trim().length() == 0) {
                continue;
            }
            String[] cells = splitCsv(ln);
            if (cells.length != header.length) {
                throw new RuntimeException("output_exporter: " + rel(f) + " line " + (i + 1) + " has "
                    + cells.length + " fields, header has " + header.length + ".");
            }
            for (int k = 0; k < col.length; k++) {
                sb.append(k == 0 ? "" : ",").append(cells[col[k]]);
            }
            sb.append('\n');
            rows++;
        }
        writeTextFile(f, sb.toString());
        return rows;
    }

    private static String[] splitCsv(String line) {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                s = s.substring(1, s.length() - 1).trim();
            }
            parts[i] = s;
        }
        return parts;
    }

    private static String headerName(String cell) {
        String s = cell;
        int cut = s.length();
        int paren = s.indexOf('(');
        int bracket = s.indexOf('[');
        if (paren >= 0 && paren < cut) {
            cut = paren;
        }
        if (bracket >= 0 && bracket < cut) {
            cut = bracket;
        }
        return s.substring(0, cut).trim();
    }

    // ================================================================= step 2: views
    // One image per card.views entry, views/<file>. The camera of the card is set, the title gets the
    // numbers of summary.json; the displayer is set to the card's parts, range and colormap, read back
    // and the macro stops naming the view if any of them does not take.
    // Returns the "views" array of render.json.
    private String exportViews() {
        File viewsDir = subDir(pointDir, "views");
        StringBuilder viewsJson = new StringBuilder();
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            if (v.create) {
                createViewScene(v);
            }
            Scene sc = scene(v.scene);
            ScalarDisplayer sd = scalarDisplayer(sc, v.scene, v.displayer);
            SimpleAnnotation title = simpleAnnotation(v.title);
            showOnlyParts(sd, v);
            applyCardColorbar(sd, "view '" + v.file + "'", v.range);
            setCamera(sc, v.scene, v.camera);
            String text = titleText(v.field, caseSummary == null ? "body" : v.parts);
            if (!text.equals(title.getText())) {
                title.setText(text);
            }
            applyRenderLayout(sc, v.scene, sd, title, v.title);
            File out = new File(viewsDir, v.file);
            try {
                // Scene.printAndWait(String file, int magnification, int width, int height):
                // signature verified by javac 2606.
                sc.printAndWait(out.getAbsolutePath(), VIEW_MAGNIFICATION, VIEW_WIDTH, VIEW_HEIGHT);
            } catch (RuntimeException e) {
                throw new RuntimeException("output_exporter: view '" + v.file + "': print of '" + v.scene + "' failed: "
                    + e.getMessage());
            }
            if (!out.isFile() || out.length() == 0L) {
                throw new RuntimeException("output_exporter: " + rel(out) + " was not written by '" + v.scene + "'.");
            }
            String camera = cameraJson(sc, v.scene);
            viewsJson.append("    {\"file\": ").append(str("views/" + v.file)).append(", \"scene\": ").append(str(v.scene))
              .append(", \"displayer\": ").append(str(v.displayer)).append(", \"field\": ").append(str(v.field))
              .append(", \"part\": ").append(str(v.parts)).append(",\n");
            viewsJson.append("      \"camera\": ").append(camera).append(",\n");
            viewsJson.append("      \"colorbar\": ").append(colorbarJson(sd, v.displayer)).append(",\n");
            viewsJson.append("      \"title\": ").append(str(title.getText())).append(",\n");
            viewsJson.append("      \"export\": {\"call\": ").append(str(printCall())).append("}}")
              .append(i == views.size() - 1 ? "\n" : ",\n");
            log(rel(out) + " " + VIEW_WIDTH + "x" + VIEW_HEIGHT + " bytes=" + out.length() + " camera " + camera);
        }
        return viewsJson.toString();
    }

    // A view whose scene the .sim lacks, built as t7/output_exporter.exportBodyView does: createScalarScene
    // as a statement, the new scene found by comparing scene names before and after, renamed view_<s>;
    // "Scalar 1" renamed disp_<s>, shown field, fill mode and clip; "Outline 1" hidden; the title
    // annotation title_<s> created and bound to the scene. Parts, range, colormap, camera, title text and
    // render layout are then applied by exportViews like for a scene that was there. Not saved.
    private void createViewScene(View v) {
        Set<String> before = sceneNames();
        sim.getSceneManager().createScalarScene("Scalar Scene", "Outline", "Scalar", null);
        Scene sc = null;
        for (Object o : sim.getSceneManager().getObjects()) {
            if (o instanceof Scene && !before.contains(((Scene) o).getPresentationName())) {
                if (sc != null) {
                    throw new RuntimeException("output_exporter: view '" + v.file + "': more than one new scene after "
                        + "createScalarScene: '" + sc.getPresentationName() + "', '" + ((Scene) o).getPresentationName() + "'.");
                }
                sc = (Scene) o;
            }
        }
        if (sc == null) {
            throw new RuntimeException("output_exporter: view '" + v.file + "': no new scene found after createScalarScene.");
        }
        sc.setPresentationName(v.scene);
        sc.resetCamera();

        ScalarDisplayer sd = scalarDisplayer(sc, v.scene, NEW_SCALAR);
        sd.setPresentationName(v.displayer);
        sd.setFillMode(ScalarFillMode.NODE_FILLED); // Smooth Filled
        // The function goes first: changing it resets a manual range.
        sd.getScalarDisplayQuantity().setFieldFunction(fieldFunction(v.field));
        sd.getScalarDisplayQuantity().setClip(ClipMode.NONE);
        requireShownField(sd, v.displayer, v.field);
        if (!sc.getDisplayerManager().hasDisplayer(NEW_OUTLINE)) {
            throw new RuntimeException("output_exporter: view '" + v.file + "': missing displayer '" + NEW_OUTLINE
                + "' in scene '" + v.scene + "'.");
        }
        Object outline = sc.getDisplayerManager().getObject(NEW_OUTLINE);
        if (!(outline instanceof PartDisplayer)) {
            throw new RuntimeException("output_exporter: view '" + v.file + "': displayer '" + NEW_OUTLINE
                + "' in scene '" + v.scene + "' is not a PartDisplayer.");
        }
        ((PartDisplayer) outline).setVisibilityOverrideMode(DisplayerVisibilityOverride.HIDE_ALL_PARTS);

        SimpleAnnotation title = sim.getAnnotationManager().createSimpleAnnotation();
        title.setPresentationName(v.title);
        title.setShadow(false);
        AnnotationProp prop = sc.getAnnotationPropManager().createPropForAnnotation(title);
        prop.setVisible(true);
        ((SimpleAnnotationProp) sc.getAnnotationPropManager().getObject(v.title))
            .setPosition(new DoubleVector(TITLE_POSITION));
        log("view '" + v.file + "': transient scene " + v.scene + " created (not saved)");
    }

    private Set<String> sceneNames() {
        Set<String> names = new HashSet<String>();
        for (Object o : sim.getSceneManager().getObjects()) {
            if (o instanceof Scene) {
                names.add(((Scene) o).getPresentationName());
            }
        }
        return names;
    }

    // Title of a view or a plane image. With run mode's summary.json: its status and iteration, as
    // before. With the case macro's: card.body.formation.fields, as written.
    private String titleText(String field, String on) {
        String head = field + " on " + on + ", " + groupSymbol + " = " + reTarget + ", ";
        if (caseSummary != null) {
            return head + formationFields;
        }
        return head + status + ", snapshot at iteration " + iterations;
    }

    private static String printCall() {
        return "printAndWait(file, " + VIEW_MAGNIFICATION + ", " + VIEW_WIDTH + ", " + VIEW_HEIGHT + ")";
    }

    // Range (if the card gives one) and colormap of the card on a displayer, each written only if the
    // value read differs and then read back, with the calls of run_macro.fixDisplayRange and
    // run_macro.applyLegendDeclared: the automatic range is switched off before the range is set.
    // Set before every print and never saved, like the render layout.
    // TODO(unverified signature): ScalarDisplayQuantity.getAutoRange()/setAutoRange(AutoRangeMode),
    // getRange()/setRange(DoubleVector), as marked in run_macro.java.
    private void applyCardColorbar(ScalarDisplayer sd, String where, double[] range) {
        if (range != null) {
            Object auto = sd.getScalarDisplayQuantity().getAutoRange();
            if (!AutoRangeMode.NONE.equals(auto)) {
                sd.getScalarDisplayQuantity().setAutoRange(AutoRangeMode.NONE);
            }
            Object read = vector(sd.getScalarDisplayQuantity().getRange(), 2);
            if (!(read instanceof double[]) || !near((double[]) read, range)) {
                sd.getScalarDisplayQuantity().setRange(new DoubleVector(range));
                read = vector(sd.getScalarDisplayQuantity().getRange(), 2);
            }
            if (!(read instanceof double[]) || !near((double[]) read, range)) {
                throw new RuntimeException("output_exporter: " + where + ": displayer range reads back "
                    + (read instanceof double[] ? Arrays.toString((double[]) read) : ((Unexpected) read).text)
                    + ", card asks " + Arrays.toString(range) + ".");
            }
        }
        Legend legend = sd.getLegend();
        LookupTable lut = lookupTable(colormap);
        if (!lut.equals(legend.getLookupTable())) { // getter of a recorded setter
            legend.setLookupTable(lut);
        }
        Object map = colormapRead(legend);
        if (!colormap.equals(map)) {
            throw new RuntimeException("output_exporter: " + where + ": legend colormap reads back '"
                + (map instanceof String ? (String) map : ((Unexpected) map).text) + "', card.body.colormap is '"
                + colormap + "'.");
        }
    }

    // The predefined table by its name, looked up as run_macro.colorBarLookupTable does; absent throws
    // with the name.
    private LookupTable lookupTable(String name) {
        Object o = sim.get(LookupTableManager.class).getObject(name);
        if (!(o instanceof LookupTable)) {
            throw new RuntimeException("output_exporter: lookup table '" + name + "' read as "
                + (o == null ? "null" : o.getClass().getName()) + ", not a LookupTable.");
        }
        return (LookupTable) o;
    }

    // ================================================================= step 3: plane images
    // One image per station in card order, showing the station's image field: LLM_disp_plane_<f> of
    // scene LLM_plane_<f> is pointed at section_<stem>, the camera is set, the title gets the numbers of
    // summary.json, the scene is printed to planes/plane_<stem>.png. A scene the .sim lacks is created
    // first (createPlaneScene). The camera is read back after each render; the cameras, the colorbar,
    // the titles, the field and the scene of each plane and the views go to render.json for session C.
    private void exportPlaneImages(String viewsJson) {
        File planesDir = subDir(pointDir, "planes");
        Set<String> ready = new HashSet<String>();

        StringBuilder planesJson = new StringBuilder();
        for (int p = 0; p < stations.size(); p++) {
            Station st = stations.get(p);
            String plane = st.stem;
            String f = st.imageField;
            String sceneName = PLANE_SCENE + f;
            if (planeCreate.get(f).booleanValue() && !ready.contains(f)) {
                createPlaneScene(f);
            }
            Scene sc = scene(sceneName);
            ScalarDisplayer sd = planeDisplayer(sc, f);
            SimpleAnnotation title = planeTitle(f);
            if (ready.add(f)) {
                applyCardColorbar(sd, "scene '" + sceneName + "'", null);
            }
            showOnlySection(sd, plane);
            setCamera(sc, sceneName, st.camera);
            String text = titleText(f, plane);
            if (!text.equals(title.getText())) {
                title.setText(text);
            }
            applyRenderLayout(sc, sceneName, sd, title, PLANE_TITLE + f);

            File out = new File(planesDir, "plane_" + plane + ".png");
            try {
                // Same call as the views: Scene.printAndWait(String, int, int, int), verified by javac 2606.
                sc.printAndWait(out.getAbsolutePath(), VIEW_MAGNIFICATION, VIEW_WIDTH, VIEW_HEIGHT);
            } catch (RuntimeException e) {
                throw new RuntimeException("output_exporter: station '" + plane + "': print of '" + sceneName
                    + "' failed: " + e.getMessage());
            }
            if (!out.isFile() || out.length() == 0L) {
                throw new RuntimeException("output_exporter: " + rel(out) + " was not written by '" + sceneName + "'.");
            }

            String camera = cameraJson(sc, sceneName + " " + plane);
            String titleRead = title.getText();
            planesJson.append("    ").append(str(plane)).append(": {\"file\": ").append(str("planes/plane_" + plane + ".png"))
              .append(", \"part\": ").append(str("section_" + plane)).append(", \"scene\": ").append(str(sceneName))
              .append(", \"displayer\": ").append(str(PLANE_DISP + f)).append(", \"field\": ").append(str(f)).append(",\n");
            planesJson.append("      \"camera\": ").append(camera).append(",\n");
            planesJson.append("      \"colorbar\": ").append(colorbarJson(sd, PLANE_DISP + f)).append(",\n");
            planesJson.append("      \"title\": ").append(str(titleRead)).append("}")
              .append(p == stations.size() - 1 ? "\n" : ",\n");
            log(rel(out) + " " + VIEW_WIDTH + "x" + VIEW_HEIGHT + " bytes=" + out.length() + " camera " + camera);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"export\": {\"call\": ").append(str(printCall())).append("},\n");
        sb.append("  \"layout\": ").append(layoutJson()).append(",\n");
        sb.append("  \"planes\": {\n").append(planesJson).append("  },\n");
        sb.append("  \"views\": [\n").append(viewsJson).append("  ],\n");
        sb.append("  \"half_width\": ").append(halfWidth == null ? "null" : num(halfWidth.doubleValue())).append(",\n");
        sb.append("  \"written_by\": \"output_exporter\"\n");
        sb.append("}\n");
        File render = new File(pointDir, "render.json");
        writeTextFile(render, sb.toString());
        log(rel(render) + " written");
    }

    // A plane scene the .sim lacks, built like createViewScene: createScalarScene as a statement, the new
    // scene found by comparing scene names before and after, renamed LLM_plane_<f>; "Scalar 1" renamed
    // LLM_disp_plane_<f> with the field shown; "Outline 1" renamed LLM_body_plane_<f> and pointed at
    // card.body.body_boundaries; the title annotation LLM_title_plane_<f> created and bound to the scene.
    // Parts of the section, camera, title text, colormap and render layout are applied by exportPlaneImages.
    // Not saved.
    private void createPlaneScene(String f) {
        String sceneName = PLANE_SCENE + f;
        Set<String> before = sceneNames();
        sim.getSceneManager().createScalarScene("Scalar Scene", "Outline", "Scalar", null);
        Scene sc = null;
        for (Object o : sim.getSceneManager().getObjects()) {
            if (o instanceof Scene && !before.contains(((Scene) o).getPresentationName())) {
                if (sc != null) {
                    throw new RuntimeException("output_exporter: image field '" + f + "': more than one new scene after "
                        + "createScalarScene: '" + sc.getPresentationName() + "', '" + ((Scene) o).getPresentationName() + "'.");
                }
                sc = (Scene) o;
            }
        }
        if (sc == null) {
            throw new RuntimeException("output_exporter: image field '" + f + "': no new scene found after createScalarScene.");
        }
        sc.setPresentationName(sceneName);
        sc.resetCamera();

        ScalarDisplayer sd = scalarDisplayer(sc, sceneName, NEW_SCALAR);
        sd.setPresentationName(PLANE_DISP + f);
        sd.setFillMode(ScalarFillMode.NODE_FILLED); // Smooth Filled
        // The function goes first: changing it resets a manual range.
        sd.getScalarDisplayQuantity().setFieldFunction(fieldFunction(f));
        sd.getScalarDisplayQuantity().setClip(ClipMode.NONE);
        requireShownField(sd, PLANE_DISP + f, f);
        if (!sc.getDisplayerManager().hasDisplayer(NEW_OUTLINE)) {
            throw new RuntimeException("output_exporter: image field '" + f + "': missing displayer '" + NEW_OUTLINE
                + "' in scene '" + sceneName + "'.");
        }
        Object outline = sc.getDisplayerManager().getObject(NEW_OUTLINE);
        if (!(outline instanceof PartDisplayer)) {
            throw new RuntimeException("output_exporter: image field '" + f + "': displayer '" + NEW_OUTLINE
                + "' in scene '" + sceneName + "' is not a PartDisplayer.");
        }
        PartDisplayer body = (PartDisplayer) outline;
        body.setPresentationName(PLANE_BODY + f);
        Region fluid = region(fluidRegion);
        NamedObject[] parts = new NamedObject[bodyBoundaries.size()];
        for (int k = 0; k < parts.length; k++) {
            parts[k] = viewPart(fluid, bodyBoundaries.get(k));
        }
        // TODO(unverified signature): PartDisplayer.getInputParts() with setQuery(null) and setObjects(NamedObject[]),
        // the same pair as on ScalarDisplayer.
        body.getInputParts().setQuery(null);
        body.getInputParts().setObjects(parts);

        SimpleAnnotation title = sim.getAnnotationManager().createSimpleAnnotation();
        title.setPresentationName(PLANE_TITLE + f);
        title.setShadow(false);
        AnnotationProp prop = sc.getAnnotationPropManager().createPropForAnnotation(title);
        prop.setVisible(true);
        ((SimpleAnnotationProp) sc.getAnnotationPropManager().getObject(PLANE_TITLE + f))
            .setPosition(new DoubleVector(TITLE_POSITION));
        log("image field '" + f + "': transient scene " + sceneName + " created (not saved)");
    }

    private ScalarDisplayer planeDisplayer(Scene sc, String f) {
        ScalarDisplayer sd = scalarDisplayer(sc, PLANE_SCENE + f, PLANE_DISP + f);
        requireShownField(sd, PLANE_DISP + f, f);
        return sd;
    }

    private void requireShownField(ScalarDisplayer sd, String dispName, String field) {
        Object ff = sd.getScalarDisplayQuantity().getFieldFunction(); // getter of a verified setter
        if (isMissingFieldFunction(ff)) {
            throw new RuntimeException("output_exporter: error: displayer '" + dispName
                + "' shows a missing field function, expected '" + field + "'.");
        }
        String ffName = ff instanceof FieldFunction ? ((FieldFunction) ff).getPresentationName() : String.valueOf(ff);
        if (!field.equals(ffName)) {
            throw new RuntimeException("output_exporter: displayer '" + dispName + "' shows '" + ffName
                + "', expected '" + field + "'.");
        }
    }

    private ScalarDisplayer scalarDisplayer(Scene sc, String sceneName, String dispName) {
        if (!sc.getDisplayerManager().hasDisplayer(dispName)) {
            throw new RuntimeException("output_exporter: missing displayer '" + dispName + "' in scene '"
                + sceneName + "'.");
        }
        Object d = sc.getDisplayerManager().getObject(dispName);
        if (!(d instanceof ScalarDisplayer)) {
            throw new RuntimeException("output_exporter: displayer '" + dispName + "' is not a ScalarDisplayer.");
        }
        return (ScalarDisplayer) d;
    }

    private SimpleAnnotation planeTitle(String f) {
        return simpleAnnotation(PLANE_TITLE + f);
    }

    private SimpleAnnotation simpleAnnotation(String name) {
        Object a = sim.getAnnotationManager().hasObject(name);
        if (a == null) {
            throw new RuntimeException("output_exporter: missing annotation '" + name + "'.");
        }
        if (!(a instanceof SimpleAnnotation)) {
            throw new RuntimeException("output_exporter: annotation '" + name + "' is not a SimpleAnnotation.");
        }
        return (SimpleAnnotation) a;
    }

    // Render layout (v12), written before each printAndWait with the calls recorded in the 2606 GUI on
    // 30.09 (font, height, width, position, shadow) and the setLabelFormat of run_macro.java. Session
    // B does not save, so the values are written, not read first: nothing here outlives the session.
    // The prop is the one run_macro binds to the scene; any other class under its name throws.
    private void applyRenderLayout(Scene sc, String sceneName, ScalarDisplayer sd, SimpleAnnotation title,
            String titleName) {
        title.setFontString(TITLE_FONT);
        Object p = sc.getAnnotationPropManager().getObject(titleName);
        if (!(p instanceof SimpleAnnotationProp)) {
            throw new RuntimeException("output_exporter: prop of '" + titleName + "' in '" + sceneName + "' read as "
                + (p == null ? "null" : p.getClass().getName()) + ", not a SimpleAnnotationProp.");
        }
        ((SimpleAnnotationProp) p).setHeight(TITLE_HEIGHT);
        Legend legend = sd.getLegend();
        legend.setWidth(LEGEND_WIDTH);
        legend.setPositionCoordinate(new DoubleVector(LEGEND_POSITION));
        legend.setShadow(LEGEND_SHADOW);
        legend.setLabelFormat(COLOR_BAR_LABEL_FORMAT);
        log("layout " + sceneName + ": title font '" + TITLE_FONT + "' height " + num(TITLE_HEIGHT)
            + ", legend width " + num(LEGEND_WIDTH) + " position [" + num(LEGEND_POSITION[0]) + ", "
            + num(LEGEND_POSITION[1]) + "] shadow " + LEGEND_SHADOW + " label format '" + COLOR_BAR_LABEL_FORMAT + "'");
    }

    // The layout as applied, for render.json; manifest_writer copies it into the colorbar and
    // title blocks of every view and plane image (session C reads the saved .sim, not these values).
    private String layoutJson() {
        StringBuilder scenes = new StringBuilder();
        for (int i = 0; i < views.size(); i++) {
            scenes.append(str(views.get(i).scene)).append(", ");
        }
        for (String f : planeCreate.keySet()) {
            scenes.append(str(PLANE_SCENE + f)).append(", ");
        }
        scenes.setLength(scenes.length() - 2);
        return "{\"scenes\": [" + scenes + "], \"title\": {\"font_string\": " + str(TITLE_FONT)
            + ", \"height\": " + num(TITLE_HEIGHT) + "}, \"colorbar\": {\"width\": " + num(LEGEND_WIDTH)
            + ", \"position\": [" + num(LEGEND_POSITION[0]) + ", " + num(LEGEND_POSITION[1]) + "], \"shadow\": "
            + LEGEND_SHADOW + ", \"label_format\": " + str(COLOR_BAR_LABEL_FORMAT) + "}}";
    }

    // The cameras (card.stations and card.views) are declared in metres for D = 1 m.
    private void requireUnitD() {
        Object o = sim.getGlobalParameterManager().hasObject("D");
        if (!(o instanceof ScalarGlobalParameter)) {
            throw new RuntimeException("output_exporter: missing global parameter 'D'.");
        }
        double d = ((ScalarGlobalParameter) o).getQuantity().getSIValue();
        if (Math.abs(d - 1.0) > 1.0e-12) {
            throw new RuntimeException("output_exporter: D reads " + d + " m; the cameras are declared for D = 1 m.");
        }
    }

    // Leaves section_<plane> as the only part the plane displayer shows, with the two calls recorded in
    // the 2606 GUI on 29.09: the query is cleared, then the part list is set to that one section.
    private void showOnlySection(ScalarDisplayer sd, String plane) {
        Object target = derivedPart("section_" + plane);
        if (!(target instanceof NamedObject)) {
            throw new RuntimeException("output_exporter: derived part 'section_" + plane + "' is not a NamedObject.");
        }
        // TODO(unverified signature): the recording passes a variable of the part's own class; NamedObject
        // is the type addPart takes in run_macro.java. If javac rejects it, cast to that class instead.
        sd.getInputParts().setQuery(null);
        sd.getInputParts().setObjects((NamedObject) target);
    }

    // Leaves the parts of card.views.parts (boundaries of card.body.fluid_region, Derived Parts or
    // regions, see viewPart) as the only parts the view displayer shows: the query is cleared, then the
    // part list is set to that array, as run_macro.provisionPrismOffControl does with its part surfaces.
    // TODO(unverified signature): setObjects with a NamedObject[] (the parts may be of mixed classes);
    // run_macro.java passes a PartSurface[].
    private void showOnlyParts(ScalarDisplayer sd, View v) {
        Region fluid = region(fluidRegion);
        NamedObject[] parts = new NamedObject[v.partNames.length];
        for (int k = 0; k < parts.length; k++) {
            parts[k] = viewPart(fluid, v.partNames[k]);
        }
        sd.getInputParts().setQuery(null);
        sd.getInputParts().setObjects(parts);
    }

    // Each setter is written only if the value read differs, as in run_macro.fixCamera; same calls.
    private void setCamera(Scene sc, String sceneName, Camera c) {
        double[] focal = c.focal;
        double[] position = c.position;
        double[] viewUp = c.viewUp;
        double scale = c.scale;
        // TODO(unverified signature): CurrentView.getProjectionMode() (deprecated on 2606, kept) and
        // setProjectionMode(VisProjectionMode.PARALLEL), same calls as run_macro.java.
        Object mode = sc.getCurrentView().getProjectionMode();
        if (!isParallel(mode)) {
            sc.getCurrentView().setProjectionMode(VisProjectionMode.PARALLEL);
        }
        if (!near(vec3(sc.getCurrentView().getFocalPoint()), focal)) {
            sc.getCurrentView().setFocalPoint(new DoubleVector(focal));
        }
        if (!near(vec3(sc.getCurrentView().getPosition()), position)) {
            sc.getCurrentView().setPosition(new DoubleVector(position));
        }
        if (!near(vec3(sc.getCurrentView().getViewUp()), viewUp)) {
            sc.getCurrentView().setViewUp(new DoubleVector(viewUp));
        }
        // TODO(unverified signature): ParallelScale.getValue()/setValue(double), deprecated on 2606 and
        // kept: no replacement is in any source this chain may use.
        ParallelScale ps = (ParallelScale) sc.getCurrentView().getParallelScale();
        if (ps == null) {
            throw new RuntimeException("output_exporter: '" + sceneName + "' has no parallel scale.");
        }
        if (!near(new double[] { ps.getValue() }, new double[] { scale })) {
            ps.setValue(scale);
        }
    }

    // Measured on 2606 (run_macro.java): after setProjectionMode(PARALLEL) the getter returns Integer 1.
    private static boolean isParallel(Object mode) {
        return Integer.valueOf(1).equals(mode) || VisProjectionMode.PARALLEL.equals(mode)
            || (mode != null && "PARALLEL".equalsIgnoreCase(mode.toString()));
    }

    // A camera vector for the "differs" test; anything but three doubles is null and forces the write.
    private static double[] vec3(Object o) {
        Object v = vector(o, 3);
        return v instanceof double[] ? (double[]) v : null;
    }

    private static boolean near(double[] a, double[] b) {
        if (a == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1.0e-9 * Math.max(1.0, Math.abs(b[i]))) {
                return false;
            }
        }
        return true;
    }

    // ---- camera and colorbar as read, same getters and same JSON shape as manifest_writer.java: a value
    // of an unexpected type is written as null with a "<key>_read_as" sibling and logged.
    private String cameraJson(Scene sc, String where) {
        Object focal = vector(sc.getCurrentView().getFocalPoint(), 3);
        Object position = vector(sc.getCurrentView().getPosition(), 3);
        Object up = vector(sc.getCurrentView().getViewUp(), 3);
        ParallelScale ps = (ParallelScale) sc.getCurrentView().getParallelScale();
        Object scale = ps == null ? new Unexpected(null) : (Object) Double.valueOf(ps.getValue());
        Object projection = projectionRead(sc.getCurrentView().getProjectionMode(), where);
        return "{" + slot("focal_point", focal, where) + ", " + slot("position", position, where)
            + ", " + slot("view_up", up, where) + ", " + slot("parallel_scale", scale, where)
            + ", " + slot("projection", projection, where) + "}";
    }

    // An integer is written as read (1 is parallel, measured on 2606); anything else as lower-case text.
    private Object projectionRead(Object o, String where) {
        if (o instanceof Number) {
            return Long.valueOf(((Number) o).longValue());
        }
        if (o == null) {
            return new Unexpected(null);
        }
        return String.valueOf(o).toLowerCase(Locale.ROOT);
    }

    // TODO(unverified signature): ScalarDisplayQuantity.getRange() (deprecated on 2606, kept); same call
    // as manifest_writer.java.
    private String colorbarJson(ScalarDisplayer sd, String dispName) {
        Object range = vector(sd.getScalarDisplayQuantity().getRange(), 2);
        Legend legend = sd.getLegend();
        Object levels = integerOrUnexpected(legend.getLevels()); // getter of a verified setter
        Object labels = integerOrUnexpected(legend.getNumberOfLabels()); // getter of a recorded setter
        Object format = legend.getLabelFormat(); // getter of a recorded setter
        if (format != null && !(format instanceof String)) {
            format = new Unexpected(format);
        }
        Object clip = sd.getScalarDisplayQuantity().getClip(); // getter of a verified setter
        Object fill = sd.getFillMode(); // getter of a verified setter
        String clipText = ClipMode.NONE.equals(clip) ? "CLIP_NONE" : String.valueOf(clip);
        String fillText = ScalarFillMode.NODE_FILLED.equals(fill) ? "smooth_filled" : String.valueOf(fill);
        return "{" + slot("range", range, dispName) + ", " + slot("levels", levels, dispName)
            + ", \"clip\": " + str(clipText) + ", " + slot("labels", labels, dispName)
            + ", " + slot("label_format", format, dispName) + ", \"contour_style\": " + str(fillText)
            + ", " + slot("colormap", colormapRead(legend), dispName) + "}";
    }

    // The legend's lookup table, written as its presentation name. Anything but a named object is
    // written as null with a "colormap_read_as" sibling.
    private static Object colormapRead(Legend legend) {
        Object t = legend.getLookupTable(); // getter of a recorded setter
        if (t instanceof NamedObject) {
            return ((NamedObject) t).getPresentationName();
        }
        return new Unexpected(t);
    }

    private static Object integerOrUnexpected(Object o) {
        if (o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) {
            return Long.valueOf(((Number) o).longValue());
        }
        return new Unexpected(o);
    }

    // What a getter read when its type was not the one expected: "<simple class name>: <toString()>".
    private static final class Unexpected {
        final String text;

        Unexpected(Object o) {
            String s;
            try {
                s = o instanceof double[] ? Arrays.toString((double[]) o) : String.valueOf(o);
            } catch (RuntimeException e) {
                s = "toString() threw " + e.getClass().getSimpleName();
            }
            text = o == null ? "null" : o.getClass().getSimpleName() + ": " + s;
        }
    }

    // A DoubleVector or a double[] of n components; anything else, or another length, is Unexpected.
    // TODO(unverified signature): DoubleVector.toDoubleArray(); used by run_macro.java.
    private static Object vector(Object o, int n) {
        double[] a = null;
        if (o instanceof DoubleVector) {
            a = ((DoubleVector) o).toDoubleArray();
        } else if (o instanceof double[]) {
            a = (double[]) o;
        }
        if (a == null || a.length != n) {
            return new Unexpected(o);
        }
        return a;
    }

    private String slot(String key, Object v, String where) {
        if (v instanceof Unexpected) {
            String text = scrub(((Unexpected) v).text);
            log(where + " " + key + "_read_as: " + text);
            return str(key) + ": null, " + str(key + "_read_as") + ": " + str(text);
        }
        String json;
        if (v instanceof double[]) {
            double[] a = (double[]) v;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) {
                sb.append(i == 0 ? "" : ", ").append(num(a[i]));
            }
            json = sb.append(']').toString();
        } else if (v instanceof Double) {
            json = num(((Double) v).doubleValue());
        } else if (v instanceof Long) {
            json = v.toString();
        } else if (v == null || v instanceof String) {
            json = str((String) v);
        } else {
            throw new IllegalStateException("output_exporter: slot '" + key + "' holds " + v.getClass().getName() + ".");
        }
        return str(key) + ": " + json;
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

    // ================================================================= step 4: setup.txt
    private File writeSetupTxt() {
        File html = new File(pointDir, "summary_re" + nnnn + ".html");
        exportSummaryReportHtml(html);
        if (!html.isFile() || html.length() == 0L) {
            throw new RuntimeException("output_exporter: summary report '" + rel(html) + "' was not written.");
        }
        if (html.lastModified() < startMillis - FRESHNESS_SLACK_MS) {
            throw new RuntimeException("output_exporter: summary report '" + rel(html)
                + "' predates this session; refusing a stale report.");
        }
        List<String> htmlLines = readLines(html);
        String trimmed = redact(processReport(htmlLines));
        File setup = new File(pointDir, "setup.txt");
        writeTextFile(setup, trimmed);
        log(rel(setup) + " html_bytes=" + html.length() + " setup_bytes="
            + trimmed.getBytes(StandardCharsets.UTF_8).length);
        return setup;
    }

    // Summary Report to HTML; the call is executed code from the owner's own macro. The freshness
    // check in writeSetupTxt runs right after it.
    private void exportSummaryReportHtml(File html) {
        new SimulationSummaryReporter().report(sim, html.getAbsolutePath());
    }

    // ---- TrimReportForAI port: processReport, parseSimpleTable, parseTreeTable and helpers. Logic
    // unchanged; rewritten to this file's style only (explicit generics, braces, indexed loops).
    private String processReport(List<String> lines) {
        StringBuilder sb = new StringBuilder();

        int simPropsLine = -1;
        int solutionLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            if (ln.contains("<H3>Simulation Properties</H3>")) {
                simPropsLine = i;
            }
            if (ln.contains("<H3>Solution</H3>")) {
                solutionLine = i;
            }
        }

        sb.append("============================================================\n");
        sb.append("STAR-CCM+ SIMULATION REPORT (AI-TRIMMED)\n");
        sb.append("============================================================\n");
        if (simPropsLine > 0) {
            sb.append(parseSimpleTable(lines, 0, simPropsLine));
        }

        Pattern secPat = Pattern.compile(
            "<tr[^>]*><td class=\"node\"><tt>(?:&nbsp;){2}\\+-(\\d+)&nbsp;</tt>(.*?)</td>"
        );
        List<int[]> ranges = new ArrayList<int[]>();
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = secPat.matcher(lines.get(i));
            if (m.find()) {
                names.add(stripHtml(m.group(2)));
                ranges.add(new int[] { i, -1 });
            }
        }
        for (int i = 0; i < ranges.size(); i++) {
            ranges.get(i)[1] = (i + 1 < ranges.size())
                ? ranges.get(i + 1)[0]
                : ((solutionLine > 0) ? solutionLine : lines.size());
        }

        sb.append("\n============================================================\n");
        sb.append("SIMULATION PROPERTIES\n");
        sb.append("============================================================\n");
        for (int i = 0; i < names.size(); i++) {
            if (!KEEP_SECTIONS.contains(names.get(i))) {
                continue;
            }
            sb.append("\n--- ").append(names.get(i)).append(" ---\n");
            sb.append(parseTreeTable(lines, ranges.get(i)[0], ranges.get(i)[1]));
        }

        if (solutionLine > 0) {
            sb.append("\n============================================================\n");
            sb.append("SOLUTION\n");
            sb.append("============================================================\n");
            sb.append(parseSimpleTable(lines, solutionLine, lines.size()));
        }
        return sb.toString();
    }

    private String parseSimpleTable(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        Pattern boldPat = Pattern.compile("<b>([^<]+)</b>");
        Pattern tdPat = Pattern.compile("<td[^>]*>(.*?)</td>");

        // Key and value <td> may sit on separate lines: join each <tr> first.
        List<String> trBlocks = new ArrayList<String>();
        StringBuilder currentTr = null;
        for (int i = start; i < end && i < lines.size(); i++) {
            String ln = lines.get(i);
            if (ln.contains("<H2>") || ln.contains("<H3>")) {
                if (currentTr != null) {
                    trBlocks.add(currentTr.toString());
                    currentTr = null;
                }
                trBlocks.add(ln);
            } else if (ln.contains("<tr")) {
                if (currentTr != null) {
                    trBlocks.add(currentTr.toString());
                }
                currentTr = new StringBuilder(ln);
            } else if (currentTr != null) {
                currentTr.append(" ").append(ln);
            }
        }
        if (currentTr != null) {
            trBlocks.add(currentTr.toString());
        }

        for (int b = 0; b < trBlocks.size(); b++) {
            String block = trBlocks.get(b);
            if (block.contains("<H2>") || block.contains("<H3>")) {
                String t = stripHtml(block).trim();
                if (!t.isEmpty()) {
                    sb.append(t).append("\n");
                }
                continue;
            }
            Matcher bm = boldPat.matcher(block);
            if (bm.find() && !block.contains("class=\"node\"")) {
                sb.append("\n[").append(bm.group(1).trim()).append("]\n");
                continue;
            }
            List<String> tds = new ArrayList<String>();
            Matcher tm = tdPat.matcher(block);
            while (tm.find()) {
                String content = tm.group(1).replaceAll("<br>", " | ").replaceAll("<[^>]*>", "").trim();
                tds.add(content);
            }
            if (tds.size() >= 2 && !tds.get(0).isEmpty() && !tds.get(1).isEmpty()) {
                String val = tds.get(1)
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
                sb.append("  ").append(tds.get(0)).append(": ").append(val).append("\n");
            }
        }
        return sb.toString();
    }

    private String parseTreeTable(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        Pattern ttPat = Pattern.compile("<tt>(.*?)</tt>");
        Pattern namePat = Pattern.compile("</tt>(?:<b>)?([^<]+)(?:</b>)?</td>");
        Pattern keyPat = Pattern.compile("(?:first-)?prop-key\">([^<]+)</td>");
        Pattern valPat = Pattern.compile("(?:first-)?prop-value\">(?:<IT>)?([^<]+)(?:</IT>)?</td>");

        for (int i = start; i < end && i < lines.size(); i++) {
            String ln = lines.get(i);
            if (!ln.contains("class=\"node\"")
                && !ln.contains("prop-key")
                && !ln.contains("first-prop-key")) {
                continue;
            }

            String pKey = null;
            String pVal = null;
            Matcher km = keyPat.matcher(ln);
            if (km.find()) {
                pKey = km.group(1).trim();
            }
            Matcher vm = valPat.matcher(ln);
            if (vm.find()) {
                pVal = vm.group(1).trim();
            }
            if (pKey != null && SKIP_PROPS.contains(pKey)) {
                continue;
            }
            if (pVal != null && pVal.replace("&nbsp;", "").trim().isEmpty()) {
                pVal = null;
            }

            String nodeName = null;
            int depth = 0;
            Matcher ttm = ttPat.matcher(ln);
            if (ttm.find()) {
                depth = computeDepth(ttm.group(1));
                Matcher nm = namePat.matcher(ln);
                if (nm.find()) {
                    nodeName = nm.group(1).trim();
                }
            }

            String indent = spaces(depth * 2);
            if (nodeName != null) {
                sb.append(indent).append(nodeName).append("\n");
                if (pKey != null && pVal != null) {
                    sb.append(indent).append("  ").append(pKey).append(": ").append(pVal).append("\n");
                }
            } else if (pKey != null && pVal != null) {
                sb.append(indent).append("  ").append(pKey).append(": ").append(pVal).append("\n");
            }
        }
        return sb.toString();
    }

    // Tree depth from the <tt> content: "|" is a parent level, "+-" or "`-" adds one.
    private static int computeDepth(String ttContent) {
        String s = ttContent.replace("&nbsp;", " ");
        int d = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '|') {
                d++;
            }
        }
        if (s.contains("+-") || s.contains("`-")) {
            d++;
        }
        return d;
    }

    private static String stripHtml(String s) {
        return s.replaceAll("<[^>]*>", "");
    }

    private static String spaces(int n) {
        char[] arr = new char[n];
        Arrays.fill(arr, ' ');
        return new String(arr);
    }

    // Not in TrimReportForAI. The report header carries the .sim location ("Simulation: <path>") and
    // host names. A value that looks like an absolute path is cut to its last component; a value under
    // a REDACT_KEYS key is replaced. Applies to every line, not only the header.
    private String redact(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int redactions = 0;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            int colon = ln.indexOf(": ");
            if (colon > 0) {
                String key = ln.substring(0, colon).trim();
                String val = ln.substring(colon + 2);
                String newVal = val;
                if (REDACT_KEYS.contains(key)) {
                    newVal = "(redacted)";
                } else if (looksAbsolute(val.trim())) {
                    newVal = lastComponent(val.trim());
                }
                if (!newVal.equals(val)) {
                    ln = ln.substring(0, colon + 2) + newVal;
                    redactions++;
                }
            }
            sb.append(ln);
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        log("setup.txt redactions=" + redactions);
        return sb.toString();
    }

    private static boolean looksAbsolute(String v) {
        if (v.length() >= 3 && Character.isLetter(v.charAt(0)) && v.charAt(1) == ':'
            && (v.charAt(2) == '\\' || v.charAt(2) == '/')) {
            return true;
        }
        if (v.startsWith("\\\\")) {
            return true;
        }
        return v.startsWith("/") && v.indexOf('/', 1) > 0;
    }

    private static String lastComponent(String v) {
        int cut = Math.max(v.lastIndexOf('/'), v.lastIndexOf('\\'));
        return cut >= 0 ? v.substring(cut + 1) : v;
    }

    // ================================================================= step 5: freshness
    // Three anchors, each read from setup.txt, summary.json and the live session. cells and iterations
    // are enforced: all three must be equal (contract v10; run mode now saves at the iteration
    // summary.json reports, with no extra iteration). cd is recorded only, never a gate: forces.cd is
    // a pooled window mean for stationary / no_steady_state. The live iteration is the one of
    // SimulationIterator.getCurrentIteration(), the call run_macro.java reads it with.
    // With the case macro's summary.json there is no run-mode anchor to compare: staleness.json records
    // the setup.txt and live iterations, the setup.txt cells and the planes, and enforces nothing.
    private void checkStaleness(File setup) {
        if (caseSummary != null) {
            checkStalenessCase(setup);
            return;
        }
        List<String> s = readLines(setup);
        String json = readText(new File(pointDir, SUMMARY_NAME));

        Long setupIterations = setupIterations(s);
        Long setupCells = setupCells(s);
        Double setupCd = setupCd(s);

        long jsonIterations = jsonLong(json, "convergence", "iterations");
        long jsonCells = jsonLong(json, "mesh", "cells");
        double jsonCd = jsonDouble(json, "forces", "cd");

        double simCd = report("cd").monitoredValue();
        double simCellsD = report("LLM_cell_count").monitoredValue();
        long simCells = (long) simCellsD;
        boolean simCellsWhole = !Double.isNaN(simCellsD) && !Double.isInfinite(simCellsD) && (double) simCells == simCellsD;
        long simIterations = sim.getSimulationIterator().getCurrentIteration();

        boolean cellsOk = setupCells != null && setupCells.longValue() == jsonCells
            && simCellsWhole && simCells == jsonCells;
        boolean iterationsOk = setupIterations != null && setupIterations.longValue() == jsonIterations
            && simIterations == jsonIterations;

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"iterations\": {\"setup_txt\": ").append(setupIterations == null ? "null" : setupIterations.toString())
          .append(", \"summary_json\": ").append(jsonIterations)
          .append(", \"sim_report\": ").append(simIterations).append("},\n");
        sb.append("  \"cd\": {\"setup_txt\": ").append(setupCd == null ? "null" : num(setupCd.doubleValue()))
          .append(", \"summary_json\": ").append(num(jsonCd))
          .append(", \"sim_report\": ").append(num(simCd)).append("},\n");
        sb.append("  \"cells\": {\"setup_txt\": ").append(setupCells == null ? "null" : setupCells.toString())
          .append(", \"summary_json\": ").append(jsonCells)
          .append(", \"sim_report\": ").append(simCellsWhole ? Long.toString(simCells) : "null").append("},\n");
        sb.append(planesStalenessJson());
        sb.append("  \"enforced\": {\"cells\": true, \"iterations\": true, \"cd\": false},\n");
        sb.append("  \"cells_equal\": ").append(cellsOk).append(",\n");
        sb.append("  \"iterations_equal\": ").append(iterationsOk).append(",\n");
        sb.append("  \"written_by\": \"output_exporter\"\n");
        sb.append("}\n");
        File out = new File(pointDir, "staleness.json");
        writeTextFile(out, sb.toString());
        log(rel(out) + " iterations " + setupIterations + "/" + jsonIterations + "/" + simIterations
            + " cd " + setupCd + "/" + num(jsonCd) + " cells " + setupCells + "/" + jsonCells + "/"
            + (simCellsWhole ? Long.toString(simCells) : "null"));

        if (!cellsOk || !iterationsOk) {
            String detail = "cells setup_txt=" + setupCells + " summary_json=" + jsonCells
                + " sim_report=" + (simCellsWhole ? Long.toString(simCells) : num(simCellsD))
                + ", iterations setup_txt=" + setupIterations + " summary_json=" + jsonIterations
                + " sim_report=" + simIterations;
            log("staleness: FAIL " + detail);
            throw new RuntimeException("output_exporter: staleness check failed, " + detail + ".");
        }
        log("staleness: cells and iterations ok");
    }

    private void checkStalenessCase(File setup) {
        List<String> s = readLines(setup);
        Long setupIterations = setupIterations(s);
        Long setupCells = setupCells(s);
        long simIterations = sim.getSimulationIterator().getCurrentIteration();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"iterations\": {\"setup_txt\": ").append(setupIterations == null ? "null" : setupIterations.toString())
          .append(", \"summary_json\": null, \"sim_report\": ").append(simIterations).append("},\n");
        sb.append("  \"cells\": {\"setup_txt\": ").append(setupCells == null ? "null" : setupCells.toString())
          .append(", \"summary_json\": null, \"sim_report\": null},\n");
        sb.append(planesStalenessJson());
        sb.append("  \"enforced\": {\"cells\": false, \"iterations\": false, \"cd\": false},\n");
        sb.append("  \"summary_json\": \"case macro\",\n");
        sb.append("  \"written_by\": \"output_exporter\"\n");
        sb.append("}\n");
        File out = new File(pointDir, "staleness.json");
        writeTextFile(out, sb.toString());
        log(rel(out) + " iterations " + setupIterations + "/" + simIterations + " cells " + setupCells
            + " (summary.json of the case macro: nothing enforced)");
    }

    private String planesStalenessJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("  \"planes\": {\n");
        for (int p = 0; p < stations.size(); p++) {
            Station st = stations.get(p);
            sb.append("    ").append(str(st.stem)).append(": {\"rows_written\": ").append(st.rowsWritten)
              .append(", \"rows_expected\": ").append(st.rowsExpected)
              .append(", \"missing_in_body\": ").append(st.missingInBody)
              .append(p == stations.size() - 1 ? "}\n" : "},\n");
        }
        sb.append("  },\n");
        return sb.toString();
    }

    // ================================================================= step 5: environment
    // SPEC 0.4, 4.2.1 (simulation-capsule, SPEC.md and tools/check_capsule.py): environment { solver,
    // version, build, platform, precision }, the last four copied from the Version: line of setup.txt
    // (PresentationVersion, ReleaseNumber, BuildArch; precision from BuildEnv, "-r8" at its end is
    // double, without it mixed). A value that is not on that line is written null and logged. solver
    // is not on it: it is the product name SPEC 4.2.1 fixes (ghb-spec04). No renders block: the images come from the same build as the run. The block takes
    // the place of environment_placeholder (or of the environment a previous session B wrote); every
    // other byte of summary.json is kept, so its number formatting is the one run_macro wrote.
    // With the case macro's summary.json, that file is read and left as it is; the copy written to the
    // point directory gets the block in place of either key or, with neither, as the last root key.
    private void writeEnvironment(File setup) {
        String line = null;
        int versionLines = 0;
        List<String> s = readLines(setup);
        for (int i = 0; i < s.size(); i++) {
            String t = s.get(i).trim();
            if (t.startsWith("Version:")) {
                versionLines++;
                if (line == null) {
                    line = t.substring("Version:".length()).trim();
                }
            }
        }
        if (line == null) {
            log("environment: setup.txt has no Version: line");
        } else if (versionLines > 1) {
            log("environment: setup.txt has " + versionLines + " Version: lines, the first is used");
        }
        Map<String, String> fields = new TreeMap<String, String>();
        if (line != null) {
            String[] segments = line.split(" \\| ", -1);
            for (int i = 0; i < segments.length; i++) {
                int colon = segments[i].indexOf(": ");
                if (colon > 0) {
                    String key = segments[i].substring(0, colon).trim();
                    String val = segments[i].substring(colon + 2).trim();
                    if (val.length() > 0 && !fields.containsKey(key)) {
                        fields.put(key, val);
                    }
                }
            }
        }

        StringBuilder block = new StringBuilder();
        block.append(str("environment")).append(": {\n");
        for (int k = 0; k < ENVIRONMENT_KEYS.length; k++) {
            String field = ENVIRONMENT_FIELDS[k];
            String v = field == null ? ENVIRONMENT_SOLVER : fields.get(field);
            if (v != null && "precision".equals(ENVIRONMENT_KEYS[k])) {
                v = v.endsWith("-r8") ? "double" : "mixed";
            }
            if (v == null) {
                log("environment." + ENVIRONMENT_KEYS[k] + " null: " + field + " not on the Version: line");
            }
            block.append("    ").append(str(ENVIRONMENT_KEYS[k])).append(": ").append(str(v))
              .append(k == ENVIRONMENT_KEYS.length - 1 ? "\n" : ",\n");
        }
        block.append("  }");

        File summary = new File(pointDir, SUMMARY_NAME);
        String json = new String(readBytes(caseSummary != null ? caseSummary : summary), StandardCharsets.UTF_8);
        int root = json.indexOf('{');
        String key = ENVIRONMENT_PLACEHOLDER;
        int at = root < 0 ? -1 : keyValueStart(json, root, key);
        if (at < 0) {
            key = "environment";
            at = root < 0 ? -1 : keyValueStart(json, root, key);
        }
        if (at < 0 && caseSummary != null && root >= 0) {
            int end = matchingBrace(json, root);
            if (end < 0) {
                throw new RuntimeException("output_exporter: " + SUMMARY_NAME + " of the case macro is not an object.");
            }
            int last = end - 1;
            while (last > root && Character.isWhitespace(json.charAt(last))) {
                last--;
            }
            String out = json.substring(0, last + 1) + (last == root ? "\n  " : ",\n  ") + block + "\n"
                + json.substring(end);
            String check = jsonScalar(out, "environment", "platform");
            writeTextFile(summary, out);
            log(rel(summary) + " written: " + SUMMARY_NAME + " of the case macro plus environment (platform " + check + ")");
            return;
        }
        if (at < 0) {
            throw new RuntimeException("output_exporter: summary.json has neither '" + ENVIRONMENT_PLACEHOLDER
                + "' nor 'environment' at the root.");
        }
        int start = json.lastIndexOf(str(key), at);
        int open = at;
        while (open < json.length() && Character.isWhitespace(json.charAt(open))) {
            open++;
        }
        int close = open < json.length() && json.charAt(open) == '{' ? matchingBrace(json, open) : -1;
        if (start < 0 || close < 0) {
            throw new RuntimeException("output_exporter: summary.json '" + key + "' is not an object.");
        }
        String out = json.substring(0, start) + block + json.substring(close + 1);
        String check = jsonScalar(out, "environment", "platform");
        writeTextFile(summary, out);
        log(rel(summary) + " " + key + " replaced by environment (platform " + check + ")"
            + (caseSummary != null ? ", from " + SUMMARY_NAME + " of the case macro" : ""));
    }

    // Index of the '}' closing the object that opens at 'open', strings skipped, or -1.
    private static int matchingBrace(String s, int open) {
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return c == '}' ? i : -1;
                }
            }
        }
        return -1;
    }

    // "Iterations: N" in the SOLUTION block the trim writes last.
    private static Long setupIterations(List<String> s) {
        int start = indexOfLine(s, "SOLUTION", 0);
        if (start < 0) {
            return null;
        }
        for (int i = start + 1; i < s.size(); i++) {
            String t = s.get(i).trim();
            if (t.startsWith("Iterations:")) {
                return parseLongOrNull(firstToken(t.substring("Iterations:".length())));
            }
        }
        return null;
    }

    // "Cells: N" of the region node card.body.fluid_region.name inside the "--- Representations ---" section. The trim
    // writes the region's properties right after its node line. Missing is a hard failure.
    private Long setupCells(List<String> s) {
        int start = indexOfLine(s, "--- Representations ---", 0);
        if (start < 0) {
            throw new RuntimeException("output_exporter: setup.txt has no 'Representations' section; cells anchor unreadable.");
        }
        boolean inFluid = false;
        for (int i = start + 1; i < s.size(); i++) {
            String t = s.get(i).trim();
            if (t.startsWith("--- ")) {
                break;
            }
            if (t.equals(fluidRegion)) {
                inFluid = true;
                continue;
            }
            if (inFluid && t.startsWith("Cells:")) {
                Long v = parseLongOrNull(firstToken(t.substring("Cells:".length())));
                if (v == null) {
                    throw new RuntimeException("output_exporter: setup.txt cells anchor is not an integer: '" + t + "'.");
                }
                return v;
            }
        }
        throw new RuntimeException("output_exporter: setup.txt has no 'Cells:' line under region '" + fluidRegion
            + "' in 'Representations'.");
    }

    // A "Value: <number>" property under the report node "cd" in "--- Reports ---". The published
    // trims seen so far list report definitions and no value, so null is the expected result; it is
    // recorded, not enforced.
    private static Double setupCd(List<String> s) {
        int start = indexOfLine(s, "--- Reports ---", 0);
        if (start < 0) {
            return null;
        }
        boolean inCd = false;
        for (int i = start + 1; i < s.size(); i++) {
            String t = s.get(i).trim();
            if (t.startsWith("--- ")) {
                break;
            }
            boolean isNode = t.length() > 0 && t.indexOf(": ") < 0 && !t.endsWith(":");
            if (isNode) {
                if (inCd) {
                    break;
                }
                inCd = t.equals("cd");
                continue;
            }
            if (inCd && t.startsWith("Value:")) {
                String tok = firstToken(t.substring("Value:".length()));
                try {
                    return Double.valueOf(Double.parseDouble(tok));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static int indexOfLine(List<String> s, String exact, int from) {
        for (int i = from; i < s.size(); i++) {
            if (s.get(i).trim().equals(exact)) {
                return i;
            }
        }
        return -1;
    }

    private static String firstToken(String v) {
        String t = v.trim();
        int sp = t.indexOf(' ');
        return sp >= 0 ? t.substring(0, sp) : t;
    }

    private static Long parseLongOrNull(String v) {
        try {
            return Long.valueOf(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- summary.json: a scalar at depth one of a top-level block. Enough for the file run_macro
    // writes; not a general JSON parser.
    private long jsonLong(String json, String block, String key) {
        String raw = jsonScalar(json, block, key);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new RuntimeException("output_exporter: summary.json " + block + "." + key
                + " is not an integer: '" + raw + "'.");
        }
    }

    private double jsonDouble(String json, String block, String key) {
        String raw = jsonScalar(json, block, key);
        if ("null".equals(raw)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new RuntimeException("output_exporter: summary.json " + block + "." + key
                + " is not a number: '" + raw + "'.");
        }
    }

    // A string scalar with no escapes in it (convergence.status is one word of an enum).
    private String jsonString(String json, String block, String key) {
        String raw = jsonScalar(json, block, key);
        if (raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"'
            || raw.indexOf('\\') >= 0) {
            throw new RuntimeException("output_exporter: summary.json " + block + "." + key
                + " is not a plain string: '" + raw + "'.");
        }
        return raw.substring(1, raw.length() - 1);
    }

    private String jsonScalar(String json, String block, String key) {
        int root = json.indexOf('{');
        int b = root < 0 ? -1 : keyValueStart(json, root, block);
        while (b >= 0 && b < json.length() && Character.isWhitespace(json.charAt(b))) {
            b++;
        }
        if (b < 0 || b >= json.length() || json.charAt(b) != '{') {
            throw new RuntimeException("output_exporter: summary.json has no block '" + block + "'.");
        }
        int v = keyValueStart(json, b, key);
        if (v < 0) {
            throw new RuntimeException("output_exporter: summary.json has no key '" + block + "." + key + "'.");
        }
        int e = v;
        while (e < json.length() && json.charAt(e) != ',' && json.charAt(e) != '}' && json.charAt(e) != '\n') {
            e++;
        }
        return json.substring(v, e).trim();
    }

    // Index just past the ':' of "key" at depth one of the object opening at 'open', or -1.
    private static int keyValueStart(String s, int open, String key) {
        int depth = 0;
        boolean inStr = false;
        int strStart = -1;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inStr = false;
                    if (depth == 1 && s.substring(strStart + 1, i).equals(key)) {
                        int j = i + 1;
                        while (j < s.length() && Character.isWhitespace(s.charAt(j))) {
                            j++;
                        }
                        if (j < s.length() && s.charAt(j) == ':') {
                            return j + 1;
                        }
                    }
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                strStart = i;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return -1;
                }
            }
        }
        return -1;
    }

    // ================================================================= generic JSON read
    // Copied from manifest_writer.java (JsonReader family), used here for card.json: objects become
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
            return new RuntimeException("output_exporter: " + name + " is not valid JSON (" + what + " at offset " + i + ").");
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
            throw new RuntimeException("output_exporter: " + what + " is not a JSON object.");
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
                throw new RuntimeException("output_exporter: " + file + " has no '" + dotted + "'.");
            }
            cur = m.get(path[k]);
        }
        return cur;
    }

    private static long requireLong(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        String dotted = String.join(".", path);
        if (!(v instanceof JsonNumber)) {
            throw new RuntimeException("output_exporter: " + file + " " + dotted + " is not a number.");
        }
        try {
            return Long.parseLong(((JsonNumber) v).raw);
        } catch (NumberFormatException e) {
            throw new RuntimeException("output_exporter: " + file + " " + dotted + " is not an integer: '"
                + ((JsonNumber) v).raw + "'.");
        }
    }

    private static String requireString(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof String)) {
            throw new RuntimeException("output_exporter: " + file + " " + String.join(".", path) + " is not a string.");
        }
        return (String) v;
    }

    private static double requireDouble(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof JsonNumber)) {
            throw new RuntimeException("output_exporter: " + file + " " + String.join(".", path) + " is not a number.");
        }
        return Double.parseDouble(((JsonNumber) v).raw);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireList(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof List)) {
            throw new RuntimeException("output_exporter: " + file + " " + String.join(".", path) + " is not an array.");
        }
        return (List<Object>) v;
    }

    private static long integerOf(Object v, String what) {
        if (v instanceof JsonNumber) {
            try {
                return Long.parseLong(((JsonNumber) v).raw);
            } catch (NumberFormatException e) {
                // falls through to the throw
            }
        }
        throw new RuntimeException("output_exporter: " + CARD_NAME + " " + what + " is not an integer.");
    }

    private static double[] doubles(List<Object> l, int n, String what) {
        if (l.size() != n) {
            throw new RuntimeException("output_exporter: " + CARD_NAME + " " + what + " has " + l.size()
                + " entries, expected " + n + ".");
        }
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            if (!(l.get(i) instanceof JsonNumber)) {
                throw new RuntimeException("output_exporter: " + CARD_NAME + " " + what + "[" + i + "] is not a number.");
            }
            a[i] = Double.parseDouble(((JsonNumber) l.get(i)).raw);
        }
        return a;
    }

    // ================================================================= logging / files
    private void log(String s) {
        sim.println("output_exporter: " + s);
        if (runLog != null) {
            appendLine(runLog, LOG_PREFIX + s);
        }
    }

    // One FAIL line per run; the exception is rethrown by the caller. Before the point is resolved
    // there is no run_log.txt to write to, so the reason goes to the output window only.
    private void fail(RuntimeException e) {
        String reason = scrub(String.valueOf(e.getMessage())).replace('\n', ' ').replace('\r', ' ');
        sim.println("output_exporter: FAIL " + reason);
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

    private static String four(long n) {
        String s = Long.toString(n);
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
            throw new RuntimeException("output_exporter: could not read '" + rel(f) + "'.");
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

    // The file as bytes, line endings untouched (summary.json is rewritten byte for byte but one block).
    private byte[] readBytes(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[65536];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("output_exporter: could not read '" + rel(f) + "'.");
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

    private String readText(File f) {
        List<String> lines = readLines(f);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    private void writeTextFile(File f, String content) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            w.write(content);
        } catch (Exception e) {
            throw new RuntimeException("output_exporter: could not write '" + rel(f) + "'.");
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

    private void appendLine(File f, String line) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
            w.write(line);
            w.write('\n');
        } catch (Exception e) {
            throw new RuntimeException("output_exporter: could not append to '" + rel(f) + "'.");
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
