// object_audit.java
// T7 chain -- piece 5 of 5, session D. Simcenter STAR-CCM+ 2606.
// One public class, no records, no modules.
//
// Walks the object managers of the open .sim (a solved cube_re<NNNN>.sim or the freshly prepared
// cube_sweep_template.sim), classifies every object against the closed list of 28 LLM_ objects and
// the inherited list, checks the prism guard LLM_prism_off_nonwall and writes object_audit.json to
// work/cube_re<NNNN>/ or, on the template, to work/_prepare/.
// Read-only on a sweep point. On the template, after the object walk, it creates one transient XYZ
// internal table, exports the cell centroids and volumes of region fluid to work/_prepare/mesh_cells.csv
// and removes the table in the same session. It never saves the simulation, so the closed list of 28
// is unchanged.
// Spec v16 (decision 78): the 16 objects of the four retired cut views (LLM_view_*, LLM_disp_*,
// LLM_body_* and LLM_title_* on y0 and x2) left the list and are unlisted wherever they remain. The
// .sim files solved on 30.09 still carry them and are not audited again (spec 1, decision 76). The body
// view of session B is transient and never reaches a saved .sim (decision 77).
//
// Markers "TODO(unverified signature)" name the API members that no compiled source of this chain
// calls; they are the expected compile risks. "getter of a verified setter" marks a getter whose
// setter run_macro.java calls in prepare mode.

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import star.base.neo.NamedObject;
import star.common.Boundary;
import star.common.FieldFunction;
import star.common.PartSurface;
import star.common.Region;
import star.common.ResidualMonitor; // TODO(unverified signature): package of ResidualMonitor not read
import star.common.Simulation;
import star.common.StarMacro;
import star.common.Table; // TODO(unverified signature): base class of every table, as in run_macro.java
import star.common.UserFieldFunction; // TODO(unverified signature): package of UserFieldFunction not read
import star.common.XyzInternalTable;
import star.meshing.AutoMeshOperation;
import star.meshing.CustomMeshControlManager;
import star.meshing.MeshOperationManager;
import star.meshing.SurfaceCustomMeshControl;
import star.prismmesher.PartsCustomPrismsOption;
import star.prismmesher.PartsCustomizePrismMesh;
import star.vis.Scene;

public class object_audit extends StarMacro {

    // ---------------------------------------------------------------- constants
    private static final String PROPS_NAME = "LLM_point.properties";
    private static final String LOG_PREFIX = "audit: ";
    private static final String OUT_NAME = "object_audit.json";
    private static final int CLOSED_LIST_EXPECTED = 28;
    private static final String WORK_DIR = "work";

    // Container keys, in walk order. The first nine are the managers of spec 3.5; displayers and
    // annotations are walked because the closed list has entries there.
    private static final String M_REPORTS = "reports";
    private static final String M_PARTS = "parts";
    private static final String M_TABLES = "tables";
    private static final String M_SCENES = "scenes";
    private static final String M_FIELD_FUNCTIONS = "field_functions";
    private static final String M_MONITORS = "monitors";
    private static final String M_STOPPING_CRITERIA = "stopping_criteria";
    private static final String M_GLOBAL_PARAMETERS = "global_parameters";
    private static final String M_CUSTOM_MESH_CONTROLS = "custom_mesh_controls";
    private static final String M_DISPLAYERS_PREFIX = "displayers:";
    private static final String M_ANNOTATIONS = "annotations";

    private static final String LLM_PREFIX = "LLM_";
    // Contract v10: the plane scene output_exporter renders once per plane, with its own displayers.
    private static final String PLANE_SCENE = "LLM_plane_u_over_U";
    private static final String MESH_OPERATION = "mesh_cube";
    private static final String PRISM_CONTROL = "LLM_prism_off_nonwall";
    private static final String PRISM_OPTION_EXPECTED = "DISABLE";
    private static final String FLUID_REGION = "fluid";

    // Mesh table, template pass only: read by check_mirror.py (simulation-capsule tools/) in step 0 of
    // build_capsule.py. The table exists only between its creation and its removal in this session.
    private static final String MESH_TABLE = "LLM_audit_mesh_cells";
    private static final String MESH_FIELD_FUNCTION = "Volume";
    private static final String MESH_CSV = "mesh_cells.csv";

    // Same boundary lists as run_macro.java. The six non-wall ones are what the prism guard expects;
    // the six walls only let a surface found in the control be named by its boundary.
    private static final String[] WALL_BOUNDARIES = {
        "cube_back", "cube_bottom", "cube_front", "cube_left", "cube_right", "cube_top"
    };
    private static final String[] NONWALL_BOUNDARIES = {
        "inlet", "outlet", "lateral_yminus", "lateral_yplus", "lateral_zminus", "lateral_zplus"
    };

    // Patterns of the closed list (same lists as run_macro.java).
    private static final String[] FIELDS = { "cp", "u_over_U" };
    private static final String[] EXTREMA = { "min", "max" };
    private static final String[] PLANES = { "y0", "x1", "x2", "x4" };

    // Closed list of LLM_ objects: {container, name}, generated from the patterns above.
    private static final List<String[]> CLOSED_LIST = closedList();

    // Inherited names per container (spec section 1, plus the two default stopping criteria).
    private static final String[] INHERITED_GLOBAL_PARAMETERS = { "D", "U", "rho", "Re", "mu" };
    private static final String[] INHERITED_PARTS = {
        "grid_x1", "grid_x2", "grid_x4", "grid_y0",
        "section_x1", "section_x2", "section_x4", "section_y0", "wake_reversed", "Plane Section"
    };
    // v12: the three velocity ratios carry their final names (run_macro renames them in prepare).
    private static final String[] INHERITED_FIELD_FUNCTIONS = {
        "cp", "u_over_U", "v_over_U", "w_over_U", "x_D", "y_D", "z_D"
    };
    private static final String[] INHERITED_REPORTS = {
        "cd", "cd_friction", "cd_pressure", "cl", "cy",
        "cp_base", "cp_min", "cp_stagnation",
        "mass_imbalance", "mdot_in", "mdot_out",
        "recirculation_length_D", "x_reattach"
    };
    private static final String[] INHERITED_MONITORS = { "cd_monitor", "cl_monitor" };
    private static final String[] INHERITED_STOPPING_CRITERIA = {
        "cd_asymptotic", "cl_asymptotic", "Maximum Steps", "Stop File"
    };
    private static final String[] INHERITED_CUSTOM_MESH_CONTROLS = { "cube_refinement", "wake_refinement" };
    // Inherited names no object of the chain uses: reported as inherited_unused, never deleted.
    private static final String[] INHERITED_UNUSED_PARTS = { "Plane Section" };

    // Simple class names of the objects STAR-CCM+ creates by itself and that cannot be deleted from a
    // simulation, measured in the "class" column of object_audit.json. Compared as strings so no new
    // STAR-CCM+ import is needed.
    private static final String[] SYSTEM_MONITOR_CLASSES = { "IterationMonitor", "PhysicalTimeMonitor" };
    private static final String USER_ANNOTATION_CLASS = "SimpleAnnotation";

    private static final String C_OWNED = "owned";
    private static final String C_UNLISTED = "unlisted";
    private static final String C_INHERITED = "inherited";
    private static final String C_INHERITED_UNUSED = "inherited_unused";
    private static final String C_SYSTEM = "system";
    private static final String C_UNKNOWN = "unknown";
    private static final String[] CLASSIFICATIONS = {
        C_OWNED, C_UNLISTED, C_INHERITED, C_INHERITED_UNUSED, C_SYSTEM, C_UNKNOWN
    };

    private static List<String[]> closedList() {
        List<String[]> l = new ArrayList<String[]>();
        l.add(new String[] { M_CUSTOM_MESH_CONTROLS, PRISM_CONTROL });
        l.add(new String[] { M_PARTS, "LLM_probe_axis_x2D" });
        l.add(new String[] { M_REPORTS, "LLM_u_over_U_axis_x2D" });
        l.add(new String[] { M_REPORTS, "LLM_cell_count" });
        for (int f = 0; f < FIELDS.length; f++) {
            for (int e = 0; e < EXTREMA.length; e++) {
                for (int p = 0; p < PLANES.length; p++) {
                    l.add(new String[] { M_REPORTS, "LLM_" + FIELDS[f] + "_" + EXTREMA[e] + "_" + PLANES[p] });
                }
            }
        }
        for (int p = 0; p < PLANES.length; p++) {
            l.add(new String[] { M_TABLES, "LLM_table_" + PLANES[p] });
        }
        l.add(new String[] { M_SCENES, PLANE_SCENE });
        l.add(new String[] { M_DISPLAYERS_PREFIX + PLANE_SCENE, "LLM_disp_plane_u_over_U" });
        l.add(new String[] { M_DISPLAYERS_PREFIX + PLANE_SCENE, "LLM_body_plane_u_over_U" });
        l.add(new String[] { M_ANNOTATIONS, "LLM_title_plane_u_over_U" });
        return Collections.unmodifiableList(l);
    }

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private File sessionBase;
    private File outDir;
    private File logFile;
    private boolean templatePass;   // mode=prepare or no properties file: output to work/_prepare/
    private final List<String[]> managerRows = new ArrayList<String[]>();   // {manager, class, count}
    private final List<String[]> objectRows = new ArrayList<String[]>();    // {manager, name, class, classification}
    private final Map<String, Set<String>> ownedByContainer = new LinkedHashMap<String, Set<String>>();
    private final Map<String, Set<String>> inheritedByContainer = new LinkedHashMap<String, Set<String>>();
    private final Set<String> ownedSeen = new HashSet<String>();
    private final List<String> missingOwned = new ArrayList<String>();
    private final List<String> pendingLog = new ArrayList<String>();

    // prism guard readings
    private final List<String> prismPartsFound = new ArrayList<String>();
    private String prismOptionFound;
    private boolean prismPass;

    // ================================================================= entry point
    public void execute() {
        // The closed list is checked before the simulation is touched.
        checkClosedList();

        sim = getActiveSimulation();
        try {
            resolveOutput();
            buildLookupSets();

            walkReports();
            walkParts();
            walkTables();
            List<Scene> views = walkScenes();
            requireFieldFunctions();
            walkFieldFunctions();
            walkMonitors();
            walkStoppingCriteria();
            walkGlobalParameters();
            AutoMeshOperation mesh = meshCube();
            walkCustomMeshControls(mesh);
            for (int i = 0; i < views.size(); i++) {
                walkDisplayers(views.get(i));
            }
            walkAnnotations();
            if (templatePass) {
                writeMeshTable();
            }

            collectMissingOwned();
            checkPrismGuard(mesh);

            String json = buildJson();
            File out = new File(outDir, OUT_NAME);
            writeOnce(out, json);

            flushResultLines();
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= closed list
    private static void checkClosedList() {
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < CLOSED_LIST.size(); i++) {
            String[] c = CLOSED_LIST.get(i);
            if (!keys.add(c[0] + "\n" + c[1])) {
                throw new RuntimeException("object_audit: closed list repeats '" + c[1] + "' in " + c[0] + ".");
            }
        }
        if (CLOSED_LIST.size() != CLOSED_LIST_EXPECTED) {
            throw new RuntimeException("object_audit: closed list has " + CLOSED_LIST.size()
                + " names, expected " + CLOSED_LIST_EXPECTED + ".");
        }
    }

    private void buildLookupSets() {
        for (int i = 0; i < CLOSED_LIST.size(); i++) {
            String[] c = CLOSED_LIST.get(i);
            setFor(ownedByContainer, c[0]).add(c[1]);
        }
        addAll(setFor(inheritedByContainer, M_GLOBAL_PARAMETERS), INHERITED_GLOBAL_PARAMETERS);
        addAll(setFor(inheritedByContainer, M_PARTS), INHERITED_PARTS);
        addAll(setFor(inheritedByContainer, M_FIELD_FUNCTIONS), INHERITED_FIELD_FUNCTIONS);
        addAll(setFor(inheritedByContainer, M_REPORTS), INHERITED_REPORTS);
        addAll(setFor(inheritedByContainer, M_MONITORS), INHERITED_MONITORS);
        addAll(setFor(inheritedByContainer, M_STOPPING_CRITERIA), INHERITED_STOPPING_CRITERIA);
        addAll(setFor(inheritedByContainer, M_CUSTOM_MESH_CONTROLS), INHERITED_CUSTOM_MESH_CONTROLS);
    }

    private static Set<String> setFor(Map<String, Set<String>> m, String key) {
        Set<String> s = m.get(key);
        if (s == null) {
            s = new HashSet<String>();
            m.put(key, s);
        }
        return s;
    }

    private static void addAll(Set<String> s, String[] names) {
        for (int i = 0; i < names.length; i++) {
            s.add(names[i]);
        }
    }

    // ================================================================= properties / output directory
    // Same lookup order as run_macro.loadProperties: session directory first, then the directory
    // returned by Simulation.getSessionDirFile(). Unlike run_macro, an absent file is not an error:
    // it means the audit of the freshly prepared template.
    private File findProperties() {
        String sessionDir = null;
        try {
            sessionDir = sim.getSessionDir();
        } catch (Exception e) {
            sessionDir = null;
        }
        if (sessionDir != null && sessionDir.length() > 0) {
            File f = new File(sessionDir, PROPS_NAME);
            if (f.isFile()) {
                return f;
            }
        }
        File simDir = null;
        try {
            simDir = sim.getSessionDirFile();
        } catch (Exception e) {
            simDir = null;
        }
        if (simDir != null) {
            File f = new File(simDir, PROPS_NAME);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private Properties loadProperties(File candidate) {
        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(candidate);
            p.load(in);
        } catch (Exception e) {
            throw new RuntimeException("object_audit: could not parse '" + PROPS_NAME + "'.");
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

    private static String requireProperty(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().length() == 0) {
            throw new RuntimeException("object_audit: property '" + key + "' missing or empty in " + PROPS_NAME + ".");
        }
        return v.trim();
    }

    // mode=run: work/cube_re<NNNN>/ from re_target, exactly as run_macro.doRun resolves it; the
    // directory and its run_log.txt must already exist (run mode wrote them). mode=prepare or no
    // properties file (the template): work/_prepare/, created if missing, log in audit_log.txt.
    private void resolveOutput() {
        String sessionDir = sim.getSessionDir();
        if (sessionDir != null && sessionDir.length() > 0) {
            sessionBase = new File(sessionDir);
        } else {
            sessionBase = sim.getSessionDirFile();
        }
        File propsFile = findProperties();
        String mode = "prepare";
        Properties props = null;
        if (propsFile != null) {
            props = loadProperties(propsFile);
            mode = requireProperty(props, "mode");
        }
        if ("run".equals(mode)) {
            String v = requireProperty(props, "re_target");
            int reTarget;
            try {
                reTarget = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new RuntimeException("object_audit: property 're_target' is not an integer: '" + v + "'.");
            }
            if (reTarget != 100 && reTarget != 300 && reTarget != 1000 && reTarget != 3000) {
                throw new RuntimeException("object_audit: property 're_target' must be 100, 300, 1000 or 3000, got "
                    + reTarget + ".");
            }
            String relative = WORK_DIR + File.separator + "cube_re" + four(reTarget);
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
        } else if ("prepare".equals(mode)) {
            File dir = new File(new File(sessionBase, WORK_DIR), "_prepare");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new RuntimeException("object_audit: could not create directory 'work/_prepare'.");
            }
            outDir = dir;
            logFile = new File(dir, "audit_log.txt");
            templatePass = true;
        } else {
            throw new RuntimeException("object_audit: property 'mode' must be 'prepare' or 'run', got '" + mode + "'.");
        }
        sim.println("object_audit: mode=" + mode + " output=" + rel(outDir));
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

    // Returns the scenes whose name starts with LLM_, sorted by name, whose displayers are walked
    // afterwards: LLM_plane_u_over_U and any unlisted LLM_ scene, so that its displayers are reported too.
    private List<Scene> walkScenes() {
        Collection<?> all = sim.getSceneManager().getObjects();
        walk(M_SCENES, sim.getSceneManager(), all);
        List<Scene> views = new ArrayList<Scene>();
        for (Object o : all) {
            if (o instanceof Scene && nameOf(o).startsWith(LLM_PREFIX)) {
                views.add((Scene) o);
            }
        }
        Collections.sort(views, new Comparator<Scene>() {
            public int compare(Scene a, Scene b) {
                return a.getPresentationName().compareTo(b.getPresentationName());
            }
        });
        return views;
    }

    // Measured on 2606 (30.09): FieldFunctionManager.getFunction(name) for a name that does not exist
    // returns an object of class NullFieldFunction, neither null nor an exception. That class is matched
    // by its simple name, not imported.
    private static boolean isMissingFieldFunction(Object ff) {
        return ff == null || ff.getClass().getSimpleName().equals("NullFieldFunction");
    }

    // The inherited field functions, looked up by name with the call recorded in the 2606 GUI,
    // FieldFunctionManager.getFunction(String). A missing one is an error, not an audit finding.
    private void requireFieldFunctions() {
        for (int i = 0; i < INHERITED_FIELD_FUNCTIONS.length; i++) {
            if (isMissingFieldFunction(sim.getFieldFunctionManager().getFunction(INHERITED_FIELD_FUNCTIONS[i]))) {
                throw new RuntimeException("object_audit: error: missing field function '"
                    + INHERITED_FIELD_FUNCTIONS[i] + "'.");
            }
        }
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

    private void walkCustomMeshControls(AutoMeshOperation mesh) {
        CustomMeshControlManager m = mesh.getCustomMeshControls();
        // TODO(unverified signature): CustomMeshControlManager.getObjects() -- the CustomMeshControl
        // family was never measured by the bench; run_macro.java only calls hasObject and
        // createSurfaceControl on it. The compile settles whether it is a ClientServerObjectManager.
        Collection<?> all = m.getObjects();
        walk(M_CUSTOM_MESH_CONTROLS, m, all);
    }

    private void walkDisplayers(Scene scene) {
        // TODO(unverified signature): DisplayerManager.getObjects() -- run_macro.java calls only
        // hasDisplayer, getObject and the create methods on it.
        Collection<?> all = scene.getDisplayerManager().getObjects();
        walk(M_DISPLAYERS_PREFIX + scene.getPresentationName(), scene.getDisplayerManager(), all);
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
            String cls = o == null ? "null" : o.getClass().getSimpleName();
            String c = classify(manager, name, o);
            objectRows.add(new String[] { manager, name, cls, c });
            if (C_OWNED.equals(c)) {
                ownedSeen.add(manager + "\n" + name);
            }
        }
    }

    // An object that is not a NamedObject has no presentation name to classify; it is written with an
    // empty name and falls through to unknown.
    private static String nameOf(Object o) {
        if (o instanceof NamedObject) {
            String n = ((NamedObject) o).getPresentationName();
            return n == null ? "" : n;
        }
        return "";
    }

    // Expected results:
    // - template regenerated from v16, and any .sim derived from it: RESULT PASS, 28 owned, 0 unlisted,
    //   0 unknown, prism guard pass.
    // - .sim prepared before v16 (such as the 30.09 sweep): RESULT FAIL (16 unlisted: the four
    //   LLM_view_* scenes, their eight displayers and their four titles; 0 missing).
    // - .sim prepared before contract v10: RESULT FAIL (0 unlisted, 0 unknown, 4 missing: the plane
    //   scene, its two displayers and its title).
    // - .sim derived from the old template (such as the 28.09 cube_re3000.sim):
    //   RESULT FAIL (10 unlisted, 15 unknown, 0 missing, prism guard pass). The monitors Iteration and
    //   Physical Time and the annotations Logo, Scene, Iteration, Solution Time and Time Step are
    //   system; the 15 unknown are table_{y0,x1,x2,x4}, scene_{y0,x1,x2,x4}, anno_{y0,x1,x2,x4},
    //   Geometry Scene 1, Mesh Scene 1 and Scalar Scene 1, leftovers that prepare mode removes.
    private String classify(String manager, String name, Object o) {
        if (name.startsWith(LLM_PREFIX)) {
            Set<String> owned = ownedByContainer.get(manager);
            return owned != null && owned.contains(name) ? C_OWNED : C_UNLISTED;
        }
        Set<String> inherited = inheritedByContainer.get(manager);
        if (inherited != null && inherited.contains(name)) {
            if (M_PARTS.equals(manager) && contains(INHERITED_UNUSED_PARTS, name)) {
                return C_INHERITED_UNUSED;
            }
            return C_INHERITED;
        }
        // The four system families, decided by class, never by name.
        if (M_FIELD_FUNCTIONS.equals(manager) && o != null && !(o instanceof UserFieldFunction)) {
            return C_SYSTEM;
        }
        if (M_MONITORS.equals(manager) && o instanceof ResidualMonitor) {
            return C_SYSTEM;
        }
        if (M_MONITORS.equals(manager) && o != null && contains(SYSTEM_MONITOR_CLASSES, o.getClass().getSimpleName())) {
            return C_SYSTEM;
        }
        // A SimpleAnnotation is never system: LLM_title_* took the branch above, any other stays unknown.
        if (M_ANNOTATIONS.equals(manager) && o != null && !USER_ANNOTATION_CLASS.equals(o.getClass().getSimpleName())) {
            return C_SYSTEM;
        }
        return C_UNKNOWN;
    }

    private static boolean contains(String[] a, String s) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(s)) {
                return true;
            }
        }
        return false;
    }

    private void collectMissingOwned() {
        for (int i = 0; i < CLOSED_LIST.size(); i++) {
            String[] c = CLOSED_LIST.get(i);
            if (!ownedSeen.contains(c[0] + "\n" + c[1])) {
                missingOwned.add(c[1]);
                pendingLog.add("missing " + c[0] + " " + c[1]);
            }
        }
        Collections.sort(missingOwned);
    }

    // ================================================================= mesh_cube
    // Same lookup as run_macro.meshCube(): MeshOperationManager.getObject in try/catch covers a throw
    // or a null return alike.
    private AutoMeshOperation meshCube() {
        MeshOperationManager mom = sim.get(MeshOperationManager.class);
        Object op;
        try {
            op = mom.getObject(MESH_OPERATION);
        } catch (RuntimeException e) {
            op = null;
        }
        if (op == null) {
            throw new RuntimeException("object_audit: missing mesh operation '" + MESH_OPERATION + "'.");
        }
        if (!(op instanceof AutoMeshOperation)) {
            throw new RuntimeException("object_audit: mesh operation '" + MESH_OPERATION + "' is not an AutoMeshOperation.");
        }
        return (AutoMeshOperation) op;
    }

    // ================================================================= prism guard (spec section 7)
    // Reads the part surfaces and the Customize Prism Mesh option of LLM_prism_off_nonwall. The
    // surfaces are named by the boundary of region fluid they belong to: a boundary whose surfaces are
    // all in the control is written by its name, a boundary only partly in it as "<name> (partial)",
    // a surface of no known boundary as "surface:<name>". Number of Prism Layers is not read.
    private void checkPrismGuard(AutoMeshOperation mesh) {
        prismOptionFound = null;
        prismPass = false;
        Object existing = mesh.getCustomMeshControls().hasObject(PRISM_CONTROL);
        if (existing == null) {
            pendingLog.add("prism guard fail: " + PRISM_CONTROL + " absent under " + MESH_OPERATION);
            return;
        }
        if (!(existing instanceof SurfaceCustomMeshControl)) {
            pendingLog.add("prism guard fail: " + PRISM_CONTROL + " is a "
                + existing.getClass().getSimpleName() + ", not a SurfaceCustomMeshControl");
            return;
        }
        SurfaceCustomMeshControl ctrl = (SurfaceCustomMeshControl) existing;

        // Part surfaces of the twelve boundaries of fluid, keyed by boundary name.
        Region fluid = region(FLUID_REGION);
        Map<String, Collection<PartSurface>> byBoundary = new LinkedHashMap<String, Collection<PartSurface>>();
        for (int i = 0; i < NONWALL_BOUNDARIES.length; i++) {
            byBoundary.put(NONWALL_BOUNDARIES[i], boundary(fluid, NONWALL_BOUNDARIES[i]).getPartSurfaceGroup().getObjects());
        }
        for (int i = 0; i < WALL_BOUNDARIES.length; i++) {
            byBoundary.put(WALL_BOUNDARIES[i], boundary(fluid, WALL_BOUNDARIES[i]).getPartSurfaceGroup().getObjects());
        }

        // getter of a verified setter: run_macro.java calls getGeometryObjects().setObjects(PartSurface[]).
        Collection<?> inControl = ctrl.getGeometryObjects().getObjects();
        if (inControl == null) {
            throw new RuntimeException("object_audit: " + PRISM_CONTROL + " returned no geometry object collection.");
        }
        Set<String> found = new TreeSet<String>();
        for (Object g : inControl) {
            String owner = null;
            for (Map.Entry<String, Collection<PartSurface>> e : byBoundary.entrySet()) {
                if (e.getValue() != null && e.getValue().contains(g)) {
                    owner = e.getKey();
                    break;
                }
            }
            if (owner == null) {
                found.add("surface:" + nameOf(g));
            }
        }
        for (Map.Entry<String, Collection<PartSurface>> e : byBoundary.entrySet()) {
            Collection<PartSurface> ps = e.getValue();
            if (ps == null || ps.isEmpty()) {
                continue;
            }
            int in = 0;
            for (PartSurface s : ps) {
                if (inControl.contains(s)) {
                    in++;
                }
            }
            if (in == ps.size()) {
                found.add(e.getKey());
            } else if (in > 0) {
                found.add(e.getKey() + " (partial)");
            }
        }
        prismPartsFound.addAll(found);

        PartsCustomizePrismMesh customize = ctrl.getCustomConditions().get(PartsCustomizePrismMesh.class);
        // getter of a verified setter: run_macro.java calls getCustomPrismOptions().setSelected(Type.DISABLE);
        // getSelectedElement() is the getter it uses on the other option classes of prepare mode.
        PartsCustomPrismsOption.Type selected = customize.getCustomPrismOptions().getSelectedElement();
        prismOptionFound = String.valueOf(selected);

        Set<String> expected = new TreeSet<String>();
        addAll(expected, NONWALL_BOUNDARIES);
        boolean partsOk = found.equals(expected);
        boolean optionOk = selected == PartsCustomPrismsOption.Type.DISABLE;
        prismPass = partsOk && optionOk;
        pendingLog.add("prism guard " + (prismPass ? "pass" : "fail") + ": parts " + (partsOk ? "ok" : "differ")
            + " " + found + ", option " + prismOptionFound);
    }

    // ================================================================= mesh table (template pass)
    // Cell centroids and cell volumes of region fluid, for check_mirror.py. The table calls are the
    // ones this chain already compiles: createTabularObject, getParts().addPart, setFieldFunctions,
    // setRepresentation and the vertex-data pair from run_macro.provisionTable; extract and
    // export(path, ",") from output_exporter.exportPlanes; TableManager.remove(Table) and the
    // hasTable read-back from run_macro's leftover removal. Data on vertices is set off here: the
    // rows must be cells, one centroid and one volume each. A failed export or a CSV that does not
    // pass checkMeshCsv leaves no mesh_cells.csv, so step 0 of build_capsule.py never measures a
    // stale or partial mesh.
    private void writeMeshTable() {
        if (sim.getTableManager().hasTable(MESH_TABLE) != null) {
            throw new RuntimeException("object_audit: table '" + MESH_TABLE + "' already exists in the .sim; "
                + "it is transient and must never be saved.");
        }
        Object fluid = region(FLUID_REGION);
        if (!(fluid instanceof NamedObject)) { // TODO[I-05], as run_macro.addPartToReport
            throw new RuntimeException("object_audit: region '" + FLUID_REGION + "' is not a NamedObject.");
        }
        Object ff = sim.getFieldFunctionManager().getFunction(MESH_FIELD_FUNCTION);
        if (isMissingFieldFunction(ff)) {
            throw new RuntimeException("object_audit: error: missing field function '" + MESH_FIELD_FUNCTION + "'.");
        }
        if (!(ff instanceof FieldFunction)) {
            throw new RuntimeException("object_audit: object '" + MESH_FIELD_FUNCTION + "' is not a FieldFunction.");
        }
        File out = new File(outDir, MESH_CSV);
        if (out.exists() && !out.delete()) {
            throw new RuntimeException("object_audit: could not remove the previous '" + rel(out) + "'.");
        }

        XyzInternalTable t = sim.getTableManager().createTabularObject(XyzInternalTable.class);
        RuntimeException failure = null;
        try {
            t.setPresentationName(MESH_TABLE);
            t.getParts().addPart((NamedObject) fluid);
            List<FieldFunction> ffs = new ArrayList<FieldFunction>();
            ffs.add((FieldFunction) ff);
            t.setFieldFunctions(ffs);
            t.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
            if (t.getExtractVertexData()) {
                t.setExtractVertexData(false);
            }
            t.extract();
            t.export(out.getAbsolutePath(), ",");
        } catch (RuntimeException e) {
            failure = new RuntimeException("object_audit: mesh table export failed: " + e.getMessage());
        }
        try {
            removeMeshTable(t);
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            }
        }
        int rows = 0;
        if (failure == null) {
            try {
                rows = checkMeshCsv(out);
            } catch (RuntimeException e) {
                failure = e;
            }
        }
        if (failure != null) {
            if (out.exists() && !out.delete()) {
                sim.println("object_audit: could not remove '" + rel(out) + "'");
            }
            throw failure;
        }
        pendingLog.add("mesh table written " + rows + " rows");
    }

    // TODO(unverified signature): star.common.Table as the base class of XyzInternalTable, and
    // TableManager.remove(Table); both carried from run_macro.java's leftover removal, whose own TODO
    // is not retired yet.
    private void removeMeshTable(XyzInternalTable t) {
        Object o = t;
        if (!(o instanceof Table)) {
            throw new RuntimeException("object_audit: table '" + MESH_TABLE + "' is not a Table; it was not removed.");
        }
        sim.getTableManager().remove((Table) o);
        if (sim.getTableManager().getObjects().contains(o) || sim.getTableManager().hasTable(MESH_TABLE) != null) {
            throw new RuntimeException("object_audit: table '" + MESH_TABLE + "' still present after removal.");
        }
    }

    // The header must hold the four columns check_mirror.py resolves, by its own rule: a name, stripped
    // of quotes and lower-cased, equal to x, y or z or starting with "x " or "x(" (same for y, z); and a
    // name starting with "volume". check_mirror.py divides centroid distances by the cube root of the
    // volume, so a unit suffix in "(...)" must agree: x, y and z in one unit u, volume in u^3.
    // TODO(unverified behaviour): the header XyzInternalTable.export writes for its coordinates is not
    // documented in any source read; simulation-capsule's test_check_mirror.py uses "X (m)","Y (m)",
    // "Z (m)","Volume (m^3)". A header that does not match throws with the header found.
    // Returns the number of data rows, which must be positive.
    private int checkMeshCsv(File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            String header = r.readLine();
            while (header != null && header.trim().length() == 0) {
                header = r.readLine();
            }
            if (header == null) {
                throw new RuntimeException("object_audit: " + rel(f) + " is empty.");
            }
            if (header.length() > 0 && header.charAt(0) == '﻿') {
                header = header.substring(1);
            }
            String[] cells = header.split(",", -1);
            String[] axes = { "x", "y", "z" };
            String[] units = new String[3];
            for (int a = 0; a < axes.length; a++) {
                int j = findColumn(cells, axes[a], false);
                if (j < 0) {
                    throw new RuntimeException("object_audit: " + rel(f) + ": no column '" + axes[a]
                        + "' for check_mirror.py; header is: " + header);
                }
                units[a] = unitOf(cells[j]);
            }
            int jv = findColumn(cells, "volume", true);
            if (jv < 0) {
                throw new RuntimeException("object_audit: " + rel(f) + ": no column 'volume' for check_mirror.py; "
                    + "header is: " + header);
            }
            String volumeUnit = unitOf(cells[jv]);
            boolean anyUnit = units[0] != null || units[1] != null || units[2] != null || volumeUnit != null;
            if (anyUnit && (units[0] == null || !units[0].equals(units[1]) || !units[0].equals(units[2])
                    || !(units[0] + "^3").equals(volumeUnit))) {
                throw new RuntimeException("object_audit: " + rel(f) + ": units of x, y, z and volume disagree; "
                    + "header is: " + header);
            }
            int rows = 0;
            for (String ln = r.readLine(); ln != null; ln = r.readLine()) {
                if (ln.trim().length() > 0) {
                    rows++;
                }
            }
            if (rows == 0) {
                throw new RuntimeException("object_audit: " + rel(f) + " has no data rows; is the template meshed?");
            }
            return rows;
        } catch (java.io.IOException e) {
            throw new RuntimeException("object_audit: could not read '" + rel(f) + "'.");
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }

    // First header cell matching key by check_mirror.resolve_columns: prefix for volume, else the
    // exact name or the name followed by a space or "(".
    private static int findColumn(String[] cells, String key, boolean prefix) {
        for (int j = 0; j < cells.length; j++) {
            String n = cells[j].trim();
            n = stripQuotes(n).trim().toLowerCase(Locale.ROOT);
            boolean hit = prefix ? n.startsWith(key)
                : (n.equals(key) || n.startsWith(key + " ") || n.startsWith(key + "("));
            if (hit) {
                return j;
            }
        }
        return -1;
    }

    private static String stripQuotes(String s) {
        int b = 0;
        int e = s.length();
        while (b < e && s.charAt(b) == '"') {
            b++;
        }
        while (e > b && s.charAt(e - 1) == '"') {
            e--;
        }
        return s.substring(b, e);
    }

    // The text between the first "(" and the last ")" of a header cell, or null when there is none.
    private static String unitOf(String cell) {
        String n = stripQuotes(cell.trim()).trim();
        int open = n.indexOf('(');
        int close = n.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        return n.substring(open + 1, close).trim();
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
        return count(C_UNLISTED) == 0 && count(C_UNKNOWN) == 0 && missingOwned.isEmpty() && prismPass;
    }

    private String resultLine() {
        if (passed()) {
            return "RESULT PASS";
        }
        return "RESULT FAIL (" + count(C_UNLISTED) + " unlisted, " + count(C_UNKNOWN) + " unknown, "
            + missingOwned.size() + " missing, prism guard " + (prismPass ? "pass" : "fail") + ")";
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
        sb.append("  \"closed_list_size\": ").append(CLOSED_LIST.size()).append(",\n");

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

        sb.append("  \"prism_guard\": {\n");
        sb.append("    \"control\": ").append(str(PRISM_CONTROL)).append(",\n");
        sb.append("    \"parts_expected\": ").append(strArray(NONWALL_BOUNDARIES)).append(",\n");
        sb.append("    \"parts_found\": ").append(strArray(prismPartsFound.toArray(new String[0]))).append(",\n");
        sb.append("    \"prism_option_expected\": ").append(str(PRISM_OPTION_EXPECTED)).append(",\n");
        sb.append("    \"prism_option_found\": ").append(str(prismOptionFound)).append(",\n");
        sb.append("    \"pass\": ").append(prismPass).append('\n');
        sb.append("  },\n");

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

    private static String four(int n) {
        String s = Integer.toString(n);
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }

    // ================================================================= logging / files
    // Log lines are held until object_audit.json is written, then appended in order: one per
    // container, one per unlisted or unknown object, one per missing owned name, the prism guard,
    // the RESULT line and "done". A FAIL result is not a macro failure: the macro returns normally.
    private void flushResultLines() {
        List<String> lines = new ArrayList<String>();
        List<String> tail = new ArrayList<String>();
        for (int i = 0; i < pendingLog.size(); i++) {
            String s = pendingLog.get(i);
            if (s.startsWith("missing ") || s.startsWith("prism guard ")) {
                tail.add(s);
            } else {
                lines.add(s);
            }
        }
        List<String[]> flagged = new ArrayList<String[]>();
        for (int i = 0; i < objectRows.size(); i++) {
            String[] o = objectRows.get(i);
            if (C_UNLISTED.equals(o[3]) || C_UNKNOWN.equals(o[3])) {
                flagged.add(o);
            }
        }
        Collections.sort(flagged, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) {
                int c = a[0].compareTo(b[0]);
                return c != 0 ? c : a[1].compareTo(b[1]);
            }
        });
        for (int i = 0; i < flagged.size(); i++) {
            String[] o = flagged.get(i);
            lines.add(o[3] + " " + o[0] + " " + o[1] + " " + o[2]);
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
