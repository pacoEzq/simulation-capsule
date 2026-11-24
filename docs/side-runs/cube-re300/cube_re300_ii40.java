// cube_re300_ii40.java: from t = 300, 40 inner iterations, monitors on Iteration trigger, run to t = 310, save under a new name.
package macro;

import java.util.*;
import star.common.*;
import star.base.neo.*;
import star.base.report.*;
import star.flow.*;

public class cube_re300_ii40 extends StarMacro {

  @Override
  public void execute() {
    Simulation sim = getActiveSimulation();
    Units s = (Units) sim.getUnitsManager().getObject("s");
    InnerIterationStoppingCriterion inner =
      (InnerIterationStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Inner Iterations");
    inner.getMaxIterations().getQuantity().setValue(40);
    for (String name : new String[] {"cd_monitor", "cl_monitor", "cy Monitor"}) {
      ReportMonitor m = (ReportMonitor) sim.getMonitorManager().getMonitor(name);
      m.getStarUpdateStageable().getStarUpdate().getUpdateModeOption().setSelected(StarUpdateModeOption.Type.ITERATION);
    }
    PhysicalTimeStoppingCriterion tmax =
      (PhysicalTimeStoppingCriterion) sim.getSolverStoppingCriterionManager().getSolverStoppingCriterion("Maximum Physical Time");
    tmax.getMaximumTime().setValueAndUnits(310.0, s);
    sim.getSimulationIterator().run();
    sim.saveState("cube_re0300_unsteady_ii40.sim");
  }
}
