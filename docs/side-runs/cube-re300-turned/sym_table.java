// sym_table.java: mid-plane table of the time-mean streamwise vorticity and monitor histories of the
// cube at Re 300. Opens SYM_SIM (<tag>_mean.sim written by sym_mean.java), never saves it. Builds a
// PlaneSection x = SYM_SLICE_X (2 D by default) on region fluid, an XyzInternalTable of "Mean of omega_x
// D/U" on it, extracts and exports <SYM_OUT>/<SYM_TAG>_slice_x2.csv. Then exports the history of the
// monitors cd_monitor, cl_monitor and "cy Monitor" to <SYM_OUT>/<SYM_TAG>_<monitor>.csv (monitor name with
// every character outside [A-Za-z0-9_.-] replaced by '_': cd_monitor, cl_monitor, cy_Monitor). Each file
// is checked to exist and its line count goes to the log.
// Launch: SYM_SIM=<tag>_mean.sim SYM_TAG=<tag> SYM_OUT=<dir> starccm+ -batch sym_table.java -np 1
// Any exception is printed as "TABLE ERROR <class>: <message>" and rethrown.
package macro;

import java.io.*;
import java.util.*;
import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.vis.*;

public class sym_table extends StarMacro {

  private static final String SIM = envString("SYM_SIM", "");
  private static final String TAG = envString("SYM_TAG", "");
  private static final String OUT = envString("SYM_OUT", System.getProperty("user.dir"));

  private static final double D = 1.0;
  private static final String REGION = "fluid";
  private static final double SLICE_X = Double.parseDouble(envString("SYM_SLICE_X", "2.0")) * D;
  private static final String SLICE_PART = "sym_slice_x";
  private static final String TABLE = "sym_slice_x_table";
  // Presentation name of the monitor field mean of sym_omx written by sym_mean.java.
  private static final String MEAN_LABEL = "Mean of omega_x D/U";
  private static final String[] MONITORS = {"cd_monitor", "cl_monitor", "cy Monitor"};

  private Simulation sim;

  public void execute() {
    try {
      table();
    } catch (RuntimeException e) {
      String msg = "TABLE ERROR " + e.getClass().getName() + ": " + e.getMessage();
      if (sim != null) {
        sim.println(msg);
      } else {
        System.out.println(msg);
      }
      throw e;
    }
  }

  private void table() {
    if (SIM.isEmpty() || TAG.isEmpty()) {
      throw new RuntimeException("sym_table: set SYM_SIM to the <tag>_mean.sim and SYM_TAG to the case tag");
    }
    File outDir = new File(OUT);
    if (!outDir.isDirectory() && !outDir.mkdirs()) {
      throw new RuntimeException("sym_table: cannot create SYM_OUT '" + OUT + "'");
    }
    sim = new Simulation(new File(SIM).getAbsolutePath());
    sim.println("sym_table: " + new File(SIM).getName() + " opened, tag " + TAG + ", slice x = " + SLICE_X);

    Region region = sim.getRegionManager().getRegion(REGION);
    PlaneSection slice = slicePart(region);
    FieldFunction mean = meanFunction();

    XyzInternalTable t = sim.getTableManager().createTable("star.common.XyzInternalTable");
    t.setPresentationName(TABLE);
    t.getParts().setQuery(null);
    t.getParts().setObjects(slice);
    t.setFieldFunctions(Collections.singletonList(mean));
    t.extract();
    File csv = new File(outDir, TAG + "_slice_x2.csv");
    t.export(csv.getAbsolutePath());
    check(csv);

    for (String name : MONITORS) {
      Monitor m = findMonitor(name);
      File f = new File(outDir, TAG + "_" + name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".csv");
      m.export(f.getAbsolutePath());
      check(f);
    }
    sim.println("sym_table: done, .sim not saved");
  }

  private PlaneSection slicePart(Region region) {
    PlaneSection slice = sim.getPartManager().createPlaneSection();
    slice.setPresentationName(SLICE_PART);
    slice.setInputPartsCollection(Collections.singletonList(region));
    slice.setOrigin(new DoubleVector(new double[] {SLICE_X, 0.0, 0.0}));
    slice.setOrientation(new DoubleVector(new double[] {1.0, 0.0, 0.0}));
    return slice;
  }

  private FieldFunction meanFunction() {
    for (FieldFunction ff : sim.getFieldFunctionManager().getObjects()) {
      if (!(ff instanceof UserFieldFunction) && ff.getPresentationName().equals(MEAN_LABEL)) {
        sim.println("sym_table: table of '" + ff.getPresentationName() + "' (" + ff.getFunctionName() + ")");
        return ff;
      }
    }
    throw new RuntimeException("sym_table: no '" + MEAN_LABEL + "' in this .sim (run sym_mean.java first)");
  }

  // Looked up by presentation name in the list, as findFunction (no placeholder surprises).
  private Monitor findMonitor(String name) {
    List<String> seen = new ArrayList<String>();
    for (Monitor m : sim.getMonitorManager().getObjects()) {
      if (m.getPresentationName().equals(name)) {
        return m;
      }
      seen.add(m.getPresentationName());
    }
    throw new RuntimeException("sym_table: no monitor '" + name + "' in this .sim; monitors " + seen);
  }

  private void check(File f) {
    if (!f.isFile()) {
      throw new RuntimeException("sym_table: " + f.getName() + " not written");
    }
    int n = 0;
    try {
      BufferedReader r = new BufferedReader(new FileReader(f));
      try {
        while (r.readLine() != null) {
          n++;
        }
      } finally {
        r.close();
      }
    } catch (IOException e) {
      throw new RuntimeException("sym_table: cannot read " + f.getName() + ": " + e.getMessage());
    }
    sim.println("sym_table: " + f.getName() + " " + n + " lines");
  }

  private static String envString(String name, String dflt) {
    String v = System.getenv(name);
    return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
  }
}
