// cube_re300_flip_extend.java: continue cube_re0300_flip_t300.sim from t*U/D 300 to 725, one CSV row per step.
// Same calls as cube_re300_flip.java. Run: starccm+ -batch cube_re300_flip_extend.java cube_re0300_flip_t300.sim
package macro;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import star.common.*;
import star.base.report.*;

public class cube_re300_flip_extend extends StarMacro {

  private static final double DT = 0.05;
  private static final double T_START = 300.0;
  private static final double T_END = 725.0;
  private Simulation sim;

  @Override
  public void execute() {
    sim = getActiveSimulation();
    Units s = (Units) sim.getUnitsManager().getObject("s");
    PhysicalTimeStoppingCriterion tmax =
      (PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time");
    tmax.getMaximumTime().setValueAndUnits(T_END, s);
    StepStoppingCriterion maxSteps =
      (StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
    maxSteps.setIsUsed(false);

    SimulationIterator it = sim.getSimulationIterator();
    int k0 = (int) Math.round(T_START / DT);
    int k1 = (int) Math.round(T_END / DT);
    StringBuilder csv = new StringBuilder("tU_D,cd,cl,cy\n");
    File csvFile = new File(sim.getSessionDirFile(), "flip_transient_ext.csv");
    for (int k = k0 + 1; k <= k1; k++) {
      it.step(1, true);
      csv.append(k * DT).append(',').append(value("cd")).append(',').append(value("cl"))
        .append(',').append(value("cy")).append('\n');
      if (k % 500 == 0) {
        writeTextFile(csvFile, csv.toString());
        sim.println("cube_re300_flip_extend: tU_D " + (k * DT));
      }
    }
    writeTextFile(csvFile, csv.toString());
    sim.saveState(new File(sim.getSessionDirFile(), "cube_re0300_flip_t725.sim").getAbsolutePath());
  }

  private double value(String reportName) {
    return sim.getReportManager().getReport(reportName).monitoredValue();
  }

  private void writeTextFile(File f, String content) {
    BufferedWriter w = null;
    try {
      w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
      w.write(content);
    } catch (Exception e) {
      throw new RuntimeException("cube_re300_flip_extend: could not write '" + f.getName() + "': " + e.getMessage());
    } finally {
      if (w != null) {
        try { w.close(); } catch (Exception ignored) { }
      }
    }
  }
}
