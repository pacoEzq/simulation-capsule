// run_macro.java
// Simulation Capsule -- piece 1 of 5. Simcenter STAR-CCM+ 2606 (Build 21.04.007).
// One public class, no records, no modules. javac 25.0.1 against the 2606 client jars.
//
// Markers TODO[U-nn] correspond to entries in the VERIFICATION LOG under UNRESOLVED.
// Members marked TODO[I-nn] are used but rest on an inference, not on a page that was read;
// they are listed in the log as UNRESOLVED (inferred) and are the expected compile risks.

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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

import star.base.neo.NeoObjectVector;
import star.base.report.AnalysisReport;
import star.base.report.ElementCountReport;
import star.base.report.MaxReport;
import star.base.report.MinReport;
import star.base.report.Report;
import star.base.report.ScalarReport;
import star.common.Boundary;
import star.common.FieldFunction;
import star.common.PartGroup;
import star.common.Region;
import star.common.ScalarGlobalParameter;
import star.common.Simulation;
import star.common.SimulationIterator;
import star.common.StarMacro;
import star.common.XyzInternalTable;
import star.common.ScalarPhysicalQuantity;
import star.meshing.AutoMeshOperation;
import star.meshing.BaseSize;
import star.meshing.CustomMeshControlManager;
import star.meshing.CustomMeshControlValueManager;
import star.meshing.MeshOperationManager;
import star.meshing.PartsMinimumSurfaceSize;
import star.meshing.PartsMinimumSurfaceSizeOption;
import star.meshing.PartsRelativeOrAbsoluteSize;
import star.meshing.PartsTargetSurfaceSize;
import star.meshing.PartsTargetSurfaceSizeOption;
import star.meshing.RelativeOrAbsoluteOption;
import star.meshing.SurfaceCustomMeshControl;
import star.prismmesher.CustomPrismValuesManager;
import star.prismmesher.NumPrismLayers;
import star.prismmesher.PartsCustomPrismsOption;
import star.prismmesher.PartsCustomizePrismMesh;
import star.prismmesher.PrismLayerStretching;
import star.vis.PartDisplayer;
import star.vis.PointPart;
import star.vis.ScalarDisplayer;
import star.vis.ScalarFillMode;
import star.vis.Scene;
import star.vis.SimpleAnnotation;
import java.awt.Color;
import star.base.neo.DoubleVector;
import star.base.neo.NamedObject;
import star.common.AbortFileStoppingCriterion;
import star.common.SolverStoppingCriterion;
import star.common.StepStoppingCriterion;
import star.vis.Annotation;
import star.vis.AnnotationProp;
import star.vis.AnnotationPropManager;
import star.vis.ClipMode;
import star.vis.DisplayerBase;
import star.vis.Legend;
import star.vis.PartColorMode;
// TODO(unverified signature): the recording shows the three class names, not their imports; star.vis
// is inferred from Legend and SimpleAnnotation. The laptop compile settles it.
import star.vis.LookupTableManager;
import star.vis.PredefinedLookupTable;
import star.vis.SimpleAnnotationProp;
import star.base.report.ReportMonitor;
import star.common.Table; // TODO(unverified signature): base class of every table, not read
import star.common.UserFieldFunction; // TODO(unverified signature): package as in object_audit.java, not read
import star.vis.AutoRangeMode; // TODO(unverified signature): enum and its NONE constant not read
import star.vis.VisProjectionMode; // TODO(unverified signature): enum and its PARALLEL constant not read

public class run_macro extends StarMacro {

    // ---------------------------------------------------------------- constants (spec, verbatim)
    // Corrected 19.09 (spec v3 sec. 6, tramo 0 on Re 3000, now invalidated by the mesh_level removal):
    // 100 was sub-Nyquist against a measured ~67-iteration period. The period must be re-measured on the
    // new template, but the corrected constant stands regardless.
    private static final int SAMPLING_INTERVAL = 10;
    // WINDOW_ITERATIONS is the window the window-stationarity criterion (v2, 23.09) is measured over --
    // as a sample count via SAMPLING_INTERVAL for the in-memory arrays (speed over
    // reading the native report live). The native StatisticsReport LLM_<mag>_mean/_var that mirrored it
    // were retired in spec v4 (section 4, 26.09): nothing read them.
    private static final int WINDOW_ITERATIONS = 2000;
    // Added 23.09 (criterion_version 2). The band criteria (band(last 5) < 1e-4, band(last 5) <
    // 2*band(previous 5)) and the limit_cycle status are retired: both tested a 50-iteration span, far
    // shorter than the shedding period, so they measured sampling noise and not stationarity. The macro
    // now compares consecutive closed windows; the inherited asymptotic criteria are disabled in run
    // mode so the decision has a single owner.
    private static final int DISCARD_ITERATIONS = 1000;
    private static final int MAX_WINDOWS = 7;
    private static final int MAX_ITERATIONS = DISCARD_ITERATIONS + MAX_WINDOWS * WINDOW_ITERATIONS; // 15000
    private static final double K_GATE = 2.0;
    private static final double K_SD = 3.0;
    private static final int MIN_CYCLES = 10;
    private static final double HYSTERESIS_SD = 0.1;
    private static final double CONVERGED_SD = 1.0e-4;
    private static final String[] QUANTITIES = { "cd", "cl", "cy", "cp_base" };
    // Spec v4 6 (mesh): the single mesh is declared for the whole sweep range, not for one Re. The range
    // is a declaration of sweep.json ("designed_for_Re_range"), passed by the driver as the property
    // designed_for_re_range; a point outside it runs anyway.
    private static final int PRISM_LAYERS_NONWALL = 0;
    // Mesh settings fixed by hand in the GUI 20-21.09.2026 and now owned by prepare (change A).
    private static final String CUBE_SURFACE_CONTROL = "cube_refinement";
    private static final String CUBE_SURFACE_SIZE = "0.03125*${D}";
    private static final int PRISM_LAYERS_DEFAULT = 12;
    private static final double PRISM_STRETCHING_DEFAULT = 1.2;
    // T3 leftovers (prepare only). The template is a copy of the T3 case and carries these 15 objects,
    // neither inherited nor LLM_; object_audit classifies them unknown. The list is literal: nothing
    // else is removed, by pattern or otherwise. Removal order is scenes, annotations, tables, since a
    // scene may reference an annotation or a table.
    private static final String[] LEFTOVER_SCENES = {
        "scene_y0", "scene_x1", "scene_x2", "scene_x4", "Geometry Scene 1", "Mesh Scene 1", "Scalar Scene 1"
    };
    private static final String[] LEFTOVER_ANNOTATIONS = { "anno_y0", "anno_x1", "anno_x2", "anno_x4" };
    private static final String[] LEFTOVER_TABLES = { "table_y0", "table_x1", "table_x2", "table_x4" };
    // Contract v10: the plane scene that output_exporter renders once per plane (y0, x1, x2, x4). It
    // shows u_over_U with the bar [-0.8, 1.4]; prepare creates it, run mode only checks it.
    private static final String PLANE_SCENE = "LLM_plane_u_over_U";
    private static final String PLANE_DISP = "LLM_disp_plane_u_over_U";
    private static final String PLANE_BODY = "LLM_body_plane_u_over_U";
    private static final String PLANE_TITLE = "LLM_title_plane_u_over_U";
    private static final String PLANE_FIELD = "u_over_U";
    // Size of the closed list of owned objects, as object_audit.java counts it: 1 mesh control, 1 probe,
    // 18 reports, 4 tables, and the plane scene with its 2 displayers and 1 title (spec v16, 7). The
    // four cut views and their 16 objects left the list (decision 78); the body view is a transient
    // object of output_exporter and never enters it (decision 77).
    private static final int OWNED_OBJECTS_EXPECTED = 28;
    // The fixed (non-generated) names of the closed list; the 16 extreme reports and the 4 tables are
    // generated from FIELDS, EXTREMA and PLANES. Prepare removes every LLM_ object outside the list.
    private static final String PRISM_CONTROL = "LLM_prism_off_nonwall";
    private static final String PROBE = "LLM_probe_axis_x2D";
    private static final String[] OWNED_FIXED_REPORTS = { "LLM_u_over_U_axis_x2D", "LLM_cell_count" };
    private static final String LLM_PREFIX = "LLM_";
    private static final double RANGE_PLANE_MIN = -0.8;
    private static final double RANGE_PLANE_MAX = 1.4;
    // Colour bar of the plane scene. Levels, clip, contour style, colormap,
    // label count and label format have setters in this file; legend title does not (see
    // applyLegendDeclared).
    private static final int COLOR_BAR_LEVELS = 256;
    private static final String COLOR_BAR_COLORMAP = "blue-red uniform perception";
    private static final int COLOR_BAR_LABELS = 6;
    private static final String COLOR_BAR_LABEL_FORMAT = "%.2f";
    // Top band of the plane title, as recorded in the 2606 GUI on 29.09.
    private static final double[] TITLE_POSITION = { 0.023, 0.916, 0.0 };
    private static final double MASS_IMBALANCE_LIMIT = 1.0e-3;
    // Added 20.09: the mass_imbalance guard fired on the first real sample (iteration 10), on the
    // transient of the Re jump, not on real divergence. Before 19.09 its first check fell at iteration
    // 100 because it was coupled to SAMPLING_INTERVAL; lowering that to 10 for Nyquist on the
    // convergence signal dragged this guard's first check along with it. The grace period restores the
    // former cut-off as a constant of its own, decoupled from SAMPLING_INTERVAL. The NaN/Inf checks get
    // no grace: they are unambiguous at any iteration.
    private static final int MASS_IMBALANCE_GRACE_ITERATIONS = 100;
    private static final String MU_EXPRESSION = "${rho}*${U}*${D}/${Re}";
    private static final String PROPS_NAME = "LLM_point.properties";
    // Run directory layout (contract v10): everything a point writes goes under work/cube_re<NNNN>/,
    // prepare's log under work/_prepare/. Nothing is written under capsules/ by this macro.
    private static final String WORK_DIR = "work";

    private static final String[] WALL_BOUNDARIES = {
        "cube_back", "cube_bottom", "cube_front", "cube_left", "cube_right", "cube_top"
    };
    private static final String[] NONWALL_BOUNDARIES = {
        "inlet", "outlet", "lateral_yminus", "lateral_yplus", "lateral_zminus", "lateral_zplus"
    };
    private static final String[] INHERITED_PARAMETERS = { "D", "U", "rho", "Re", "mu" };
    private static final String[] INHERITED_FIELD_FUNCTIONS = {
        "cp", "u_over_U", "v_over_U", "w_over_U", "x_D", "y_D", "z_D"
    };
    // v12: the three velocity ratios carry their final names, function name and presentation name
    // alike. Prepare renames a template that still has the old ones; run mode requires the new ones.
    // Same order in both arrays: old name -> new name.
    private static final String[] RENAMED_FIELD_FUNCTIONS_OLD = { "u_U", "v_U", "w_U" };
    private static final String[] RENAMED_FIELD_FUNCTIONS_NEW = { "u_over_U", "v_over_U", "w_over_U" };
    private static final String[] INHERITED_PARTS = {
        "grid_x1", "grid_x2", "grid_x4", "grid_y0",
        "section_x1", "section_x2", "section_x4", "section_y0", "wake_reversed"
    };
    private static final String[] INHERITED_REPORTS = {
        "cd", "cd_friction", "cd_pressure", "cl", "cy",
        "cp_base", "cp_min", "cp_stagnation",
        "mass_imbalance", "mdot_in", "mdot_out",
        "recirculation_length_D", "x_reattach"
    };
    private static final String[] INHERITED_MONITORS = { "cd_monitor", "cl_monitor" };
    private static final String[] INHERITED_CRITERIA = { "cd_asymptotic", "cl_asymptotic", "Maximum Steps" };

    // field / extremum / plane lists: the sixteen plane-extreme report names are generated from these
    private static final String[] FIELDS = { "cp", "u_over_U" };
    private static final String[] EXTREMA = { "min", "max" };
    private static final String[] PLANES = { "y0", "x1", "x2", "x4" };

    // field label -> underlying inherited field function name (v12: the same name for both fields)
    private static String fieldFunctionNameOf(String field) {
        if ("cp".equals(field)) {
            return "cp";
        }
        if ("u_over_U".equals(field)) {
            return "u_over_U";
        }
        throw new RuntimeException("run_macro: unknown field label '" + field + "'.");
    }

    // Extra quantities (25.09): read once at the stop iteration today, sampled here instead so they can
    // publish a pooled statistic over the closed windows, exactly like cd/cl/cy/cp_base already do. No
    // new STAR-CCM+ objects: every name below is an existing report, just read more often.
    // MEAN_EXTRA_QUANTITIES publish the pooled mean (iteration_mean, with sd); MIN_/MAX_EXTRA_QUANTITIES
    // publish the pooled min/max (window_min / window_max, no sd) over the same samples.
    private static final String[] MEAN_EXTRA_QUANTITIES = {
        "x_reattach", "recirculation_length_D", "LLM_u_over_U_axis_x2D"
    };
    // Spec v4 6.3 (27.09): the force components and the two mass flows were a single read at the stop
    // iteration; they are sampled with the rest and publish the family-A quintet. mass_imbalance is not
    // listed here: it keeps its own CSV column (read first, for the divergence guard) and is pooled
    // under its own name next to these. CSV order puts these four right after mass_imbalance (6.4).
    private static final String[] COMPONENT_EXTRA_QUANTITIES = {
        "cd_pressure", "cd_friction", "mdot_in", "mdot_out"
    };
    private static final String[] MIN_EXTRA_QUANTITIES = { "cp_min" };
    private static final String[] MAX_EXTRA_QUANTITIES = { "cp_stagnation" };

    // Report name of one plane-extreme quantity ("LLM_<field>_<extremum>_<plane>"), and the full list
    // for one extremum in FIELDS x PLANES order, matching the nesting buildSummaryJson already uses.
    private static String planeExtremeReportName(String field, String plane, String extremum) {
        return "LLM_" + field + "_" + extremum + "_" + plane;
    }

    private static String[] planeExtremeReportNames(String extremum) {
        String[] names = new String[PLANES.length * FIELDS.length];
        int i = 0;
        for (int p = 0; p < PLANES.length; p++) {
            for (int f = 0; f < FIELDS.length; f++) {
                names[i++] = planeExtremeReportName(FIELDS[f], PLANES[p], extremum);
            }
        }
        return names;
    }

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private final List<String> logLines = new ArrayList<String>();
    private final List<String> createdObjects = new ArrayList<String>();
    private final List<String> foundObjects = new ArrayList<String>();
    private final List<String> modifiedObjects = new ArrayList<String>();
    private final List<String> preconditionsChecked = new ArrayList<String>();
    private final List<String> unresolvedNotes = new ArrayList<String>();

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();

        String[] propsLocation = new String[1];
        Properties props = loadProperties(propsLocation);
        String mode = requireProperty(props, "mode");
        if (!"prepare".equals(mode) && !"run".equals(mode)) {
            throw new RuntimeException("run_macro: property 'mode' must be 'prepare' or 'run', got '" + mode + "'.");
        }

        log("mode=" + mode);
        log("properties_file_directory=" + propsLocation[0]);

        if ("prepare".equals(mode)) {
            doPrepare(props);
        } else {
            doRun(props);
        }
    }

    // ================================================================= properties
    private Properties loadProperties(String[] whichDirOut) {
        String sessionDir = null;
        try {
            sessionDir = sim.getSessionDir();
        } catch (Exception e) {
            sessionDir = null;
        }
        File candidate = null;
        String which = null;
        if (sessionDir != null && sessionDir.length() > 0) {
            File f = new File(sessionDir, PROPS_NAME);
            if (f.isFile()) {
                candidate = f;
                which = "session directory (Simulation.getSessionDir)";
            }
        }
        if (candidate == null) {
            File simDir = null;
            try {
                File sf = sim.getSessionDirFile();
                simDir = sf;
            } catch (Exception e) {
                simDir = null;
            }
            if (simDir != null) {
                File f = new File(simDir, PROPS_NAME);
                if (f.isFile()) {
                    candidate = f;
                    which = "directory of the .sim file (Simulation.getSessionDirFile)";
                }
            }
        }
        if (candidate == null) {
            throw new RuntimeException("run_macro: could not locate '" + PROPS_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }
        whichDirOut[0] = which;

        Properties p = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(candidate);
            p.load(in);
        } catch (Exception e) {
            throw new RuntimeException("run_macro: could not parse '" + PROPS_NAME + "': " + e.getMessage());
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

    private String requireProperty(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null) {
            throw new RuntimeException("run_macro: property '" + key + "' missing from " + PROPS_NAME + ".");
        }
        v = v.trim();
        if (v.length() == 0) {
            throw new RuntimeException("run_macro: property '" + key + "' is empty in " + PROPS_NAME + ".");
        }
        return v;
    }

    private int requireIntProperty(Properties p, String key) {
        String v = requireProperty(p, key);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new RuntimeException("run_macro: property '" + key + "' is not an integer: '" + v + "'.");
        }
    }

    // ================================================================= lookups (fail loudly)
    // TODO[I-01]: GlobalParameterManager is one of the nine ClientServerObjectManager subtypes
    //             object_audit.java walks with getObjects() (spec 3.1, Q76, "global parameters"), the
    //             same family MonitorManager was individually bench-measured under (I-07): getObject
    //             throws on a missing name, so the old "== null" check below never fired. Not itself
    //             bench-read, so kept as inferred; hasObject(String) is the fix.
    private ScalarGlobalParameter parameter(String name) {
        Object o = sim.getGlobalParameterManager().hasObject(name); // TODO[I-01]
        if (o == null) {
            sim.println("run_macro: missing global parameter: " + name);
            throw new RuntimeException("run_macro: missing global parameter '" + name + "'.");
        }
        if (!(o instanceof ScalarGlobalParameter)) {
            sim.println("run_macro: wrong type for global parameter: " + name);
            throw new RuntimeException("run_macro: global parameter '" + name + "' is not a ScalarGlobalParameter.");
        }
        return (ScalarGlobalParameter) o;
    }

    // TODO[I-02]: RegionManager is not among the nine managers spec 3.1 confirms as
    //             ClientServerObjectManager, so whether it throws or returns null on a missing name is
    //             still unread. try/catch treats a thrown exception as absence (spec 3.1 point 2, the
    //             PartGroup.getPart pattern) and also covers a plain null return -- safe either way.
    private Region region(String name) {
        Object o;
        try {
            o = sim.getRegionManager().getObject(name); // TODO[I-02]
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            sim.println("run_macro: missing region: " + name);
            throw new RuntimeException("run_macro: missing region '" + name + "'.");
        }
        if (!(o instanceof Region)) {
            throw new RuntimeException("run_macro: object '" + name + "' is not a Region.");
        }
        return (Region) o;
    }

    // TODO[I-03]: same situation as I-02 -- BoundaryManager is not among the nine confirmed managers.
    //             try/catch covers a thrown exception or a null return alike, without assuming which.
    private Boundary boundary(Region r, String name) {
        Object o;
        try {
            o = r.getBoundaryManager().getObject(name); // TODO[I-03]
        } catch (RuntimeException e) {
            o = null;
        }
        if (o == null) {
            sim.println("run_macro: missing boundary: " + name);
            throw new RuntimeException("run_macro: missing boundary '" + name + "'.");
        }
        if (!(o instanceof Boundary)) {
            throw new RuntimeException("run_macro: object '" + name + "' is not a Boundary.");
        }
        return (Boundary) o;
    }

    // TODO[I-04]: FieldFunctionManager is one of the nine confirmed ClientServerObjectManager
    //             subtypes (spec 3.1, Q76, "field functions"); same fix and same confidence tier as
    //             I-01/I-08 -- inferred from the manager-family grouping, not individually bench-read.
    private FieldFunction fieldFunction(String name) {
        Object o = sim.getFieldFunctionManager().hasObject(name); // TODO[I-04]
        if (isMissingFieldFunction(o)) {
            String msg = "run_macro: error: missing field function '" + name + "'.";
            sim.println(msg);
            throw new RuntimeException(msg);
        }
        if (!(o instanceof FieldFunction)) {
            throw new RuntimeException("run_macro: object '" + name + "' is not a FieldFunction.");
        }
        return (FieldFunction) o;
    }

    // Measured on 2606 (30.09, mode=prepare on a fresh template): FieldFunctionManager.getFunction(name)
    // for a name that does not exist returns an object of class NullFieldFunction, neither null nor an
    // exception. That class is matched by its simple name, not imported. Every field function looked up
    // by name in this file goes through this test, whatever the lookup call.
    private static boolean isMissingFieldFunction(Object ff) {
        return ff == null || ff.getClass().getSimpleName().equals("NullFieldFunction");
    }

    // ---- v12 rename of the velocity ratios ---------------------------------------------------
    // Lookup with the call recorded in the 2606 GUI (30.09): FieldFunctionManager.getFunction(String),
    // cast to UserFieldFunction; missing (see isMissingFieldFunction) is null here. Another class under
    // the name throws.
    private UserFieldFunction userFieldFunctionOrNull(String name) {
        Object o = sim.getFieldFunctionManager().getFunction(name);
        if (isMissingFieldFunction(o)) {
            return null;
        }
        if (!(o instanceof UserFieldFunction)) {
            throw new RuntimeException("run_macro: field function '" + name + "' is a "
                + o.getClass().getSimpleName() + ", not a UserFieldFunction; it cannot be renamed.");
        }
        return (UserFieldFunction) o;
    }

    // Prepare only. Each function is looked up by its new name, else by its old one, and renamed with
    // the recorded setters (function name and presentation name). Read before write: a function found
    // under its new name only gets its presentation name written if that one differs; the function
    // name is not written then, because no getter of it is in any source this chain may use (the
    // lookup by the new name is what reads it). Both names present as two objects throws.
    private void renameVelocityRatioFunctions() {
        for (int i = 0; i < RENAMED_FIELD_FUNCTIONS_NEW.length; i++) {
            String oldName = RENAMED_FIELD_FUNCTIONS_OLD[i];
            String newName = RENAMED_FIELD_FUNCTIONS_NEW[i];
            UserFieldFunction byNew = userFieldFunctionOrNull(newName);
            UserFieldFunction byOld = userFieldFunctionOrNull(oldName);
            if (byNew != null && byOld != null && byNew != byOld) {
                throw new RuntimeException("run_macro: field functions '" + oldName + "' and '" + newName
                    + "' are both present; remove one by hand before prepare renames it.");
            }
            UserFieldFunction ff = byNew != null ? byNew : byOld;
            if (ff == null) {
                String msg = "run_macro: error: field function '" + newName + "' missing, and '" + oldName
                    + "' too; nothing to rename.";
                sim.println(msg);
                throw new RuntimeException(msg);
            }
            if (byNew == null) {
                ff.setFunctionName(newName);
                modified(oldName, "Function Name", oldName, newName);
            } else {
                found(newName + " function name already " + newName);
            }
            String oldPresentation = ff.getPresentationName();
            if (!newName.equals(oldPresentation)) {
                ff.setPresentationName(newName);
                modified(oldPresentation, "Presentation Name", oldPresentation, newName);
            } else {
                found(newName + " presentation name already " + newName);
            }
            if (userFieldFunctionOrNull(newName) == null) {
                throw new RuntimeException("run_macro: field function '" + newName
                    + "' not found by its name after the rename.");
            }
        }
    }

    // Both modes, before the inherited field functions are checked. Run mode renames nothing: a .sim
    // that still carries an old name has not been through prepare since v12.
    private void requireRenamedFieldFunctions() {
        for (int i = 0; i < RENAMED_FIELD_FUNCTIONS_NEW.length; i++) {
            String newName = RENAMED_FIELD_FUNCTIONS_NEW[i];
            if (!isMissingFieldFunction(sim.getFieldFunctionManager().hasObject(newName))) { // TODO[I-04]
                continue;
            }
            String oldName = RENAMED_FIELD_FUNCTIONS_OLD[i];
            boolean oldThere = !isMissingFieldFunction(sim.getFieldFunctionManager().hasObject(oldName)); // TODO[I-04]
            String msg = "run_macro: error: field function '" + newName + "' not found"
                + (oldThere ? "; the .sim still carries '" + oldName + "', run mode=prepare to rename it" : "")
                + ".";
            sim.println(msg);
            throw new RuntimeException(msg);
        }
    }

    // Fixed 19.09: "verified" here always meant PartManager.getPart(String) is bench-confirmed to
    // throw on a missing name (spec 3.1 point 1), not that it returns null -- the check below never
    // fired for a genuinely missing part. hasObject(String) is confirmed working on this same manager
    // elsewhere in this file (LLM_probe_axis_x2D).
    private Object derivedPart(String name) {
        Object p = sim.getPartManager().hasObject(name);
        if (p == null) {
            sim.println("run_macro: missing derived part: " + name);
            throw new RuntimeException("run_macro: missing derived part '" + name + "'.");
        }
        return p;
    }

    // ReportManager.getReport(String) is verified.
    private Report report(String name) {
        Report r = sim.getReportManager().getReport(name);
        if (r == null) {
            sim.println("run_macro: missing report: " + name);
            throw new RuntimeException("run_macro: missing report '" + name + "'.");
        }
        return r;
    }

    private boolean reportExists(String name) {
        return sim.getReportManager().hasObject(name) != null;
    }

    // MeshOperationManager is a @TopLevelManager; RootObject.get(Class) is verified. Whether
    // MeshOperationManager.getObject(String) throws or returns null on a missing name is not among
    // the nine managers spec 3.1 confirms; try/catch covers both, same technique as I-02/I-03.
    private AutoMeshOperation meshCube() {
        MeshOperationManager mom = sim.get(MeshOperationManager.class);
        Object op;
        try {
            op = mom.getObject("mesh_cube");
        } catch (RuntimeException e) {
            op = null;
        }
        if (op == null) {
            sim.println("run_macro: missing mesh operation: mesh_cube");
            throw new RuntimeException("run_macro: missing mesh operation 'mesh_cube'.");
        }
        if (!(op instanceof AutoMeshOperation)) {
            throw new RuntimeException("run_macro: mesh operation 'mesh_cube' is not an AutoMeshOperation.");
        }
        return (AutoMeshOperation) op;
    }

    // Report.monitoredValue() is bench-verified and returns SI regardless of getUnits().
    private double value(String reportName) {
        return report(reportName).monitoredValue();
    }

    // MonitorManager.getObject(String) throws on a missing name (I-07, bench-measured, spec 3.1 tramo
    // 0); hasObject(String) is the confirmed existence test.
    private ReportMonitor monitor(String name) {
        Object o = sim.getMonitorManager().hasObject(name);
        if (o == null) {
            sim.println("run_macro: missing monitor: " + name);
            throw new RuntimeException("run_macro: missing monitor '" + name + "'.");
        }
        if (!(o instanceof ReportMonitor)) {
            throw new RuntimeException("run_macro: object '" + name + "' is not a ReportMonitor.");
        }
        return (ReportMonitor) o;
    }

    // ================================================================= PREPARE MODE
    private void doPrepare(Properties props) {
        // Single mesh level by design: the T3 published mesh.
        double baseSize = 0.5;

        renameVelocityRatioFunctions();
        checkInheritedObjects();

        Region fluid = region("fluid");
        AutoMeshOperation mesh = meshCube();

        // --- base size ------------------------------------------------------
        BaseSize bs = mesh.getDefaultValues().get(BaseSize.class);
        double oldBase = bs.getSIValue();
        if (oldBase != baseSize) {
            bs.setValue(baseSize);
            modified("mesh_cube", "Base Size", String.valueOf(oldBase), String.valueOf(baseSize));
        } else {
            found("mesh_cube Base Size already " + baseSize);
        }

        // --- cube surface sizes and default prism layers (A, 20-21.09 GUI settings) ---
        applyCubeSurfaceSizes();
        applyDefaultPrismLayers(mesh);

        // --- 1. LLM_prism_off_nonwall ---------------------------------------
        provisionPrismOffControl(mesh, fluid);

        // --- 2. LLM_probe_axis_x2D ------------------------------------------
        PointPart probe = provisionProbe(fluid);

        // --- 3. LLM_u_over_U_axis_x2D ---------------------------------------
        provisionScalarReport("LLM_u_over_U_axis_x2D", true, fieldFunction("u_over_U"), probe);

        // --- 4. LLM_cell_count ----------------------------------------------
        provisionElementCountReport("LLM_cell_count", fluid);

        // --- 5. sixteen plane-extreme reports (generated, not hand written) --
        for (int f = 0; f < FIELDS.length; f++) {
            for (int e = 0; e < EXTREMA.length; e++) {
                for (int p = 0; p < PLANES.length; p++) {
                    String name = "LLM_" + FIELDS[f] + "_" + EXTREMA[e] + "_" + PLANES[p];
                    boolean isMax = "max".equals(EXTREMA[e]);
                    FieldFunction ff = fieldFunction(fieldFunctionNameOf(FIELDS[f]));
                    Object section = derivedPart("section_" + PLANES[p]);
                    provisionScalarReport(name, isMax, ff, section);
                }
            }
        }

        // --- 6. four tables --------------------------------------------------
        for (int p = 0; p < PLANES.length; p++) {
            provisionTable("LLM_table_" + PLANES[p], "grid_" + PLANES[p]);
        }

        // --- 7. plane scene (spec v16: the only owned scene; the four cut views are gone) ---
        provisionPlaneScene(fluid);

        // --- 8-10. T3 leftovers, LLM_ objects outside the closed list, camera, colorbar range ------
        try {
            removeT3Leftovers();
            removeUnlistedLlmObjects();
            fixPlaneCamera();
            fixColorbarRanges();
            // The closed list of 28, checked with the same lookups run mode uses as its precondition.
            checkOwnedObjects();
            log("prepare: closed list of " + OWNED_OBJECTS_EXPECTED + " present");
        } catch (RuntimeException e) {
            log("prepare: FAIL " + e.getMessage());
            throw e;
        }

        // --- mesh pipeline, clear solution, save -----------------------------
        log("executing mesh pipeline (MeshOperationManager.executeAll)");
        sim.get(MeshOperationManager.class).executeAll();
        sim.clearSolution();
        log("solution cleared");

        double cells = value("LLM_cell_count");
        log("mesh_cells=" + cells);

        sim.saveState(sim.getSessionPath());
        log("simulation saved in place");

        // Spec v4 6.6.4: prepare's log goes to its own folder, not loose in capsules/.
        writeRunLog(prepareRunLogBody(baseSize, cells), sessionRelativeDir(WORK_DIR + File.separator + "_prepare"));
    }

    // A1 -- cube surface sizes, prepare only. Reproduces the two sizes fixed by hand in the GUI on
    // 20-21.09.2026 and read back from the mesh panel (405673 -> 405180 -> 501860 cells): Target and
    // Minimum Surface Size both 0.03125*${D}, absolute, on the cube's existing custom surface control.
    // The control keeps its inherited name; no second control is created.
    //
    // Note (log, not code): 0.03125*${D} only lands on trimmer step n=4 while D / Maximum Cell Size = 2,
    // i.e. at Base Size 0.5 with the current relative maximum. With another Base Size the mesher rounds
    // the request to the neighbouring step and the effective size is no longer 0.03125*D.
    private void applyCubeSurfaceSizes() {
        String name = CUBE_SURFACE_CONTROL;
        AutoMeshOperation mesh = meshCube();
        Object existing = mesh.getCustomMeshControls().hasObject(name);
        if (existing == null) {
            throw new RuntimeException("run_macro: missing custom mesh control '" + name + "'.");
        }
        if (!(existing instanceof SurfaceCustomMeshControl)) {
            throw new RuntimeException("run_macro: '" + name + "' exists but is not a SurfaceCustomMeshControl.");
        }
        SurfaceCustomMeshControl ctrl = (SurfaceCustomMeshControl) existing;
        found(name);

        PartsTargetSurfaceSizeOption tgtOpt = ctrl.getCustomConditions().get(PartsTargetSurfaceSizeOption.class);
        if (tgtOpt.getSelectedElement() != PartsTargetSurfaceSizeOption.Type.CUSTOM) {
            String old = String.valueOf(tgtOpt.getSelectedElement());
            tgtOpt.setSelected(PartsTargetSurfaceSizeOption.Type.CUSTOM);
            modified(name, "Target Surface Size (option)", old, "CUSTOM");
        } else {
            found(name + " Target Surface Size option already CUSTOM");
        }
        PartsMinimumSurfaceSizeOption minOpt = ctrl.getCustomConditions().get(PartsMinimumSurfaceSizeOption.class);
        if (minOpt.getSelectedElement() != PartsMinimumSurfaceSizeOption.Type.CUSTOM) {
            String old = String.valueOf(minOpt.getSelectedElement());
            minOpt.setSelected(PartsMinimumSurfaceSizeOption.Type.CUSTOM);
            modified(name, "Minimum Surface Size (option)", old, "CUSTOM");
        } else {
            found(name + " Minimum Surface Size option already CUSTOM");
        }

        CustomMeshControlValueManager values = ctrl.getCustomValues();
        applyAbsoluteSurfaceSize(name, "Target Surface Size",
            values.get(PartsTargetSurfaceSize.class));
        applyAbsoluteSurfaceSize(name, "Minimum Surface Size",
            values.get(PartsMinimumSurfaceSize.class));
    }

    private void applyAbsoluteSurfaceSize(String controlName, String label, PartsRelativeOrAbsoluteSize size) {
        RelativeOrAbsoluteOption opt = size.getRelativeOrAbsoluteOption();
        if (opt.getSelectedElement() != RelativeOrAbsoluteOption.Type.ABSOLUTE) {
            String old = String.valueOf(opt.getSelectedElement());
            opt.setSelected(RelativeOrAbsoluteOption.Type.ABSOLUTE);
            modified(controlName, label + " (relative/absolute)", old, "ABSOLUTE");
        } else {
            found(controlName + " " + label + " already ABSOLUTE");
        }
        ScalarPhysicalQuantity q = size.getAbsoluteSizeValue();
        String oldDef = q.getDefinition();
        if (!CUBE_SURFACE_SIZE.equals(oldDef)) {
            q.setDefinition(CUBE_SURFACE_SIZE);
            modified(controlName, label, String.valueOf(oldDef), CUBE_SURFACE_SIZE);
        } else {
            found(controlName + " " + label + " already " + CUBE_SURFACE_SIZE);
        }
    }

    // A2 -- Default Controls / Prism Layer Controls, prepare only: 12 layers, stretching 1.2
    // (was 2 / 1.5). Everything else in that panel is left as inherited: total thickness 33.33 % of
    // base (0.16665 m), Reduction 50 %, Convex 360 deg, Gap Fill 25 %, Min Thickness 10 %. Prism layers
    // stay disabled on the six non-wall boundaries through LLM_prism_off_nonwall, which is not touched.
    private void applyDefaultPrismLayers(AutoMeshOperation mesh) {
        NumPrismLayers layers = mesh.getDefaultValues().get(NumPrismLayers.class);
        // Read through the value objects, not the deprecated getNumLayers()/getStretching() pair
        // (javac -Xlint:deprecation flags both on 2606).
        int oldLayers = layers.getNumLayersValue().getValue();
        if (oldLayers != PRISM_LAYERS_DEFAULT) {
            layers.setNumLayers(PRISM_LAYERS_DEFAULT);
            modified("mesh_cube", "Number of Prism Layers",
                String.valueOf(oldLayers), String.valueOf(PRISM_LAYERS_DEFAULT));
        } else {
            found("mesh_cube Number of Prism Layers already " + PRISM_LAYERS_DEFAULT);
        }

        PrismLayerStretching stretching = mesh.getDefaultValues().get(PrismLayerStretching.class);
        double oldStretch = stretching.getStretchingQuantity().getSIValue();
        if (oldStretch != PRISM_STRETCHING_DEFAULT) {
            stretching.setStretching(PRISM_STRETCHING_DEFAULT);
            modified("mesh_cube", "Prism Layer Stretching",
                String.valueOf(oldStretch), String.valueOf(PRISM_STRETCHING_DEFAULT));
        } else {
            found("mesh_cube Prism Layer Stretching already " + PRISM_STRETCHING_DEFAULT);
        }
    }

    private void provisionPrismOffControl(AutoMeshOperation mesh, Region fluid) {
        String name = "LLM_prism_off_nonwall";
        CustomMeshControlManager controls = mesh.getCustomMeshControls();
        Object existing = controls.hasObject(name);
        SurfaceCustomMeshControl ctrl;
        if (existing == null) {
            ctrl = controls.createSurfaceControl();
            ctrl.setPresentationName(name);
            created(name + " (SurfaceCustomMeshControl under mesh_cube)");
        } else {
            if (!(existing instanceof SurfaceCustomMeshControl)) {
                throw new RuntimeException("run_macro: '" + name + "' exists but is not a SurfaceCustomMeshControl.");
            }
            ctrl = (SurfaceCustomMeshControl) existing;
            found(name);
        }

        if (!ctrl.getEnableControl()) {
            ctrl.setEnableControl(true);
            modified(name, "Enable Control", "false", "true");
        }

        // Part surfaces of the six non-wall boundaries. The selection is cleared and then set,
        // so a second prepare run does not stack surfaces (pattern already run on 2606 in this series).
        List<star.common.PartSurface> surfaces = new ArrayList<star.common.PartSurface>();
        for (int i = 0; i < NONWALL_BOUNDARIES.length; i++) {
            Boundary b = boundary(fluid, NONWALL_BOUNDARIES[i]);
            Collection<star.common.PartSurface> ps = b.getPartSurfaceGroup().getObjects();
            if (ps == null || ps.isEmpty()) {
                throw new RuntimeException("run_macro: boundary '" + NONWALL_BOUNDARIES[i] + "' has no part surfaces.");
            }
            surfaces.addAll(ps);
        }
        ctrl.getGeometryObjects().setQuery(null);
        ctrl.getGeometryObjects().setObjects(surfaces.toArray(new star.common.PartSurface[0]));
        modified(name, "Part Surfaces", "(replaced)", surfaces.size() + " part surfaces of the six non-wall boundaries");

        PartsCustomizePrismMesh customize = ctrl.getCustomConditions().get(PartsCustomizePrismMesh.class);
        customize.getCustomPrismOptions().setSelected(PartsCustomPrismsOption.Type.DISABLE);
        modified(name, "Customize Prism Mesh", "(unread)", "DISABLE");
    }

    private PointPart provisionProbe(Region fluid) {
        String name = "LLM_probe_axis_x2D";
        Object existing = sim.getPartManager().hasObject(name);
        PointPart pp;
        if (existing == null) {
            pp = sim.getPartManager().createPointPart();
            pp.setPresentationName(name);
            created(name + " (PointPart)");
        } else {
            if (!(existing instanceof PointPart)) {
                throw new RuntimeException("run_macro: '" + name + "' exists but is not a PointPart.");
            }
            pp = (PointPart) existing;
            found(name);
        }
        // Input region and point in lab coordinates (pattern run on 2602 in this series; confirmed in the dry run).
        List<NamedObject> input = new ArrayList<NamedObject>();
        input.add(fluid);
        pp.setInputPartsCollection(input);
        modified(name, "Input Parts", "(unread)", "fluid");
        double d = parameter("D").getQuantity().getSIValue();
        double[] point = { 2.0 * d, 0.0, 0.0 };
        pp.setPoint(new DoubleVector(point));
        modified(name, "Point", "(unread)", "[" + point[0] + ", 0.0, 0.0] from 2.0 * D with D = " + d);
        return pp;
    }

    private void provisionScalarReport(String name, boolean isMax, FieldFunction ff, Object part) {
        ScalarReport rep;
        if (!reportExists(name)) {
            if (isMax) {
                rep = sim.getReportManager().createReport(MaxReport.class);
            } else {
                rep = sim.getReportManager().createReport(MinReport.class);
            }
            rep.setPresentationName(name);
            created(name + " (" + (isMax ? "MaxReport" : "MinReport") + ")");
        } else {
            Report r = report(name);
            if (!(r instanceof ScalarReport)) {
                throw new RuntimeException("run_macro: report '" + name + "' is not a ScalarReport.");
            }
            if (isMax && !(r instanceof MaxReport)) {
                throw new RuntimeException("run_macro: report '" + name + "' exists but is not a MaxReport.");
            }
            if (!isMax && !(r instanceof MinReport)) {
                throw new RuntimeException("run_macro: report '" + name + "' exists but is not a MinReport.");
            }
            rep = (ScalarReport) r;
            found(name);
        }
        FieldFunction oldFf = rep.getFieldFunction();
        if (oldFf != ff) {
            rep.setFieldFunction(ff);
            modified(name, "Field Function", oldFf == null ? "null" : oldFf.getPresentationName(),
                ff.getPresentationName());
        }
        addPartToReport(name, rep, part);
    }

    private void provisionElementCountReport(String name, Region fluid) {
        ElementCountReport rep;
        if (!reportExists(name)) {
            rep = sim.getReportManager().createReport(ElementCountReport.class);
            rep.setPresentationName(name);
            created(name + " (ElementCountReport)");
        } else {
            Report r = report(name);
            if (!(r instanceof ElementCountReport)) {
                throw new RuntimeException("run_macro: report '" + name + "' is not an ElementCountReport.");
            }
            rep = (ElementCountReport) r;
            found(name);
        }
        addPartToReport(name, rep, fluid);
    }

    // AnalysisReport.getParts() -> PartGroup, PartGroup.addPart(NamedObject) are both verified.
    // TODO[I-05]: Part / Region are assumed to be assignable to NamedObject (their own pages were
    //             not read for Region; Part's hierarchy was not read). Cast is unchecked at spec level.
    private void addPartToReport(String reportName, AnalysisReport rep, Object part) {
        PartGroup pg = rep.getParts();
        if (!(part instanceof star.base.neo.NamedObject)) { // TODO[I-05]
            throw new RuntimeException("run_macro: part for report '" + reportName + "' is not a NamedObject.");
        }
        star.base.neo.NamedObject no = (star.base.neo.NamedObject) part;
        boolean alreadyIn1 = false;
        try { alreadyIn1 = (pg.getPart(no.getPresentationName()) != null); }
        catch (RuntimeException e1) { alreadyIn1 = false; }
        if (!alreadyIn1) {
            pg.addPart(no);
            modified(reportName, "Parts", "(without " + no.getPresentationName() + ")",
                "+" + no.getPresentationName());
        }
    }

    private void provisionTable(String name, String gridPartName) {
        Object existing = sim.getTableManager().hasTable(name);
        XyzInternalTable table;
        if (existing == null) {
            table = sim.getTableManager().createTabularObject(XyzInternalTable.class);
            table.setPresentationName(name);
            created(name + " (XyzInternalTable)");
        } else {
            if (!(existing instanceof XyzInternalTable)) {
                throw new RuntimeException("run_macro: table '" + name + "' exists but is not an XyzInternalTable.");
            }
            table = (XyzInternalTable) existing;
            found(name);
        }

        Object grid = derivedPart(gridPartName);
        if (!(grid instanceof star.base.neo.NamedObject)) { // TODO[I-05]
            throw new RuntimeException("run_macro: derived part '" + gridPartName + "' is not a NamedObject.");
        }
        star.base.neo.NamedObject gridNo = (star.base.neo.NamedObject) grid;
        boolean alreadyIn2 = false;
        try { alreadyIn2 = (table.getParts().getPart(gridNo.getPresentationName()) != null); }
        catch (RuntimeException e2) { alreadyIn2 = false; }
        if (!alreadyIn2) {
            table.getParts().addPart(gridNo);
            modified(name, "Parts", "(without " + gridPartName + ")", "+" + gridPartName);
        }

        List<FieldFunction> ffs = new ArrayList<FieldFunction>(Arrays.<FieldFunction>asList(
            fieldFunction("x_D"), fieldFunction("y_D"), fieldFunction("z_D"),
            fieldFunction("cp"), fieldFunction("u_over_U"), fieldFunction("v_over_U"), fieldFunction("w_over_U")));
        table.setFieldFunctions(ffs);
        modified(name, "Field Functions", "(unread)", "x_D,y_D,z_D,cp,u_over_U,v_over_U,w_over_U");

        table.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
        modified(name, "Representation", "(unread)", "default FV representation");

        if (!table.getExtractVertexData()) {
            table.setExtractVertexData(true);
            modified(name, "Data on Vertices", "false", "true");
        }
    }

    // Contract v10: LLM_plane_u_over_U, one scene that output_exporter points at section_<plane> and
    // renders once per plane. Prepare gives the displayer no part: the exporter sets it per plane.
    private void provisionPlaneScene(Region fluid) {
        Scene scene = sim.getSceneManager().hasScene(PLANE_SCENE);
        if (scene == null) {
            scene = sim.getSceneManager().createScene();
            scene.setPresentationName(PLANE_SCENE);
            created(PLANE_SCENE + " (Scene)");
        } else {
            found(PLANE_SCENE);
        }
        provisionScalarDisplayer(scene, PLANE_DISP, PLANE_FIELD);
        provisionBodyDisplayer(scene, PLANE_BODY, fluid);
        provisionTitle(PLANE_SCENE, scene, PLANE_TITLE, PLANE_FIELD + " on plane");
    }

    // Scalar displayer of a view or of the plane scene: representation, smooth filled, function, clamp,
    // colour bar. The fixed range is not set here (fixColorbarRanges, after the function).
    private ScalarDisplayer provisionScalarDisplayer(Scene scene, String dispName, String field) {
        ScalarDisplayer sd;
        if (!scene.getDisplayerManager().hasDisplayer(dispName)) {
            sd = scene.getDisplayerManager().createScalarDisplayer(dispName);
            sd.setPresentationName(dispName);
            created(dispName + " (ScalarDisplayer)");
        } else {
            Object d = scene.getDisplayerManager().getObject(dispName);
            if (!(d instanceof ScalarDisplayer)) {
                throw new RuntimeException("run_macro: displayer '" + dispName + "' is not a ScalarDisplayer.");
            }
            sd = (ScalarDisplayer) d;
            found(dispName);
        }
        sd.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
        sd.setFillMode(ScalarFillMode.NODE_FILLED); // Smooth Filled
        modified(dispName, "Contour Style", "(unread)", "NODE_FILLED (Smooth Filled)");
        // The function goes first: changing it resets a manual range.
        sd.getScalarDisplayQuantity().setFieldFunction(fieldFunction(fieldFunctionNameOf(field)));
        modified(dispName, "Function", "(unread)", fieldFunctionNameOf(field));
        // The fixed range is set later in prepare, by fixColorbarRanges(), after this function.
        sd.getScalarDisplayQuantity().setClip(ClipMode.NONE); // clamp: out of range keeps the end colour
        modified(dispName, "Clip", "(unread)", "NONE (clamp)");
        Legend legend = sd.getLegend();
        legend.setLevels(COLOR_BAR_LEVELS);
        modified(dispName, "Color Bar Levels", "(unread)", String.valueOf(COLOR_BAR_LEVELS));
        applyLegendDeclared(dispName, legend);
        return sd;
    }

    // Colormap, label count and label format of a colour bar, with the calls recorded in the 2606 GUI
    // on 29.09. Each value is read first and written only if it differs.
    // legend title: not set, no recorded call.
    private void applyLegendDeclared(String dispName, Legend legend) {
        PredefinedLookupTable lut = colorBarLookupTable();
        Object oldLut = legend.getLookupTable(); // getter of a recorded setter
        if (!lut.equals(oldLut)) {
            legend.setLookupTable(lut);
            modified(dispName, "Color Map", lookupTableName(oldLut), COLOR_BAR_COLORMAP);
        } else {
            found(dispName + " Color Map already '" + COLOR_BAR_COLORMAP + "'");
        }

        Object oldLabels = legend.getNumberOfLabels(); // getter of a recorded setter
        if (!(oldLabels instanceof Number && ((Number) oldLabels).longValue() == COLOR_BAR_LABELS)) {
            legend.setNumberOfLabels(COLOR_BAR_LABELS);
            modified(dispName, "Number of Labels", String.valueOf(oldLabels), String.valueOf(COLOR_BAR_LABELS));
        } else {
            found(dispName + " Number of Labels already " + COLOR_BAR_LABELS);
        }

        Object oldFormat = legend.getLabelFormat(); // getter of a recorded setter
        if (!COLOR_BAR_LABEL_FORMAT.equals(oldFormat)) {
            legend.setLabelFormat(COLOR_BAR_LABEL_FORMAT);
            modified(dispName, "Label Format", String.valueOf(oldFormat), COLOR_BAR_LABEL_FORMAT);
        } else {
            found(dispName + " Label Format already '" + COLOR_BAR_LABEL_FORMAT + "'");
        }
    }

    // The predefined table by its name, looked up as the recording does; absent throws with the name.
    private PredefinedLookupTable colorBarLookupTable() {
        Object o = sim.get(LookupTableManager.class).getObject(COLOR_BAR_COLORMAP);
        if (!(o instanceof PredefinedLookupTable)) {
            throw new RuntimeException("run_macro: lookup table '" + COLOR_BAR_COLORMAP + "' read as "
                + (o == null ? "null" : o.getClass().getName()) + ", not a PredefinedLookupTable.");
        }
        return (PredefinedLookupTable) o;
    }

    private static String lookupTableName(Object o) {
        if (o instanceof NamedObject) {
            return ((NamedObject) o).getPresentationName();
        }
        return o == null ? "null" : o.getClass().getName();
    }

    // Part displayer: the six wall boundaries in plain gray, surface only, no mesh.
    private void provisionBodyDisplayer(Scene scene, String bodyName, Region fluid) {
        PartDisplayer pd;
        if (!scene.getDisplayerManager().hasDisplayer(bodyName)) {
            pd = scene.getDisplayerManager().createPartDisplayer(bodyName);
            pd.setPresentationName(bodyName);
            created(bodyName + " (PartDisplayer)");
        } else {
            Object d = scene.getDisplayerManager().getObject(bodyName);
            if (!(d instanceof PartDisplayer)) {
                throw new RuntimeException("run_macro: displayer '" + bodyName + "' is not a PartDisplayer.");
            }
            pd = (PartDisplayer) d;
            found(bodyName);
        }
        pd.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
        for (int i = 0; i < WALL_BOUNDARIES.length; i++) {
            addPartOnce(bodyName, pd, boundary(fluid, WALL_BOUNDARIES[i]), WALL_BOUNDARIES[i]);
        }
        pd.setSurface(true);
        pd.setMesh(false);
        pd.setOutline(false);
        modified(bodyName, "Surface/Mesh/Outline", "(unread)", "true/false/false");
        pd.setColorMode(PartColorMode.CONSTANT);
        pd.setDisplayerColorColor(Color.GRAY);
        modified(bodyName, "Color", "(unread)", "CONSTANT, gray");
    }

    // Title annotation bound to its scene. The text is written only if it differs from the one read.
    private void provisionTitle(String sceneName, Scene scene, String titleName, String text) {
        Object existingAnn = sim.getAnnotationManager().hasObject(titleName);
        SimpleAnnotation ann;
        if (existingAnn == null) {
            ann = sim.getAnnotationManager().createSimpleAnnotation();
            ann.setPresentationName(titleName);
            created(titleName + " (SimpleAnnotation)");
        } else {
            if (!(existingAnn instanceof SimpleAnnotation)) {
                throw new RuntimeException("run_macro: annotation '" + titleName + "' is not a SimpleAnnotation.");
            }
            ann = (SimpleAnnotation) existingAnn;
            found(titleName);
        }
        writeTitleText(titleName, ann, text);
        placeTitle(sceneName, scene, ann);
        applyTitleBand(sceneName, scene, titleName, ann);
    }

    private void writeTitleText(String titleName, SimpleAnnotation ann, String text) {
        String oldText = ann.getText();
        if (oldText == null || !text.equals(oldText)) {
            ann.setText(text);
            modified(titleName, "Text", String.valueOf(oldText), text);
        }
    }

    // Top band, no shadow, with the calls recorded in the 2606 GUI on 29.09: the shadow is set on the
    // annotation, the position on the scene's prop of it (placeTitle has bound it). Each value is read
    // first and written only if it differs.
    private void applyTitleBand(String sceneName, Scene scene, String titleName, SimpleAnnotation ann) {
        // getter of a recorded setter
        // TODO(unverified signature): getShadow() is the name inferred from setShadow(boolean); if javac
        // rejects it, isShadow() is the other form the pair can take.
        Object shadow = ann.getShadow();
        if (!Boolean.FALSE.equals(shadow)) {
            ann.setShadow(false);
            modified(titleName, "Shadow", String.valueOf(shadow), "false");
        } else {
            found(titleName + " Shadow already false");
        }

        Object p = scene.getAnnotationPropManager().getObject(titleName);
        if (!(p instanceof SimpleAnnotationProp)) {
            throw new RuntimeException("run_macro: prop of '" + titleName + "' in '" + sceneName + "' read as "
                + (p == null ? "null" : p.getClass().getName()) + ", not a SimpleAnnotationProp.");
        }
        SimpleAnnotationProp prop = (SimpleAnnotationProp) p;
        double[] pos = viewVector(prop.getPosition(), sceneName, titleName + " position"); // getter of a recorded setter
        if (!near(pos, TITLE_POSITION)) {
            prop.setPosition(new DoubleVector(TITLE_POSITION));
            modified(titleName, "Position", vec(pos), vec(TITLE_POSITION));
        } else {
            found(titleName + " Position already " + vec(TITLE_POSITION));
        }
    }

    // Adding a part is additive: a re-run must not stack duplicates.
    private void addPartOnce(String owner, DisplayerBase disp, Object part, String label) {
        if (!(part instanceof NamedObject)) {
            throw new RuntimeException("run_macro: '" + label + "' cannot be shown in " + owner + ": not a NamedObject.");
        }
        NamedObject no = (NamedObject) part;
        if (disp.hasPart(no)) {
            found(owner + " already shows " + label);
            return;
        }
        disp.addPart(no);
        modified(owner, "Parts", "(without " + label + ")", "+" + label);
    }

    // The per-scene prop binds the annotation to the scene. Its position is set by applyTitleBand.
    private void placeTitle(String sceneName, Scene scene, Annotation ann) {
        AnnotationPropManager apm = scene.getAnnotationPropManager();
        AnnotationProp prop = apm.hasPropForAnnotation(ann)
                            ? apm.getPropForAnnotation(ann)
                            : apm.createPropForAnnotation(ann);
        prop.setVisible(true);
        modified(ann.getPresentationName(), "Scene", "(unbound)", sceneName);
    }

    // ---- 8. T3 leftovers (prepare only) -------------------------------------------------------
    // Each name is tested with the lookup its manager already uses in this file; absent is not an
    // error. After a removal the name is looked up again, so "removed" is only logged once it is gone.
    private void removeT3Leftovers() {
        for (int i = 0; i < LEFTOVER_SCENES.length; i++) {
            String name = LEFTOVER_SCENES[i];
            Scene sc = sim.getSceneManager().hasScene(name);
            if (sc == null) {
                log("prepare: leftover " + name + " absent");
                continue;
            }
            // TODO(unverified signature): SceneManager.deleteScenes(Collection) taking a NeoObjectVector.
            // No listed source removes a scene; the laptop compile settles it.
            sim.getSceneManager().deleteScenes(new NeoObjectVector(new Object[] { sc }));
            if (sim.getSceneManager().hasScene(name) != null) {
                throw new RuntimeException("run_macro: leftover scene '" + name + "' still present after removal.");
            }
            leftoverRemoved(name);
        }
        for (int i = 0; i < LEFTOVER_ANNOTATIONS.length; i++) {
            String name = LEFTOVER_ANNOTATIONS[i];
            Object a = sim.getAnnotationManager().hasObject(name);
            if (a == null) {
                log("prepare: leftover " + name + " absent");
                continue;
            }
            if (!(a instanceof Annotation)) {
                throw new RuntimeException("run_macro: leftover '" + name + "' is not an Annotation.");
            }
            // TODO(unverified signature): AnnotationManager.remove(Annotation). No listed source removes an
            // annotation; the laptop compile settles it.
            sim.getAnnotationManager().remove((Annotation) a);
            if (sim.getAnnotationManager().hasObject(name) != null) {
                throw new RuntimeException("run_macro: leftover annotation '" + name + "' still present after removal.");
            }
            leftoverRemoved(name);
        }
        for (int i = 0; i < LEFTOVER_TABLES.length; i++) {
            String name = LEFTOVER_TABLES[i];
            Object t = sim.getTableManager().hasTable(name);
            if (t == null) {
                log("prepare: leftover " + name + " absent");
                continue;
            }
            // TODO(unverified signature): star.common.Table as the base class of every table, and
            // TableManager.remove(Table). No listed source removes a table; the laptop compile settles it.
            if (!(t instanceof Table)) {
                throw new RuntimeException("run_macro: leftover '" + name + "' is not a Table.");
            }
            sim.getTableManager().remove((Table) t);
            if (sim.getTableManager().hasTable(name) != null) {
                throw new RuntimeException("run_macro: leftover table '" + name + "' still present after removal.");
            }
            leftoverRemoved(name);
        }
    }

    private void leftoverRemoved(String name) {
        modified(name, "Object", "present", "removed");
        log("prepare: leftover " + name + " removed");
    }

    // ---- 8b. LLM_ objects outside the closed list (prepare and run, spec v16 3.1 and 7) ---------
    // Decision 78 took the 16 objects of the four cut views out of the list; a template prepared before
    // v16 still carries them. The managers object_audit.java walks are walked here with the same
    // getObjects() calls. Scenes, annotations and tables are removed with the calls of
    // removeT3Leftovers, scenes first, since a scene binds its title and owns its displayers. For the
    // other managers this chain has no removal call: an unlisted LLM_ object there stops prepare with its
    // name, as it would make object_audit fail.
    private void removeUnlistedLlmObjects() {
        // Scenes: only LLM_plane_u_over_U is owned.
        List<Scene> scenes = new ArrayList<Scene>();
        for (Object o : sim.getSceneManager().getObjects()) {
            if (o instanceof Scene && isUnlisted(((Scene) o).getPresentationName(), new String[] { PLANE_SCENE })) {
                scenes.add((Scene) o);
            }
        }
        for (int i = 0; i < scenes.size(); i++) {
            String name = scenes.get(i).getPresentationName();
            // Same call as removeT3Leftovers (TODO there).
            sim.getSceneManager().deleteScenes(new NeoObjectVector(new Object[] { scenes.get(i) }));
            if (sim.getSceneManager().hasScene(name) != null) {
                throw new RuntimeException("run_macro: unlisted scene '" + name + "' still present after removal.");
            }
            unlistedRemoved(name, "scene");
        }

        // Displayers of the owned scene: only its two.
        Scene plane = viewScene(PLANE_SCENE);
        for (Object o : plane.getDisplayerManager().getObjects()) {
            String name = o instanceof NamedObject ? ((NamedObject) o).getPresentationName() : "";
            if (isUnlisted(name, new String[] { PLANE_DISP, PLANE_BODY })) {
                throw unlistedNotRemovable(name, "displayer of " + PLANE_SCENE);
            }
        }

        // Annotations: only LLM_title_plane_u_over_U.
        List<Annotation> annotations = new ArrayList<Annotation>();
        for (Object o : sim.getAnnotationManager().getObjects()) {
            if (o instanceof Annotation && isUnlisted(((Annotation) o).getPresentationName(), new String[] { PLANE_TITLE })) {
                annotations.add((Annotation) o);
            }
        }
        for (int i = 0; i < annotations.size(); i++) {
            String name = annotations.get(i).getPresentationName();
            // Same call as removeT3Leftovers (TODO there).
            sim.getAnnotationManager().remove(annotations.get(i));
            if (sim.getAnnotationManager().hasObject(name) != null) {
                throw new RuntimeException("run_macro: unlisted annotation '" + name + "' still present after removal.");
            }
            unlistedRemoved(name, "annotation");
        }

        // Tables: the four LLM_table_<plane>.
        String[] ownedTables = new String[PLANES.length];
        for (int p = 0; p < PLANES.length; p++) {
            ownedTables[p] = "LLM_table_" + PLANES[p];
        }
        List<Table> tables = new ArrayList<Table>();
        for (Object o : sim.getTableManager().getObjects()) {
            if (o instanceof Table && o instanceof NamedObject
                && isUnlisted(((NamedObject) o).getPresentationName(), ownedTables)) {
                tables.add((Table) o);
            }
        }
        for (int i = 0; i < tables.size(); i++) {
            String name = ((NamedObject) tables.get(i)).getPresentationName();
            // Same call as removeT3Leftovers (TODO there).
            sim.getTableManager().remove(tables.get(i));
            if (sim.getTableManager().hasTable(name) != null) {
                throw new RuntimeException("run_macro: unlisted table '" + name + "' still present after removal.");
            }
            unlistedRemoved(name, "table");
        }

        // Managers without a removal call in this chain: found means stop.
        List<String> reports = new ArrayList<String>(Arrays.asList(OWNED_FIXED_REPORTS));
        for (int f = 0; f < FIELDS.length; f++) {
            for (int e = 0; e < EXTREMA.length; e++) {
                for (int p = 0; p < PLANES.length; p++) {
                    reports.add("LLM_" + FIELDS[f] + "_" + EXTREMA[e] + "_" + PLANES[p]);
                }
            }
        }
        requireNoUnlisted(sim.getReportManager().getObjects(), reports.toArray(new String[0]), "report");
        requireNoUnlisted(sim.getPartManager().getObjects(), new String[] { PROBE }, "part");
        requireNoUnlisted(sim.getFieldFunctionManager().getObjects(), new String[0], "field function");
        requireNoUnlisted(sim.getMonitorManager().getObjects(), new String[0], "monitor");
        requireNoUnlisted(sim.getSolverStoppingCriterionManager().getObjects(), new String[0], "stopping criterion");
        requireNoUnlisted(sim.getGlobalParameterManager().getObjects(), new String[0], "global parameter");
        // Same call as object_audit.walkCustomMeshControls (TODO there).
        requireNoUnlisted(meshCube().getCustomMeshControls().getObjects(), new String[] { PRISM_CONTROL },
            "custom mesh control of mesh_cube");
        log("no LLM_ object outside the closed list of " + OWNED_OBJECTS_EXPECTED);
    }

    private static boolean isUnlisted(String name, String[] owned) {
        return name != null && name.startsWith(LLM_PREFIX) && !Arrays.asList(owned).contains(name);
    }

    private void requireNoUnlisted(Collection<?> objects, String[] owned, String kind) {
        for (Object o : objects) {
            String name = o instanceof NamedObject ? ((NamedObject) o).getPresentationName() : "";
            if (isUnlisted(name, owned)) {
                throw unlistedNotRemovable(name, kind);
            }
        }
    }

    private RuntimeException unlistedNotRemovable(String name, String kind) {
        sim.println("run_macro: unlisted LLM_ object: " + name);
        return new RuntimeException("run_macro: " + kind + " '" + name + "' is an LLM_ object outside the closed "
            + "list of " + OWNED_OBJECTS_EXPECTED + " and this chain has no verified call to remove a " + kind
            + "; remove it by hand and record the call.");
    }

    private void unlistedRemoved(String name, String kind) {
        modified(name, "Object", "present", "removed");
        log("unlisted " + kind + " " + name + " removed");
    }

    // A view scene or displayer missing at this point is a macro failure; doPrepare logs it as FAIL.
    private Scene viewScene(String name) {
        Scene sc = sim.getSceneManager().hasScene(name);
        if (sc == null) {
            sim.println("run_macro: missing owned object: " + name);
            throw new RuntimeException("run_macro: missing owned object '" + name + "'.");
        }
        return sc;
    }

    private ScalarDisplayer viewDisplayer(Scene sc, String name) {
        if (!sc.getDisplayerManager().hasDisplayer(name)) {
            sim.println("run_macro: missing owned object: " + name);
            throw new RuntimeException("run_macro: missing owned object '" + name + "'.");
        }
        Object d = sc.getDisplayerManager().getObject(name);
        if (!(d instanceof ScalarDisplayer)) {
            throw new RuntimeException("run_macro: displayer '" + name + "' is not a ScalarDisplayer.");
        }
        return (ScalarDisplayer) d;
    }

    // ---- 9. camera of the plane scene (prepare only) -----------------------------------------
    // Projection only: output_exporter sets the rest of its camera per plane.
    private void fixPlaneCamera() {
        ensureParallel(PLANE_SCENE, viewScene(PLANE_SCENE));
    }

    // Contract v10: projection, like the rest of the camera, is written only if the value read
    // differs. The getter returns the Integer 1 for parallel (measured on 2606), so the old test
    // against VisProjectionMode.PARALLEL rewrote it on every prepare.
    // TODO(unverified signature): star.vis.VisProjectionMode.PARALLEL and
    // CurrentView.setProjectionMode(VisProjectionMode); getProjectionMode() is deprecated on 2606
    // and kept, no replacement is in any source this chain may use.
    private void ensureParallel(String sceneName, Scene sc) {
        Object mode = sc.getCurrentView().getProjectionMode();
        if (!isParallel(mode)) {
            sc.getCurrentView().setProjectionMode(VisProjectionMode.PARALLEL);
            modified(sceneName, "Projection", String.valueOf(mode), "PARALLEL");
        } else {
            found(sceneName + " Projection already PARALLEL");
        }
    }

    private static boolean isParallel(Object mode) {
        return Integer.valueOf(1).equals(mode) || VisProjectionMode.PARALLEL.equals(mode)
            || (mode != null && "PARALLEL".equalsIgnoreCase(mode.toString()));
    }

    // A camera getter's value as three doubles. Anything else fails with its class name, since the
    // getters' return type is not verified.
    // TODO(unverified signature): DoubleVector.toDoubleArray(); used by manifest_writer.java.
    private static double[] viewVector(Object o, String sceneName, String what) {
        double[] a = null;
        if (o instanceof DoubleVector) {
            a = ((DoubleVector) o).toDoubleArray();
        } else if (o instanceof double[]) {
            a = (double[]) o;
        }
        if (a == null || a.length != 3) {
            throw new RuntimeException("run_macro: " + what + " of '" + sceneName + "' read as "
                + (o == null ? "null" : o.getClass().getName()) + ", not three doubles.");
        }
        return a;
    }

    private static boolean near(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1.0e-9 * Math.max(1.0, Math.abs(b[i]))) {
                return false;
            }
        }
        return true;
    }

    private static String vec(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(a[i]);
        }
        return sb.append(']').toString();
    }

    // ---- 10. fixed colorbar range of LLM_disp_plane_u_over_U (prepare only) ----------------------
    // Clip stays as provisionScalarDisplayer sets it (ClipMode.NONE). provisionScalarDisplayer sets the
    // function on every prepare, and changing it resets a manual range, so the range is set after it.
    private void fixColorbarRanges() {
        fixDisplayRange(PLANE_SCENE, PLANE_DISP, RANGE_PLANE_MIN, RANGE_PLANE_MAX);
    }

    private void fixDisplayRange(String sceneName, String dispName, double min, double max) {
        ScalarDisplayer sd = viewDisplayer(viewScene(sceneName), dispName);
        boolean wrote = false;

        // TODO(unverified signature): star.vis.AutoRangeMode.NONE, ScalarDisplayQuantity.getAutoRange()
        // and setAutoRange(AutoRangeMode). No listed source switches the automatic range off.
        Object auto = sd.getScalarDisplayQuantity().getAutoRange();
        if (!AutoRangeMode.NONE.equals(auto)) {
            sd.getScalarDisplayQuantity().setAutoRange(AutoRangeMode.NONE);
            modified(dispName, "Auto Range", String.valueOf(auto), "NONE");
            wrote = true;
        } else {
            found(dispName + " Auto Range already NONE");
        }

        // TODO(unverified signature): ScalarDisplayQuantity.setRange(DoubleVector). getRange() compiles
        // in manifest_writer.java, return type unknown there (read as Object).
        double[] target = { min, max };
        double[] r = displayRange(sd, dispName);
        if (!near(r, target)) {
            sd.getScalarDisplayQuantity().setRange(new DoubleVector(target));
            modified(dispName, "Range", vec(r), vec(target));
            wrote = true;
        } else {
            found(dispName + " Range already " + vec(target));
        }

        double[] back = displayRange(sd, dispName);
        log("prepare: range " + dispName + " [" + back[0] + ", " + back[1] + "] "
            + (wrote ? "written" : "already set"));
        if (!near(back, target)) {
            throw new RuntimeException("run_macro: range of '" + dispName + "' read back as " + vec(back)
                + ", declared " + vec(target) + ".");
        }
    }

    // TODO(unverified signature): ScalarDisplayQuantity.getRange() returning a DoubleVector of two.
    private static double[] displayRange(ScalarDisplayer sd, String dispName) {
        Object o = sd.getScalarDisplayQuantity().getRange();
        double[] a = null;
        if (o instanceof DoubleVector) {
            a = ((DoubleVector) o).toDoubleArray();
        } else if (o instanceof double[]) {
            a = (double[]) o;
        }
        if (a == null || a.length != 2) {
            throw new RuntimeException("run_macro: range of '" + dispName + "' read as "
                + (o == null ? "null" : o.getClass().getName()) + ", not two doubles.");
        }
        return a;
    }

    // ================================================================= RUN MODE
    private void doRun(Properties props) {
        int reTarget = requireIntProperty(props, "re_target");
        // Any Re the four-digit NNNN of the point name can hold; there is no list of points.
        if (reTarget < 1 || reTarget > 9999) {
            throw new RuntimeException("run_macro: property 're_target' must be an integer in 1..9999, got " + reTarget + ".");
        }
        log("re_target=" + reTarget);
        Declarations decl = readDeclarations(props);

        // ---- preconditions: every inherited and owned object must exist. No create calls below.
        checkInheritedObjects();
        checkOwnedObjects();
        // A template prepared before spec v16 (decision 78) still carries the 16 objects of the four
        // retired cut views; they do not touch the solution, but object_audit counts them as unlisted
        // and session D fails. They are removed here from the point's own copy, with the calls prepare
        // uses, so a point can be added on the published template without preparing it again (which
        // would remesh it and change its sha256). The template file is never opened in run mode.
        removeUnlistedLlmObjects();

        // ---- Re and mu
        ScalarGlobalParameter re = parameter("Re");
        double oldRe = re.getQuantity().getSIValue();
        re.getQuantity().setValue(reTarget);
        log("Re set: " + oldRe + " -> " + reTarget);

        ScalarGlobalParameter mu = parameter("mu");
        String muDef = mu.getQuantity().getDefinition();
        String muDefCompact = muDef == null ? "" : muDef.replace(" ", "");
        if (!MU_EXPRESSION.replace(" ", "").equals(muDefCompact)) {
            throw new RuntimeException("run_macro: parameter 'mu' does not hold the inherited expression "
                + MU_EXPRESSION + "; found '" + muDef + "'.");
        }
        precondition("mu holds " + MU_EXPRESSION);

        double dVal = parameter("D").getQuantity().getSIValue();
        double uVal = parameter("U").getQuantity().getSIValue();
        double rhoVal = parameter("rho").getQuantity().getSIValue();
        double muVal = mu.getQuantity().getSIValue();
        // mdot_in/mdot_out are published over rho*U*D^2 (6.3). Checked here, before the solve, so a bad
        // parameter fails now and not after the run.
        double massFlowScale = rhoVal * uVal * dVal * dVal;
        if (!(massFlowScale > 0.0) || Double.isInfinite(massFlowScale)) {
            throw new RuntimeException("run_macro: rho*U*D^2 = " + massFlowScale
                + " from parameters 'rho', 'U', 'D'; cannot make mdot dimensionless.");
        }
        // mesh.cells is written as an integer (spec v4 6). Read before the solve -- run mode does not
        // remesh, so it is the same count -- so a report that does not return a whole number fails
        // here and not after the run, when summary.json is built.
        long cells = cellCount();

        // ---- solve loop. Window-stationarity criterion (criterion_version 2, 23.09). The band tests
        // (band(last 5) < 1e-4 and band(last 5) < 2*band(previous 5)) and the limit_cycle status are
        // gone: both looked at a 50-iteration span, far shorter than the shedding period, so they
        // measured sampling noise. The macro now compares consecutive closed windows of
        // WINDOW_ITERATIONS iterations each, after discarding the first DISCARD_ITERATIONS.
        ConvergenceState st = new ConvergenceState();

        // Samples are taken every SAMPLING_INTERVAL iterations, so a closed window holds exactly
        // WINDOW_ITERATIONS / SAMPLING_INTERVAL = 200 of them. windowBuffer accumulates the current
        // window; pooled keeps every sample of every closed window, which is what C1 publishes.
        int windowSamples = WINDOW_ITERATIONS / SAMPLING_INTERVAL;
        List<List<Double>> windowBuffer = new ArrayList<List<Double>>();
        for (int q = 0; q < QUANTITIES.length; q++) {
            windowBuffer.add(new ArrayList<Double>());
            st.pooled.add(new ArrayList<Double>());
            st.windows.add(new ArrayList<WindowStats>());
            st.last.add(Double.valueOf(Double.NaN));
        }

        // Extra quantities (25.09): same window buffer / pooled-on-close pattern as above, for probes,
        // wake, cp_min/cp_stagnation and the plane extremes. extraNames fixes the sampling order, reused
        // for the CSV header below.
        List<String> extraNames = new ArrayList<String>();
        extraNames.addAll(Arrays.asList(COMPONENT_EXTRA_QUANTITIES));
        extraNames.addAll(Arrays.asList(MEAN_EXTRA_QUANTITIES));
        extraNames.addAll(Arrays.asList(MIN_EXTRA_QUANTITIES));
        extraNames.addAll(Arrays.asList(MAX_EXTRA_QUANTITIES));
        extraNames.addAll(Arrays.asList(planeExtremeReportNames("min")));
        extraNames.addAll(Arrays.asList(planeExtremeReportNames("max")));
        Map<String, List<Double>> extraWindowBuffer = new LinkedHashMap<String, List<Double>>();
        for (int i = 0; i < extraNames.size(); i++) {
            String name = extraNames.get(i);
            extraWindowBuffer.put(name, new ArrayList<Double>());
            st.extraPooled.put(name, new ArrayList<Double>());
        }
        // mass_imbalance pools like the extras (6.3) but is not in extraNames: its CSV column and its
        // read stay where the divergence guard has them.
        extraWindowBuffer.put("mass_imbalance", new ArrayList<Double>());
        st.extraPooled.put("mass_imbalance", new ArrayList<Double>());

        // The macro owns the stop decision in run mode, so the inherited asymptotic criteria are
        // switched off for this session rather than consulted.
        disableInheritedAsymptoticCriteria();

        // Contract v10: the macro stops by leaving its own step loop below. No Stop File criterion is
        // armed and no abort file is written; v12: one the template carries is taken out of use.
        // Maximum Steps is touched only if it would stop the solver before MAX_ITERATIONS.
        disableStopFileCriteria();
        ensureStepCeiling();

        // Captured now, before the solve: the path the .sim was loaded from, which the final save of
        // this mode overwrites (saveUnderOwnName).
        String simOwnPath = sim.getSessionPath();

        SimulationIterator it = sim.getSimulationIterator();
        long t0 = System.nanoTime();
        int iterations = it.getCurrentIteration();
        int nextWindowEnd = DISCARD_ITERATIONS + WINDOW_ITERATIONS;

        // Per-sample rows (25.09): run_log.txt used to take one line per sample and reached 456 KB on a
        // 30000-iteration run. They go to monitor_history_re<NNNN>.csv instead; run_log.txt keeps only
        // the per-window and per-run lines logged elsewhere in this method.
        StringBuilder monitorHistoryCsv = new StringBuilder();
        monitorHistoryCsv.append("iteration,cd,cl,cy,cp_base,mass_imbalance");
        for (int i = 0; i < extraNames.size(); i++) {
            monitorHistoryCsv.append(',').append(extraNames.get(i));
        }
        monitorHistoryCsv.append('\n');

        while (st.status == null) {
            it.step(SAMPLING_INTERVAL, true);
            iterations = it.getCurrentIteration();

            double[] x = new double[QUANTITIES.length];
            for (int q = 0; q < QUANTITIES.length; q++) {
                x[q] = value(QUANTITIES[q]);
                st.last.set(q, Double.valueOf(x[q]));
            }
            double mi = value("mass_imbalance");
            double[] extraX = new double[extraNames.size()];
            for (int i = 0; i < extraNames.size(); i++) {
                extraX[i] = value(extraNames.get(i));
            }
            monitorHistoryCsv.append(iterations).append(',').append(x[0]).append(',').append(x[1])
                .append(',').append(x[2]).append(',').append(x[3]).append(',').append(mi);
            for (int i = 0; i < extraX.length; i++) {
                monitorHistoryCsv.append(',').append(extraX[i]);
            }
            monitorHistoryCsv.append('\n');

            // Decision 1 -- divergence, checked at every sample, stops immediately.
            boolean nan = Double.isNaN(mi) || Double.isInfinite(mi);
            for (int q = 0; q < QUANTITIES.length && !nan; q++) {
                nan = Double.isNaN(x[q]) || Double.isInfinite(x[q]);
            }
            if (nan) {
                st.status = "diverged";
                st.stopReason = "NaN / mass imbalance " + num(mi) + " at iteration " + iterations + ".";
                break;
            }
            if (iterations >= MASS_IMBALANCE_GRACE_ITERATIONS && Math.abs(mi) > MASS_IMBALANCE_LIMIT) {
                st.status = "diverged";
                st.stopReason = "NaN / mass imbalance " + mi + " at iteration " + iterations + ".";
                break;
            }

            // Samples taken before DISCARD_ITERATIONS are written to the CSV above but never enter a window.
            if (iterations <= DISCARD_ITERATIONS) {
                continue;
            }
            for (int q = 0; q < QUANTITIES.length; q++) {
                windowBuffer.get(q).add(Double.valueOf(x[q]));
            }
            for (int i = 0; i < extraNames.size(); i++) {
                extraWindowBuffer.get(extraNames.get(i)).add(Double.valueOf(extraX[i]));
            }
            extraWindowBuffer.get("mass_imbalance").add(Double.valueOf(mi));
            if (iterations < nextWindowEnd) {
                continue;
            }

            // ---- close window k = st.windowsClosed + 1
            closeWindow(st, windowBuffer, windowSamples);
            for (Map.Entry<String, List<Double>> e : extraWindowBuffer.entrySet()) {
                List<Double> buf = e.getValue();
                st.extraPooled.get(e.getKey()).addAll(buf);
                buf.clear();
            }
            nextWindowEnd += WINDOW_ITERATIONS;
            int k = st.windowsClosed;
            int endK = DISCARD_ITERATIONS + k * WINDOW_ITERATIONS;

            // Decision 2 -- steady floor, from the first closed window on.
            boolean allBelowFloor = true;
            for (int q = 0; q < QUANTITIES.length; q++) {
                WindowStats wk = st.windows.get(q).get(k - 1);
                if (!(wk.sd <= CONVERGED_SD * Math.max(Math.abs(wk.mean), 1.0))) {
                    allBelowFloor = false;
                }
            }
            if (allBelowFloor) {
                st.status = "converged";
                st.stopReason = "All four coefficients below the steady floor at iteration " + endK + ".";
                break;
            }

            // Decision 3 -- stationarity between the two last closed windows, from k = 2 on.
            if (k >= 2) {
                boolean allPass = true;
                double maxDriftOverSe = 0.0;
                for (int q = 0; q < QUANTITIES.length; q++) {
                    gateQuantity(st, q, k);
                    if (!st.pass[q]) {
                        allPass = false;
                    }
                    if (!Double.isNaN(st.driftOverSe[q]) && st.driftOverSe[q] > maxDriftOverSe) {
                        maxDriftOverSe = st.driftOverSe[q];
                    }
                }
                if (allPass) {
                    st.status = "stationary";
                    st.stopReason = "Window statistics of cd, cl, cy, cp_base stationary between iterations "
                        + (endK - 2 * WINDOW_ITERATIONS) + "-" + (endK - WINDOW_ITERATIONS)
                        + " and " + (endK - WINDOW_ITERATIONS) + "-" + endK
                        + " (max drift/SE = " + maxDriftOverSe + ").";
                    break;
                }
            }

            // Decision 4 -- iteration ceiling.
            if (k >= MAX_WINDOWS) {
                int failed = 0;
                for (int q = 0; q < QUANTITIES.length; q++) {
                    if (!st.pass[q]) {
                        failed = q;
                        break;
                    }
                }
                st.status = "no_steady_state";
                st.stopReason = noSteadyStateReason(st, failed, k, endK);
                break;
            }
        }
        st.iterations = iterations;
        publish(st);

        double wallclockSolveS = (System.nanoTime() - t0) / 1.0e9;
        log("wallclock_solve_s=" + wallclockSolveS);
        log("status=" + st.status);
        log("stop_reason=" + st.stopReason);

        // ---- outputs
        String nnnn = four(reTarget);
        File outDir = sessionRelativeDir(WORK_DIR + File.separator + "cube_re" + nnnn);
        String json = buildSummaryJson(reTarget, decl, dVal, uVal, rhoVal, muVal, cells, st);
        writeTextFile(new File(outDir, "summary.json"), json);
        log("summary.json written");
        writeTextFile(new File(outDir, "monitor_history_re" + nnnn + ".csv"), monitorHistoryCsv.toString());
        log("monitor_history_re" + nnnn + ".csv written");

        // No view title is written here since v16: the titles with numbers are written by
        // output_exporter in session B, from summary.json (spec 3.1 and 5.1).

        // ---- final save. The loop above advances the solver only with step() and has ended, so the
        // solver is at rest, not mid-iteration. The .sim is saved over simOwnPath (captured before the
        // solve), keeping the chain's name cube_re<NNNN>.sim, with the call doPrepare uses to save in
        // place. Guarded, since a save in this position has thrown before and must be reported, not
        // assumed.
        saveUnderOwnName(simOwnPath);

        writeRunLog(runRunLogBody(reTarget, st, wallclockSolveS), outDir);
    }

    // Saves the simulation over the given path, the file it was loaded from before the solve, so it
    // keeps its own name. That path already exists on disk (prepare wrote it, or an earlier point's
    // run-mode save did), so this overwrites it in place, exactly as doPrepare's identical call does.
    private void saveUnderOwnName(String path) {
        try {
            sim.saveState(path);
            log("simulation saved under its own name");
        } catch (Exception e) {
            unresolved("save under own name failed after the run-mode stop (" + e.getMessage()
                + "); " + path + " does not hold the final state of this run");
            log("WARNING: simulation NOT saved under its own name: " + e.getMessage());
        }
    }

    // Per-quantity statistics of one closed window.
    private static final class WindowStats {
        final double mean;
        final double sd;
        final int nCycles;

        WindowStats(double mean, double sd, int nCycles) {
            this.mean = mean;
            this.sd = sd;
            this.nCycles = nCycles;
        }
    }

    // Everything the stopping criterion decides and summary.json then reports. Indices follow
    // QUANTITIES: 0 cd, 1 cl, 2 cy, 3 cp_base.
    private static final class ConvergenceState {
        String status = null;
        String stopReason = null;
        int iterations = 0;
        int windowsClosed = 0;
        final List<List<WindowStats>> windows = new ArrayList<List<WindowStats>>();
        final List<List<Double>> pooled = new ArrayList<List<Double>>();
        final List<Double> last = new ArrayList<Double>();
        final double[] driftLast = new double[4];
        final double[] driftOverSe = new double[4];
        final double[] sdLogRatioOverTol = new double[4];
        final boolean[] pass = new boolean[4];
        final double[] published = new double[4];
        final double[] publishedSd = new double[4];
        String publishedStatistic = "instantaneous";
        int publishedWindowIterations = 1;
        int publishedNWindows = 0;
        int samplesPerWindow = 0;
        // Extra quantities (25.09), keyed by report name: every sample from every CLOSED window after
        // the discard, same as 'pooled' above but for reports with no stationarity gate of their own.
        // publishedStatistic/publishedWindowIterations/publishedNWindows, already decided for the gated
        // four, apply to these unchanged -- the pooled/instantaneous choice is the same choice.
        final Map<String, List<Double>> extraPooled = new LinkedHashMap<String, List<Double>>();
    }

    // The macro alone decides when to stop in run mode (spec change B), so the inherited asymptotic
    // criteria are taken out of use for this session. Absent criteria are not an error here: run mode
    // creates nothing and these two are not owned objects.
    private void disableInheritedAsymptoticCriteria() {
        String[] names = { "cd_asymptotic", "cl_asymptotic" };
        for (int i = 0; i < names.length; i++) {
            SolverStoppingCriterion c = sim.getSolverStoppingCriterionManager().hasSolverStoppingCriterion(names[i]);
            if (c == null) {
                log("inherited stopping criterion " + names[i] + " not present; nothing to disable");
                continue;
            }
            boolean was = c.getIsUsed();
            if (was) {
                c.setIsUsed(false);
            }
            modified(names[i], "Enabled", String.valueOf(was), "false");
            log("inherited stopping criterion " + names[i] + " disabled (was in use: " + was
                + "); the window-stationarity criterion of this macro is the only stop decision in run mode");
        }
    }

    // v12: the Stop File criterion is taken out of use for this session, read before write, with the
    // calls of the removed stop path (git history, run_macro.java before b608eff): looked up by class
    // through getObjects(), not by name, so one the template carries under any name is found. Absent
    // is not an error: run mode creates nothing. One line per criterion goes to run_log.txt.
    private void disableStopFileCriteria() {
        Collection<SolverStoppingCriterion> all = sim.getSolverStoppingCriterionManager().getObjects();
        int seen = 0;
        for (SolverStoppingCriterion c : all) {
            if (!(c instanceof AbortFileStoppingCriterion)) {
                continue;
            }
            seen++;
            String name = c.getPresentationName();
            boolean was = c.getIsUsed();
            if (was) {
                c.setIsUsed(false);
            }
            modified(name, "Enabled", String.valueOf(was), "false");
            log("stopping criterion " + name + " (Stop File) disabled (was in use: " + was
                + "); the window-stationarity criterion of this macro is the only stop decision in run mode");
        }
        if (seen == 0) {
            log("stopping criterion Stop File not present; nothing to disable");
        }
    }

    // ---- step ceiling (contract v10) ---------------------------------------------------------
    // The macro leaves its own step loop; the inherited "Maximum Steps" must not stop the solver
    // first. It is raised for this session only if its value is below MAX_ITERATIONS, and the change
    // goes to run_log.txt; otherwise it is left alone.
    private void ensureStepCeiling() {
        Object maxSteps = sim.getSolverStoppingCriterionManager().hasObject("Maximum Steps");
        if (!(maxSteps instanceof StepStoppingCriterion)) {
            throw new RuntimeException("run_macro: stopping criterion 'Maximum Steps' is "
                + (maxSteps == null ? "missing" : "a " + maxSteps.getClass().getSimpleName())
                + ", not a StepStoppingCriterion; the step ceiling cannot be checked.");
        }
        StepStoppingCriterion steps = (StepStoppingCriterion) maxSteps;
        int was = steps.getMaximumNumberSteps();
        if (was < MAX_ITERATIONS) {
            steps.setMaximumNumberSteps(MAX_ITERATIONS);
            modified("Maximum Steps", "Maximum Steps", String.valueOf(was), String.valueOf(MAX_ITERATIONS));
            log("step ceiling raised for this session from " + was + " to " + MAX_ITERATIONS
                + ": Maximum Steps would have stopped the solver before MAX_ITERATIONS");
        } else {
            log("step ceiling Maximum Steps=" + was + " left alone (not below " + MAX_ITERATIONS + ")");
        }
    }

    // Second stop_reason template (contract v10), written when the ceiling is reached. The quantity
    // named is the first one that failed the gate between windows A-B and B-C, the last two closed;
    // n is the smaller of their two cycle counts, as in gateQuantity. The clause names the first test
    // that failed, in gateQuantity's order: too few cycles, then drift/SE, then the sd log ratio.
    private String noSteadyStateReason(ConvergenceState st, int q, int k, int endK) {
        WindowStats a = st.windows.get(q).get(k - 2);
        WindowStats b = st.windows.get(q).get(k - 1);
        int n = Math.min(a.nCycles, b.nCycles);
        String clause;
        if (n < MIN_CYCLES) {
            clause = "n = " + n + " cycles < " + MIN_CYCLES;
        } else if (!(st.driftOverSe[q] <= K_GATE)) {
            clause = "drift/SE = " + st.driftOverSe[q] + " > " + K_GATE;
        } else {
            double logRatio = (a.sd > 0.0 && b.sd > 0.0) ? Math.abs(Math.log(b.sd / a.sd)) : Double.POSITIVE_INFINITY;
            clause = "sd log ratio = " + logRatio + " over tolerance";
        }
        return "Reached " + MAX_ITERATIONS + " iterations; " + QUANTITIES[q] + " failed the stationarity gate in windows "
            + (endK - 2 * WINDOW_ITERATIONS) + "-" + (endK - WINDOW_ITERATIONS) + " and "
            + (endK - WINDOW_ITERATIONS) + "-" + endK + " (" + clause + ").";
    }

    // Closes the current window: checks its sample count, computes mean, sd, crossings and n_cycles per
    // quantity, appends the window's samples to the pooled set and clears the buffer.
    private void closeWindow(ConvergenceState st, List<List<Double>> buffer, int windowSamples) {
        for (int q = 0; q < QUANTITIES.length; q++) {
            List<Double> w = buffer.get(q);
            if (w.size() != windowSamples) {
                throw new RuntimeException("run_macro: closed window " + (st.windowsClosed + 1) + " of "
                    + QUANTITIES[q] + " holds " + w.size() + " samples, expected exactly " + windowSamples + ".");
            }
            double m = meanOf(w);
            double s = sdOf(w, m);
            int crossings = crossings(w, m, HYSTERESIS_SD * s);
            int nCycles = crossings / 2;
            st.windows.get(q).add(new WindowStats(m, s, nCycles));
            st.pooled.get(q).addAll(w);
            w.clear();
        }
        st.windowsClosed++;
        st.samplesPerWindow = windowSamples;
        StringBuilder sb = new StringBuilder();
        sb.append("window ").append(st.windowsClosed).append(" closed at iteration ")
          .append(DISCARD_ITERATIONS + st.windowsClosed * WINDOW_ITERATIONS)
          .append(" with ").append(windowSamples).append(" samples per quantity:");
        for (int q = 0; q < QUANTITIES.length; q++) {
            WindowStats ws = st.windows.get(q).get(st.windowsClosed - 1);
            sb.append(' ').append(QUANTITIES[q]).append("(m=").append(ws.mean).append(" s=").append(ws.sd)
              .append(" n_cycles=").append(ws.nCycles).append(')');
        }
        log(sb.toString());
    }

    // Stationarity gate of decision 3 for one quantity, between windows k-1 and k. n is the smaller of
    // the two cycle counts: below MIN_CYCLES the signal is too slow for the standard error between the
    // two windows to mean anything, and the quantity fails regardless of the numbers.
    private void gateQuantity(ConvergenceState st, int q, int k) {
        WindowStats a = st.windows.get(q).get(k - 2);
        WindowStats b = st.windows.get(q).get(k - 1);
        int n = Math.min(a.nCycles, b.nCycles);
        double drift = b.mean - a.mean;
        st.driftLast[q] = drift;
        if (n < MIN_CYCLES) {
            st.driftOverSe[q] = Double.NaN;
            st.sdLogRatioOverTol[q] = Double.NaN;
            st.pass[q] = false;
            return;
        }
        double se = Math.sqrt((a.sd * a.sd + b.sd * b.sd) / n);
        double driftOverSe = se > 0.0 ? Math.abs(drift) / se : (drift == 0.0 ? 0.0 : Double.POSITIVE_INFINITY);
        double tol = K_SD / Math.sqrt(n);
        double logRatio = (a.sd > 0.0 && b.sd > 0.0) ? Math.abs(Math.log(b.sd / a.sd)) : Double.POSITIVE_INFINITY;
        st.driftOverSe[q] = driftOverSe;
        st.sdLogRatioOverTol[q] = logRatio / tol;
        st.pass[q] = driftOverSe <= K_GATE && logRatio <= tol;
    }

    // C1 -- what summary.json publishes. 'converged' publishes the last sample; 'stationary' and
    // 'no_steady_state' publish the pooled mean over every closed window after the discard, never the
    // mean of the last window alone and never an instantaneous value at the cut: measured on 23.09, the
    // last-window mean would have published cd 1.1341 against 1.1263 pooled (+0.7 %) in one run and
    // 1.1255 against 1.1307 (-0.5 %) in the other. 'diverged' has no usable window, so it falls back to
    // the last sample and says so.
    private void publish(ConvergenceState st) {
        boolean pooledPath = ("stationary".equals(st.status) || "no_steady_state".equals(st.status))
            && st.windowsClosed > 0;
        if (pooledPath) {
            for (int q = 0; q < QUANTITIES.length; q++) {
                List<Double> p = st.pooled.get(q);
                double m = meanOf(p);
                st.published[q] = m;
                st.publishedSd[q] = sdOf(p, m);
            }
            st.publishedStatistic = "iteration_mean";
            st.publishedWindowIterations = st.windowsClosed * WINDOW_ITERATIONS;
            st.publishedNWindows = st.windowsClosed;
        } else {
            for (int q = 0; q < QUANTITIES.length; q++) {
                st.published[q] = st.last.get(q).doubleValue();
                st.publishedSd[q] = 0.0;
            }
            st.publishedStatistic = "instantaneous";
            st.publishedWindowIterations = 1;
            st.publishedNWindows = "converged".equals(st.status) ? 1 : 0;
        }
        log("published statistic=" + st.publishedStatistic
            + " window_iterations=" + st.publishedWindowIterations
            + " n_windows=" + st.publishedNWindows);
    }

    private static double meanOf(List<Double> xs) {
        if (xs.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (int i = 0; i < xs.size(); i++) {
            sum += xs.get(i).doubleValue();
        }
        return sum / xs.size();
    }

    // Sample standard deviation over the whole list: variance with n-1, then the square root. Computed
    // from the array on purpose (spec change B) and not read from the solver's Variance statistic,
    // whose divisor was never bench-checked in this series.
    private static double sdOf(List<Double> xs, double m) {
        int n = xs.size();
        if (n < 2) {
            return Double.NaN;
        }
        double sq = 0.0;
        for (int i = 0; i < n; i++) {
            double d = xs.get(i).doubleValue() - m;
            sq += d * d;
        }
        return Math.sqrt(sq / (n - 1));
    }

    // Pooled min/max over a closed-window sample list (25.09): cp_min/cp_stagnation and the plane
    // extremes publish these instead of a mean.
    private static double minOf(List<Double> xs) {
        if (xs.isEmpty()) {
            return Double.NaN;
        }
        double m = xs.get(0).doubleValue();
        for (int i = 1; i < xs.size(); i++) {
            double v = xs.get(i).doubleValue();
            if (v < m) {
                m = v;
            }
        }
        return m;
    }

    private static double maxOf(List<Double> xs) {
        if (xs.isEmpty()) {
            return Double.NaN;
        }
        double m = xs.get(0).doubleValue();
        for (int i = 1; i < xs.size(); i++) {
            double v = xs.get(i).doubleValue();
            if (v > m) {
                m = v;
            }
        }
        return m;
    }

    // Crossings of the mean counted through a Schmitt trigger of half-width band = HYSTERESIS_SD * sd:
    // the state only flips once the signal has gone past the far side of the band, so noise around the
    // mean does not inflate the count. n_cycles is crossings / 2.
    private static int crossings(List<Double> xs, double m, double band) {
        double hi = m + band;
        double lo = m - band;
        int state = 0;
        int count = 0;
        for (int i = 0; i < xs.size(); i++) {
            double v = xs.get(i).doubleValue();
            if (v > hi) {
                if (state == -1) {
                    count++;
                }
                state = 1;
            } else if (v < lo) {
                if (state == 1) {
                    count++;
                }
                state = -1;
            }
        }
        return count;
    }

    // Declarations of the point, written by sweep_driver.sh from sweep.json into LLM_point.properties.
    // None is measured by the macro and all are optional: a point without them is normal.
    //   designed_for_re_range  "<min>,<max>", the declared range of the single mesh (else null)
    //   regime_expected        the declared regime of this point; regime_ref goes with it
    //   uncertainty_keys       ordered key list of the declared uncertainty block, each key's value in
    //                          uncertainty.<key> as a JSON literal written by the driver
    private static final class Declarations {
        int[] designedRange;
        String regime;
        String regimeRef;
        List<String> uncertaintyKeys = new ArrayList<String>();
        List<String> uncertaintyValues = new ArrayList<String>();
    }

    private Declarations readDeclarations(Properties p) {
        Declarations d = new Declarations();
        if (p.getProperty("designed_for_re_range") != null) {
            String v = requireProperty(p, "designed_for_re_range");
            String[] parts = v.split(",", -1);
            if (parts.length != 2) {
                throw new RuntimeException("run_macro: property 'designed_for_re_range' must be '<min>,<max>', got '" + v + "'.");
            }
            d.designedRange = new int[2];
            for (int i = 0; i < 2; i++) {
                try {
                    d.designedRange[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException e) {
                    throw new RuntimeException("run_macro: property 'designed_for_re_range' is not two integers: '" + v + "'.");
                }
            }
        }
        if (p.getProperty("regime_expected") != null) {
            d.regime = requireProperty(p, "regime_expected");
            d.regimeRef = requireProperty(p, "regime_ref");
        } else if (p.getProperty("regime_ref") != null) {
            throw new RuntimeException("run_macro: property 'regime_ref' without 'regime_expected'.");
        }
        if (p.getProperty("uncertainty_keys") != null) {
            String[] keys = requireProperty(p, "uncertainty_keys").split(",", -1);
            for (int i = 0; i < keys.length; i++) {
                String k = keys[i].trim();
                if (k.length() == 0) {
                    throw new RuntimeException("run_macro: property 'uncertainty_keys' has an empty key.");
                }
                d.uncertaintyKeys.add(k);
                d.uncertaintyValues.add(requireProperty(p, "uncertainty." + k));
            }
        }
        log("declared designed_for_re_range=" + (d.designedRange == null ? "none" : d.designedRange[0] + "," + d.designedRange[1])
            + " regime_expected=" + (d.regime == null ? "none" : d.regime)
            + " uncertainty=" + (d.uncertaintyKeys.isEmpty() ? "none" : d.uncertaintyKeys.toString()));
        return d;
    }

    private static String four(int n) {
        String s = Integer.toString(n);
        while (s.length() < 4) {
            s = "0" + s;
        }
        return s;
    }

    // ================================================================= preconditions
    private void checkInheritedObjects() {
        for (int i = 0; i < INHERITED_PARAMETERS.length; i++) {
            parameter(INHERITED_PARAMETERS[i]);
            precondition("parameter " + INHERITED_PARAMETERS[i]);
        }
        // TODO[I-06]: ContinuumManager is not among the nine managers spec 3.1 confirms as
        // ClientServerObjectManager. try/catch covers a thrown exception or a null return alike.
        Object continuum;
        try {
            continuum = sim.getContinuumManager().getObject("fluid_physics"); // TODO[I-06]
        } catch (RuntimeException e) {
            continuum = null;
        }
        if (continuum == null) {
            sim.println("run_macro: missing physics continuum: fluid_physics");
            throw new RuntimeException("run_macro: missing physics continuum 'fluid_physics'.");
        }
        precondition("physics continuum fluid_physics");

        Region fluid = region("fluid");
        precondition("region fluid");
        for (int i = 0; i < WALL_BOUNDARIES.length; i++) {
            boundary(fluid, WALL_BOUNDARIES[i]);
            precondition("wall boundary " + WALL_BOUNDARIES[i]);
        }
        for (int i = 0; i < NONWALL_BOUNDARIES.length; i++) {
            boundary(fluid, NONWALL_BOUNDARIES[i]);
            precondition("non-wall boundary " + NONWALL_BOUNDARIES[i]);
        }

        AutoMeshOperation mesh = meshCube();
        precondition("mesh operation mesh_cube");
        String[] inheritedControls = { "cube_refinement", "wake_refinement" };
        for (int i = 0; i < inheritedControls.length; i++) {
            if (mesh.getCustomMeshControls().hasObject(inheritedControls[i]) == null) {
                sim.println("run_macro: missing custom mesh control: " + inheritedControls[i]);
                throw new RuntimeException("run_macro: missing custom mesh control '" + inheritedControls[i] + "'.");
            }
            precondition("custom mesh control " + inheritedControls[i]);
        }

        requireRenamedFieldFunctions();
        for (int i = 0; i < INHERITED_FIELD_FUNCTIONS.length; i++) {
            fieldFunction(INHERITED_FIELD_FUNCTIONS[i]);
            precondition("field function " + INHERITED_FIELD_FUNCTIONS[i]);
        }
        for (int i = 0; i < INHERITED_PARTS.length; i++) {
            derivedPart(INHERITED_PARTS[i]);
            precondition("derived part " + INHERITED_PARTS[i]);
        }
        for (int i = 0; i < INHERITED_REPORTS.length; i++) {
            report(INHERITED_REPORTS[i]);
            precondition("report " + INHERITED_REPORTS[i]);
        }
        for (int i = 0; i < INHERITED_MONITORS.length; i++) {
            // Fixed 19.09 (I-07, bench-measured tramo 0, spec 3.1): MonitorManager.getObject(String)
            // throws on a missing name; the old "== null" check here never fired. Routed through the
            // monitor() lookup, which uses hasObject(String).
            monitor(INHERITED_MONITORS[i]);
            precondition("monitor " + INHERITED_MONITORS[i]);
        }
        for (int i = 0; i < INHERITED_CRITERIA.length; i++) {
            // TODO[I-08]: SolverStoppingCriterionManager.hasObject(String) is not itself bench-read;
            // this rests on object_audit.java's confirmed ClientServerObjectManager grouping (spec 3.1,
            // Q76, "stopping criteria") -- same confidence tier as I-01/I-04, one notch below I-07's
            // direct measurement. Fixed 19.09: getObject(String) throws, so the old "== null" check
            // here never fired; hasObject(String) is used instead.
            if (sim.getSolverStoppingCriterionManager().hasObject(INHERITED_CRITERIA[i]) == null) { // TODO[I-08]
                sim.println("run_macro: missing stopping criterion: " + INHERITED_CRITERIA[i]);
                throw new RuntimeException("run_macro: missing stopping criterion '" + INHERITED_CRITERIA[i] + "'.");
            }
            precondition("stopping criterion " + INHERITED_CRITERIA[i]);
        }
    }

    private void checkOwnedObjects() {
        int firstCheck = preconditionsChecked.size();
        AutoMeshOperation mesh = meshCube();
        if (mesh.getCustomMeshControls().hasObject("LLM_prism_off_nonwall") == null) {
            sim.println("run_macro: missing owned object: LLM_prism_off_nonwall");
            throw new RuntimeException("run_macro: missing owned object 'LLM_prism_off_nonwall'.");
        }
        precondition("owned LLM_prism_off_nonwall");

        if (sim.getPartManager().hasObject("LLM_probe_axis_x2D") == null) {
            sim.println("run_macro: missing owned object: LLM_probe_axis_x2D");
            throw new RuntimeException("run_macro: missing owned object 'LLM_probe_axis_x2D'.");
        }
        precondition("owned LLM_probe_axis_x2D");

        List<String> ownedReports = new ArrayList<String>();
        ownedReports.add("LLM_u_over_U_axis_x2D");
        ownedReports.add("LLM_cell_count");
        for (int f = 0; f < FIELDS.length; f++) {
            for (int e = 0; e < EXTREMA.length; e++) {
                for (int p = 0; p < PLANES.length; p++) {
                    ownedReports.add("LLM_" + FIELDS[f] + "_" + EXTREMA[e] + "_" + PLANES[p]);
                }
            }
        }
        for (int i = 0; i < ownedReports.size(); i++) {
            report(ownedReports.get(i));
            precondition("owned report " + ownedReports.get(i));
        }

        for (int p = 0; p < PLANES.length; p++) {
            String t = "LLM_table_" + PLANES[p];
            if (sim.getTableManager().hasTable(t) == null) {
                sim.println("run_macro: missing owned object: " + t);
                throw new RuntimeException("run_macro: missing owned object '" + t + "'.");
            }
            precondition("owned table " + t);
        }

        // The plane scene, its two displayers and its title (spec v16: the only owned scene).
        Scene plane = sim.getSceneManager().hasScene(PLANE_SCENE);
        if (plane == null) {
            sim.println("run_macro: missing owned object: " + PLANE_SCENE);
            throw new RuntimeException("run_macro: missing owned object '" + PLANE_SCENE + "'.");
        }
        precondition("owned scene " + PLANE_SCENE);
        String[] planeDisplayers = { PLANE_DISP, PLANE_BODY };
        for (int i = 0; i < planeDisplayers.length; i++) {
            if (!plane.getDisplayerManager().hasDisplayer(planeDisplayers[i])) {
                sim.println("run_macro: missing owned object: " + planeDisplayers[i]);
                throw new RuntimeException("run_macro: missing owned object '" + planeDisplayers[i] + "'.");
            }
            precondition("owned displayer " + planeDisplayers[i]);
        }
        if (sim.getAnnotationManager().hasObject(PLANE_TITLE) == null) {
            sim.println("run_macro: missing owned object: " + PLANE_TITLE);
            throw new RuntimeException("run_macro: missing owned object '" + PLANE_TITLE + "'.");
        }
        precondition("owned annotation " + PLANE_TITLE);
        int owned = 0;
        for (int i = firstCheck; i < preconditionsChecked.size(); i++) {
            if (preconditionsChecked.get(i).startsWith("owned ")) {
                owned++;
            }
        }
        if (owned != OWNED_OBJECTS_EXPECTED) {
            throw new RuntimeException("run_macro: closed list checked " + owned + " owned objects, expected "
                + OWNED_OBJECTS_EXPECTED + ".");
        }
        precondition("closed list of " + OWNED_OBJECTS_EXPECTED + " owned objects");
    }

    // ================================================================= summary.json
    private String buildSummaryJson(int reTarget, Declarations decl, double d, double u, double rho, double mu,
            long cells, ConvergenceState st) {
        // What the four coefficients publish is decided in publish() (C1): the last sample with
        // "converged", the pooled mean over every closed window after the discard with "stationary" and
        // "no_steady_state". This method only writes it out. cd_samples/cl_samples were dropped from
        // the convergence block in the 19.09 correction and stay out.
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // frame
        sb.append("  \"frame\": {\n");
        sb.append("    \"x\": \"streamwise, from inlet to outlet\",\n");
        sb.append("    \"y\": \"normal to lateral_yminus/lateral_yplus\",\n");
        sb.append("    \"z\": \"normal to lateral_zminus/lateral_zplus\",\n");
        sb.append("    \"handedness\": \"right\"\n");
        sb.append("  },\n");

        // reference
        sb.append("  \"reference\": {\n");
        sb.append("    \"side_m\": ").append(num(d)).append(",\n");
        sb.append("    \"velocity_m_per_s\":").append(num(u)).append(",\n");
        sb.append("    \"density_kg_per_m3\":").append(num(rho)).append(",\n");
        sb.append("    \"dynamic_viscosity_pa_s\": ").append(num(mu)).append(",\n");
        sb.append("    \"note\": \"Only dimensional block in this file. ")
          .append("All other scalars are nondimensional groups.\"\n");
        sb.append("  },\n");

        // environment_placeholder
        sb.append("  \"environment_placeholder\": {\n");
        sb.append("    \"written_by\": \"output_exporter\"\n");
        sb.append("  },\n");

        // sweep
        sb.append("  \"sweep\": {\n");
        sb.append("    \"parameter\": \"Re\",\n");
        sb.append("    \"value\": ").append(reTarget).append("\n");
        sb.append("  },\n");

        // mesh
        sb.append("  \"mesh\": {\n");
        sb.append("    \"cells\": ").append(cells).append(",\n");
        sb.append("    \"identical_across_sweep\": true,\n");
        if (decl.designedRange == null) {
            sb.append("    \"designed_for_Re_range\": null,\n");
        } else {
            sb.append("    \"designed_for_Re_range\": [").append(decl.designedRange[0]).append(", ")
              .append(decl.designedRange[1]).append("],\n");
        }
        sb.append("    \"prism_layers_nonwall\": ").append(PRISM_LAYERS_NONWALL).append(",\n");
        sb.append("    \"note\": \"declared range; no y+ or mesh-independence study\"\n");
        sb.append("  },\n");

        // convergence (C2, criterion_version 2). The band criteria and their band_tolerance /
        // cd_band_last_500 keys are gone with the criterion they belonged to.
        String regime = decl.regime;
        // null when no regime is declared: there is nothing to mismatch.
        String mismatch = regime == null ? "null"
            : String.valueOf("converged".equals(st.status) && !"steady".equals(regime));
        sb.append("  \"convergence\": {\n");
        sb.append("    \"solver\": \"steady segregated\",\n");
        sb.append("    \"criterion\": \"window_stationarity\",\n");
        sb.append("    \"criterion_version\": 2,\n");
        sb.append("    \"time_basis\": \"solver iterations (pseudo-time); iteration means are not ")
          .append("physical time averages\",\n");
        if (regime == null) {
            sb.append("    \"regime_expected\": {\"value\": null, \"source\": \"not declared\"},\n");
        } else {
            sb.append("    \"regime_expected\": {\"value\": ").append(str(regime))
              .append(", \"source\": \"declared\", \"ref\": ").append(str(decl.regimeRef)).append("},\n");
        }
        sb.append("    \"status\": ").append(str(st.status)).append(",\n");
        sb.append("    \"stop_reason\": ").append(str(st.stopReason)).append(",\n");
        sb.append("    \"solver_regime_mismatch\": ").append(mismatch).append(",\n");
        sb.append("    \"iterations\": ").append(st.iterations).append(",\n");
        sb.append("    \"discard_iterations\": ").append(DISCARD_ITERATIONS).append(",\n");
        sb.append("    \"window_iterations\": ").append(WINDOW_ITERATIONS).append(",\n");
        sb.append("    \"windows_closed\": ").append(st.windowsClosed).append(",\n");
        sb.append("    \"sampling_interval\": ").append(SAMPLING_INTERVAL).append(",\n");
        sb.append("    \"max_iterations\": ").append(MAX_ITERATIONS).append(",\n");
        sb.append("    \"k_gate\": ").append(num(K_GATE)).append(",\n");
        sb.append("    \"k_sd\": ").append(num(K_SD)).append(",\n");
        sb.append("    \"min_cycles\": ").append(MIN_CYCLES).append(",\n");
        // Declared in sweep.json, not computed here; written only where declared, else the key is absent.
        if (!decl.uncertaintyKeys.isEmpty()) {
            sb.append("    \"uncertainty\": {\n");
            for (int i = 0; i < decl.uncertaintyKeys.size(); i++) {
                sb.append("      ").append(str(decl.uncertaintyKeys.get(i))).append(": ")
                  .append(decl.uncertaintyValues.get(i))
                  .append(i + 1 < decl.uncertaintyKeys.size() ? ",\n" : "\n");
            }
            sb.append("    },\n");
        }
        sb.append("    \"quantities\": {\n");
        for (int q = 0; q < QUANTITIES.length; q++) {
            List<WindowStats> ws = st.windows.get(q);
            WindowStats lastW = ws.isEmpty() ? null : ws.get(ws.size() - 1);
            sb.append("      \"").append(QUANTITIES[q]).append("\": {\"window_means\": [");
            for (int w = 0; w < ws.size(); w++) {
                sb.append(w == 0 ? "" : ", ").append(num(ws.get(w).mean));
            }
            sb.append("], \"window_sds\": [");
            for (int w = 0; w < ws.size(); w++) {
                sb.append(w == 0 ? "" : ", ").append(num(ws.get(w).sd));
            }
            sb.append("],\n");
            sb.append("             \"n_cycles_last\": ").append(lastW == null ? 0 : lastW.nCycles).append(",\n");
            sb.append("             \"drift_last\": ").append(num(st.driftLast[q]))
              .append(", \"drift_over_se\": ").append(num(st.driftOverSe[q]))
              .append(", \"sd_log_ratio_over_tol\": ").append(num(st.sdLogRatioOverTol[q]))
              .append(", \"pass\": ").append(st.pass[q]).append("}");
            sb.append(q == QUANTITIES.length - 1 ? "\n" : ",\n");
        }
        sb.append("    }\n");
        sb.append("  },\n");

        // forces (C1). cd, cl and cy carry the published value with its *_sd, *_window_iterations,
        // *_statistic and *_n_windows. cd_pressure and cd_friction (6.3) carry the same quintet from
        // their own samples, with the same pooled/instantaneous choice.
        sb.append("  \"forces\": {\n");
        appendPublished(sb, st, 0, false);
        appendExtraMeanPublished(sb, st, "cd_pressure", "cd_pressure", false);
        appendExtraMeanPublished(sb, st, "cd_friction", "cd_friction", false);
        appendPublished(sb, st, 1, false);
        appendPublished(sb, st, 2, true);
        sb.append("  },\n");

        // pressure. cp_base carries the same quintet as forces. cp_min and cp_stagnation (25.09) now
        // carry the pooled window_min / window_max over the same closed-window samples, falling back to
        // instantaneous exactly when the gated four do (publish()'s condition, reused via
        // st.publishedStatistic): status converged (or diverged) stays instantaneous.
        sb.append("  \"pressure\": {\n");
        appendPublished(sb, st, 3, false);
        appendExtraExtremumPublished(sb, st, "cp_min", "cp_min", "min", false);
        appendExtraExtremumPublished(sb, st, "cp_stagnation", "cp_stagnation", "max", true);
        sb.append("  },\n");

        // wake (25.09): pooled iteration_mean over the closed windows, same fallback rule as above.
        // Mean of instantaneous lengths, not a time-averaged-field recirculation length -- see the note.
        sb.append("  \"wake\": {\n");
        appendExtraMeanPublished(sb, st, "x_reattach", "x_reattach_D", false);
        appendExtraMeanPublished(sb, st, "recirculation_length_D", "recirculation_length_D", false);
        sb.append("    \"note\": \"mean of instantaneous lengths; not the recirculation length of the ")
          .append("time-averaged field, which is out of scope for this part\"\n");
        sb.append("  },\n");

        // mass (6.3): family-A quintet for all three. The mdot reports are in kg/s and are divided by
        // rho*U*D^2 with the reference values written above, after pooling (value and sd alike); no
        // dimensional mdot key remains.
        double massFlowScale = rho * u * d * d;
        sb.append("  \"mass\": {\n");
        appendExtraMeanPublished(sb, st, "mdot_in", "mdot_in_over_rhoUD2", massFlowScale, false);
        appendExtraMeanPublished(sb, st, "mdot_out", "mdot_out_over_rhoUD2", massFlowScale, false);
        appendExtraMeanPublished(sb, st, "mass_imbalance", "mass_imbalance", true);
        sb.append("  },\n");

        // probes (25.09): pooled iteration_mean over the closed windows, same fallback rule as above.
        sb.append("  \"probes\": {\n");
        sb.append("    \"LLM_u_over_U_axis_x2D\": {\n");
        sb.append("      \"value\": ")
          .append(num(extraPublishedValue(st, "LLM_u_over_U_axis_x2D", "mean"))).append(",\n");
        sb.append("      \"sd\": ").append(num(extraPublishedSd(st, "LLM_u_over_U_axis_x2D"))).append(",\n");
        sb.append("      \"window_iterations\": ").append(st.publishedWindowIterations).append(",\n");
        sb.append("      \"n_windows\": ").append(st.publishedNWindows).append(",\n");
        sb.append("      \"statistic\": ").append(str(extraStatistic(st, "mean"))).append(",\n");
        sb.append("      \"position_D\": [2.0, 0.0, 0.0],\n");
        sb.append("      \"report\": \"LLM_u_over_U_axis_x2D\"\n");
        sb.append("    }\n");
        sb.append("  },\n");

        // plane_extremes (25.09): each [min, max] pair pooled over the closed windows (window_min_max),
        // same fallback rule as above. statistic/window_iterations/n_windows are written once for the
        // whole block, not per station: they are the same triple for every field of every plane.
        sb.append("  \"plane_extremes\": {\n");
        for (int p = 0; p < PLANES.length; p++) {
            String plane = PLANES[p];
            sb.append("    \"").append(plane).append("\": {\n");
            for (int f = 0; f < FIELDS.length; f++) {
                String field = FIELDS[f];
                double lo = extraPublishedValue(st, planeExtremeReportName(field, plane, "min"), "min");
                double hi = extraPublishedValue(st, planeExtremeReportName(field, plane, "max"), "max");
                sb.append("      \"").append(field).append("\": [")
                  .append(num(lo)).append(", ").append(num(hi)).append("]");
                sb.append(f == FIELDS.length - 1 ? "\n" : ",\n");
            }
            sb.append("    },\n");
        }
        sb.append("    \"statistic\": ").append(str(extraStatistic(st, "minmax"))).append(",\n");
        sb.append("    \"window_iterations\": ").append(st.publishedWindowIterations).append(",\n");
        sb.append("    \"n_windows\": ").append(st.publishedNWindows).append("\n");
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    // One published coefficient and its four companion keys, in the order C1 fixes.
    private static void appendPublished(StringBuilder sb, ConvergenceState st, int q, boolean last) {
        String n = QUANTITIES[q];
        sb.append("    \"").append(n).append("\": ").append(num(st.published[q])).append(",\n");
        sb.append("    \"").append(n).append("_sd\": ").append(num(st.publishedSd[q])).append(",\n");
        sb.append("    \"").append(n).append("_window_iterations\": ")
          .append(st.publishedWindowIterations).append(",\n");
        sb.append("    \"").append(n).append("_n_windows\": ").append(st.publishedNWindows).append(",\n");
        sb.append("    \"").append(n).append("_statistic\": ").append(str(st.publishedStatistic))
          .append(last ? "\n" : ",\n");
    }

    // Extra quantities (25.09): the same pooled/instantaneous choice publish() already made for the
    // gated four (st.publishedStatistic is "iteration_mean" exactly when that path was taken), applied
    // to a report with no stationarity gate of its own. 'kind' is "mean", "min" or "max"; outside the
    // pooled path every kind falls back to the plain instantaneous read, exactly as cp_min/cp_stagnation
    // and the plane extremes did before this change.
    private double extraPublishedValue(ConvergenceState st, String reportName, String kind) {
        if (!"iteration_mean".equals(st.publishedStatistic)) {
            return value(reportName);
        }
        List<Double> xs = st.extraPooled.get(reportName);
        if ("min".equals(kind)) {
            return minOf(xs);
        }
        if ("max".equals(kind)) {
            return maxOf(xs);
        }
        return meanOf(xs);
    }

    // sd is only meaningful for a mean; 0.0 outside the pooled path, matching publishedSd's instantaneous
    // fallback above.
    private double extraPublishedSd(ConvergenceState st, String reportName) {
        if (!"iteration_mean".equals(st.publishedStatistic)) {
            return 0.0;
        }
        List<Double> xs = st.extraPooled.get(reportName);
        return sdOf(xs, meanOf(xs));
    }

    // statistic name published alongside an extra quantity: the pooled name for its kind
    // ("iteration_mean" / "window_min" / "window_max" / "window_min_max"), or "instantaneous" outside
    // the pooled path.
    private static String extraStatistic(ConvergenceState st, String kind) {
        if (!"iteration_mean".equals(st.publishedStatistic)) {
            return "instantaneous";
        }
        if ("min".equals(kind)) {
            return "window_min";
        }
        if ("max".equals(kind)) {
            return "window_max";
        }
        if ("minmax".equals(kind)) {
            return "window_min_max";
        }
        return "iteration_mean";
    }

    // probes.*/wake companion keys: value, _sd, _window_iterations, _n_windows, _statistic -- the same
    // quintet shape as appendPublished() above, computed from extraPooled instead of the gated arrays.
    private void appendExtraMeanPublished(StringBuilder sb, ConvergenceState st, String reportName,
                                          String jsonKey, boolean last) {
        appendExtraMeanPublished(sb, st, reportName, jsonKey, 1.0, last);
    }

    // Same, with value and sd divided by 'scale' after pooling (mdot over rho*U*D^2, 6.3). Division by
    // 1.0 is exact, so the unscaled callers above publish the same bits as before.
    private void appendExtraMeanPublished(StringBuilder sb, ConvergenceState st, String reportName,
                                          String jsonKey, double scale, boolean last) {
        sb.append("    \"").append(jsonKey).append("\": ")
          .append(num(extraPublishedValue(st, reportName, "mean") / scale)).append(",\n");
        sb.append("    \"").append(jsonKey).append("_sd\": ")
          .append(num(extraPublishedSd(st, reportName) / scale)).append(",\n");
        sb.append("    \"").append(jsonKey).append("_window_iterations\": ")
          .append(st.publishedWindowIterations).append(",\n");
        sb.append("    \"").append(jsonKey).append("_n_windows\": ").append(st.publishedNWindows).append(",\n");
        sb.append("    \"").append(jsonKey).append("_statistic\": ").append(str(extraStatistic(st, "mean")))
          .append(last ? "\n" : ",\n");
    }

    // cp_min/cp_stagnation companion keys: same shape as appendExtraMeanPublished, minus _sd (a min or
    // max has no standard deviation of its own).
    private void appendExtraExtremumPublished(StringBuilder sb, ConvergenceState st, String reportName,
                                              String jsonKey, String kind, boolean last) {
        sb.append("    \"").append(jsonKey).append("\": ")
          .append(num(extraPublishedValue(st, reportName, kind))).append(",\n");
        sb.append("    \"").append(jsonKey).append("_window_iterations\": ")
          .append(st.publishedWindowIterations).append(",\n");
        sb.append("    \"").append(jsonKey).append("_n_windows\": ").append(st.publishedNWindows).append(",\n");
        sb.append("    \"").append(jsonKey).append("_statistic\": ").append(str(extraStatistic(st, kind)))
          .append(last ? "\n" : ",\n");
    }

    // LLM_cell_count through value() is a double; the count it carries must be a whole, finite,
    // positive number to be written as the integer spec v4 6 asks for.
    private long cellCount() {
        double c = value("LLM_cell_count");
        long n = (long) c;
        if (Double.isNaN(c) || Double.isInfinite(c) || n <= 0 || (double) n != c) {
            throw new RuntimeException("run_macro: report 'LLM_cell_count' returned " + c
                + ", not a positive whole cell count.");
        }
        return n;
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "null";
        }
        return Double.toString(v);
    }

    // numArray(List<Double>) removed 19.09: cd_samples/cl_samples left the convergence block when it
    // was corrected (spec 6 does not list them), and it had no other caller.

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

    // ================================================================= logging / files
    private void log(String s) {
        logLines.add(s);
        sim.println("run_macro: " + s);
    }

    private void created(String s) {
        createdObjects.add(s);
        log("created " + s);
    }

    private void found(String s) {
        foundObjects.add(s);
    }

    private void modified(String object, String property, String oldValue, String newValue) {
        modifiedObjects.add(object + " | " + property + " | old=" + oldValue + " | new=" + newValue);
    }

    private void precondition(String s) {
        preconditionsChecked.add(s);
    }

    private void unresolved(String s) {
        unresolvedNotes.add(s);
    }

    private File sessionRelativeDir(String relative) {
        File base;
        String sessionDir = sim.getSessionDir();
        if (sessionDir != null && sessionDir.length() > 0) {
            base = new File(sessionDir);
        } else {
            base = sim.getSessionDirFile();
        }
        File dir = new File(base, relative);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new RuntimeException("run_macro: could not create output directory '" + relative + "'.");
        }
        return dir;
    }

    private void writeTextFile(File f, String content) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            w.write(content);
        } catch (Exception e) {
            throw new RuntimeException("run_macro: could not write '" + f.getName() + "': " + e.getMessage());
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

    private String prepareRunLogBody(double baseSize, double cells) {
        StringBuilder sb = new StringBuilder();
        sb.append("run_macro run_log -- mode=prepare").append('\n');
        sb.append("base_size_m=").append(baseSize).append('\n');
        sb.append("mesh_cells=").append(cells).append('\n');
        appendList(sb, "OBJECTS CREATED", createdObjects);
        appendList(sb, "OBJECTS FOUND AND LEFT ALONE", foundObjects);
        appendList(sb, "OBJECTS MODIFIED (name | property | old | new)", modifiedObjects);
        appendList(sb, "TRACE", logLines);
        appendList(sb, "UNRESOLVED", unresolvedNotes);
        return sb.toString();
    }

    private String runRunLogBody(int reTarget, ConvergenceState st, double wallclockSolveS) {
        StringBuilder sb = new StringBuilder();
        sb.append("run_macro run_log -- mode=run").append('\n');
        sb.append("re_target=").append(reTarget).append('\n');
        sb.append("iterations=").append(st.iterations).append('\n');
        sb.append("status=").append(st.status).append('\n');
        sb.append("wallclock_solve_s=").append(wallclockSolveS).append('\n');
        appendList(sb, "PRECONDITIONS CHECKED", preconditionsChecked);
        appendList(sb, "OBJECTS CREATED", createdObjects);
        appendList(sb, "OBJECTS MODIFIED (name | property | old | new)", modifiedObjects);
        appendList(sb, "TRACE", logLines);
        appendList(sb, "UNRESOLVED", unresolvedNotes);

        // VERIFICATION LOG, written from the final code (project rule 27): the constants as compiled,
        // the sample count of a closed window, and the per-window statistics the decision was taken on.
        int expectedSamples = WINDOW_ITERATIONS / SAMPLING_INTERVAL;
        sb.append('\n').append("VERIFICATION LOG").append('\n');
        sb.append("See the VERIFICATION LOG delivered with the source of run_macro.java: ").append('\n');
        sb.append("every API member used is classified there as bench-verified, get_doc-verified, ").append('\n');
        sb.append("or UNRESOLVED with a matching TODO marker in the source.").append('\n');
        sb.append('\n').append("constants as compiled").append('\n');
        sb.append("  SAMPLING_INTERVAL=").append(SAMPLING_INTERVAL).append('\n');
        sb.append("  WINDOW_ITERATIONS=").append(WINDOW_ITERATIONS).append('\n');
        sb.append("  DISCARD_ITERATIONS=").append(DISCARD_ITERATIONS).append('\n');
        sb.append("  MAX_WINDOWS=").append(MAX_WINDOWS).append('\n');
        sb.append("  MAX_ITERATIONS=").append(MAX_ITERATIONS).append('\n');
        sb.append("  K_GATE=").append(K_GATE).append('\n');
        sb.append("  K_SD=").append(K_SD).append('\n');
        sb.append("  MIN_CYCLES=").append(MIN_CYCLES).append('\n');
        sb.append("  HYSTERESIS_SD=").append(HYSTERESIS_SD).append('\n');
        sb.append("  CONVERGED_SD=").append(CONVERGED_SD).append('\n');
        sb.append("  MASS_IMBALANCE_LIMIT=").append(MASS_IMBALANCE_LIMIT).append('\n');
        sb.append("  MASS_IMBALANCE_GRACE_ITERATIONS=").append(MASS_IMBALANCE_GRACE_ITERATIONS).append('\n');
        sb.append("samples per closed window=").append(expectedSamples)
          .append(" (WINDOW_ITERATIONS / SAMPLING_INTERVAL)").append('\n');
        sb.append("windows closed=").append(st.windowsClosed).append('\n');
        for (int k = 0; k < st.windowsClosed; k++) {
            int from = DISCARD_ITERATIONS + k * WINDOW_ITERATIONS;
            sb.append("  window ").append(k + 1).append(" (iterations ").append(from).append('-')
              .append(from + WINDOW_ITERATIONS).append(", samples=").append(st.samplesPerWindow).append(')').append('\n');
            for (int q = 0; q < QUANTITIES.length; q++) {
                WindowStats ws = st.windows.get(q).get(k);
                sb.append("    ").append(QUANTITIES[q]).append(": m=").append(ws.mean)
                  .append(" s=").append(ws.sd).append(" n_cycles=").append(ws.nCycles).append('\n');
            }
        }
        // closeWindow() already throws if a closed window does not hold exactly expectedSamples
        // samples; this is the same check restated where the log is written, so a log can never
        // report a window of the wrong length.
        if (st.windowsClosed > 0 && st.samplesPerWindow != expectedSamples) {
            throw new RuntimeException("run_macro: closed windows hold " + st.samplesPerWindow
                + " samples, expected exactly " + expectedSamples + ".");
        }
        return sb.toString();
    }

    private static void appendList(StringBuilder sb, String title, List<String> items) {
        sb.append('\n').append(title).append('\n');
        if (items.isEmpty()) {
            sb.append("  (none)").append('\n');
        }
        for (int i = 0; i < items.size(); i++) {
            sb.append("  - ").append(items.get(i)).append('\n');
        }
    }

    private void writeRunLog(String body, File dir) {
        writeTextFile(new File(dir, "run_log.txt"), body);
    }
}
