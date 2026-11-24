// lateral_probe.java -- read-only extraction: opens cube_re0100.sim, cube_re0300.sim and
// cube_re3000.sim in turn (relative names), creates a lateral Line Probe + Internal Table in
// each, exports a CSV, and closes WITHOUT saving. Never touches the .sim files on disk.
// The region is named "fluid", as in the published cube capsules.
// All created objects carry the LLM_ prefix as requested.
package macro;

import java.io.*;
import java.util.*;
import star.base.neo.*;
import star.common.*;
import star.vis.LinePart;

public class lateral_probe extends StarMacro {

  // ---- edit here ----
  static final String REGION_NAME = "fluid";
  static final String[] SIM_FILES = {"cube_re0100.sim", "cube_re0300.sim", "cube_re3000.sim"};
  static final String PROBE_NAME = "LLM_probe_lateral_x-0p5";
  static final String TABLE_NAME = "LLM_table_lateral";
  static final double[] FROM = {-0.5, 0.0, 0.5};
  static final double[] TO = {-0.5, 0.0, 5.0};
  static final int POINTS = 181;
  // -------------------

  StringBuilder log = new StringBuilder();

  @Override
  public void execute() {
    for (String simFile : SIM_FILES) {
      processOne(simFile);
    }
    writeRunLog();
  }

  void processOne(String simFile) {
    String reNNNN = reynoldsTag(simFile);
    Simulation sim = new Simulation(simFile);
    try {
      try {
        log.append("== ").append(simFile).append(" ==\n");

        Region region = region(sim, REGION_NAME);

        // Read-before-write: getObject/getPart/getTable throw on a missing name instead of
        // returning null (confirmed at runtime for PartManager). Use hasObject/hasTable, which
        // return the object or null, with three-way logic: null -> create; right class -> reuse;
        // wrong class -> throw (a pre-existing LLM_ object of the wrong type is a real error).
        Part existingPart = sim.getPartManager().hasObject(PROBE_NAME);
        LinePart probe;
        if (existingPart == null) {
          probe = sim.getPartManager().createLinePart(
              new NeoObjectVector(new Object[] {region}),
              new DoubleVector(FROM),
              new DoubleVector(TO),
              POINTS);
          probe.setPresentationName(PROBE_NAME);
          log.append("created part: ").append(PROBE_NAME).append("\n");
        } else if (existingPart instanceof LinePart) {
          probe = (LinePart) existingPart;
          log.append("reused part: ").append(PROBE_NAME).append("\n");
        } else {
          throw new RuntimeException("lateral_probe: object '" + PROBE_NAME
              + "' exists in " + simFile + " but is not a LinePart.");
        }

        FieldFunction zD = fieldFunction(sim, "z_D");
        FieldFunction uU = fieldFunctionEither(sim, "u_over_U", "u_U");

        // Exported column headers come from the attached FieldFunctions' own names, so the
        // velocity column reads u_over_U or u_U, whichever resolved; see fieldFunctionEither().
        Table existingTable = sim.getTableManager().hasTable(TABLE_NAME);
        XyzInternalTable table;
        if (existingTable == null) {
          table = sim.getTableManager().createTabularObject(XyzInternalTable.class);
          table.setPresentationName(TABLE_NAME);
          log.append("created table: ").append(TABLE_NAME);
        } else if (existingTable instanceof XyzInternalTable) {
          table = (XyzInternalTable) existingTable;
          log.append("reused table: ").append(TABLE_NAME);
        } else {
          throw new RuntimeException("lateral_probe: object '" + TABLE_NAME
              + "' exists in " + simFile + " but is not an XyzInternalTable.");
        }
        table.getParts().addPart(probe);
        table.setFieldFunctions(new ArrayList<FieldFunction>(Arrays.asList(zD, uU)));
        log.append(" (columns from field functions: ").append(zD.getPresentationName())
            .append(", ").append(uU.getPresentationName()).append(")\n");

        table.extract();

        String outName = "lateral_re" + reNNNN + ".csv";
        File out = new File(sim.getSessionDir(), outName);
        table.export(out.toString(), ",");
        if (!out.isFile() || out.length() == 0) {
          throw new RuntimeException("lateral_probe: export failed or empty: " + outName);
        }
        log.append("exported: ").append(outName).append("\n");
      } finally {
        // NO saveState call anywhere: Disconnect leaves the server running without persisting changes.
        sim.close(star.base.neo.ServerConnection.CloseOption.Disconnect);
      }
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  // A missing region name may throw or return null depending on the manager; try/catch
  // treats either outcome as missing.
  Region region(Simulation sim, String name) {
    Object o;
    try {
      o = sim.getRegionManager().getObject(name);
    } catch (RuntimeException e) {
      o = null;
    }
    if (o == null) {
      throw new RuntimeException("lateral_probe: missing region '" + name + "'.");
    }
    if (!(o instanceof Region)) {
      throw new RuntimeException("lateral_probe: object '" + name + "' is not a Region.");
    }
    return (Region) o;
  }

  FieldFunction fieldFunction(Simulation sim, String name) {
    FieldFunction ff = sim.getFieldFunctionManager().hasObject(name);
    if (ff == null) {
      throw new RuntimeException("lateral_probe: missing field function '" + name + "'.");
    }
    return ff;
  }

  FieldFunction fieldFunctionEither(Simulation sim, String preferred, String fallback) {
    FieldFunction ff = sim.getFieldFunctionManager().hasObject(preferred);
    if (ff != null) {
      return ff;
    }
    ff = sim.getFieldFunctionManager().hasObject(fallback);
    if (ff != null) {
      return ff;
    }
    throw new RuntimeException("lateral_probe: missing field function (tried '" + preferred
        + "' and '" + fallback + "').");
  }

  // "cube_re0100.sim" -> "0100"
  String reynoldsTag(String simFile) {
    String base = simFile.replace(".sim", "");
    int i = base.lastIndexOf("re");
    if (i < 0) {
      throw new RuntimeException("lateral_probe: cannot parse Reynolds tag from '" + simFile + "'.");
    }
    return base.substring(i + 2);
  }

  void writeRunLog() {
    try {
      FileWriter w = new FileWriter(new File("run_log.txt"));
      w.write(log.toString());
      w.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
