// export_stats.java
// Karman vortex street, Re = 100, non-dimensional (rho = U = D = 1, mu = 0.01), region fluid, 2D.
// Exports mean and RMS of the fluctuation of u, v and p (field functions from setup_monitors:
// u_over_U_mean/rms, v_over_U_mean/rms, cp_mean/rms) over the window x/D in [-2,10],
// y/D in [-3,3]: table work/stats_raw/stats_table.csv (x_D, y_D, ...) and scene stats_scene.png.
// Headers carry no dimensional magnitudes: STAR's coordinate columns are dropped from the CSV.

import java.io.*;
import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.vis.*;

public class export_stats extends StarMacro {

    private static final String[] STATS = {
        "u_over_U_mean", "u_over_U_rms", "v_over_U_mean", "v_over_U_rms", "cp_mean", "cp_rms"};

    public void execute() {

        Simulation sim = getActiveSimulation();
        FieldFunctionManager ffMgr = sim.getFieldFunctionManager();

        File outDir = new File(sim.getSessionDir(), "work" + File.separator + "stats_raw");
        outDir.mkdirs();

        // --- x_D, y_D coordinate field functions, scaled by the parameter D ---
        FieldFunction xDFF = userFF(ffMgr, "x_D", "$$Position[0]/${D}");
        FieldFunction yDFF = userFF(ffMgr, "y_D", "$$Position[1]/${D}");

        // --- Statistics field functions, created by setup_monitors before the run ---
        List<FieldFunction> columns = new ArrayList<>(Arrays.asList(xDFF, yDFF));
        for (String name : STATS) {
            if (!ffMgr.has(name)) {
                throw new RuntimeException("export_stats: missing field function '" + name + "' (run setup_monitors).");
            }
            columns.add(ffMgr.getFunction(name));
        }

        // --- Spatial window: x/D in [-2,10], y/D in [-3,3], via chained thresholds on fluid ---
        Region region = sim.getRegionManager().getRegion("fluid");
        ThresholdPart xWindow = threshold(sim, "stats_window_x", region, xDFF, -2.0, 10.0);
        ThresholdPart statsWindow = threshold(sim, "stats_window", xWindow, yDFF, -3.0, 3.0);

        // --- Spatial table: x_D, y_D and the six statistics, cell values ---
        TableManager tableMgr = sim.getTableManager();
        XyzInternalTable table;
        if (tableMgr.has("stats_table")) {
            table = (XyzInternalTable) tableMgr.getTable("stats_table");
        } else {
            table = tableMgr.createTable(XyzInternalTable.class);
            table.setPresentationName("stats_table");
        }
        table.getParts().setObjects(region);
        table.setFieldFunctions(new NeoObjectVector(columns.toArray()));
        table.setRepresentation(sim.getRepresentationManager().getDefaultFvRepresentation());
        table.extract();
        File raw = new File(outDir, "stats_table_star.csv");
        table.export(raw.getAbsolutePath(), ",");

        List<String> wanted = new ArrayList<>(Arrays.asList("x_D", "y_D"));
        wanted.addAll(Arrays.asList(STATS));
        keepColumns(raw, new File(outDir, "stats_table.csv"), wanted);
        raw.delete();

        // --- Scalar scene PNG: u_over_U_mean over the stats window ---
        SceneManager sceneMgr = sim.getSceneManager();
        Scene scene;
        if (sceneMgr.has("stats_scene")) {
            scene = sceneMgr.getScene("stats_scene");
        } else {
            sceneMgr.createScalarScene("stats_scene", "Outline", "Scalar"); scene = sceneMgr.getScene("stats_scene 1");
            scene.setPresentationName("stats_scene");
        }
        scene.initializeAndWait();
        for (Object displayer : scene.getDisplayerManager().getObjects()) {
            if (displayer instanceof ScalarDisplayer) {
                ScalarDisplayer scalar = (ScalarDisplayer) displayer;
                scalar.getInputParts().setObjects(region);
                scalar.getScalarDisplayQuantity().setFieldFunction(ffMgr.getFunction("u_over_U_mean"));
            }
        }
        scene.resetCamera();
        scene.printAndWait(new File(outDir, "stats_scene.png").getAbsolutePath(), 1, 1280, 800, true, false);
    }

    private FieldFunction userFF(FieldFunctionManager ffMgr, String name, String definition) {
        UserFieldFunction ff;
        if (ffMgr.has(name)) {
            ff = (UserFieldFunction) ffMgr.getFunction(name);
        } else {
            ff = ffMgr.createFieldFunction();
            ff.setFunctionName(name);
            ff.setPresentationName(name);
        }
        ff.getTypeOption().setSelected(FieldFunctionTypeOption.Type.SCALAR);
        ff.setDefinition(definition);
        return ff;
    }

    // Cells of input with lo <= ff <= hi; ff is dimensionless.
    private ThresholdPart threshold(Simulation sim, String name, Object input, FieldFunction ff,
                                    double lo, double hi) {
        PartManager partMgr = sim.getPartManager();
        Units none = sim.getUnitsManager().getUnits("");
        ThresholdPart part;
        if (partMgr.has(name)) {
            part = (ThresholdPart) partMgr.getObject(name);
        } else {
            part = partMgr.createThresholdPart(new NeoObjectVector(new Object[] {input}),
                new DoubleVector(new double[] {lo, hi}), none, ff, 0);
            part.setPresentationName(name);
        }

        part.setFieldFunction(ff);
        part.setMode(ThresholdMode.BETWEEN_TAG);
        part.getRangeQuantities().setArray(new DoubleVector(new double[] {lo, hi}));
        return part;
    }

    // Copies the wanted columns, in order, from STAR's export; unit suffixes and quotes are stripped
    // from the headers, and STAR's X, Y, Z coordinate columns (in m) are left out.
    private void keepColumns(File in, File out, List<String> wanted) {
        try (BufferedReader br = new BufferedReader(new FileReader(in));
             PrintWriter pw = new PrintWriter(new FileWriter(out))) {
            String[] header = br.readLine().split(",");
            int[] idx = new int[wanted.size()];
            for (int k = 0; k < wanted.size(); k++) {
                idx[k] = -1;
                for (int j = 0; j < header.length; j++) {
                    String h = header[j].replace("\"", "").replaceAll("\\s*[(\\[].*$", "").trim();
                    if (h.equals(wanted.get(k))) {
                        idx[k] = j;
                    }
                }
                if (idx[k] < 0) {
                    throw new RuntimeException("export_stats: column '" + wanted.get(k) + "' not in " + in);
                }
            }
            pw.println(String.join(",", wanted));
            String line;
            while ((line = br.readLine()) != null) {
                String[] cells = line.split(",");
                double xd = Double.parseDouble(cells[idx[0]].trim()), yd = Double.parseDouble(cells[idx[1]].trim());
                if (xd < -2.0 || xd > 10.0 || yd < -3.0 || yd > 3.0) { continue; }
                StringBuilder row = new StringBuilder();
                for (int k = 0; k < idx.length; k++) {
                    if (k > 0) {
                        row.append(",");
                    }
                    row.append(cells[idx[k]].trim());
                }
                pw.println(row);
            }
        } catch (IOException e) {
            throw new RuntimeException("export_stats: failed to write " + out, e);
        }
    }
}
