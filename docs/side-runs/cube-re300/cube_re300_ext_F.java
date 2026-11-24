package macro;

import java.io.*;
import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;

public class cube_re300_ext_F extends StarMacro {

  private static final double T_START = 1000.0;
  private static final double T_END = 1150.0;
  private static final String TAG = "F";
  private static final String DIR = "";

  private Simulation sim;

  private Report find(String name) {
    for (Report r : sim.getReportManager().getObjects()) {
      if (r.getPresentationName().equals(name)) {
        return r;
      }
    }
    throw new RuntimeException("cube_re300_ext_" + TAG + ": report " + name + " not found.");
  }

  private double[] readTimes() {
    return new double[] {
      find("cube_re300_phys_time").getReportMonitorValue(),
      find("cube_re300_elapsed").getReportMonitorValue(),
      find("cube_re300_cpu").getReportMonitorValue() };
  }

  @Override
  public void execute() {
    sim = getActiveSimulation();
    Units s = ((Units) sim.getUnitsManager().getObject("s"));

    double[] t0 = readTimes();
    if (Math.abs(t0[0] - T_START) > 1.0) {
      throw new RuntimeException("cube_re300_ext_" + TAG + ": initial physical time " + t0[0] + " is not " + T_START + "; aborting.");
    }

    PhysicalTimeStoppingCriterion tmax =
      ((PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time"));
    tmax.getMaximumTime().setValueAndUnits(T_END, s);

    sim.getSimulationIterator().run();

    double[] t1 = readTimes();
    try (PrintWriter w = new PrintWriter(new FileWriter(DIR + "cube_re300_timing_" + TAG + "_ext.txt"))) {
      w.println("run=" + TAG + "_ext");
      w.println("start phys=" + t0[0] + " elapsed_s=" + t0[1] + " cpu_s=" + t0[2]);
      w.println("end   phys=" + t1[0] + " elapsed_s=" + t1[1] + " cpu_s=" + t1[2]);
      w.println("delta phys=" + (t1[0] - t0[0]) + " elapsed_s=" + (t1[1] - t0[1]) + " cpu_s=" + (t1[2] - t0[2]));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    sim.saveState(DIR + "cube_re300_" + TAG + "_t1150.sim");
  }
}
