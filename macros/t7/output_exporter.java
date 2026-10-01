// output_exporter.java
// T7 chain -- session B. Simcenter STAR-CCM+ 2606.
// Runs on an already solved cube_re<NNNN>.sim and exports, under work/cube_re<NNNN>/, the raw plane
// tables, the body view in colour and in gray, the four plane images, setup.txt and the freshness
// record, and replaces the environment_placeholder of summary.json with the environment block read from
// setup.txt. It never saves the simulation. The only objects it creates are the transient ones of the
// body view (spec v16, 3.2 and decision 77): the scene view_cp_body, its scalar displayer disp_cp_body
// and its title title_cp_body. They carry no LLM_ prefix, are not in the closed list and go with the
// session. Every other object it touches is resolved by name and a missing one throws with its name.
// Changing the parts of a displayer, a camera or an annotation text is not creating. The capsule itself
// is assembled later, outside the session, by build_capsule.py.
//
// Signatures come from run_macro.java and manifest_writer.java (same repository, compiled against
// 2606), from TrimReportForAI.java of the public simulation-capsule repository (plain Java only), from
// the calls recorded in the 2606 GUI on 29.09 (displayer parts, legend lookup table) and on 30.09
// (title font and height, legend width, position and shadow; body view material, emissive, light and
// gray), or from body_view_test.java. Anything else
// is marked TODO(unverified signature) with the reason.

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import star.base.neo.DoubleVector;
import star.base.neo.NamedObject;
import star.base.report.Report;
import star.common.Boundary;
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
import star.vis.PredefinedLookupTable;
import star.vis.ScalarDisplayer;
import star.vis.ScalarFillMode;
import star.vis.Scene;
import star.vis.SimpleAnnotation;
import star.vis.SimpleAnnotationProp;
import star.vis.VisProjectionMode; // TODO(unverified signature): same import as run_macro.java
// TODO(unverified signature): package of the next four. The recordings and body_view_test.java use them
// under "import star.vis.*; import star.common.*; import star.base.neo.*;", so the class names are
// verified and the package is not; star.vis is the inferred one. If javac rejects an import, the class
// is in one of the other two.
import star.vis.AdvancedRenderingMaterials;
import star.vis.BlackWhiteLookupTable;
import star.vis.DisplayerVisibilityOverride;
import star.vis.Light;

public class output_exporter extends StarMacro {

    // ---------------------------------------------------------------- constants
    // Same properties file and same point directory rule as run_macro.java: re_target from
    // LLM_point.properties, point directory work/cube_re<NNNN>/ under the session directory.
    private static final String PROPS_NAME = "LLM_point.properties";
    private static final String LOG_PREFIX = "exporter: ";
    private static final String WORK_DIR = "work";

    private static final String[] PLANES = { "y0", "x1", "x2", "x4" };
    // rows_expected = (intervals + 1) per axis of grid_<plane>: grid_y0 is 32 x 12 intervals (33 x 13),
    // grid_x1, grid_x2 and grid_x4 are 12 x 12 intervals (13 x 13). Same order as PLANES.
    private static final int[] PLANE_ROWS_EXPECTED = { 33 * 13, 13 * 13, 13 * 13, 13 * 13 };
    // Column indices into CSV_COLUMNS per plane, same order as PLANES: the two in-plane coordinates,
    // then the plane's constant coordinate. y0: x_D, z_D, y_D. x-planes: y_D, z_D, x_D.
    private static final int[][] PLANE_AXES = { { 0, 2, 1 }, { 1, 2, 0 }, { 1, 2, 0 }, { 1, 2, 0 } };
    // Cube half-width in units of D. A lattice node missing from a plane CSV is tolerated only inside
    // the body: |x_D|, |y_D| and |z_D| all within this value (the section has no vertices there).
    private static final double CUBE_HALF_WIDTH_D = 0.5;
    // Coordinates closer than this (in D) are the same lattice value; also the slack on the body test.
    private static final double COORD_TOL = 1.0e-6;

    // Columns of the raw plane CSV, in this order. Since v12 the tables hold field functions under
    // these same names, so the header is taken as exported: no name mapping.
    private static final String[] CSV_COLUMNS = {
        "x_D", "y_D", "z_D", "cp", "u_over_U", "v_over_U", "w_over_U"
    };

    private static final String[] GRID_PARTS = { "grid_y0", "grid_x1", "grid_x2", "grid_x4" };
    private static final String[] INHERITED_FIELD_FUNCTIONS = {
        "cp", "u_over_U", "v_over_U", "w_over_U", "x_D", "y_D", "z_D"
    };

    // Views and plane images: resolution goes in the print call; the file is kept as printed, no crop.
    private static final int VIEW_MAGNIFICATION = 1;
    private static final int VIEW_WIDTH = 1024;
    private static final int VIEW_HEIGHT = 739;

    // A summary report older than the start of this macro (minus file-time granularity) is stale.
    private static final long FRESHNESS_SLACK_MS = 2000L;

    // Plane images (contract v10): one scene, pointed at section_<plane> and rendered once per plane.
    private static final String PLANE_SCENE = "LLM_plane_u_over_U";
    private static final String PLANE_DISP = "LLM_disp_plane_u_over_U";
    private static final String PLANE_BODY = "LLM_body_plane_u_over_U";
    private static final String PLANE_TITLE = "LLM_title_plane_u_over_U";
    // v12: the field label and the field function name are the same.
    private static final String PLANE_FIELD = "u_over_U";
    // Cameras, lab frame in metres with D = 1 m (checked in requireUnitD), as in run_macro.java.
    // y0 looks along +y towards y = 0; x1, x2 and x4 look along -x towards x = 1, 2 and 4 D, z up.
    // Same order as PLANES.
    private static final double[][] PLANE_CAMERA_FOCAL = {
        { 2.0, 0.0, 0.0 }, { 1.0, 0.0, 0.0 }, { 2.0, 0.0, 0.0 }, { 4.0, 0.0, 0.0 }
    };
    private static final double[][] PLANE_CAMERA_POSITION = {
        { 2.0, -20.0, 0.0 }, { 21.0, 0.0, 0.0 }, { 22.0, 0.0, 0.0 }, { 24.0, 0.0, 0.0 }
    };
    private static final double[] PLANE_CAMERA_SCALE = { 2.9, 1.8, 1.8, 1.8 };
    private static final double[] CAMERA_VIEW_UP = { 0.0, 0.0, 1.0 };

    // Body view (spec v16, 8.1): cp on the six cube_* boundaries, seen three quarters from downstream.
    // Transient objects of this session (decision 77), names of decision 79. The scene is created by
    // createScalarScene with the displayers "Scalar 1" and "Outline 1" (spec 3.1, signature 12); the
    // first is renamed, the second is hidden and keeps its name.
    private static final String BODY_SCENE = "view_cp_body";
    private static final String BODY_DISP = "disp_cp_body";
    private static final String BODY_TITLE = "title_cp_body";
    private static final String BODY_FIELD = "cp";
    private static final String BODY_FILE = "views/view_cp_body.png";
    private static final String BODY_GRAY_FILE = "diffsrc/view_cp_body.png";
    private static final String BODY_NEW_SCALAR = "Scalar 1";
    private static final String BODY_NEW_OUTLINE = "Outline 1";
    private static final String[] BODY_BOUNDARIES = {
        "cube_back", "cube_bottom", "cube_front", "cube_left", "cube_right", "cube_top"
    };
    // Camera of decision 80, one CurrentView.setInput call: focal point, position, view up, parallel
    // scale, projection mode (1, parallel) and view angle, as in body_view_test.java.
    private static final double[] BODY_CAMERA_FOCAL = { 0.07, 0.099, -0.008 };
    private static final double[] BODY_CAMERA_POSITION = { 37.781, -25.043, 20.107 };
    private static final double BODY_CAMERA_SCALE = 0.988;
    private static final int BODY_CAMERA_PROJECTION = 1;
    private static final double BODY_CAMERA_VIEW_ANGLE = 30.0;
    // Colour bar of the body (decision 82): [-2.8, 1.1]; Re 100 saturates at the edges. The rest of the
    // bar is the one prepare gives the plane scene (run_macro.java).
    private static final double BODY_RANGE_MIN = -2.8;
    private static final double BODY_RANGE_MAX = 1.1;
    private static final String COLOR_BAR_COLORMAP = "blue-red uniform perception";
    private static final int COLOR_BAR_LEVELS = 256;
    private static final int COLOR_BAR_LABELS = 6;
    // Colour without light (decision 81, signature 13): emissive material of intensity 1.0, no surface
    // shadows, no secondary rays, the four lights of the scene off.
    private static final double BODY_EMISSIVE_INTENSITY = 1.0;
    private static final String[] BODY_LIGHTS = { "Light 1", "Light 2", "Light 3", "Light 4" };
    // Gray frame for the diff (8.3, decision 83), recording view_cp_body_gray: lookup table "grayscale"
    // (a BlackWhiteLookupTable) and solid magenta background; no title, so the diff needs no crop.
    private static final String GRAY_COLORMAP = "grayscale";
    private static final double[] GRAY_BACKGROUND = { 1.0, 0.0, 1.0 };
    // The two top corners of the gray frame must read magenta within this many levels per channel: the
    // gray frame has no title, and the legend (bottom band, x from 0.6 to 0.9 of the width) and the
    // body stay clear of them. The bottom corners are only logged, since a default annotation of a new
    // scene could sit there.
    private static final int GRAY_CORNER_TOLERANCE = 2;
    // Top band of the titles, as run_macro.java sets it on the plane title (GUI recording of 29.09).
    private static final double[] TITLE_POSITION = { 0.023, 0.916, 0.0 };
    // Six-argument print, as recorded and as body_view_test.java ran it in -batch (spec 3.1, signature 14).
    private static final String BODY_EXPORT_CALL = "printAndWait(file, 1, 1024, 739, true, false)";

    // Render layout (v12), set on the body view and on the plane scene before every printAndWait and
    // never saved. Title: font on the annotation, height on the scene's prop of it; the position and
    // the shadow of the title stay as prepare sets them. Legend: width, position and no shadow, and
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
    private File pointDir;
    private File runLog;
    private String nnnn;
    private int reTarget;
    private final int[] planeRowsWritten = new int[PLANES.length];
    private final int[] planeMissingInBody = new int[PLANES.length];
    // Numbers of the titles, read once from summary.json (readTitleNumbers).
    private String titleStatus;
    private long titleIterations;
    private long titleValue;
    // The body view entry of plane_render.json, written by exportBodyView for exportPlaneImages.
    private String bodyViewJson;

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();
        startMillis = System.currentTimeMillis();
        try {
            Properties props = loadProperties();
            reTarget = requireIntProperty(props, "re_target");
            if (reTarget != 100 && reTarget != 300 && reTarget != 1000 && reTarget != 3000) {
                throw new RuntimeException("output_exporter: property 're_target' must be 100, 300, 1000 or 3000, got "
                    + reTarget + ".");
            }
            nnnn = four(reTarget);
            resolvePointDir();
            log("start re_target=" + reTarget + " point=" + rel(pointDir));

            checkObjects();
            readTitleNumbers();
            exportPlanes();
            exportBodyView();
            exportPlaneImages();
            File setup = writeSetupTxt();
            checkStaleness(setup);
            writeEnvironment(setup);

            log("done");
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= properties / point directory
    // Same lookup order as run_macro.loadProperties: session directory first, then the directory
    // returned by Simulation.getSessionDirFile().
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
            throw new RuntimeException("output_exporter: could not locate '" + PROPS_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }

        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(candidate);
            p.load(in);
        } catch (Exception e) {
            throw new RuntimeException("output_exporter: could not parse '" + PROPS_NAME + "'.");
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
            throw new RuntimeException("output_exporter: property '" + key + "' missing or empty in " + PROPS_NAME + ".");
        }
        v = v.trim();
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new RuntimeException("output_exporter: property '" + key + "' is not an integer: '" + v + "'.");
        }
    }

    // Same base as run_macro.sessionRelativeDir, but the point directory is not created here: run
    // mode must already have written it. Only planes/, views/ and diffsrc/ are created, inside it.
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

    // BoundaryManager.getBoundary(String), as body_view_test.java calls it; try/catch covers a throw or a
    // null return.
    private Boundary boundary(Region r, String name) {
        Object o;
        try {
            o = r.getBoundaryManager().getBoundary(name);
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            throw new RuntimeException("output_exporter: missing boundary '" + name + "' in region '"
                + r.getPresentationName() + "'.");
        }
        return (Boundary) o;
    }

    // The colour bar's predefined table, looked up as run_macro.colorBarLookupTable does.
    private PredefinedLookupTable colormap() {
        Object o = sim.get(LookupTableManager.class).getObject(COLOR_BAR_COLORMAP);
        if (!(o instanceof PredefinedLookupTable)) {
            throw new RuntimeException("output_exporter: lookup table '" + COLOR_BAR_COLORMAP + "' read as "
                + (o == null ? "null" : o.getClass().getName()) + ", not a PredefinedLookupTable.");
        }
        return (PredefinedLookupTable) o;
    }

    // The gray table of the diff frame, with the lookup and the class of the recording view_cp_body_gray.
    private BlackWhiteLookupTable grayColormap() {
        Object o = sim.get(LookupTableManager.class).getObject(GRAY_COLORMAP);
        if (!(o instanceof BlackWhiteLookupTable)) {
            throw new RuntimeException("output_exporter: lookup table '" + GRAY_COLORMAP + "' read as "
                + (o == null ? "null" : o.getClass().getName()) + ", not a BlackWhiteLookupTable.");
        }
        return (BlackWhiteLookupTable) o;
    }

    // Presentation names of the Scene objects of the scene manager. SceneManager.getObjects() yields
    // SceneBase, not Scene, so the loop filters with instanceof Scene (body_view_test.java).
    private Set<String> sceneNames() {
        Set<String> names = new HashSet<String>();
        for (Object o : sim.getSceneManager().getObjects()) {
            if (o instanceof Scene) {
                names.add(((Scene) o).getPresentationName());
            }
        }
        return names;
    }

    // Every object the exporter touches, before anything is written. The objects of the four retired
    // cut views (LLM_view_*, LLM_disp_*, LLM_body_*, LLM_title_* on y0 and x2) are neither required nor
    // touched: the .sim files solved on 30.09 still carry them, the ones prepared from v16 do not.
    private void checkObjects() {
        Region fluid = region("fluid");
        for (int i = 0; i < GRID_PARTS.length; i++) {
            derivedPart(GRID_PARTS[i]);
        }
        for (int i = 0; i < INHERITED_FIELD_FUNCTIONS.length; i++) {
            fieldFunction(INHERITED_FIELD_FUNCTIONS[i]);
        }
        report("cd");
        report("LLM_cell_count");
        for (int p = 0; p < PLANES.length; p++) {
            table("LLM_table_" + PLANES[p]);
        }
        // Body view: its six boundaries and its two lookup tables; its transient names must be free.
        for (int i = 0; i < BODY_BOUNDARIES.length; i++) {
            boundary(fluid, BODY_BOUNDARIES[i]);
        }
        colormap();
        grayColormap();
        if (sceneNames().contains(BODY_SCENE)) {
            throw new RuntimeException("output_exporter: scene '" + BODY_SCENE + "' already exists; it is a "
                + "transient object of session B and must not be saved in the .sim.");
        }
        if (sim.getAnnotationManager().hasObject(BODY_TITLE) != null) {
            throw new RuntimeException("output_exporter: annotation '" + BODY_TITLE + "' already exists; it is a "
                + "transient object of session B and must not be saved in the .sim.");
        }
        Scene plane = scene(PLANE_SCENE);
        planeDisplayer(plane);
        if (!plane.getDisplayerManager().hasDisplayer(PLANE_BODY)) {
            throw new RuntimeException("output_exporter: missing displayer '" + PLANE_BODY + "' in scene '"
                + PLANE_SCENE + "'.");
        }
        planeTitle();
        for (int p = 0; p < PLANES.length; p++) {
            derivedPart("section_" + PLANES[p]);
        }
        requireUnitD();
        log("objects found");
    }

    // ================================================================= step 1: planes
    private void exportPlanes() {
        File planesDir = subDir(pointDir, "planes");
        for (int p = 0; p < PLANES.length; p++) {
            String plane = PLANES[p];
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
                throw new RuntimeException("output_exporter: export of '" + name + "' failed: "
                    + e.getMessage());
            }
            int rows = normalisePlaneCsv(out, name);
            int missingInBody = checkLattice(out, p);
            planeRowsWritten[p] = rows;
            planeMissingInBody[p] = missingInBody;
            log("plane " + plane + " rows " + rows + "/" + PLANE_ROWS_EXPECTED[p]
                + " missing_in_body " + missingInBody);
        }
    }

    // Rebuilds the lattice from the CSV just written: the unique values of the two in-plane coordinates
    // must span exactly rows_expected nodes. A node absent from the CSV is tolerated only inside the
    // body (all three coordinates within CUBE_HALF_WIDTH_D, the plane's constant one included); any
    // other absent node fails. Returns the number of absent nodes inside the body.
    private int checkLattice(File f, int p) {
        String plane = PLANES[p];
        int a = PLANE_AXES[p][0];
        int b = PLANE_AXES[p][1];
        int c = PLANE_AXES[p][2];
        List<String> lines = readLines(f);
        Map<Long, Double> valuesA = new TreeMap<Long, Double>();
        Map<Long, Double> valuesB = new TreeMap<Long, Double>();
        Set<String> present = new HashSet<String>();
        double constant = Double.NaN;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().length() == 0) {
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
        if (n1 * n2 != PLANE_ROWS_EXPECTED[p]) {
            throw new RuntimeException(plane + " lattice incomplete (" + CSV_COLUMNS[a] + " x " + CSV_COLUMNS[b]
                + " = " + n1 + " x " + n2 + ", rows_expected " + PLANE_ROWS_EXPECTED[p] + ")");
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
                    throw new RuntimeException(plane + " missing node (" + xyz[0] + "," + xyz[1] + "," + xyz[2] + ")");
                }
                missingInBody++;
            }
        }
        return missingInBody;
    }

    private static long latticeKey(double v) {
        return Math.round(v / COORD_TOL);
    }

    private static boolean insideBody(double[] xyz) {
        for (int k = 0; k < xyz.length; k++) {
            if (!(Math.abs(xyz[k]) <= CUBE_HALF_WIDTH_D + COORD_TOL)) {
                return false;
            }
        }
        return true;
    }

    // Rewrites the solver's CSV in place with the CSV_COLUMNS header and order; values are copied
    // verbatim. Returns the number of data rows.
    // TODO(unverified behaviour): the header text XyzInternalTable.export writes is not documented in
    // either source. A column is matched when its header, stripped of quotes and of any unit suffix
    // in "(...)" or "[...]", equals the field function name. No match or two matches throws with the
    // header found, so the first real run shows the format.
    private int normalisePlaneCsv(File f, String tableName) {
        List<String> lines = readLines(f);
        int h = 0;
        while (h < lines.size() && lines.get(h).trim().length() == 0) {
            h++;
        }
        if (h >= lines.size()) {
            throw new RuntimeException("output_exporter: " + rel(f) + " from '" + tableName + "' is empty.");
        }
        String[] header = splitCsv(lines.get(h));
        int[] col = new int[CSV_COLUMNS.length];
        for (int k = 0; k < CSV_COLUMNS.length; k++) {
            col[k] = -1;
            for (int j = 0; j < header.length; j++) {
                if (CSV_COLUMNS[k].equals(headerName(header[j]))) {
                    if (col[k] >= 0) {
                        throw new RuntimeException("output_exporter: " + rel(f) + ": two columns match '"
                            + CSV_COLUMNS[k] + "'; header is: " + lines.get(h));
                    }
                    col[k] = j;
                }
            }
            if (col[k] < 0) {
                throw new RuntimeException("output_exporter: " + rel(f) + ": no column for '"
                    + CSV_COLUMNS[k] + "'; header is: " + lines.get(h));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < CSV_COLUMNS.length; k++) {
            sb.append(k == 0 ? "" : ",").append(CSV_COLUMNS[k]);
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

    // The numbers every title carries, from summary.json (spec 5.1): status, iterations and Re.
    private void readTitleNumbers() {
        String json = readText(new File(pointDir, "summary.json"));
        titleStatus = jsonString(json, "convergence", "status");
        titleIterations = jsonLong(json, "convergence", "iterations");
        titleValue = jsonLong(json, "sweep", "value");
        if (titleValue != reTarget) {
            throw new RuntimeException("output_exporter: summary.json sweep.value is " + titleValue
                + " but re_target is " + reTarget + ".");
        }
    }

    // <field> on <place>, Re = <n>, <status>, snapshot at iteration <N> (spec 8).
    private String titleText(String field, String place) {
        return field + " on " + place + ", Re = " + titleValue + ", " + titleStatus
            + ", snapshot at iteration " + titleIterations;
    }

    // ================================================================= step 2: body view
    // Spec v16 3.2 point 3, 8.1 and 8.3. One transient scene, two prints: views/view_cp_body.png in
    // colour with its title, then diffsrc/view_cp_body.png in gray on magenta without the title. Nothing
    // is saved. The camera and the colour bar are read after the colour print, before the gray changes,
    // and go to plane_render.json for session C, where this scene no longer exists.
    private void exportBodyView() {
        File viewsDir = subDir(pointDir, "views");
        File diffsrcDir = subDir(pointDir, "diffsrc");
        File out = new File(viewsDir, new File(BODY_FILE).getName());
        File gray = new File(diffsrcDir, new File(BODY_GRAY_FILE).getName());
        Region fluid = region("fluid");

        // ---- 1. scene. createScalarScene is called as a statement, as recorded; the new scene is found
        // by comparing scene names before and after (body_view_test.java).
        Set<String> before = sceneNames();
        sim.getSceneManager().createScalarScene("Scalar Scene", "Outline", "Scalar", null);
        Scene sc = null;
        for (Object o : sim.getSceneManager().getObjects()) {
            if (o instanceof Scene && !before.contains(((Scene) o).getPresentationName())) {
                if (sc != null) {
                    throw new RuntimeException("output_exporter: more than one new scene after createScalarScene: '"
                        + sc.getPresentationName() + "', '" + ((Scene) o).getPresentationName() + "'.");
                }
                sc = (Scene) o;
            }
        }
        if (sc == null) {
            throw new RuntimeException("output_exporter: no new scene found after createScalarScene.");
        }
        sc.setPresentationName(BODY_SCENE);
        sc.resetCamera();
        log("transient scene " + BODY_SCENE + " created (not saved)");

        // ---- 2. displayers: the scalar one renamed and pointed at cp on the six cube_* boundaries; the
        // outline hidden. Fill mode and clip with the calls of run_macro.provisionScalarDisplayer.
        ScalarDisplayer sd = scalarDisplayer(sc, BODY_SCENE, BODY_NEW_SCALAR);
        sd.setPresentationName(BODY_DISP);
        sd.setFillMode(ScalarFillMode.NODE_FILLED); // Smooth Filled
        // The function goes first: changing it resets a manual range.
        sd.getScalarDisplayQuantity().setFieldFunction(fieldFunction(BODY_FIELD));
        sd.getScalarDisplayQuantity().setClip(ClipMode.NONE);
        sd.getInputParts().setQuery(null);
        sd.getInputParts().setObjects(boundary(fluid, BODY_BOUNDARIES[0]), boundary(fluid, BODY_BOUNDARIES[1]),
            boundary(fluid, BODY_BOUNDARIES[2]), boundary(fluid, BODY_BOUNDARIES[3]),
            boundary(fluid, BODY_BOUNDARIES[4]), boundary(fluid, BODY_BOUNDARIES[5]));
        if (!sc.getDisplayerManager().hasDisplayer(BODY_NEW_OUTLINE)) {
            throw new RuntimeException("output_exporter: missing displayer '" + BODY_NEW_OUTLINE + "' in scene '"
                + BODY_SCENE + "'.");
        }
        Object outline = sc.getDisplayerManager().getObject(BODY_NEW_OUTLINE);
        if (!(outline instanceof PartDisplayer)) {
            throw new RuntimeException("output_exporter: displayer '" + BODY_NEW_OUTLINE + "' in scene '"
                + BODY_SCENE + "' is not a PartDisplayer.");
        }
        ((PartDisplayer) outline).setVisibilityOverrideMode(DisplayerVisibilityOverride.HIDE_ALL_PARTS);

        // ---- 3. colour bar: colormap, levels and labels as prepare sets them on the plane scene; range
        // with the calls of run_macro.fixDisplayRange, read back.
        Legend legend = sd.getLegend();
        legend.setLookupTable(colormap());
        legend.setLevels(COLOR_BAR_LEVELS);
        legend.setNumberOfLabels(COLOR_BAR_LABELS);
        sd.getScalarDisplayQuantity().setAutoRange(AutoRangeMode.NONE);
        double[] range = { BODY_RANGE_MIN, BODY_RANGE_MAX };
        sd.getScalarDisplayQuantity().setRange(new DoubleVector(range));
        Object rangeRead = vector(sd.getScalarDisplayQuantity().getRange(), 2);
        if (!(rangeRead instanceof double[]) || !near((double[]) rangeRead, range)) {
            throw new RuntimeException("output_exporter: range of '" + BODY_DISP + "' read back as "
                + (rangeRead instanceof double[] ? Arrays.toString((double[]) rangeRead) : ((Unexpected) rangeRead).text)
                + ", declared " + Arrays.toString(range) + ".");
        }

        // ---- 4. camera of decision 80, one setInput call as in body_view_test.java.
        sc.getCurrentView().setInput(new DoubleVector(BODY_CAMERA_FOCAL), new DoubleVector(BODY_CAMERA_POSITION),
            new DoubleVector(CAMERA_VIEW_UP), BODY_CAMERA_SCALE, BODY_CAMERA_PROJECTION, BODY_CAMERA_VIEW_ANGLE);

        // ---- 5. colour without light (signature 13).
        applyUnlit(sc, BODY_SCENE, sd);

        // ---- 6. title: created and bound to the scene with the calls of run_macro.provisionTitle,
        // placeTitle and applyTitleBand; text with the numbers of summary.json.
        SimpleAnnotation title = sim.getAnnotationManager().createSimpleAnnotation();
        title.setPresentationName(BODY_TITLE);
        title.setText(titleText(BODY_FIELD, "body"));
        title.setShadow(false);
        AnnotationProp titleProp = sc.getAnnotationPropManager().createPropForAnnotation(title);
        titleProp.setVisible(true);
        applyRenderLayout(sc, BODY_SCENE, sd, title, BODY_TITLE);
        ((SimpleAnnotationProp) sc.getAnnotationPropManager().getObject(BODY_TITLE))
            .setPosition(new DoubleVector(TITLE_POSITION));

        // ---- 7. colour print, then what session C needs, read now.
        printBody(sc, out);
        String camera = cameraJson(sc, BODY_SCENE);
        String colorbar = colorbarJson(sd, BODY_DISP);
        String titleRead = title.getText();
        log(rel(out) + " " + VIEW_WIDTH + "x" + VIEW_HEIGHT + " bytes=" + out.length() + " camera " + camera
            + " corners " + cornerText(out));

        // ---- 8. gray print (8.3): title hidden, lookup table "grayscale", background magenta, with the
        // calls of the recording view_cp_body_gray. Same scene, same camera, same range.
        titleProp.setVisible(false);
        legend.setLookupTable(grayColormap());
        sc.getSolidBackgroundColor().setColor(new DoubleVector(GRAY_BACKGROUND));
        printBody(sc, gray);
        requireMagentaCorners(gray);
        log(rel(gray) + " " + VIEW_WIDTH + "x" + VIEW_HEIGHT + " bytes=" + gray.length() + " lookup '" + GRAY_COLORMAP
            + "' background " + Arrays.toString(GRAY_BACKGROUND) + " no title corners " + cornerText(gray));

        StringBuilder parts = new StringBuilder();
        for (int i = 0; i < BODY_BOUNDARIES.length; i++) {
            parts.append(i == 0 ? "" : ", ").append(BODY_BOUNDARIES[i]);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    {\"file\": ").append(str(BODY_FILE)).append(", \"scene\": ").append(str(BODY_SCENE))
          .append(", \"displayer\": ").append(str(BODY_DISP)).append(", \"field\": ").append(str(BODY_FIELD))
          .append(", \"part\": ").append(str(parts.toString())).append(",\n");
        sb.append("      \"camera\": ").append(camera).append(",\n");
        sb.append("      \"colorbar\": ").append(colorbar).append(",\n");
        sb.append("      \"title\": ").append(str(titleRead)).append(",\n");
        sb.append("      \"export\": {\"call\": ").append(str(BODY_EXPORT_CALL)).append("}}\n");
        bodyViewJson = sb.toString();
    }

    // Colour without light (signature 13, decisions 77 and 87): emissive, intensity 1.0, no shadows, no
    // secondary rays, lights 1 to 4 off. On view_cp_body and, before each plane print, on the plane scene;
    // LLM_body_plane_u_over_U is left as it is. Session B does not save, so nothing outlives the session.
    private void applyUnlit(Scene sc, String sceneName, ScalarDisplayer sd) {
        sc.setAdvancedRenderingEnabled(true);
        sd.getAdvancedRenderingEffects().setEnableSurfaceShadows(false);
        sd.getAdvancedRenderingEffects().setEnableSecondaryRayInteraction(false);
        sd.getAdvancedRenderingEffects().getAdvancedRenderingMaterials()
            .setAdvancedRenderingEffectMode(AdvancedRenderingMaterials.AdvancedRenderingEffectsMode.EMISSIVE);
        sd.getAdvancedRenderingEffects().getAdvancedRenderingMaterials().getEmissiveEffectSettings()
            .setIntensity(BODY_EMISSIVE_INTENSITY);
        for (int i = 0; i < BODY_LIGHTS.length; i++) {
            Object light;
            try {
                light = sc.getLightManager().getLight(BODY_LIGHTS[i]);
            } catch (RuntimeException e) {
                light = null;
            }
            if (!(light instanceof Light)) {
                throw new RuntimeException("output_exporter: light '" + BODY_LIGHTS[i] + "' of scene '" + sceneName
                    + "' read as " + (light == null ? "null" : light.getClass().getName()) + ", not a Light.");
            }
            ((Light) light).setEnabled(false);
        }
    }

    // Scene.printAndWait(String, int, int, int, boolean, boolean): the six-argument form recorded in the
    // GUI and run by body_view_test.java in -batch.
    private void printBody(Scene sc, File out) {
        try {
            sc.printAndWait(out.getAbsolutePath(), VIEW_MAGNIFICATION, VIEW_WIDTH, VIEW_HEIGHT, true, false);
        } catch (RuntimeException e) {
            throw new RuntimeException("output_exporter: print of '" + BODY_SCENE + "' to " + rel(out) + " failed: "
                + e.getMessage());
        }
        if (!out.isFile() || out.length() == 0L) {
            throw new RuntimeException("output_exporter: " + rel(out) + " was not written by '" + BODY_SCENE + "'.");
        }
        int[] size = pngSize(out);
        if (size[0] != VIEW_WIDTH || size[1] != VIEW_HEIGHT) {
            throw new RuntimeException("output_exporter: " + rel(out) + " measures " + size[0] + "x" + size[1]
                + ", the print call asked " + VIEW_WIDTH + "x" + VIEW_HEIGHT + ".");
        }
    }

    private BufferedImage readPng(File f) {
        BufferedImage img;
        try {
            img = ImageIO.read(f);
        } catch (Exception e) {
            img = null;
        }
        if (img == null) {
            throw new RuntimeException("output_exporter: unreadable PNG '" + rel(f) + "'.");
        }
        return img;
    }

    private int[] pngSize(File f) {
        BufferedImage img = readPng(f);
        return new int[] { img.getWidth(), img.getHeight() };
    }

    // The mask of diff_capsule_views.py is the magenta background: a gray frame whose background is not
    // magenta would be measured on the background, so it fails here instead.
    private void requireMagentaCorners(File f) {
        BufferedImage img = readPng(f);
        int[][] corners = cornerPixels(img);
        int[] want = { 255, 0, 255 };
        for (int c = 0; c < 2; c++) { // top left, top right
            for (int k = 0; k < 3; k++) {
                if (Math.abs(corners[c][k] - want[k]) > GRAY_CORNER_TOLERANCE) {
                    throw new RuntimeException("output_exporter: " + rel(f) + " corners read " + cornerText(f)
                        + ", expected magenta (255, 0, 255) at the top two within " + GRAY_CORNER_TOLERANCE
                        + "; diff_capsule_views.py masks the background by that colour.");
                }
            }
        }
    }

    // RGB of the four corners: top left, top right, bottom left, bottom right.
    private static int[][] cornerPixels(BufferedImage img) {
        int w = img.getWidth() - 1;
        int h = img.getHeight() - 1;
        int[][] xy = { { 0, 0 }, { w, 0 }, { 0, h }, { w, h } };
        int[][] out = new int[4][3];
        for (int i = 0; i < 4; i++) {
            int rgb = img.getRGB(xy[i][0], xy[i][1]);
            out[i][0] = (rgb >> 16) & 0xff;
            out[i][1] = (rgb >> 8) & 0xff;
            out[i][2] = rgb & 0xff;
        }
        return out;
    }

    private String cornerText(File f) {
        int[][] c = cornerPixels(readPng(f));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            sb.append(i == 0 ? "" : " ").append('(').append(c[i][0]).append(',').append(c[i][1]).append(',')
              .append(c[i][2]).append(')');
        }
        return sb.toString();
    }

    // ================================================================= step 3: plane images
    // One image per plane in PLANES order: LLM_disp_plane_u_over_U is pointed at section_<plane>, the
    // camera is set, the title gets the numbers of summary.json, the scene is printed to
    // planes/plane_<plane>.png. The camera is read back after each render; the four read cameras, the
    // colorbar and the four titles go to plane_render.json for session C, with the body view entry.
    private void exportPlaneImages() {
        File planesDir = subDir(pointDir, "planes");

        Scene sc = scene(PLANE_SCENE);
        ScalarDisplayer sd = planeDisplayer(sc);
        SimpleAnnotation title = planeTitle();

        StringBuilder planesJson = new StringBuilder();
        for (int p = 0; p < PLANES.length; p++) {
            String plane = PLANES[p];
            showOnlySection(sd, plane);
            setPlaneCamera(sc, p);
            String text = titleText(PLANE_FIELD, plane);
            if (!text.equals(title.getText())) {
                title.setText(text);
            }
            applyRenderLayout(sc, PLANE_SCENE, sd, title, PLANE_TITLE);
            applyUnlit(sc, PLANE_SCENE, sd); // decision 87

            File out = new File(planesDir, "plane_" + plane + ".png");
            try {
                // Four-argument Scene.printAndWait(String, int, int, int), verified by javac 2606.
                sc.printAndWait(out.getAbsolutePath(), VIEW_MAGNIFICATION, VIEW_WIDTH, VIEW_HEIGHT);
            } catch (RuntimeException e) {
                throw new RuntimeException("output_exporter: print of '" + PLANE_SCENE + "' for plane " + plane
                    + " failed: " + e.getMessage());
            }
            if (!out.isFile() || out.length() == 0L) {
                throw new RuntimeException("output_exporter: " + rel(out) + " was not written by '" + PLANE_SCENE + "'.");
            }

            String camera = cameraJson(sc, PLANE_SCENE + " " + plane);
            String titleRead = title.getText();
            planesJson.append("    ").append(str(plane)).append(": {\"file\": ").append(str("planes/plane_" + plane + ".png"))
              .append(", \"part\": ").append(str("section_" + plane)).append(",\n");
            planesJson.append("      \"camera\": ").append(camera).append(",\n");
            planesJson.append("      \"title\": ").append(str(titleRead)).append("}")
              .append(p == PLANES.length - 1 ? "\n" : ",\n");
            log(rel(out) + " " + VIEW_WIDTH + "x" + VIEW_HEIGHT + " bytes=" + out.length() + " camera " + camera);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"scene\": ").append(str(PLANE_SCENE)).append(",\n");
        sb.append("  \"displayer\": ").append(str(PLANE_DISP)).append(",\n");
        sb.append("  \"field\": ").append(str(PLANE_FIELD)).append(",\n");
        sb.append("  \"export\": {\"call\": ").append(str("printAndWait(file, " + VIEW_MAGNIFICATION + ", "
            + VIEW_WIDTH + ", " + VIEW_HEIGHT + ")")).append("},\n");
        sb.append("  \"colorbar\": ").append(colorbarJson(sd, PLANE_DISP)).append(",\n");
        sb.append("  \"layout\": ").append(layoutJson()).append(",\n");
        sb.append("  \"planes\": {\n").append(planesJson).append("  },\n");
        // The body view (spec v16 3.2 point 7), same shape as an entry of manifest.json views[].
        sb.append("  \"views\": [\n").append(bodyViewJson).append("  ],\n");
        sb.append("  \"written_by\": \"output_exporter\"\n");
        sb.append("}\n");
        File render = new File(pointDir, "plane_render.json");
        writeTextFile(render, sb.toString());
        log(rel(render) + " written");
    }

    private ScalarDisplayer planeDisplayer(Scene sc) {
        ScalarDisplayer sd = scalarDisplayer(sc, PLANE_SCENE, PLANE_DISP);
        Object ff = sd.getScalarDisplayQuantity().getFieldFunction(); // getter of a verified setter
        if (isMissingFieldFunction(ff)) {
            throw new RuntimeException("output_exporter: error: displayer '" + PLANE_DISP
                + "' shows a missing field function, expected '" + PLANE_FIELD + "'.");
        }
        String ffName = ff instanceof FieldFunction ? ((FieldFunction) ff).getPresentationName() : String.valueOf(ff);
        if (!PLANE_FIELD.equals(ffName)) {
            throw new RuntimeException("output_exporter: displayer '" + PLANE_DISP + "' shows '" + ffName
                + "', expected '" + PLANE_FIELD + "'.");
        }
        return sd;
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

    private SimpleAnnotation planeTitle() {
        return simpleAnnotation(PLANE_TITLE);
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

    // The layout as applied, for plane_render.json; manifest_writer copies it into the colorbar and
    // title blocks of every view and plane image (session C reads the saved .sim, not these values).
    private String layoutJson() {
        String scenes = str(BODY_SCENE) + ", " + str(PLANE_SCENE);
        return "{\"scenes\": [" + scenes + "], \"title\": {\"font_string\": " + str(TITLE_FONT)
            + ", \"height\": " + num(TITLE_HEIGHT) + "}, \"colorbar\": {\"width\": " + num(LEGEND_WIDTH)
            + ", \"position\": [" + num(LEGEND_POSITION[0]) + ", " + num(LEGEND_POSITION[1]) + "], \"shadow\": "
            + LEGEND_SHADOW + ", \"label_format\": " + str(COLOR_BAR_LABEL_FORMAT) + "}}";
    }

    // The cameras are declared in metres for D = 1 m: the plane cameras, as in run_macro.java, and the
    // body camera of decision 80.
    private void requireUnitD() {
        Object o = sim.getGlobalParameterManager().hasObject("D");
        if (!(o instanceof ScalarGlobalParameter)) {
            throw new RuntimeException("output_exporter: missing global parameter 'D'.");
        }
        double d = ((ScalarGlobalParameter) o).getQuantity().getSIValue();
        if (Math.abs(d - 1.0) > 1.0e-12) {
            throw new RuntimeException("output_exporter: D reads " + d + " m; the plane cameras are declared for D = 1 m.");
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

    // Each setter is written only if the value read differs, as in run_macro.fixCamera; same calls.
    private void setPlaneCamera(Scene sc, int p) {
        // TODO(unverified signature): CurrentView.getProjectionMode() (deprecated on 2606, kept) and
        // setProjectionMode(VisProjectionMode.PARALLEL), same calls as run_macro.java.
        Object mode = sc.getCurrentView().getProjectionMode();
        if (!isParallel(mode)) {
            sc.getCurrentView().setProjectionMode(VisProjectionMode.PARALLEL);
        }
        if (!near(vec3(sc.getCurrentView().getFocalPoint()), PLANE_CAMERA_FOCAL[p])) {
            sc.getCurrentView().setFocalPoint(new DoubleVector(PLANE_CAMERA_FOCAL[p]));
        }
        if (!near(vec3(sc.getCurrentView().getPosition()), PLANE_CAMERA_POSITION[p])) {
            sc.getCurrentView().setPosition(new DoubleVector(PLANE_CAMERA_POSITION[p]));
        }
        if (!near(vec3(sc.getCurrentView().getViewUp()), CAMERA_VIEW_UP)) {
            sc.getCurrentView().setViewUp(new DoubleVector(CAMERA_VIEW_UP));
        }
        // TODO(unverified signature): ParallelScale.getValue()/setValue(double), deprecated on 2606 and
        // kept: no replacement is in any source this chain may use.
        ParallelScale ps = (ParallelScale) sc.getCurrentView().getParallelScale();
        if (ps == null) {
            throw new RuntimeException("output_exporter: '" + PLANE_SCENE + "' has no parallel scale.");
        }
        if (!near(new double[] { ps.getValue() }, new double[] { PLANE_CAMERA_SCALE[p] })) {
            ps.setValue(PLANE_CAMERA_SCALE[p]);
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
    private void checkStaleness(File setup) {
        List<String> s = readLines(setup);
        String json = readText(new File(pointDir, "summary.json"));

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
        sb.append("  \"planes\": {\n");
        for (int p = 0; p < PLANES.length; p++) {
            sb.append("    \"").append(PLANES[p]).append("\": {\"rows_written\": ").append(planeRowsWritten[p])
              .append(", \"rows_expected\": ").append(PLANE_ROWS_EXPECTED[p])
              .append(", \"missing_in_body\": ").append(planeMissingInBody[p])
              .append(p == PLANES.length - 1 ? "}\n" : "},\n");
        }
        sb.append("  },\n");
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

    // ================================================================= step 5: environment
    // SPEC 0.4, 4.2.1 (simulation-capsule, SPEC.md and tools/check_capsule.py): environment { solver,
    // version, build, platform, precision }, the last four copied from the Version: line of setup.txt
    // (PresentationVersion, ReleaseNumber, BuildArch; precision from BuildEnv, "-r8" at its end is
    // double, without it mixed). A value that is not on that line is written null and logged. solver
    // is not on it: it is the product name SPEC 4.2.1 fixes (ghb-spec04). No renders block: the images come from the same build as the run. The block takes
    // the place of environment_placeholder (or of the environment a previous session B wrote); every
    // other byte of summary.json is kept, so its number formatting is the one run_macro wrote.
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

        File summary = new File(pointDir, "summary.json");
        String json = new String(readBytes(summary), StandardCharsets.UTF_8);
        int root = json.indexOf('{');
        String key = ENVIRONMENT_PLACEHOLDER;
        int at = root < 0 ? -1 : keyValueStart(json, root, key);
        if (at < 0) {
            key = "environment";
            at = root < 0 ? -1 : keyValueStart(json, root, key);
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
        log("summary.json " + key + " replaced by environment (platform " + check + ")");
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

    // "Cells: N" of the region node "fluid" inside the "--- Representations ---" section. The trim
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
            if (t.equals("fluid")) {
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
        throw new RuntimeException("output_exporter: setup.txt has no 'Cells:' line under region 'fluid' in 'Representations'.");
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
