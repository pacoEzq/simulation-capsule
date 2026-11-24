// sym_mean.java: continues a saved transient solution of the cube at Re 300 for SYM_UNITS time
// units (20 by default, D/U) with a field mean monitor of sym_omx = omega_x D/U on region fluid, every
// time step from the first new step, and saves the result as <SYM_OUT>/<SYM_TAG>_mean.sim. The opened .sim
// is never overwritten. The time step is read from the solver (0.05 D/U in all five cases).
// Launch: SYM_SIM=<.sim> SYM_TAG=<tag> SYM_OUT=<dir> starccm+ -batch sym_mean.java -np <n>
// Any exception is printed as "MEAN ERROR <class>: <message>" and rethrown.
package macro;

import java.io.File;
import java.util.*;
import star.common.*;
import star.base.neo.*;
import star.base.report.*;

public class sym_mean extends StarMacro {

  private static final String SIM = envString("SYM_SIM", "");
  private static final String TAG = envString("SYM_TAG", "");
  private static final String OUT = envString("SYM_OUT", System.getProperty("user.dir"));
  private static final double UNITS = Double.parseDouble(envString("SYM_UNITS", "20"));

  private static final double D = 1.0;
  private static final double U = 1.0;
  private static final String REGION = "fluid";
  private static final String VORTICITY_BUILTIN = "VorticityVector";
  private static final String OMX_FF = "sym_omx";
  private static final String OMX_LABEL = "omega_x D/U";
  private static final String MEAN_MONITOR = "sym_omx_mean";

  private Simulation sim;

  public void execute() {
    try {
      run();
    } catch (RuntimeException e) {
      String msg = "MEAN ERROR " + e.getClass().getName() + ": " + e.getMessage();
      if (sim != null) {
        sim.println(msg);
      } else {
        System.out.println(msg);
      }
      throw e;
    }
  }

  private void run() {
    if (SIM.isEmpty() || TAG.isEmpty()) {
      throw new RuntimeException("sym_mean: set SYM_SIM to the transient .sim and SYM_TAG to the case tag");
    }
    File outDir = new File(OUT);
    if (!outDir.isDirectory() && !outDir.mkdirs()) {
      throw new RuntimeException("sym_mean: cannot create SYM_OUT '" + OUT + "'");
    }
    File saved = new File(outDir, TAG + "_mean.sim");
    if (saved.isFile()) {
      throw new RuntimeException("sym_mean: " + saved.getName() + " already exists in SYM_OUT");
    }
    sim = new Simulation(new File(SIM).getAbsolutePath());
    Region region = sim.getRegionManager().getRegion(REGION);
    FieldFunction omx = userFunction(OMX_FF, OMX_LABEL, "$${" + VORTICITY_BUILTIN + "}[0]*" + (D / U),
      VORTICITY_BUILTIN);

    ImplicitUnsteadySolver solver =
      (ImplicitUnsteadySolver) sim.getSolverManager().getSolver(ImplicitUnsteadySolver.class);
    double dt = solver.getTimeStep().getSIValue();
    int steps = (int) Math.round(UNITS * D / U / dt);
    double t0 = sim.getSolution().getPhysicalTime();
    int step0 = (int) Math.round(t0 / dt);
    sim.println("sym_mean: " + new File(SIM).getName() + " opened, tag " + TAG + ", t " + t0 + ", step "
      + step0 + ", dt " + dt + ", " + steps + " steps to run");

    MonitorManager mm = sim.getMonitorManager();
    FieldMeanMonitor mean;
    if (mm.has(MEAN_MONITOR)) {
      mean = (FieldMeanMonitor) mm.getMonitor(MEAN_MONITOR);
    } else {
      mean = mm.createMonitor(FieldMeanMonitor.class);
      mean.setPresentationName(MEAN_MONITOR);
    }
    mean.getParts().setObjects(region);
    mean.setFieldFunction(omx);
    StarUpdate update = mean.getStarUpdate();
    update.getUpdateModeOption().setSelected(StarUpdateModeOption.Type.TIMESTEP);
    TimeStepUpdateFrequency frequency = update.getTimeStepUpdateFrequency();
    frequency.setTimeSteps(1);
    frequency.setStart(step0 + 1);

    Units s = (Units) sim.getUnitsManager().getObject("s");
    PhysicalTimeStoppingCriterion tmax =
      (PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time");
    tmax.getMaximumTime().setValueAndUnits(t0 + (steps + 1) * dt, s);
    StepStoppingCriterion maxSteps =
      (StepStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Steps");
    maxSteps.setIsUsed(false);

    SimulationIterator it = sim.getSimulationIterator();
    for (int k = 1; k <= steps; k++) {
      it.step(1, true);
      if (k % 100 == 0) {
        sim.println("sym_mean: step " + k + " of " + steps + ", t " + sim.getSolution().getPhysicalTime());
      }
    }
    String fn = meanFunctionName(omx);
    sim.println("sym_mean: mean field function '" + fn + "' over " + steps + " steps, t "
      + t0 + " to " + sim.getSolution().getPhysicalTime());
    sim.saveState(saved.getAbsolutePath());
    sim.println("sym_mean: saved " + saved.getName());
  }

  // The user function may already exist in a .sim that went through sym_scene; reuse it then.
  private FieldFunction userFunction(String name, String label, String definition, String builtin) {
    FieldFunction existing = findFunction(name);
    if (existing != null) {
      sim.println("sym_mean: " + name + " already in the .sim, reused");
      return existing;
    }
    if (findFunction(builtin) == null) {
      throw new RuntimeException("sym_mean: field function '" + builtin + "' not found in this .sim");
    }
    UserFieldFunction ff = sim.getFieldFunctionManager().createFieldFunction();
    ff.getTypeOption().setSelected(FieldFunctionTypeOption.Type.SCALAR);
    ff.setPresentationName(label);
    ff.setFunctionName(name);
    ff.setDefinition(definition);
    sim.println("sym_mean: " + name + " ('" + label + "') = " + definition);
    return ff;
  }

  // Function name of the field function the mean monitor generates (its
  // presentation name is "Mean of " + the source's presentation name).
  private String meanFunctionName(FieldFunction src) {
    for (FieldFunction ff : sim.getFieldFunctionManager().getObjects()) {
      if (!(ff instanceof UserFieldFunction) && ff.getPresentationName().equals("Mean of " + src.getPresentationName())) {
        return ff.getFunctionName();
      }
    }
    StringBuilder sb = new StringBuilder();
    for (FieldFunction ff : sim.getFieldFunctionManager().getObjects()) {
      if (ff.getPresentationName().contains("Mean")) {
        sb.append(" '").append(ff.getPresentationName()).append("' = ").append(ff.getFunctionName());
      }
    }
    return "(not found; candidates:" + sb + ")";
  }

  // getFunction(<missing name>) returns a placeholder ("<Select Function>") on 2606, not null: look the
  // function name up in the manager's list instead.
  private FieldFunction findFunction(String functionName) {
    for (FieldFunction ff : sim.getFieldFunctionManager().getObjects()) {
      if (functionName.equals(ff.getFunctionName())) {
        return ff;
      }
    }
    return null;
  }

  private static String envString(String name, String dflt) {
    String v = System.getenv(name);
    return (v == null || v.trim().isEmpty()) ? dflt : v.trim();
  }
}
