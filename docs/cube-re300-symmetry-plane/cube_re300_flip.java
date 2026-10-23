// cube_re300_flip.java: from cube_re0300.sim (steady Re 300, it 15000), keep the steady run in 10-it batches
// until the side-force angle atan2(cy,cl), on the mean of the last 7 batches, is within 5 deg of -90
// (or 3000 extra it), save; then switch to transient with the steady-to-unsteady set-up and run 300 t*U/D.
// Run on a copy: starccm+ -batch cube_re300_flip.java cube_re0300.sim. The loaded .sim is never saved over;
// outputs go next to it (session dir). Assumes D = 1 m, U = 1 m/s, so 1 s = 1 D/U.
package macro;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;

public class cube_re300_flip extends StarMacro {

  private static final int BATCH = 10;
  private static final int MEAN_BATCHES = 7;
  private static final int MAX_EXTRA_IT = 3000;
  private static final double TARGET_DEG = -90.0;
  private static final double TOL_DEG = 5.0;
  private static final double DT = 0.05;
  private static final double T_END = 300.0;
  private static final String[] MONITORS = {"cd_monitor", "cl_monitor", "cy Monitor"};

  private Simulation sim;

  @Override
  public void execute() {
    sim = getActiveSimulation();
    ensureCyMonitor();
    runSteady();
    runTransient();
  }

  // cube_re0300.sim predates the steady-to-unsteady set-up, which is where "cy Monitor" was created.
  private void ensureCyMonitor() {
    if (sim.getMonitorManager().hasObject("cy Monitor") != null) {
      return;
    }
    Report cy = sim.getReportManager().getReport("cy");
    sim.getMonitorManager().createMonitors(new ArrayList<>(Arrays.<Report>asList(cy)),
      new PlotCreationInfo(PlotCreationInfo.CreatePlotChoice.SINGLE_PLOT, " Plot"));
    sim.println("cube_re300_flip: created cy Monitor");
  }

  // ---- part 1: steady, 10-it batches, stop on angle or on MAX_EXTRA_IT.
  private void runSteady() {
    StepStoppingCriterion maxSteps =
      (StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
    maxSteps.setIsUsed(false);

    SimulationIterator it = sim.getSimulationIterator();
    int start = it.getCurrentIteration();
    double[] clBuf = new double[MEAN_BATCHES];
    double[] cyBuf = new double[MEAN_BATCHES];
    int batches = 0;
    String reason = "max_extra_it";
    double angle = Double.NaN;

    StringBuilder csv = new StringBuilder("iteration,cd,cl,cy\n");
    File csvFile = new File(sim.getSessionDirFile(), "flip_steady.csv");

    while (it.getCurrentIteration() - start < MAX_EXTRA_IT) {
      it.step(BATCH, true);
      int iter = it.getCurrentIteration();
      double cd = value("cd");
      double cl = value("cl");
      double cy = value("cy");
      csv.append(iter).append(',').append(cd).append(',').append(cl).append(',').append(cy).append('\n');

      clBuf[batches % MEAN_BATCHES] = cl;
      cyBuf[batches % MEAN_BATCHES] = cy;
      batches++;
      if (batches < MEAN_BATCHES) {
        continue;
      }
      double clMean = 0.0;
      double cyMean = 0.0;
      for (int i = 0; i < MEAN_BATCHES; i++) {
        clMean += clBuf[i];
        cyMean += cyBuf[i];
      }
      clMean /= MEAN_BATCHES;
      cyMean /= MEAN_BATCHES;
      angle = Math.toDegrees(Math.atan2(cyMean, clMean));
      if (batches % 10 == 0) {
        sim.println("cube_re300_flip: it " + iter + " angle " + angle + " deg");
      }
      if (Math.abs(wrap(angle - TARGET_DEG)) <= TOL_DEG) {
        reason = "angle";
        break;
      }
    }
    writeTextFile(csvFile, csv.toString());
    sim.println("cube_re300_flip: steady stop (" + reason + ") at it " + it.getCurrentIteration()
      + ", extra " + (it.getCurrentIteration() - start) + ", angle " + angle + " deg");

    maxSteps.setIsUsed(true);
    sim.saveState(new File(sim.getSessionDirFile(), "cube_re0300_flip_steady.sim").getAbsolutePath());
  }

  // ---- part 2: transient, the steady-to-unsteady set-up, one CSV row per time step.
  private void runTransient() {
    PhysicsContinuum physics =
      (PhysicsContinuum) sim.getContinuumManager().getContinuum("fluid_physics");
    physics.disableModel(physics.getModelManager().getModel(SteadyModel.class));
    physics.enable(ImplicitUnsteadyModel.class);

    Units s = (Units) sim.getUnitsManager().getObject("s");
    ImplicitUnsteadySolver solver =
      (ImplicitUnsteadySolver) sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class);
    solver.getTimeStep().setValueAndUnits(DT, s);
    solver.getTimeDiscretizationOption().setSelected(TimeDiscretizationOption.Type.SECOND_ORDER);

    InnerIterationStoppingCriterion inner =
      (InnerIterationStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Inner Iterations");
    inner.getMaxIterations().getQuantity().setValue(10.0);
    PhysicalTimeStoppingCriterion tmax =
      (PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time");
    tmax.getMaximumTime().setValueAndUnits(T_END, s);
    StepStoppingCriterion maxSteps =
      (StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
    maxSteps.setIsUsed(false);

    for (String name : MONITORS) {
      ReportMonitor m = (ReportMonitor) sim.getMonitorManager().getMonitor(name);
      StarUpdate u = m.getStarUpdateStageable().getStarUpdate();
      u.getUpdateModeOption().setSelected(StarUpdateModeOption.Type.TIMESTEP);
      u.getTimeStepUpdateFrequency().getTimeStepFrequencyQuantity().getQuantity().setValue(1.0);
    }

    SimulationIterator it = sim.getSimulationIterator();
    int steps = (int) Math.round(T_END / DT);
    StringBuilder csv = new StringBuilder("tU_D,cd,cl,cy\n");
    File csvFile = new File(sim.getSessionDirFile(), "flip_transient.csv");
    for (int k = 1; k <= steps; k++) {
      it.step(1, true);
      csv.append(k * DT).append(',').append(value("cd")).append(',').append(value("cl"))
        .append(',').append(value("cy")).append('\n');
      if (k % 500 == 0) {
        writeTextFile(csvFile, csv.toString());
        sim.println("cube_re300_flip: tU_D " + (k * DT));
      }
    }
    writeTextFile(csvFile, csv.toString());
    sim.saveState(new File(sim.getSessionDirFile(), "cube_re0300_flip_t300.sim").getAbsolutePath());
  }

  // Report.monitoredValue() returns SI (run_macro.java, bench-verified).
  private double value(String reportName) {
    return sim.getReportManager().getReport(reportName).monitoredValue();
  }

  private static double wrap(double deg) {
    while (deg > 180.0) {
      deg -= 360.0;
    }
    while (deg < -180.0) {
      deg += 360.0;
    }
    return deg;
  }

  private void writeTextFile(File f, String content) {
    BufferedWriter w = null;
    try {
      w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
      w.write(content);
    } catch (Exception e) {
      throw new RuntimeException("cube_re300_flip: could not write '" + f.getName() + "': " + e.getMessage());
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
