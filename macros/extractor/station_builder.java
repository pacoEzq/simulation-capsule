// station_builder.java
// xtr chain (Capsule Extractor v0, STAR-CCM+ adapter) -- preparation step, run before output_exporter,
// object_audit and manifest_writer. Simcenter STAR-CCM+ 2606. One public class, no records, no modules.
//
// On an open, solved .sim it creates from card.json the objects the other three require and the case
// chain does not bring: the global length parameter card.body.length.parameter (absent: "D"; null:
// none), the field functions x_D, y_D, z_D (position over that parameter), and per card.stations[] the
// Derived Parts section_<stem> (plane section normal to axis at value) and grid_<stem> (the lattice
// origin + i * spacing of grid nodes) and the table LLM_table_<stem> (XyzInternalTable on grid_<stem>
// with x_D, y_D, z_D and stations[].fields). Station coordinates are in units of the length parameter.
// On a 2D region (a PlaneProbePart extracts no rows there) grid_<stem> is replaced by grid[1] line
// probes grid_<stem>_rNN (LinePart, one per node of the second in-plane axis, grid[0] points each),
// LLM_table_<stem> takes all of them and no section_<stem> is created.
// Idempotent: an object that already exists by its name is neither touched nor duplicated. What it
// creates, and what it keeps, goes to work/<card.body.alias>/station_builder.log. card.json is looked
// up like output_exporter does: session directory first, then the directory of the .sim. The
// simulation is saved as <name>_xtr.sim next to the original, which is never overwritten; the three
// adapter macros then run on <name>_xtr.sim.
//
// Signatures come from t7/run_macro.java (compiled against 2606), output_exporter.java and
// object_audit.java in this directory. Markers "TODO(unverified signature)" name the API members that no compiled
// source of the repository calls; they are the expected compile risks.

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import star.base.neo.DoubleVector;
import star.base.neo.NamedObject;
import star.base.neo.NeoObjectVector;
import star.common.Dimensions; // TODO(unverified signature): Dimensions.Builder() only in the GUI recording of 13.10 (UserUnits)
import star.common.FieldFunction;
import star.common.Region;
import star.common.ScalarGlobalParameter;
import star.common.Simulation;
import star.common.StarMacro;
import star.common.Units; // TODO(unverified signature): cast seen in a GUI recording only
import star.common.UserFieldFunction; // TODO(unverified signature): package as in t7/object_audit.java
import star.common.XyzInternalTable;
import star.common.*; // TODO(unverified signature): package of PlaneProbePart not read; the two on-demand imports cover either
import star.vis.*;
import star.vis.PlaneSection; // TODO(unverified signature): package from a GUI recording, not compiled here

public class station_builder extends StarMacro {

    // ---------------------------------------------------------------- constants
    private static final String CARD_NAME = "card.json";
    private static final String LOG_NAME = "station_builder.log";
    private static final String WORK_DIR = "work";
    private static final String SIM_SUFFIX = ".sim";
    private static final String NEW_SUFFIX = "_xtr";
    private static final Object JSON_NULL = new Object();
    private static final String[] COORD_COLUMNS = { "x_D", "y_D", "z_D" };

    // ---------------------------------------------------------------- session state
    private Simulation sim;
    private File sessionBase;
    private File outDir;
    private File logFile;
    private final List<String> pendingLog = new ArrayList<String>();

    // From card.json.
    private String alias;
    private String fluidRegion;
    private String lengthParameter = "D"; // card.body.length.parameter; absent: "D"; null: none
    private Double lengthValue;           // card.body.length.value, in metres; null: not in the card
    private final List<Station> stations = new ArrayList<Station>();

    private double unit = 1.0; // SI value of the length parameter; 1 when there is none
    private int created;
    private int kept;

    // One entry of card.stations. axes: the two in-plane coordinates, then the normal one; grid and
    // origin follow the order of the two in-plane ones (same convention as output_exporter).
    private static final class Station {
        String stem;
        int[] axes;
        double value;
        int[] grid;
        double[] origin;
        double spacing;
        List<String> fields = new ArrayList<String>();
    }

    // ================================================================= entry point
    public void execute() {
        sim = getActiveSimulation();
        try {
            readCard(loadCard());
            resolveOutput();
            log("start " + nowUtc() + " on " + simName());

            Region fluid = region(fluidRegion);
            boolean twoD = isTwoDimensional(fluid);
            log("region '" + fluidRegion + "' is " + (twoD ? "2D: stations as line probes grid_<stem>_rNN" : "3D: stations as plane probes grid_<stem>"));
            provisionLengthParameter();
            for (int k = 0; k < COORD_COLUMNS.length; k++) {
                provisionCoordinate(k);
            }
            for (int i = 0; i < stations.size(); i++) {
                Station st = stations.get(i);
                try {
                    if (!twoD) {
                        // A section of a 2D region holds no data; output_exporter paints the region instead.
                        provisionSection(st, fluid);
                    }
                    List<NamedObject> parts = twoD ? provisionLines(st, fluid)
                        : Collections.singletonList(provisionGrid(st, fluid));
                    provisionTable(st, parts);
                } catch (RuntimeException e) {
                    throw new RuntimeException("station_builder: station '" + st.stem + "': "
                        + String.valueOf(e.getMessage()).replace("station_builder: ", ""));
                }
            }

            String saved = saveAsNew();
            log("created " + created + ", kept " + kept + "; saved as " + saved);
            log("done");
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
    }

    // ================================================================= card.json
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
            throw new RuntimeException("station_builder: could not locate '" + CARD_NAME
                + "' in the session directory nor in the directory of the .sim file.");
        }
        String text;
        try {
            text = new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("station_builder: could not read '" + CARD_NAME + "'.");
        }
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return asObject(parseJson(text, CARD_NAME), CARD_NAME + " root");
    }

    private void readCard(Map<String, Object> card) {
        alias = requireString(card, CARD_NAME, "body", "alias");
        if (alias.length() == 0 || alias.indexOf('/') >= 0 || alias.indexOf('\\') >= 0 || alias.startsWith(".")) {
            throw new RuntimeException("station_builder: " + CARD_NAME + " body.alias '" + alias + "' is not a directory name.");
        }
        fluidRegion = requireString(card, CARD_NAME, "body", "fluid_region", "name");

        // length.parameter as output_exporter reads it; length.value (a number, or {"value": <number>})
        // is only needed when the parameter has to be created.
        Map<String, Object> body = asObject(member(card, CARD_NAME, "body"), CARD_NAME + " body");
        Object len = body.get("length");
        if (len instanceof Map) {
            Map<?, ?> lm = (Map<?, ?>) len;
            if (lm.containsKey("parameter")) {
                Object lp = lm.get("parameter");
                if (lp == null || lp == JSON_NULL) {
                    lengthParameter = null;
                } else if (lp instanceof String && ((String) lp).length() > 0) {
                    lengthParameter = (String) lp;
                } else {
                    throw new RuntimeException("station_builder: " + CARD_NAME + " body.length.parameter is neither a name nor null.");
                }
            }
            Object lv = lm.get("value");
            if (lv instanceof Map) {
                lv = ((Map<?, ?>) lv).get("value");
            }
            if (lv instanceof JsonNumber) {
                lengthValue = Double.valueOf(Double.parseDouble(((JsonNumber) lv).raw));
            } else if (lv != null && lv != JSON_NULL) {
                throw new RuntimeException("station_builder: " + CARD_NAME + " body.length.value is not a number.");
            }
        }

        List<Object> st = requireList(card, CARD_NAME, "stations");
        Set<String> stems = new HashSet<String>();
        for (int i = 0; i < st.size(); i++) {
            String where = CARD_NAME + " stations[" + i + "]";
            Map<String, Object> m = asObject(st.get(i), where);
            Station s = new Station();
            s.stem = requireString(m, where, "stem");
            if (!stems.add(s.stem)) {
                throw new RuntimeException("station_builder: " + CARD_NAME + " has two stations '" + s.stem + "'.");
            }
            String axis = requireString(m, where, "axis");
            int c = "x".equals(axis) ? 0 : "y".equals(axis) ? 1 : "z".equals(axis) ? 2 : -1;
            if (c < 0) {
                throw new RuntimeException("station_builder: station '" + s.stem + "': axis '" + axis + "' is not x, y or z.");
            }
            s.axes = c == 0 ? new int[] { 1, 2, 0 } : c == 1 ? new int[] { 0, 2, 1 } : new int[] { 0, 1, 2 };
            s.value = requireDouble(m, where, "value");
            List<Object> grid = requireList(m, where, "grid");
            if (grid.size() != 2) {
                throw new RuntimeException("station_builder: station '" + s.stem + "': grid has " + grid.size()
                    + " entries, expected 2.");
            }
            long g0 = integerOf(grid.get(0), "station '" + s.stem + "' grid[0]");
            long g1 = integerOf(grid.get(1), "station '" + s.stem + "' grid[1]");
            if (g0 < 2 || g1 < 2 || g0 * g1 > Integer.MAX_VALUE) {
                throw new RuntimeException("station_builder: station '" + s.stem + "': grid [" + g0 + ", " + g1
                    + "] is not a lattice.");
            }
            s.grid = new int[] { (int) g0, (int) g1 };
            List<Object> origin = requireList(m, where, "origin");
            if (origin.size() != 2) {
                throw new RuntimeException("station_builder: station '" + s.stem + "': origin has " + origin.size()
                    + " entries, expected 2.");
            }
            s.origin = new double[] { doubleOf(origin.get(0), "station '" + s.stem + "' origin[0]"),
                doubleOf(origin.get(1), "station '" + s.stem + "' origin[1]") };
            s.spacing = requireDouble(m, where, "spacing");
            if (!(s.spacing > 0.0) || Double.isInfinite(s.spacing)) {
                throw new RuntimeException("station_builder: station '" + s.stem + "': spacing " + s.spacing + " is not positive.");
            }
            List<Object> fields = requireList(m, where, "fields");
            for (int k = 0; k < fields.size(); k++) {
                if (!(fields.get(k) instanceof String)) {
                    throw new RuntimeException("station_builder: station '" + s.stem + "': fields[" + k + "] is not a string.");
                }
                s.fields.add((String) fields.get(k));
            }
            stations.add(s);
        }
    }

    // ================================================================= output directory
    // work/<card.body.alias>/ under the session directory; created when absent, since this macro runs
    // before output_exporter.
    private void resolveOutput() {
        String sessionDir = sim.getSessionDir();
        if (sessionDir != null && sessionDir.length() > 0) {
            sessionBase = new File(sessionDir);
        } else {
            sessionBase = sim.getSessionDirFile();
        }
        File dir = new File(sessionBase, WORK_DIR + File.separator + alias);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new RuntimeException("station_builder: could not create '" + WORK_DIR + "/" + alias + "'.");
        }
        outDir = dir;
        logFile = new File(dir, LOG_NAME);
        for (int i = 0; i < pendingLog.size(); i++) {
            appendLine(logFile, pendingLog.get(i));
        }
        pendingLog.clear();
        sim.println("station_builder: output=" + rel(outDir));
    }

    // ================================================================= 1. length parameter
    // Kept when it exists; created with card.body.length.value when it does not; never invented.
    private void provisionLengthParameter() {
        if (lengthParameter == null) {
            unit = 1.0;
            log("body.length.parameter is null: no length parameter, x_D, y_D, z_D are positions in metres");
            return;
        }
        Object o = sim.getGlobalParameterManager().hasObject(lengthParameter);
        if (o != null) {
            if (!(o instanceof ScalarGlobalParameter)) {
                throw new RuntimeException("station_builder: global parameter '" + lengthParameter
                    + "' exists but is not a ScalarGlobalParameter.");
            }
            unit = ((ScalarGlobalParameter) o).getQuantity().getSIValue();
            kept(lengthParameter + " (ScalarGlobalParameter) = " + unit + " m");
            return;
        }
        if (lengthValue == null) {
            throw new RuntimeException("station_builder: missing global parameter '" + lengthParameter
                + "' and " + CARD_NAME + " has no body.length.value to create it.");
        }
        double v = lengthValue.doubleValue();
        if (!(v > 0.0) || Double.isInfinite(v)) {
            throw new RuntimeException("station_builder: " + CARD_NAME + " body.length.value " + v + " is not positive.");
        }
        // TODO(unverified signature): GlobalParameterManager.createGlobalParameter(Class, String),
        // ScalarGlobalParameter.setDimensions(Dimensions), ScalarPhysicalQuantity.setValue(double) and
        // setUnits(Units), UnitsManager.getObject("m"); the pattern of a 2606 GUI recording, not compiled here.
        ScalarGlobalParameter p = (ScalarGlobalParameter) sim.getGlobalParameterManager()
            .createGlobalParameter(ScalarGlobalParameter.class, "Scalar");
        p.setPresentationName(lengthParameter);
        p.setDimensions(Dimensions.Builder().length(1).build());
        p.getQuantity().setUnits((Units) sim.getUnitsManager().getObject("m"));
        p.getQuantity().setValue(v);
        unit = p.getQuantity().getSIValue();
        created(lengthParameter + " (ScalarGlobalParameter) = " + unit + " m from " + CARD_NAME + " body.length.value");
    }

    // ================================================================= 2. x_D, y_D, z_D
    private void provisionCoordinate(int k) {
        String name = COORD_COLUMNS[k];
        Object o = sim.getFieldFunctionManager().hasObject(name);
        if (!isMissingFieldFunction(o)) {
            if (!(o instanceof FieldFunction)) {
                throw new RuntimeException("station_builder: object '" + name + "' is not a FieldFunction.");
            }
            kept(name + " (" + o.getClass().getSimpleName() + ")");
            return;
        }
        String definition = "$${Position}[" + k + "]" + (lengthParameter == null ? "" : "/${" + lengthParameter + "}");
        // TODO(unverified signature): FieldFunctionManager.createFieldFunction() and
        // UserFieldFunction.setDefinition(String); setFunctionName(String) is in the GUI recording of 30.09.
        UserFieldFunction ff = sim.getFieldFunctionManager().createFieldFunction();
        ff.setPresentationName(name);
        ff.setFunctionName(name);
        ff.setDefinition(definition);
        created(name + " (UserFieldFunction) = " + definition);
    }

    // ================================================================= 3. stations
    // section_<stem>: plane section of the fluid region, normal to the station axis at value.
    private void provisionSection(Station st, Region fluid) {
        String name = "section_" + st.stem;
        Object existing = sim.getPartManager().hasObject(name);
        if (existing != null) {
            kept(name + " (" + existing.getClass().getSimpleName() + ")");
            return;
        }
        double[] origin = new double[3];
        double[] normal = new double[3];
        origin[st.axes[2]] = st.value * unit;
        normal[st.axes[2]] = 1.0;
        // TODO(unverified signature): PartManager.createPlaneSection(), PlaneSection.setOrigin(DoubleVector)
        // and setOrientation(DoubleVector); setInputPartsCollection(Collection) is compiled on PointPart only
        // (t7/run_macro.java). Same calls as a GUI recording.
        PlaneSection section = sim.getPartManager().createPlaneSection();
        section.setPresentationName(name);
        List<NamedObject> input = new ArrayList<NamedObject>();
        input.add(fluid);
        section.setInputPartsCollection(input);
        section.setOrigin(new DoubleVector(origin));
        section.setOrientation(new DoubleVector(normal));
        created(name + " (PlaneSection) " + "xyz".charAt(st.axes[2]) + " = " + num(st.value) + " -> "
            + num(origin[st.axes[2]]) + " m");
    }

    // grid_<stem>: the card lattice as a presentation grid (PlaneProbePart) of the fluid region. Nodes are
    // origin + i * spacing in units of the length parameter, converted to metres; point2 runs along the
    // first in-plane axis (grid[0] nodes), point1 along the second (grid[1] nodes), and the X/Y resolution
    // count nodes, not divisions. TODO(unverified signature): that X resolution follows point2 and Y
    // resolution point1 is the first hypothesis; if the table export shows grid[1] distinct values of the
    // first in-plane coordinate, swap the two resolution arguments.
    private NamedObject provisionGrid(Station st, Region fluid) {
        String name = "grid_" + st.stem;
        if (sim.getPartManager().has(name)) {
            Object existing = sim.getPartManager().getObject(name);
            if (!(existing instanceof PlaneProbePart)) {
                throw new RuntimeException("station_builder: part '" + name + "' exists but is not a presentation grid ("
                    + existing.getClass().getSimpleName() + ").");
            }
            kept(name + " (PlaneProbePart)");
            return (NamedObject) existing;
        }
        double[] origin = new double[3];
        origin[st.axes[0]] = st.origin[0] * unit;
        origin[st.axes[1]] = st.origin[1] * unit;
        origin[st.axes[2]] = st.value * unit;
        double[] point1 = origin.clone();
        point1[st.axes[1]] += (st.grid[1] - 1) * st.spacing * unit;
        double[] point2 = origin.clone();
        point2[st.axes[0]] += (st.grid[0] - 1) * st.spacing * unit;
        double[] normal = new double[3];
        normal[st.axes[2]] = 1.0;
        if (!sim.getUnitsManager().has("m")) {
            throw new RuntimeException("station_builder: missing units 'm'.");
        }
        Units m = sim.getUnitsManager().getUnits("m");
        PlaneProbePart grid = sim.getPartManager().createPlaneProbePart(new NeoObjectVector(new Object[] { fluid }),
            new DoubleVector(origin), new DoubleVector(point1), new DoubleVector(point2), st.grid[0], st.grid[1]);
        grid.setPresentationName(name);
        grid.getInputParts().setObjects(fluid);
        grid.setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
        grid.getOriginCoordinate().setCoordinate(m, m, m, new DoubleVector(origin));
        grid.getPoint1Coordinate().setCoordinate(m, m, m, new DoubleVector(point1));
        grid.getPoint2Coordinate().setCoordinate(m, m, m, new DoubleVector(point2));
        grid.getNormalCoordinate().setCoordinate(m, m, m, new DoubleVector(normal));
        grid.setXResolution(st.grid[0]);
        grid.setYResolution(st.grid[1]);
        grid.setBatched(false);
        created(name + " (PlaneProbePart) " + st.grid[0] + " x " + st.grid[1] + " nodes, spacing " + num(st.spacing)
            + ", " + "xyz".charAt(st.axes[2]) + " = " + num(st.value));
        return grid;
    }

    // 2D: grid_<stem>_rNN, rNN = 00 .. grid[1]-1, one LinePart per node of the second in-plane axis, from
    // origin to origin + (grid[0]-1) * spacing along the first; setResolution(n) gives n + 1 points, so
    // grid[0] - 1. Calls as in a 2606 GUI recording of Derived Parts > New > Probe > Line. A row that
    // exists by its name is kept as it is.
    private List<NamedObject> provisionLines(Station st, Region fluid) {
        if (st.axes[2] != 2) {
            throw new RuntimeException("station_builder: 2D region: station axis is '" + "xyz".charAt(st.axes[2])
                + "', a line probe station needs axis 'z'.");
        }
        if (!sim.getUnitsManager().has("m")) {
            throw new RuntimeException("station_builder: missing units 'm'.");
        }
        Units m = sim.getUnitsManager().getUnits("m");
        List<NamedObject> rows = new ArrayList<NamedObject>();
        int res = st.grid[0] - 1;
        for (int j = 0; j < st.grid[1]; j++) {
            String name = "grid_" + st.stem + "_r" + (j < 10 ? "0" : "") + j;
            if (sim.getPartManager().has(name)) {
                Object existing = sim.getPartManager().getObject(name);
                if (!(existing instanceof LinePart)) {
                    throw new RuntimeException("station_builder: part '" + name + "' exists but is not a line probe ("
                        + existing.getClass().getSimpleName() + ").");
                }
                kept(name + " (LinePart)");
                rows.add((NamedObject) existing);
                continue;
            }
            double[] point1 = new double[3];
            point1[st.axes[0]] = st.origin[0] * unit;
            point1[st.axes[1]] = (st.origin[1] + j * st.spacing) * unit;
            point1[st.axes[2]] = st.value * unit;
            double[] point2 = point1.clone();
            point2[st.axes[0]] += res * st.spacing * unit;
            // TODO(unverified signature): PartManager.createLinePart, LinePart.getPoint1Coordinate/
            // getPoint2Coordinate, getInputParts, setResolution; from the GUI recording, not compiled here.
            LinePart line = sim.getPartManager().createLinePart(new ArrayList<>(Collections.<NamedObject>emptyList()),
                new DoubleVector(point1), new DoubleVector(point2), res, null);
            line.setPresentationName(name);
            line.getPoint1Coordinate().setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
            line.getPoint1Coordinate().setCoordinate(m, m, m, new DoubleVector(point1));
            line.getPoint2Coordinate().setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
            line.getPoint2Coordinate().setCoordinate(m, m, m, new DoubleVector(point2));
            line.setCoordinateSystem(sim.getCoordinateSystemManager().getLabCoordinateSystem());
            line.getInputParts().setQuery(null);
            line.getInputParts().setObjects(fluid);
            line.setResolution(res);
            created(name + " (LinePart) " + st.grid[0] + " points, " + "xyz".charAt(st.axes[1]) + " = "
                + num(st.origin[1] + j * st.spacing) + ", spacing " + num(st.spacing));
            rows.add(line);
        }
        return rows;
    }

    // LLM_table_<stem>: one XyzInternalTable on grid_<stem> (3D) or on all grid_<stem>_rNN (2D) with x_D,
    // y_D, z_D and the station's fields, vertex data, extracted once on creation.
    private void provisionTable(Station st, List<NamedObject> parts) {
        String name = "LLM_table_" + st.stem;
        if (sim.getTableManager().has(name)) {
            Object existing = sim.getTableManager().getTable(name);
            if (!(existing instanceof XyzInternalTable)) {
                throw new RuntimeException("station_builder: table '" + name + "' exists but is not an XyzInternalTable ("
                    + existing.getClass().getSimpleName() + ").");
            }
            kept(name + " (XyzInternalTable)");
            return;
        }
        List<FieldFunction> ffs = new ArrayList<FieldFunction>();
        StringBuilder names = new StringBuilder();
        for (int k = 0; k < COORD_COLUMNS.length + st.fields.size(); k++) {
            String f = k < COORD_COLUMNS.length ? COORD_COLUMNS[k] : st.fields.get(k - COORD_COLUMNS.length);
            ffs.add(fieldFunction(f));
            names.append(k > 0 ? "," : "").append(f);
        }
        XyzInternalTable table = sim.getTableManager().createTable(XyzInternalTable.class);
        table.setPresentationName(name);
        table.getParts().setQuery(null);
        // TODO(unverified signature): setObjects with several parts; the recording passes one.
        table.getParts().setObjects(parts.toArray(new NamedObject[0]));
        table.setFieldFunctions(new NeoObjectVector(ffs.toArray()));
        table.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
        table.setExtractVertexData(true);
        table.extract();
        created(name + " (XyzInternalTable) on " + (parts.size() == 1 ? parts.get(0).getPresentationName()
            : parts.size() + " parts grid_" + st.stem + "_rNN") + ": " + names);
    }

    // ================================================================= 5. save
    // <name>_xtr.sim next to the original; Simulation.saveState(String) as in t7/run_macro.java.
    private String saveAsNew() {
        String path = sim.getSessionPath();
        if (path == null || path.length() == 0) {
            throw new RuntimeException("station_builder: the simulation has no file name to derive '<name>" + NEW_SUFFIX
                + SIM_SUFFIX + "' from.");
        }
        File original = new File(path);
        String base = original.getName();
        if (base.toLowerCase(Locale.ROOT).endsWith(SIM_SUFFIX)) {
            base = base.substring(0, base.length() - SIM_SUFFIX.length());
        }
        File target = new File(original.getAbsoluteFile().getParentFile(), base + NEW_SUFFIX + SIM_SUFFIX);
        if (target.getAbsolutePath().equals(original.getAbsolutePath())) {
            throw new RuntimeException("station_builder: '" + target.getName() + "' is the original; not saved.");
        }
        if (target.exists()) {
            log(target.getName() + " exists and is replaced (the original " + original.getName() + " is not)");
        }
        sim.saveState(target.getAbsolutePath());
        return target.getName();
    }

    // ================================================================= lookups
    // 2D when the space model of the region's physics continuum is Two Dimensional; matched by the
    // simple class name so the model class need not be imported.
    // TODO(unverified signature): Region.getPhysicsContinuum(), PhysicsContinuum.getModelManager().
    private boolean isTwoDimensional(Region fluid) {
        Object pc = fluid.getPhysicsContinuum();
        if (pc == null) {
            throw new RuntimeException("station_builder: region '" + fluidRegion + "' has no physics continuum.");
        }
        for (Object model : fluid.getPhysicsContinuum().getModelManager().getObjects()) {
            if (model != null && model.getClass().getSimpleName().equals("TwoDimensionalModel")) {
                return true;
            }
        }
        return false;
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
            throw new RuntimeException("station_builder: missing region '" + name + "'.");
        }
        if (!(o instanceof Region)) {
            throw new RuntimeException("station_builder: object '" + name + "' is not a Region.");
        }
        return (Region) o;
    }

    private FieldFunction fieldFunction(String name) {
        Object o = sim.getFieldFunctionManager().hasObject(name);
        if (isMissingFieldFunction(o)) {
            throw new RuntimeException("station_builder: missing field function '" + name + "'.");
        }
        if (!(o instanceof FieldFunction)) {
            throw new RuntimeException("station_builder: object '" + name + "' is not a FieldFunction.");
        }
        return (FieldFunction) o;
    }

    // As output_exporter: a missing name may come back as an object of class NullFieldFunction.
    private static boolean isMissingFieldFunction(Object ff) {
        return ff == null || ff.getClass().getSimpleName().equals("NullFieldFunction");
    }

    private String simName() {
        String path = sim.getSessionPath();
        return path == null || path.length() == 0 ? "(unsaved)" : new File(path).getName();
    }

    // ================================================================= generic JSON read
    // Copied from object_audit.java (JsonReader family).
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
            return new RuntimeException("station_builder: " + name + " is not valid JSON (" + what + " at offset " + i + ").");
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
            throw new RuntimeException("station_builder: " + what + " is not a JSON object.");
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
                throw new RuntimeException("station_builder: " + file + " has no '" + dotted + "'.");
            }
            cur = m.get(path[k]);
        }
        return cur;
    }

    private static String requireString(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof String)) {
            throw new RuntimeException("station_builder: " + file + " " + String.join(".", path) + " is not a string.");
        }
        return (String) v;
    }

    private static double requireDouble(Map<String, Object> root, String file, String... path) {
        return doubleOf(member(root, file, path), file + " " + String.join(".", path));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireList(Map<String, Object> root, String file, String... path) {
        Object v = member(root, file, path);
        if (!(v instanceof List)) {
            throw new RuntimeException("station_builder: " + file + " " + String.join(".", path) + " is not an array.");
        }
        return (List<Object>) v;
    }

    private static double doubleOf(Object v, String what) {
        if (!(v instanceof JsonNumber)) {
            throw new RuntimeException("station_builder: " + what + " is not a number.");
        }
        double d = Double.parseDouble(((JsonNumber) v).raw);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new RuntimeException("station_builder: " + what + " is not finite.");
        }
        return d;
    }

    private static long integerOf(Object v, String what) {
        if (!(v instanceof JsonNumber)) {
            throw new RuntimeException("station_builder: " + what + " is not a number.");
        }
        try {
            return Long.parseLong(((JsonNumber) v).raw);
        } catch (NumberFormatException e) {
            throw new RuntimeException("station_builder: " + what + " is not an integer.");
        }
    }

    // ================================================================= logging / files
    private void created(String what) {
        created++;
        log("created " + what);
    }

    private void kept(String what) {
        kept++;
        log("kept " + what);
    }

    // Before the output directory exists the lines wait in pendingLog.
    private void log(String s) {
        sim.println("station_builder: " + s);
        if (logFile != null) {
            appendLine(logFile, s);
        } else {
            pendingLog.add(s);
        }
    }

    // One FAIL line per run; the exception is rethrown by the caller, without "done". Nothing is saved.
    private void fail(RuntimeException e) {
        String reason = scrub(String.valueOf(e.getMessage())).replace('\n', ' ').replace('\r', ' ');
        sim.println("station_builder: FAIL " + reason);
        if (logFile != null) {
            try {
                appendLine(logFile, "FAIL " + reason);
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

    private static String num(double d) {
        return Double.toString(d);
    }

    private static String nowUtc() {
        return DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC).format(Instant.now());
    }

    private void writeText(File f, String content) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
            w.write(content);
        } catch (Exception e) {
            throw new RuntimeException("station_builder: could not write '" + rel(f) + "'.");
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
            throw new RuntimeException("station_builder: could not append to '" + rel(f) + "'.");
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
