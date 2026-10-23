package macro;

import java.io.*;
import java.util.*;

import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;
import star.meshing.*;

public class cube_re300_run_F extends StarMacro {

  private static final double DT = 0.038;
  private static final double T_END = 1000.0;
  private static final String TAG = "F";
  private static final double BASE = 0.385;
  private static final String CUBE_SIZE = "0.0240625*${D}";
  private static final String WAKE_SIZE = "0.048125*${D}";
  private static final String DIR = "";

  private Simulation sim;

  private Report getOrCreate(String cls, String name) {
    for (Report r : sim.getReportManager().getObjects()) {
      if (r.getPresentationName().equals(name)) {
        return r;
      }
    }
    Report r = sim.getReportManager().create(cls);
    r.setPresentationName(name);
    return r;
  }

  private double[] readTimes() {
    double phys = getOrCreate("star.common.PhysicsContinuumPhysicalTimeReport", "cube_re300_phys_time").getReportMonitorValue();
    double wall = getOrCreate("star.common.CumulativeElapsedTimeReport", "cube_re300_elapsed").getReportMonitorValue();
    double cpu = getOrCreate("star.common.CumulativeCpuTimeReport", "cube_re300_cpu").getReportMonitorValue();
    return new double[] { phys, wall, cpu };
  }

  @Override
  public void execute() {
    sim = getActiveSimulation();

    Units m = ((Units) sim.getUnitsManager().getObject("m"));
    Units s = ((Units) sim.getUnitsManager().getObject("s"));

    AutoMeshOperation mesh =
      ((AutoMeshOperation) sim.get(MeshOperationManager.class).getObject("mesh_cube"));
    mesh.getDefaultValues().get(BaseSize.class).setValueAndUnits(BASE, m);

    SurfaceCustomMeshControl cube =
      ((SurfaceCustomMeshControl) mesh.getCustomMeshControls().getObject("cube_refinement"));
    ((ScalarPhysicalQuantity) cube.getCustomValues().get(PartsTargetSurfaceSize.class).getAbsoluteSizeValue()).setDefinition(CUBE_SIZE);
    ((ScalarPhysicalQuantity) cube.getCustomValues().get(PartsMinimumSurfaceSize.class).getAbsoluteSizeValue()).setDefinition(CUBE_SIZE);

    VolumeCustomMeshControl wake =
      ((VolumeCustomMeshControl) mesh.getCustomMeshControls().getObject("wake_refinement"));
    ((ScalarPhysicalQuantity) wake.getCustomValues().get(VolumeControlSize.class).getAbsoluteSizeValue()).setDefinition(WAKE_SIZE);

    sim.get(MeshPipelineController.class).generateVolumeMesh();

    double[] t0 = readTimes();
    if (Math.abs(t0[0] - 725.0) > 1.0) {
      throw new RuntimeException("cube_re300_run_" + TAG + ": initial physical time " + t0[0] + " is not 725; aborting.");
    }

    ImplicitUnsteadySolver solver =
      ((ImplicitUnsteadySolver) sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class));
    solver.getTimeStep().setValueAndUnits(DT, s);

    InnerIterationStoppingCriterion inner =
      ((InnerIterationStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Inner Iterations"));
    inner.getMaxIterations().getQuantity().setValue(10.0);

    PhysicalTimeStoppingCriterion tmax =
      ((PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time"));
    tmax.getMaximumTime().setValueAndUnits(T_END, s);

    sim.getSimulationIterator().run();

    double[] t1 = readTimes();
    try (PrintWriter w = new PrintWriter(new FileWriter(DIR + "cube_re300_timing_" + TAG + ".txt"))) {
      w.println("run=" + TAG + " dt=" + DT);
      w.println("start phys=" + t0[0] + " elapsed_s=" + t0[1] + " cpu_s=" + t0[2]);
      w.println("end   phys=" + t1[0] + " elapsed_s=" + t1[1] + " cpu_s=" + t1[2]);
      w.println("delta phys=" + (t1[0] - t0[0]) + " elapsed_s=" + (t1[1] - t0[1]) + " cpu_s=" + (t1[2] - t0[2]));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    sim.saveState(DIR + "cube_re300_" + TAG + "_t1000.sim");
  }
}
